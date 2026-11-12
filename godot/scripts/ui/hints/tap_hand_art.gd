extends RefCounted
## The V2 tap hand (assets/ui/icons/tap_hand.svg) rasterised at the size it is shown, so it
## is crisp at physical pixels. Textures are cached by height (8 px buckets). The SVG uses
## the "keep" importer so its source ships in the APK (an imported SVG only ships as .ctex).

const SVG_PATH := "res://assets/ui/icons/tap_hand.svg"
const VIEW := Vector2(128, 160)
## Fingertip in the art's own units (where the hand taps).
const TIP := Vector2(45, 8)
static var _svg := ""
static var _cache := {}

static func aspect() -> float:
	return VIEW.x / VIEW.y

## Texture whose height is about `px` physical pixels (with mipmaps for smaller draws).
static func texture(px: float) -> Texture2D:
	var h := clampi(int(ceil(px / 8.0)) * 8, 24, 512)
	if _cache.has(h):
		return _cache[h]
	if _svg.is_empty():
		_svg = FileAccess.get_file_as_string(SVG_PATH)
	var image := Image.new()
	if _svg.is_empty() or image.load_svg_from_string(_svg, h / VIEW.y) != OK:
		_cache[h] = null   # never retry a failed raster every frame
		push_error("tap hand art missing: " + SVG_PATH)
		return null
	image.generate_mipmaps()
	var tex := ImageTexture.create_from_image(image)
	_cache[h] = tex
	return tex

## Where the fingertip sits inside a hand drawn at `size` (mirrored / flipped as drawn).
static func tip_in(size: Vector2, mirrored := false, flipped := false) -> Vector2:
	var t := TIP / VIEW * size
	if mirrored:
		t.x = size.x - t.x
	if flipped:
		t.y = size.y - t.y
	return t
