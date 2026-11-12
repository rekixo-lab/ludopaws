extends RefCounted
## Live NaturalReviewController: the Play review sheet after a neutral experience (>= 3
## completed matches on >= 2 days, win or loss), at most once per 90 days, through the
## automatic prompt gate, 2.2 s after Home settles. Play decides whether the sheet shows and
## never reports a rating; there is no reward and no fallback popup.

const Gate = preload("res://scripts/game/growth/prompt_gate.gd")
const Engagement = preload("res://scripts/game/growth/engagement_store.gd")
const DELAY_S := 2.2

static func eligible(now_ms: int) -> bool:
	var previous := Engagement.last_review_attempt()
	return Engagement.completed_matches() >= 3 and Engagement.completed_days() >= 2 \
		and previous <= now_ms and (previous <= 0 or now_ms - previous >= 90 * Gate.DAY)

## Returns true when the sheet was requested. `platform` = LudoPawsPlatform (or a fake).
static func request(platform, now_ms := -1) -> bool:
	var now := now_ms if now_ms >= 0 else int(Time.get_unix_time_from_system() * 1000.0)
	if platform == null or not eligible(now) or not Gate.reserve("review"):
		return false
	platform.reviewRequest()
	while true:
		var args: Array = await platform.playcore
		if args[0] == "review_ready":
			Engagement.review_attempt(now)
			Gate.presented("review")
		elif args[0] == "review":
			break
	Gate.release("review")
	return true
