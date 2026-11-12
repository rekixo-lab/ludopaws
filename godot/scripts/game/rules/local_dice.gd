@tool
extends RefCounted
## Unbiased local die for Robot / Pass & Play.
## Same sampling method as the live server dice (server/src/dice-fast.js):
## cryptographically random bytes with rejection sampling (bytes >= 252 dropped).
## The value is decided before any animation starts; animation only displays it.

static var _crypto := Crypto.new()

static func roll() -> int:
	while true:
		var bytes := _crypto.generate_random_bytes(16)
		for byte in bytes:
			if byte < 252:
				return (byte % 6) + 1
	return 1
