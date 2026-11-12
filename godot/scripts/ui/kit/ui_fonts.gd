class_name UIFonts
extends RefCounted
## Shared font lookups for code-drawn kit controls.

const DISPLAY := preload("res://assets/ui/brand/display_serif.tres")

static func display() -> Font:
	return DISPLAY
