@tool
extends RefCounted
## Bit-exact ports of the two seeded generators the live board artwork uses, so ferns,
## specks and grain land in the same places: kotlin.random.Random(seed) (XorWow) and
## java.util.Random(seed). Decorative only; never used for dice.

const MASK32 := 0xFFFFFFFF
const MASK48 := (1 << 48) - 1
const JAVA_MULTIPLIER := 0x5DEECE66D

class XorWow:
	var x: int
	var y: int
	var z: int
	var w: int
	var v: int
	var addend: int

	## kotlin.random.Random(seed: Int) == XorWowRandom(seed, seed shr 31)
	func _init(seed: int) -> void:
		var s1 := seed & MASK32
		var s2 := (-1 if seed < 0 else 0) & MASK32
		x = s1
		y = s2
		z = 0
		w = 0
		v = (~s1) & MASK32
		addend = ((s1 << 10) ^ (s2 >> 4)) & MASK32
		for i in 64:
			next_int()

	func next_int() -> int:
		var t := x
		t = (t ^ (t >> 2)) & MASK32
		x = y
		y = z
		z = w
		var v0 := v
		w = v0
		t = (t ^ ((t << 1) & MASK32)) ^ v0 ^ ((v0 << 4) & MASK32)
		t &= MASK32
		v = t
		addend = (addend + 362437) & MASK32
		return (t + addend) & MASK32

	func next_float() -> float:
		return float(next_int() >> 8) / float(1 << 24)

class JavaRandom:
	var seed: int

	func _init(initial: int) -> void:
		seed = (initial ^ JAVA_MULTIPLIER) & MASK48

	func _mul48(a: int) -> int:
		# (a * JAVA_MULTIPLIER) mod 2^48 without 64-bit overflow.
		var lo := a & 0xFFFFFF
		var hi := a >> 24
		return (lo * JAVA_MULTIPLIER + (((hi * JAVA_MULTIPLIER) & 0xFFFFFF) << 24)) & MASK48

	func next_bits(bits: int) -> int:
		seed = (_mul48(seed) + 0xB) & MASK48
		var value := seed >> (48 - bits)
		# Java returns a signed 32-bit int.
		if bits == 32 and value >= (1 << 31):
			value -= 1 << 32
		return value

	func next_boolean() -> bool:
		return next_bits(1) != 0

	func next_float() -> float:
		return next_bits(24) / float(1 << 24)

	func next_int(bound: int) -> int:
		if (bound & -bound) == bound:
			return (bound * next_bits(31)) >> 31
		while true:
			var bits := next_bits(31)
			var value := bits % bound
			if bits - value + (bound - 1) < (1 << 31):
				return value
		return 0
