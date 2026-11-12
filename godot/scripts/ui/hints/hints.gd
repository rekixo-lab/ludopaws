extends RefCounted
## Convenience access to the Guidance autoload (absent in some tests: calls become no-ops).

const Policy = preload("res://scripts/ui/hints/tap_hint_policy.gd")

static func guidance() -> Node:
	var tree := Engine.get_main_loop() as SceneTree
	return tree.root.get_node_or_null("Guidance") if tree != null else null

static func offer(owner: Node, key: String, target: Callable, idle_ms: int, priority: int, essential := false) -> void:
	var g := guidance()
	if g != null:
		g.offer(owner, key, target, idle_ms, priority, essential)

## Shorthand: hint a button after `idle_ms` while it is visible and enabled.
static func button(btn: Control, key: String, idle_ms: int, priority: int, essential := false) -> void:
	var ref: WeakRef = weakref(btn)
	offer(btn, key, func(): return ref.get_ref(), idle_ms, priority, essential)
