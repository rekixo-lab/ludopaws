extends RefCounted
## First-launch flow (V2 Meadow look): the live age entry, then the Demo match
## (NEW V2 FEATURE), its results and the skip dialog.

const UI = preload("res://scripts/ui/preview/meadow_controls.gd")
const AgeEntry = preload("res://scripts/game/safety/age_entry.gd")
const Demo = preload("res://scripts/game/tutorial/demo_match.gd")
const Hints = preload("res://scripts/ui/hints/hints.gd")

## First launch: age entry until a band is saved, then the demo until it is completed or skipped.
static func first_launch(page) -> void:
	match first_launch_route(AgeEntry.new().has_choice(), Demo.completed()):
		&"age":
			page.history.clear()
			page._show(&"age")
		&"demo":
			start_demo(page, false)

## age -> demo -> home, each only until done.
static func first_launch_route(has_age: bool, demo_done: bool) -> StringName:
	if not has_age:
		return &"age"
	return &"home" if demo_done else &"demo"

static func start_demo(page, replay: bool) -> void:
	page.state.tutorial_replay = replay
	page.history.clear()
	page.start_match(&"demo")

## FamilyPrivacyDialog age step: "How old are you?" slider 1–100, nothing preselected.
static func age(page) -> void:
	var body: VBoxContainer = page.body
	var card := UI.panel(body, "How old are you?", Color.TRANSPARENT, "paw")
	var value := UI.label(card, "–", 44, true)
	UI.label(card, "Slide to select your age · 1–100", 13, true)
	var slider := HSlider.new()
	slider.min_value = 0
	slider.max_value = 100
	slider.step = 1
	slider.value = 0
	slider.custom_minimum_size.y = 48
	card.add_child(slider)
	var next := UI.button(card, "Continue", func(): _save_age(page, int(slider.value)), true)
	next.disabled = true
	# First launch (essential): the slider until an age is picked, then Continue.
	var slider_ref: WeakRef = weakref(slider)
	Hints.offer(slider, "age.slider", func():
		var s = slider_ref.get_ref()
		return s if s != null and next.disabled else null, Hints.Policy.IDLE_ONBOARDING_MS, 70, true)
	Hints.button(next, "age.continue", Hints.Policy.IDLE_ONBOARDING_MS, 75, true)
	slider.value_changed.connect(func(v: float):
		if int(v) == 0:
			slider.value = 1
			return
		value.text = str(int(v))
		next.disabled = int(v) < 1)
	UI.label(card, "By continuing, you confirm that you have read and agree to our Privacy Policy.", 12, true)
	UI.label(card, "Only an age range is kept on this device, never your age.", 11, true)

static func _save_age(page, years: int) -> void:
	if not AgeEntry.new().save_choice(years):
		page.notice("Age", "Couldn't save your choice. Please try again.")
		return
	if not Demo.completed():
		start_demo(page, false)
	else:
		page.go_home()

## Skip: confirm, mark the tutorial done (not on a replay) and go Home.
static func skip_dialog(page) -> void:
	var card: VBoxContainer = page.dialog("Skip tutorial?", "You can play it again any time from Settings.", true)
	UI.button(card, "Skip", func():
		page.dismiss()
		finish(page), true)
	UI.button(card, "Keep playing", page.dismiss)

static func finish(page) -> void:
	if not page.state.tutorial_replay:
		Demo.mark_completed()
	page.state.tutorial_replay = false
	page.reset_match()
	page.go_home()

## Demo results: short message + Continue (no stats, gems, standings or ads).
static func results(page, won: bool) -> void:
	var body: VBoxContainer = page.body
	var card := UI.panel(body)
	UI.image(card, UI.art("match_trophy"), 105)
	UI.label(card, "Demo complete!" if won else "Demo finished", 25, true)
	UI.label(card, ("तुम जीत गए! अब असली मैच खेलो · You won! Now try a real match." if won
		else "इस बार Robot जीता. अब तुम तैयार हो · The robot won this time. You're ready to play!"), 15, true)
	Hints.button(UI.button(body, "Continue", finish.bind(page), true, "play"), "demo.continue", Hints.Policy.IDLE_DEMO_MS, 80, true)
