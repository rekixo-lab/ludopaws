extends Node
## Short game/UI sound facade (autoload `GameAudio`). Every sound resolves its authored
## family first, rotating through the takes that exist, then falls back to the matching
## procedural sound. Presentation-only: never touches game state.
## Ports GameSoundFeedback.kt, LudoPawsAudioAssetPlayer (playSfx/playAuthoredSfx/ForEvent)
## and LudoPawsSoundPool (8 streams, priority 0..10, volume 0..1, rate 0.65..1.45).

const Catalog = preload("res://scripts/game/audio/audio_catalog.gd")
const Synth = preload("res://scripts/game/audio/procedural_audio.gd")
const VoiceProfile = preload("res://scripts/game/audio/reaction_audio_profile.gd")
const Characters = preload("res://scripts/game/characters/catalog.gd")
const MAX_CAPTURE_VOCALS := 2
const VOCAL_DUCK_MS := 650
## GameMusicController: the title theme loops at a low bed level, ducks under animal calls.
const MUSIC_PATH := "audio/music/Firefly-audio-Title-Ludo-Paws-Ro.wav"
const MUSIC_VOLUME := 0.16
const VOICE_DUCK_VOLUME := 0.075

const MAX_STREAMS := 8
const PRIORITY_UI := 0
const PRIORITY_MOVEMENT := 1
const PRIORITY_JUMP := 2
const PRIORITY_SOFT_EVENT := 3
const PRIORITY_ROLL := 4
const PRIORITY_EVENT := 5
const PRIORITY_HIGH := 7
const PRIORITY_CRITICAL := 10
const SETTINGS_PATH := "user://meadow_ui.cfg"

## Every accepted sound: [source ("asset:<path>" | "synth:<preset>"), volume, priority]. Tests read it.
var log: Array = []
## Override for tests: func(key: String, fallback) -> Variant.
var setting_source: Callable
var _players: Array = []   # [{player: AudioStreamPlayer, priority, started}]
var _next_variant := {}    # nextSfxVariantByFamily
var _streams := {}
var _settings := ConfigFile.new()
var _settings_read_ms := -100000
var _voice_queue: Array = []  # [{due, play: Callable}] (LudoPawsVoicePlayer.schedule)
## Called with a duration when an animal call should duck the music (GameMusicController.duckForVoice).
var on_voice: Callable
var music: AudioStreamPlayer
var _foreground := true
var _voice_duck_until := 0
var _music_check_ms := -100000

func _ready() -> void:
	# UiClickSoundInstaller: every button-like control clicks; the gameplay board is not a button.
	get_tree().node_added.connect(_on_node_added)
	on_voice = duck_for_voice
	music = AudioStreamPlayer.new()
	music.name = "Music"
	add_child(music)
	for i in MAX_STREAMS:
		var player := AudioStreamPlayer.new()
		player.bus = &"Master"
		add_child(player)
		_players.append({"player": player, "priority": -1, "started": 0})

## GameSettings: effects = soundEnabled, music = musicEnabled, animal_voices = animalVoicesEnabled.
func setting(key: String, fallback: Variant) -> Variant:
	if setting_source.is_valid():
		return setting_source.call(key, fallback)
	return load("res://scripts/game/settings/game_settings.gd").get_value(key, fallback)

func _process(_delta: float) -> void:
	var now := Time.get_ticks_msec()
	if now - _music_check_ms > 250:
		_music_check_ms = now
		sync_music(now)
	if _voice_queue.is_empty():
		return
	var due := _voice_queue.filter(func(v): return now >= int(v["due"]))
	_voice_queue = _voice_queue.filter(func(v): return now < int(v["due"]))
	for v in due:
		v["play"].call()

func _notification(what: int) -> void:
	match what:
		NOTIFICATION_APPLICATION_FOCUS_OUT, NOTIFICATION_APPLICATION_PAUSED:
			_foreground = false
			_voice_queue.clear()
			for s in _players:
				s["player"].stop()
			sync_music(Time.get_ticks_msec())
		NOTIFICATION_APPLICATION_FOCUS_IN, NOTIFICATION_APPLICATION_RESUMED:
			_foreground = true
			sync_music(Time.get_ticks_msec())

# ---------------------------------------------------------------- GameMusicController

## Music plays while the app is in front and Music is on; off rewinds, background pauses.
func sync_music(now: int) -> void:
	if music == null:
		return
	var enabled := bool(setting("music", true))
	if not enabled or not _foreground:
		if music.playing:
			music.stream_paused = true
		if not enabled:
			music.stop()
			music.stream_paused = false
		return
	if music.stream == null:
		var stream = load(Catalog.ROOT + MUSIC_PATH) if Catalog.exists(MUSIC_PATH) else null
		if stream is AudioStreamWAV:
			stream = stream.duplicate()
			stream.loop_mode = AudioStreamWAV.LOOP_FORWARD
			stream.loop_end = int(stream.get_length() * stream.mix_rate)
		music.stream = stream
	music.volume_db = linear_to_db(music_volume(now))
	if music.stream_paused:
		music.stream_paused = false
	elif not music.playing and music.stream != null:
		music.play()

func music_volume(now: int) -> float:
	return VOICE_DUCK_VOLUME if now < _voice_duck_until else MUSIC_VOLUME

## duckForVoice: lower the music under an animal call.
func duck_for_voice(duration_ms: int) -> void:
	if duration_ms <= 0:
		return
	_voice_duck_until = maxi(_voice_duck_until, Time.get_ticks_msec() + duration_ms)
	if music != null and music.playing:
		music.volume_db = linear_to_db(music_volume(Time.get_ticks_msec()))

func voices_enabled() -> bool:
	return bool(setting("animal_voices", true))

# ---------------------------------------------------------------- LudoPawsVoicePlayer

## playHighestPriority: the top reaction's animal call (both sides of a capture), after its
## plan delay (+ the capture contact delay for capture moments).
func play_voices(reactions: Array, character_ids_by_seat: Array, capture_contact_delay_ms := 0, now := -1) -> void:
	var probe_t := Time.get_ticks_usec()
	_probed_play_voices(reactions, character_ids_by_seat, capture_contact_delay_ms, now)
	preload("res://scripts/game/perf.gd").span("play_voices", Time.get_ticks_usec() - probe_t)

func _probed_play_voices(reactions: Array, character_ids_by_seat: Array, capture_contact_delay_ms := 0, now := -1) -> void:
	if reactions.is_empty():
		return
	if not voices_enabled():
		_voice_queue.clear()
		return
	if now < 0:
		now = Time.get_ticks_msec()
	for reaction in _select_vocal_batch(reactions):
		var seat := int(reaction["seat"])
		# canonicalCharacterForSeat when the seat has no assigned character.
		var character_id: String = ["dog", "goat", "duck", "cat"][seat] if seat < 4 else "dog"
		if seat < character_ids_by_seat.size() and character_ids_by_seat[seat] != null:
			character_id = str(character_ids_by_seat[seat])
		character_id = Characters.resolve_id(character_id)
		if not Characters.CHARACTERS.has(character_id):
			continue
		var owner = Catalog.profile(character_id)["voiceOwner"] if Catalog.PROFILES.has(character_id) else null
		var plan = VoiceProfile.plan(character_id, Characters.CHARACTERS[character_id][1], reaction)
		if plan == null:
			continue
		if owner == null and _vocal_paths(character_id, plan["names"]).is_empty():
			continue
		var key := str(reaction.get("reactionKey", ""))
		if key.is_empty():
			key = "%s:%s:%s:%s" % [reaction.get("matchId", ""), reaction.get("eventIndex", -1), reaction["playerId"], str(reaction["voiceCue"]).to_lower()]
		if not VoiceProfile.should_play(plan, key):
			continue
		var variant := VoiceProfile.deterministic_variant_index(key, plan["names"].size())
		var capture_delay := maxi(capture_contact_delay_ms, 0) if reaction.get("momentType") in ["CAPTURE_MADE", "TOKEN_CAPTURED"] else 0
		var fallback_names: Array = [] if owner == null or owner == character_id else VoiceProfile.resource_names(owner, reaction["voiceCue"])
		var priority := _voice_priority(reaction.get("momentType"))
		_voice_queue.append({"due": now + capture_delay + int(plan["delay"]), "play": func():
			if not voices_enabled():
				return
			if on_voice.is_valid():
				on_voice.call(VOCAL_DUCK_MS)
			_play_vocal(plan, variant, fallback_names, owner != null, priority)})

func _select_vocal_batch(reactions: Array) -> Array:
	var highest: Dictionary = reactions[0]
	for r in reactions:
		if int(r["priority"]) > int(highest["priority"]):
			highest = r
	if highest.get("momentType") != "CAPTURE_MADE":
		return [highest]
	var pair := reactions.filter(func(r): return r.get("eventIndex") == highest.get("eventIndex") \
		and r.get("momentType") in ["CAPTURE_MADE", "TOKEN_CAPTURED"])
	pair.sort_custom(func(a, b): return int(a["priority"]) > int(b["priority"]))
	return pair.slice(0, MAX_CAPTURE_VOCALS)

func _voice_priority(moment) -> int:
	match moment:
		"MATCH_WIN", "TEAM_WIN": return 10
		"CAPTURE_MADE", "TOKEN_CAPTURED", "HOME_REACHED", "THIRD_SIX_FORFEIT": return 9
		"SIX_ROLLED", "MATCH_LOSS", "TEAM_LOSS": return 8
	return 5

func _vocal_paths(character_id: String, names: Array) -> Array:
	var paths: Array = []
	for name in names:
		paths.append("audio/sfx/voices/%s/%s.wav" % [character_id, name])
		paths.append("audio/sfx/voices/%s/%s.ogg" % [character_id, name])
	return Catalog.available(paths)

## LudoPawsAudioAssetPlayer.playVocal: rotated names (own, then the owner's), first clip that
## exists, else the procedural call when allowed.
func _play_vocal(plan: Dictionary, variant: int, fallback_names: Array, allow_fallback: bool, priority: int) -> void:
	var ordered := _rotate(plan["names"], variant) + _rotate(fallback_names, variant)
	var paths := Catalog.available(Catalog.vocal_asset_paths(ordered))
	if not paths.is_empty() and _start("voice:" + paths[0], _asset(paths[0]), plan["volume"], priority, plan["rate"]):
		return
	if allow_fallback:
		_start("vocal:" + plan["fallback"], Synth.vocal_stream(plan["fallback"], plan["rate"]), plan["volume"], priority)

static func _rotate(values: Array, start: int) -> Array:
	if values.is_empty():
		return []
	var out: Array = []
	for i in values.size():
		out.append(values[posmod(start + i, values.size())])
	return out

func _on_node_added(node: Node) -> void:
	if node is BaseButton and not node.pressed.is_connected(click):
		node.pressed.connect(click)

func sound_enabled() -> bool:
	return bool(setting("effects", true))

# ---------------------------------------------------------------- GameSoundFeedback

func click() -> void: _play("CLICK", 0.46, PRIORITY_UI, Catalog.UI_CLICK)
func jump() -> void: _play("JUMP", 0.30, PRIORITY_JUMP, Catalog.MOVE_JUMP)
func six() -> void: _play("SIX_SPARK", 0.45, PRIORITY_HIGH, Catalog.SIX)
func yard_exit() -> void: _play("YARD_EXIT", 0.43, PRIORITY_EVENT, Catalog.YARD_EXIT)
func capture() -> void: _play("CAPTURE_IMPACT", 0.62, PRIORITY_CRITICAL, Catalog.CAPTURE)
func safe() -> void: _play("SAFE_SHIMMER", 0.44, PRIORITY_EVENT, Catalog.SAFE_RELIEF)
func home_lane() -> void: _play("HOME_LANE", 0.45, PRIORITY_EVENT, Catalog.HOME_LANE)
func home() -> void: _play("HOME_SPARKLE", 0.56, PRIORITY_HIGH, Catalog.HOME)
func frustrated() -> void: _play("FAIL_SOFT", 0.32, PRIORITY_SOFT_EVENT, Catalog.FAIL)
func exact_home_miss() -> void: _play("EXACT_HOME_MISS", 0.30, PRIORITY_SOFT_EVENT, Catalog.EXACT_HOME_MISS)
func third_six() -> void: _play("THIRD_SIX", 0.48, PRIORITY_CRITICAL, Catalog.THIRD_SIX)
func victory() -> void: _play("VICTORY", 0.60, PRIORITY_CRITICAL, Catalog.VICTORY)
func defeat() -> void: _play("DEFEAT", 0.42, PRIORITY_HIGH, Catalog.DEFEAT)

## Dice roll / settle: a stable take per shared event index (online peers hear the same clip).
func roll(event_index = null) -> void: _play_event("DICE_ROLL", 0.50, PRIORITY_ROLL, Catalog.DICE_ROLL, event_index)
func dice_settle(event_index = null) -> void: _play_event("DICE_SETTLE", 0.32, PRIORITY_HIGH, Catalog.DICE_SETTLE, event_index)

## One footstep in the character's style: its own takes, the shared takes, then the synth.
func move_step(character_id = null) -> void:
	if not sound_enabled():
		return
	var p := Catalog.profile(character_id)
	for family in Catalog.step_asset_families(p):
		if _play_authored(family, p["stepVolume"], PRIORITY_MOVEMENT):
			return
	if _play_authored(Catalog.MOVE_STEP, p["stepVolume"], PRIORITY_MOVEMENT):
		return
	_play_synth({"HOOF": "MOVE_HOOF", "WEB": "MOVE_WEB"}.get(p["footstep"], "MOVE_PAW"), p["stepVolume"], PRIORITY_MOVEMENT)

## GameSoundFeedback.reaction: the highest-priority reaction's dedicated SFX, if it has one.
## Physical moments (moves, captures, safe, home) are timed by the movement clock instead.
func reaction(reactions: Array) -> bool:
	if reactions.is_empty():
		return false
	var highest: Dictionary = reactions[0]
	for r in reactions:
		if int(r["priority"]) > int(highest["priority"]):
			highest = r
	match highest.get("momentType"):
		"SIX_ROLLED": six()
		"POOR_ROLL_STREAK", "NO_LEGAL_MOVE": frustrated()
		"EXACT_HOME_MISS": exact_home_miss()
		"THIRD_SIX_FORFEIT": third_six()
		"MATCH_WIN", "TEAM_WIN": victory()
		"MATCH_LOSS", "TEAM_LOSS": defeat()
		_: return false
	return true

# ---------------------------------------------------------------- resolution

func _play(fallback: String, volume: float, priority: int, family: Array) -> void:
	if not sound_enabled():
		return
	if not _play_authored(family, volume, priority):
		_play_synth(fallback, volume, priority)

func _play_event(fallback: String, volume: float, priority: int, family: Array, event_index) -> void:
	if not sound_enabled():
		return
	if event_index != null:
		var path = Catalog.take_available_for_event(Catalog.available(family), int(event_index))
		if path != null and _start("asset:" + path, _asset(path), volume, priority):
			return
	_play(fallback, volume, priority, family)

## playAuthoredSfx: rotate through the existing takes of one family.
func _play_authored(family: Array, volume: float, priority: int) -> bool:
	var available := Catalog.available(family)
	if available.is_empty():
		return false
	var key := "|".join(family)
	var start: int = posmod(int(_next_variant.get(key, 0)), available.size())
	for offset in available.size():
		var index := (start + offset) % available.size()
		if _start("asset:" + available[index], _asset(available[index]), volume, priority):
			_next_variant[key] = (index + 1) % available.size()
			return true
	return false

func _play_synth(preset: String, volume: float, priority: int) -> void:
	_start("synth:" + preset, Synth.sfx_stream(preset), volume, priority)

func play_stream(source: String, stream: AudioStream, volume: float, priority: int) -> bool:
	return _start(source, stream, volume, priority)

## Match start: load every gameplay sound a few per frame so the first roll, step or capture
## never waits on a file load (dice sound on time, no first-use hitch).
func warm_up() -> void:
	var paths: Array = []
	for family in Catalog.match_families():
		for path in Catalog.available(family):
			if not _streams.has(path) and not paths.has(path):
				paths.append(path)
	var loaded := 0
	for path in paths:
		_asset(path)
		loaded += 1
		if loaded % 4 == 0 and is_inside_tree():
			await get_tree().process_frame

func _asset(path: String) -> AudioStream:
	if not _streams.has(path):
		_streams[path] = load(Catalog.ROOT + path)
	return _streams[path]

## SoundPool.play: a free stream, else steal the lowest-priority (oldest) one if not higher.
func _start(source: String, stream: AudioStream, volume: float, priority: int, rate := 1.0) -> bool:
	if stream == null:
		return false
	var safe_priority := clampi(priority, 0, 10)
	if _players.is_empty(): # not in the tree (tests): resolve and record only
		_record(source, volume, safe_priority)
		return true
	var slot = null
	for s in _players:
		if not s["player"].playing:
			slot = s
			break
	if slot == null:
		for s in _players:
			if slot == null or s["priority"] < slot["priority"] or (s["priority"] == slot["priority"] and s["started"] < slot["started"]):
				slot = s
		if slot == null or slot["priority"] > safe_priority:
			return false
	_record(source, volume, safe_priority)
	if is_inside_tree():
		var player: AudioStreamPlayer = slot["player"]
		player.stream = stream
		player.volume_db = linear_to_db(maxf(clampf(volume, 0.0, 1.0), 0.0001))
		# SoundPool rate changes speed and pitch together (procedural vocals bake the rate in).
		player.pitch_scale = clampf(rate, 0.65, 1.45)
		player.play()
		slot["priority"] = safe_priority
		slot["started"] = Time.get_ticks_msec()
	return true

func _record(source: String, volume: float, priority: int) -> void:
	log.append([source, clampf(volume, 0.0, 1.0), priority])
	if log.size() > 200:
		log.pop_front()
