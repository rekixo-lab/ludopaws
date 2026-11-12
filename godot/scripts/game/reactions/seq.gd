@tool
extends RefCounted
## Small helpers that keep Kotlin collection semantics exact (stable sorts, takeLast,
## java.lang.String.hashCode). Presentation-only.

## Stable sort: `less(a, b)` is the strict ordering; ties keep their original order,
## like Kotlin's sortedWith / sortedByDescending.
static func stable_sort(items: Array, less: Callable) -> Array:
	var indexed: Array = []
	for i in items.size():
		indexed.append([items[i], i])
	indexed.sort_custom(func(a, b):
		if less.call(a[0], b[0]):
			return true
		if less.call(b[0], a[0]):
			return false
		return a[1] < b[1])
	return indexed.map(func(pair): return pair[0])

static func take_last(items: Array, count: int) -> Array:
	return items.slice(maxi(0, items.size() - count))

## java.lang.String.hashCode (UTF-16 code units, 32-bit wrap).
static func java_hash(text: String) -> int:
	var h := 0
	for i in text.length():
		h = _int32(h * 31 + text.unicode_at(i))
	return h

static func _int32(value: int) -> int:
	value = value & 0xFFFFFFFF
	return value - 0x100000000 if value >= 0x80000000 else value
