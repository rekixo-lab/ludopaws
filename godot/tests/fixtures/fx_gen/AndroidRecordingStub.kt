package android.graphics

/** JVM stand-ins that RECORD every draw call of the live FX painter (see Main.kt). */
object Recorder {
    val commands = mutableListOf<String>()
    fun f(v: Float) = v.toString()
}
class Path {
    val points = mutableListOf<Pair<Float, Float>>()
    var closed = false
    fun moveTo(x: Float, y: Float) { points += x to y }
    fun lineTo(x: Float, y: Float) { points += x to y }
    fun close() { closed = true }
}
open class Paint(flags: Int = 0) {
    enum class Cap { BUTT, ROUND, SQUARE }
    enum class Join { MITER, ROUND, BEVEL }
    enum class Style { FILL, STROKE, FILL_AND_STROKE }
    var color: Int = 0
    var strokeWidth: Float = 0f
    var strokeCap: Cap = Cap.BUTT
    var strokeJoin: Join = Join.MITER
    var style: Style = Style.FILL
    fun tag(): String = "\"${style.name}\",${color.toLong() and 0xFFFFFFFFL},${strokeWidth}"
    companion object { const val ANTI_ALIAS_FLAG = 1 }
}
object Color {
    const val WHITE = -1
    fun rgb(r: Int, g: Int, b: Int): Int = argb(255, r, g, b)
    fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b
    fun red(c: Int) = (c shr 16) and 255
    fun green(c: Int) = (c shr 8) and 255
    fun blue(c: Int) = c and 255
}
class Canvas {
    private fun add(s: String) { Recorder.commands += s }
    fun drawCircle(x: Float, y: Float, r: Float, p: Paint) = add("[\"circle\",$x,$y,$r,${p.tag()}]")
    fun drawLine(a: Float, b: Float, c: Float, d: Float, p: Paint) = add("[\"line\",$a,$b,$c,$d,${p.tag()}]")
    fun drawOval(l: Float, t: Float, r: Float, b: Float, p: Paint) = add("[\"oval\",$l,$t,$r,$b,${p.tag()}]")
    fun drawRect(l: Float, t: Float, r: Float, b: Float, p: Paint) = add("[\"rect\",$l,$t,$r,$b,${p.tag()}]")
    fun drawPath(path: Path, p: Paint) =
        add("[\"path\",[${path.points.joinToString(",") { "${it.first},${it.second}" }}],${path.closed},${p.tag()}]")
    fun save(): Int { add("[\"save\"]"); return 0 }
    fun restore() = add("[\"restore\"]")
    fun rotate(d: Float, x: Float, y: Float) = add("[\"rotate\",$d,$x,$y]")
}
