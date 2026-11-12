extends RefCounted
## What the match's message pill shows now that the status box is gone (V2 layout).
## Priority: Demo lesson > first-match coach hint > a short game event. Plain roll / move
## results are not repeated (the dice and the board already show them).

const TRANSIENT_MS := 2200

## Engine messages worth a toast: anything but the plain "Rolled n" / "Paw n moved".
static func is_event(message: String) -> bool:
	if message.is_empty():
		return false
	if message.begins_with("Paw ") and message.ends_with(" moved"):
		return false
	if message.begins_with("Rolled ") and not message.contains("·"):
		return false
	return true

## Returns {text, kind: "lesson" | "coach" | "event" | ""}.
static func pick(lesson: String, coach, event: String) -> Dictionary:
	if not lesson.is_empty():
		return {"text": lesson, "kind": "lesson"}
	if coach != null and not str(coach).is_empty():
		return {"text": str(coach), "kind": "coach"}
	if is_event(event):
		return {"text": event, "kind": "event"}
	return {"text": "", "kind": ""}
