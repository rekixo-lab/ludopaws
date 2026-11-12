package android.graphics

/** No-op stand-ins so the live 2D avatar fallbacks compile on the JVM (they are not recorded). */
class RectF(var left: Float = 0f, var top: Float = 0f, var right: Float = 0f, var bottom: Float = 0f)
class Path {
    fun moveTo(x: Float, y: Float) = Unit
    fun lineTo(x: Float, y: Float) = Unit
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) = Unit
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) = Unit
    fun close() = Unit
}
open class Paint(flags: Int = 0) {
    enum class Cap { BUTT, ROUND, SQUARE }
    enum class Style { FILL, STROKE, FILL_AND_STROKE }
    var color: Int = 0
    var strokeWidth: Float = 0f
    var strokeCap: Cap = Cap.BUTT
    var style: Style = Style.FILL
    companion object { const val ANTI_ALIAS_FLAG = 1 }
}
object Color { const val WHITE = -1 }
class Canvas {
    fun drawOval(r: RectF, p: Paint) = Unit
    fun drawCircle(x: Float, y: Float, r: Float, p: Paint) = Unit
    fun drawLine(a: Float, b: Float, c: Float, d: Float, p: Paint) = Unit
    fun drawPath(path: Path, p: Paint) = Unit
    fun drawArc(r: RectF, start: Float, sweep: Float, center: Boolean, p: Paint) = Unit
    fun drawRoundRect(r: RectF, rx: Float, ry: Float, p: Paint) = Unit
    fun save(): Int = 0
    fun restore() = Unit
    fun rotate(d: Float, x: Float, y: Float) = Unit
}
