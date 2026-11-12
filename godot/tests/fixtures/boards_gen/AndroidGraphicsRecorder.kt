package android.graphics

/**
 * JVM stand-ins that RECORD the live board renderers' Canvas calls as JSON commands
 * (see Main.kt). Paths record their verbs; paints record style, colour, stroke and shader.
 */
object Recorder {
    val out = StringBuilder()
    var first = true
    fun add(s: String) { if (!first) out.append(",\n"); out.append(s); first = false }
    fun f(v: Float) = if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()
}
class RectF(@JvmField var left: Float = 0f, @JvmField var top: Float = 0f, @JvmField var right: Float = 0f, @JvmField var bottom: Float = 0f) {
    constructor(o: RectF) : this(o.left, o.top, o.right, o.bottom)
    fun set(o: RectF) { left = o.left; top = o.top; right = o.right; bottom = o.bottom }
    fun set(l: Float, t: Float, r: Float, b: Float) { left = l; top = t; right = r; bottom = b }
    fun width() = right - left
    fun height() = bottom - top
    fun centerX() = (left + right) * .5f
    fun centerY() = (top + bottom) * .5f
    fun inset(dx: Float, dy: Float) { left += dx; top += dy; right -= dx; bottom -= dy }
    fun offset(dx: Float, dy: Float) { left += dx; right += dx; top += dy; bottom += dy }
    fun j() = "${Recorder.f(left)},${Recorder.f(top)},${Recorder.f(right)},${Recorder.f(bottom)}"
}
class Path() {
    enum class Direction { CW, CCW }
    enum class FillType { WINDING, EVEN_ODD, INVERSE_WINDING, INVERSE_EVEN_ODD }
    val verbs = mutableListOf<String>()
    var fillType = FillType.WINDING
    constructor(other: Path) : this() { verbs += other.verbs; fillType = other.fillType }
    fun moveTo(x: Float, y: Float) { verbs += "[\"M\",${Recorder.f(x)},${Recorder.f(y)}]" }
    fun lineTo(x: Float, y: Float) { verbs += "[\"L\",${Recorder.f(x)},${Recorder.f(y)}]" }
    fun quadTo(a: Float, b: Float, c: Float, d: Float) { verbs += "[\"Q\",${Recorder.f(a)},${Recorder.f(b)},${Recorder.f(c)},${Recorder.f(d)}]" }
    fun cubicTo(a: Float, b: Float, c: Float, d: Float, e: Float, g: Float) {
        verbs += "[\"C\",${Recorder.f(a)},${Recorder.f(b)},${Recorder.f(c)},${Recorder.f(d)},${Recorder.f(e)},${Recorder.f(g)}]"
    }
    fun close() { verbs += "[\"Z\"]" }
    private var lx = 0f
    private var ly = 0f
    fun rQuadTo(a: Float, b: Float, c: Float, d: Float) { verbs += "[\"q\",${Recorder.f(a)},${Recorder.f(b)},${Recorder.f(c)},${Recorder.f(d)}]" }
    fun reset() { verbs.clear(); fillType = FillType.WINDING }
    fun rewind() = reset()
    fun addCircle(x: Float, y: Float, r: Float, d: Direction) { verbs += "[\"CIRCLE\",${Recorder.f(x)},${Recorder.f(y)},${Recorder.f(r)}]" }
    fun addOval(r: RectF, d: Direction) { verbs += "[\"OVAL\",${r.j()}]" }
    fun addRect(l: Float, t: Float, r: Float, b: Float, d: Direction) { verbs += "[\"RECT\",${Recorder.f(l)},${Recorder.f(t)},${Recorder.f(r)},${Recorder.f(b)}]" }
    fun addRoundRect(r: RectF, rx: Float, ry: Float, d: Direction) { verbs += "[\"RRECT\",${r.j()},${Recorder.f(rx)},${Recorder.f(ry)}]" }
    fun addArc(r: RectF, start: Float, sweep: Float) { verbs += "[\"ARC\",${r.j()},${Recorder.f(start)},${Recorder.f(sweep)}]" }
    fun arcTo(r: RectF, start: Float, sweep: Float) { verbs += "[\"ARCTO\",${r.j()},${Recorder.f(start)},${Recorder.f(sweep)}]" }
    fun arcTo(r: RectF, start: Float, sweep: Float, force: Boolean) = arcTo(r, start, sweep)
    fun transform(m: Matrix) { verbs += "[\"MATRIX\",${m.values.joinToString(",") { Recorder.f(it) }}]" }
    fun j() = "[${verbs.joinToString(",")}]"
}
class Matrix {
    val values = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    fun setScale(x: Float, y: Float) { values.fill(0f); values[0] = x; values[4] = y; values[8] = 1f }
    /** Same projective solution as Android (square-to-quad composition), normalised by p2. */
    fun setPolyToPoly(src: FloatArray, srcIndex: Int, dst: FloatArray, dstIndex: Int, count: Int): Boolean {
        require(count == 4)
        val a = squareToQuad(dst, dstIndex) ?: return false
        val b = squareToQuad(src, srcIndex) ?: return false
        val inv = inverse(b) ?: return false
        val m = mul(a, inv)
        for (i in 0 until 9) values[i] = (m[i] / m[8]).toFloat()
        return true
    }
    private fun squareToQuad(q: FloatArray, o: Int): DoubleArray? {
        val x0 = q[o].toDouble(); val y0 = q[o + 1].toDouble(); val x1 = q[o + 2].toDouble(); val y1 = q[o + 3].toDouble()
        val x2 = q[o + 4].toDouble(); val y2 = q[o + 5].toDouble(); val x3 = q[o + 6].toDouble(); val y3 = q[o + 7].toDouble()
        val dx1 = x1 - x2; val dx2 = x3 - x2; val sx = x0 - x1 + x2 - x3
        val dy1 = y1 - y2; val dy2 = y3 - y2; val sy = y0 - y1 + y2 - y3
        val den = dx1 * dy2 - dx2 * dy1
        if (kotlin.math.abs(den) < 1e-12) return null
        val g = (sx * dy2 - dx2 * sy) / den
        val h = (dx1 * sy - sx * dy1) / den
        return doubleArrayOf(x1 - x0 + g * x1, x3 - x0 + h * x3, x0, y1 - y0 + g * y1, y3 - y0 + h * y3, y0, g, h, 1.0)
    }
    private fun mul(a: DoubleArray, b: DoubleArray) = DoubleArray(9) { i -> val r = i / 3; val c = i % 3; a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c] }
    private fun inverse(m: DoubleArray): DoubleArray? {
        val det = m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6]) + m[2] * (m[3] * m[7] - m[4] * m[6])
        if (kotlin.math.abs(det) < 1e-12) return null
        val i = 1.0 / det
        return doubleArrayOf((m[4] * m[8] - m[5] * m[7]) * i, (m[2] * m[7] - m[1] * m[8]) * i, (m[1] * m[5] - m[2] * m[4]) * i,
            (m[5] * m[6] - m[3] * m[8]) * i, (m[0] * m[8] - m[2] * m[6]) * i, (m[2] * m[3] - m[0] * m[5]) * i,
            (m[3] * m[7] - m[4] * m[6]) * i, (m[1] * m[6] - m[0] * m[7]) * i, (m[0] * m[4] - m[1] * m[3]) * i)
    }
}
open class Shader {
    enum class TileMode { CLAMP, REPEAT, MIRROR }
    open fun j(): String = "null"
    fun setLocalMatrix(m: Matrix) = Unit
}
class LinearGradient(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val colors: IntArray, val stops: FloatArray?, val mode: Shader.TileMode) : Shader() {
    constructor(x0: Float, y0: Float, x1: Float, y1: Float, c0: Int, c1: Int, mode: Shader.TileMode) : this(x0, y0, x1, y1, intArrayOf(c0, c1), null, mode)
    override fun j() = "[\"linear\",${Recorder.f(x0)},${Recorder.f(y0)},${Recorder.f(x1)},${Recorder.f(y1)},[${colors.joinToString(",") { (it.toLong() and 0xFFFFFFFFL).toString() }}],${stops?.joinToString(",", "[", "]") { Recorder.f(it) } ?: "null"},\"${mode}\"]"
}
class RadialGradient(val x: Float, val y: Float, val r: Float, val colors: IntArray, val stops: FloatArray?, val mode: Shader.TileMode) : Shader() {
    constructor(x: Float, y: Float, r: Float, c0: Int, c1: Int, mode: Shader.TileMode) : this(x, y, r, intArrayOf(c0, c1), null, mode)
    override fun j() = "[\"radial\",${Recorder.f(x)},${Recorder.f(y)},${Recorder.f(r)},[${colors.joinToString(",") { (it.toLong() and 0xFFFFFFFFL).toString() }}],${stops?.joinToString(",", "[", "]") { Recorder.f(it) } ?: "null"},\"${mode}\"]"
}
class Bitmap private constructor(val w: Int, val h: Int, var key: String) {
    enum class Config { ARGB_8888 }
    val pixels = IntArray(w * h)
    companion object {
        @JvmStatic fun createBitmap(w: Int, h: Int, c: Config) = Bitmap(w, h, "")
        @JvmStatic fun named(name: String) = Bitmap(1, 1, name)
    }
    fun setPixels(p: IntArray, o: Int, s: Int, x: Int, y: Int, w: Int, h: Int) {
        for (row in 0 until h) for (col in 0 until w) pixels[(y + row) * this.w + x + col] = p[o + row * s + col]
    }
    fun setPixel(x: Int, y: Int, c: Int) { pixels[y * w + x] = c }
    fun eraseColor(c: Int) = pixels.fill(c)
    val width get() = w
    val height get() = h
    /** Generated bitmaps are keyed by a content hash so ports can prove identical pixels. */
    fun recordKey(): String = key.ifEmpty { "pixels:${w}x${h}:" + pixels.fold(17L) { a, v -> (a * 31 + v) and 0xFFFFFFFFL } }
}
object BitmapFactory {
    class Options { var inSampleSize = 1; var inScaled = true; var inPreferredConfig: Bitmap.Config? = null }
    @JvmStatic fun decodeResource(res: android.content.res.Resources, id: Int, o: Options?): Bitmap = Bitmap.named(com.rekixo.ludopaws.R.drawable.name(id))
}
class BitmapShader(val bitmap: Bitmap, val x: Shader.TileMode, val y: Shader.TileMode) : Shader() {
    override fun j() = "[\"bitmap\",${bitmap.w},${bitmap.h}]"
}
open class Paint(flags: Int = 0) {
    enum class Cap { BUTT, ROUND, SQUARE }
    enum class Join { MITER, ROUND, BEVEL }
    enum class Style { FILL, STROKE, FILL_AND_STROKE }
    enum class Align { LEFT, CENTER, RIGHT }
    var color: Int = 0xFF000000.toInt()
    var alpha: Int
        get() = (color ushr 24) and 255
        set(v) { color = (color and 0x00FFFFFF) or ((v and 255) shl 24) }
    var strokeWidth: Float = 0f
    var strokeCap: Cap = Cap.BUTT
    var strokeJoin: Join = Join.MITER
    var style: Style = Style.FILL
    var shader: Shader? = null
    var isAntiAlias = true
    var textAlign = Align.LEFT
    var textSize = 12f
    var isFakeBoldText = false
    var isFilterBitmap = false
    constructor(other: Paint) : this() { color = other.color; strokeWidth = other.strokeWidth; strokeCap = other.strokeCap; strokeJoin = other.strokeJoin; style = other.style; shader = other.shader }
    fun setARGB(a: Int, r: Int, g: Int, b: Int) { color = Color.argb(a, r, g, b) }
    private var shadowLayer: String? = null
    fun setShadowLayer(radius: Float, dx: Float, dy: Float, c: Int) {
        shadowLayer = "[\"shadow\",${Recorder.f(radius)},${Recorder.f(dx)},${Recorder.f(dy)},${c.toLong() and 0xFFFFFFFFL}]"
    }
    fun clearShadowLayer() { shadowLayer = null }
    fun j() = "\"${style}\",${color.toLong() and 0xFFFFFFFFL},${Recorder.f(strokeWidth)},\"${strokeCap}\",\"${strokeJoin}\",${shader?.j() ?: "null"}" + (shadowLayer?.let { ",$it" } ?: "")
    companion object { const val ANTI_ALIAS_FLAG = 1; const val FILTER_BITMAP_FLAG = 2; const val DITHER_FLAG = 4 }
}
object Color {
    const val WHITE = -1
    const val BLACK = -16777216
    const val TRANSPARENT = 0
    fun rgb(r: Int, g: Int, b: Int): Int = argb(255, r, g, b)
    fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b
    fun alpha(c: Int) = (c ushr 24) and 255
    fun red(c: Int) = (c shr 16) and 255
    fun green(c: Int) = (c shr 8) and 255
    fun blue(c: Int) = c and 255
}
class Canvas(bitmap: Bitmap? = null) {
    private var depth = 0
    private fun add(s: String) = Recorder.add(s)
    fun save(): Int { add("[\"save\"]"); return ++depth }
    fun restore() { add("[\"restore\"]"); depth-- }
    fun restoreToCount(count: Int) { while (depth >= count) restore() }
    fun translate(x: Float, y: Float) = add("[\"translate\",${Recorder.f(x)},${Recorder.f(y)}]")
    fun scale(x: Float, y: Float) = add("[\"scale\",${Recorder.f(x)},${Recorder.f(y)}]")
    fun scale(x: Float, y: Float, px: Float, py: Float) { translate(px, py); scale(x, y); translate(-px, -py) }
    fun rotate(d: Float) = add("[\"rotate\",${Recorder.f(d)},0,0]")
    fun rotate(d: Float, x: Float, y: Float) = add("[\"rotate\",${Recorder.f(d)},${Recorder.f(x)},${Recorder.f(y)}]")
    fun clipRect(l: Float, t: Float, r: Float, b: Float): Boolean { add("[\"clip\",${Recorder.f(l)},${Recorder.f(t)},${Recorder.f(r)},${Recorder.f(b)}]"); return true }
    fun clipRect(r: RectF): Boolean = clipRect(r.left, r.top, r.right, r.bottom)
    fun clipPath(p: Path): Boolean { add("[\"clipPath\",${p.j()}]"); return true }
    fun drawRect(l: Float, t: Float, r: Float, b: Float, p: Paint) = add("[\"rect\",${Recorder.f(l)},${Recorder.f(t)},${Recorder.f(r)},${Recorder.f(b)},${p.j()}]")
    fun drawRect(r: RectF, p: Paint) = drawRect(r.left, r.top, r.right, r.bottom, p)
    fun drawRoundRect(l: Float, t: Float, r: Float, b: Float, rx: Float, ry: Float, p: Paint) =
        add("[\"rrect\",${Recorder.f(l)},${Recorder.f(t)},${Recorder.f(r)},${Recorder.f(b)},${Recorder.f(rx)},${Recorder.f(ry)},${p.j()}]")
    fun drawRoundRect(r: RectF, rx: Float, ry: Float, p: Paint) = drawRoundRect(r.left, r.top, r.right, r.bottom, rx, ry, p)
    fun drawOval(l: Float, t: Float, r: Float, b: Float, p: Paint) = add("[\"oval\",${Recorder.f(l)},${Recorder.f(t)},${Recorder.f(r)},${Recorder.f(b)},${p.j()}]")
    fun drawOval(r: RectF, p: Paint) = drawOval(r.left, r.top, r.right, r.bottom, p)
    fun drawCircle(x: Float, y: Float, r: Float, p: Paint) = add("[\"circle\",${Recorder.f(x)},${Recorder.f(y)},${Recorder.f(r)},${p.j()}]")
    fun drawLine(a: Float, b: Float, c: Float, d: Float, p: Paint) = add("[\"line\",${Recorder.f(a)},${Recorder.f(b)},${Recorder.f(c)},${Recorder.f(d)},${p.j()}]")
    fun drawArc(r: RectF, start: Float, sweep: Float, center: Boolean, p: Paint) =
        add("[\"arc\",${r.j()},${Recorder.f(start)},${Recorder.f(sweep)},$center,${p.j()}]")
    fun drawArc(l: Float, t: Float, r: Float, b: Float, start: Float, sweep: Float, center: Boolean, p: Paint) = drawArc(RectF(l, t, r, b), start, sweep, center, p)
    fun drawPath(path: Path, p: Paint) = add("[\"path\",${path.j()},\"${path.fillType}\",${p.j()}]")
    fun drawColor(c: Int) = add("[\"color\",${c.toLong() and 0xFFFFFFFFL}]")
    fun drawBitmap(b: Bitmap, x: Float, y: Float, p: Paint?) = add("[\"bitmap\",\"${b.recordKey()}\",${Recorder.f(x)},${Recorder.f(y)},${Recorder.f(x + b.w)},${Recorder.f(y + b.h)},${p?.alpha ?: 255}]")
    fun drawBitmap(b: Bitmap, src: Rect?, dst: RectF, p: Paint?) = add("[\"bitmap\",\"${b.recordKey()}\",${dst.j()},${p?.alpha ?: 255}]")
    fun concat(m: Matrix) = add("[\"concat\",${m.values.joinToString(",") { Recorder.f(it) }}]")
}

class Rect(var left: Int = 0, var top: Int = 0, var right: Int = 0, var bottom: Int = 0)
