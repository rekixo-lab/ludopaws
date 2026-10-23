package com.rekixo.ludopaws

import android.opengl.Matrix
import com.rekixo.ludopaws.feature.characters.rendering.CharacterDrawContext
import com.rekixo.ludopaws.feature.characters.rendering.CharacterSurface
import com.rekixo.ludopaws.rendering.meshes.MeshGeometry
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Four rigid roof wedges share one apex; only the separately hinged doors move. */
internal class LudoPawsCenterHouses3D {
    private val house = FloatArray(16)
    private val facade = FloatArray(16)
    private val hinge = FloatArray(16)
    private val garden = FloatArray(16)
    private val colors = arrayOf("BLUE", "RED", "GREEN", "YELLOW")
    private val roofs = arrayOf(
        rgb(0x268DE2), rgb(0xED3936), rgb(0x41B854), rgb(0xF3C43B),
    )
    private val roofShades = roofs.map { base ->
        Array(4) { shade -> tint(base, .94f + shade * .045f) }
    }
    private val roofEdges = roofs.map { tint(it, .68f) }
    private val roofBase = roofs.map { tint(it, .84f) }
    private val tiledRoof = roofTiles()
    private val ridgeCap = raisedRidge()
    private val shell = MeshBuilder().apply {
        // Extend the cream wall to meet the inset roof, giving the openings more headroom.
        // The existing facade transform maps these points to the roof's front edge.
        prism(listOf(v(-.90f, -.25f), v(0f, -1.15f), v(.90f, -.25f),
            v(.86f, 0f), v(-.86f, 0f)), -.055f, 0f)
    }.build()
    private val arch = MeshBuilder().apply {
        val outline = mutableListOf(v(-1f, 0f), v(-1f, -.65f))
        for (step in 0..16) {
            val angle = PI.toFloat() - step * PI.toFloat() / 16f
            outline.add(v(cos(angle), -.65f - sin(angle) * .35f))
        }
        outline.add(v(1f, 0f))
        prism(outline, -.5f, .5f)
    }.build()
    private val cube = MeshBuilder().apply {
        prism(listOf(v(-1f, -1f), v(1f, -1f), v(1f, 1f), v(-1f, 1f)), -1f, 1f)
    }.build()
    private val grassTexture = MeshBuilder().apply {
        // Deterministic flat flecks: no motion or raised blades in the standing areas.
        for (row in -11..11) {
            for (col in -11..11) {
                val x = col * .085f
                val y = row * .085f
                if (kotlin.math.abs(x) < .67f && kotlin.math.abs(y) < .67f) continue
                if (Math.floorMod(col * 13 + row * 7, 3) != 0) continue
                quad(v(x - .018f, y, -.075f), v(x, y - .008f, -.075f),
                    v(x + .025f, y, -.075f), v(x, y + .008f, -.075f))
            }
        }
    }.build()
    private val roofBody = MeshBuilder().apply {
        for (side in intArrayOf(-1, 1)) {
            val a = roofApex()
            val b = roofEave(side)
            val c = roofFront()
            triangle(a, b, c)
            val down = v(0f, 0f, -.035f)
            quad(a, a + down, b + down, b)
            quad(b, b + down, c + down, c)
            quad(c, c + down, a + down, a)
        }
    }.build()

    fun draw(
        context: CharacterDrawContext,
        size: Float,
        quarterTurns: Int,
        state: LudoPaws3DSceneState,
        nowMillis: Long,
    ) {
        // The approved stretched mapping puts logical boundaries 6 and 9 at 5/15 and 10/15.
        // Half the center square is consequently 2.5 base cells, not 1.5 cells.
        val halfSpan = size / 6f
        // Scale the complete group about its shared center. Leave a perimeter strip
        // even around the flowers, thresholds and fully outward-swinging doors.
        val artworkHalfSpan = halfSpan * .75f
        val previousSurface = context.surface
        context.surface = CharacterSurface.HOUSE_WALL
        context.life = null
        context.limbBendY = 0f
        context.limbBendZ = 0f
        drawGarden(context, size, halfSpan, quarterTurns)
        repeat(4) { index ->
            Matrix.setIdentityM(house, 0)
            Matrix.translateM(house, 0, size * .5f, size * .5f, 0f)
            Matrix.rotateM(house, 0, (quarterTurns * 90 + index * 90).toFloat(), 0f, 0f, 1f)
            Matrix.scaleM(house, 0, artworkHalfSpan, artworkHalfSpan, artworkHalfSpan)

            // A shallow projected facade gives the top-down board a readable storybook front.
            // Actual thickness, normals, roof planes and hinge rotation remain full 3D geometry.
            System.arraycopy(house, 0, facade, 0, 16)
            Matrix.translateM(facade, 0, 0f, 1f, .02f)
            Matrix.rotateM(facade, 0, -48.990913f, 1f, 0f, 0f)
            Matrix.scaleM(facade, 0, 1f, .60959f, 1f)
            context.surface = CharacterSurface.HOUSE_WALL
            part(context, facade, shell, 0f, 0f, 0f, 1f, 1f, 1f, CREAM)

            // All four roof planes climb to the same higher center summit.
            context.surface = CharacterSurface.HOUSE_ROOF
            part(context, house, roofBody, 0f, 0f, 0f, 1f, 1f, 1f, roofBase[index])
            tiledRoof.forEachIndexed { shade, mesh ->
                part(context, house, mesh, 0f, 0f, 0f, 1f, 1f, 1f, roofShades[index][shade])
            }
            beam(context, house, roofEave(-1), roofFront(), .018f, roofEdges[index])
            beam(context, house, roofEave(1), roofFront(), .018f, roofEdges[index])
            part(context, house, ridgeCap, 0f, 0f, 0f, 1f, 1f, 1f, roofShades[index][3])

            // Gold arched surround, recessed dark interior, then the moving wooden leaf.
            context.surface = CharacterSurface.HOUSE_WALL
            part(context, facade, arch, 0f, -.025f, .016f, .195f, .76f, .025f, GOLD)
            part(context, facade, arch, 0f, -.025f, .033f, .163f, .69f, .018f, INTERIOR)
            System.arraycopy(facade, 0, hinge, 0, 16)
            Matrix.translateM(hinge, 0, -.151f, -.025f, .057f)
            Matrix.rotateM(hinge, 0, -82f * doorOpenness(index, state, nowMillis), 0f, 1f, 0f)
            part(context, hinge, arch, .151f, 0f, 0f, .151f, .675f, .024f, WOOD)
            // Planks follow the door transform, including the knob and its little brass plate.
            for (plank in 1..4) {
                part(context, hinge, cube, plank * .0604f, -.23f, .027f,
                    .0035f, .22f, .002f, WOOD_GRAIN)
            }
            context.drawPart(hinge, .244f, -.30f, .041f, .022f, .028f, .025f, GOLD)

            for (side in intArrayOf(-1, 1)) {
                val x = side * .49f
                part(context, facade, arch, x, -.10f, .014f, .14f, .42f, .022f, GOLD)
                part(context, facade, arch, x, -.12f, .04f, .108f, .35f, .015f, GLASS)
                part(context, facade, cube, x, -.28f, .061f, .009f, .15f, .009f, GOLD)
                part(context, facade, cube, x, -.28f, .061f, .108f, .013f, .009f, GOLD)
                part(context, facade, cube, x, -.07f, .064f, .16f, .044f, .045f, TERRACOTTA)
                // Compact flower boxes keep the small center readable.
                for (flower in -1..1) {
                    val fx = x + flower * .082f
                    context.drawPart(facade, fx, -.13f, .085f, .065f, .055f, .035f, LEAF)
                    context.drawPart(facade, fx, -.17f, .107f, .028f, .035f, .016f,
                        if (flower == 0) WHITE else roofShades[index][3])
                    context.drawPart(facade, fx, -.17f, .124f, .011f, .013f, .008f, GOLD)
                }
            }
            // A little stone threshold stays inside the center square.
            part(context, facade, cube, 0f, -.008f, .075f, .25f, .025f, .045f, STONE)
        }
        context.surface = previousSurface
    }

    private fun drawGarden(c: CharacterDrawContext, size: Float, halfSpan: Float, turns: Int) {
        repeat(4) { side ->
            Matrix.setIdentityM(garden, 0)
            Matrix.translateM(garden, 0, size * .5f, size * .5f, 0f)
            Matrix.rotateM(garden, 0, (turns * 90 + side * 90).toFloat(), 0f, 0f, 1f)
            Matrix.scaleM(garden, 0, halfSpan, halfSpan, halfSpan)
            if (side == 0) {
                part(c, garden, cube, 0f, 0f, -.10f, 1f, 1f, .02f, GRASS)
                part(c, garden, grassTexture, 0f, 0f, 0f, 1f, 1f, 1f, GRASS_FLECK)
            }
            // Wide, flat lawn court reserved for four arrivals; no plants or raised rims.
            part(c, garden, cube, 0f, .875f, -.068f, .66f, .115f, .004f, COURTYARD_GRASS)
            // The middle remains a short walk from the home lane to the doorway.
            repeat(3) { step ->
                val y = .785f + step * .082f
                part(c, garden, cube, 0f, y, -.052f, .092f, .031f, .009f, PATH_EDGE)
                part(c, garden, cube, 0f, y - .002f, -.040f, .082f, .025f, .005f, STONE)
            }
            // One low corner bed per rotation. Courtyard slots stop well before these corners.
            c.drawPart(garden, .84f, .84f, -.028f, .115f, .115f, .012f, BED_SOIL)
            for (plant in 0..2) {
                val x = .79f + plant * .052f
                val y = if (plant == 1) .875f else .81f
                c.drawPart(garden, x, y, .002f, .057f, .047f, .025f, LEAF)
                val bloom = if (plant == 1) FLOWER_PINK else WHITE
                repeat(5) { petal ->
                    val angle = petal * 2f * PI.toFloat() / 5f
                    c.drawPart(garden, x + cos(angle) * .019f, y + sin(angle) * .019f,
                        .033f, .017f, .017f, .009f, bloom)
                }
                c.drawPart(garden, x, y, .043f, .012f, .012f, .006f, GOLD)
            }
        }
    }

    private fun doorOpenness(index: Int, state: LudoPaws3DSceneState, now: Long): Float {
        val phase = ((now + index * 3_100L) % 14_000L).toFloat()
        val idle = envelope(phase, 850f, 1_650f, 2_550f)
        val motion = state.forwardMotion ?: return idle
        if (motion.toPosition != LudoPathEncoding.HOME_POSITION) return idle
        val player = state.snapshot?.players?.firstOrNull { it.playerId == motion.playerId }
        if (player?.color != colors[index]) return idle
        val arrival = state.forwardStartedAtMillis + state.forwardDurationMillis -
            LudoPaws3DRenderCadencePolicy.FORWARD_LANDING_SETTLE_MILLIS
        val sinceOpening = (now - (arrival - 650L)).toFloat()
        return maxOf(idle, envelope(sinceOpening, 500f, 1_650f, 2_350f))
    }

    private fun envelope(time: Float, openAt: Float, closeAt: Float, end: Float): Float = when {
        time < 0f || time >= end -> 0f
        time < openAt -> smooth(time / openAt)
        time < closeAt -> 1f
        else -> 1f - smooth((time - closeAt) / (end - closeAt))
    }

    private fun smooth(value: Float): Float = value * value * (3f - 2f * value)

    private fun part(c: CharacterDrawContext, parent: FloatArray, mesh: MeshGeometry,
        x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, color: FloatArray,
    ) = c.drawGeometry(parent, mesh, x, y, z, sx, sy, sz, color)

    private fun beam(c: CharacterDrawContext, parent: FloatArray, a: V, b: V,
        radius: Float, color: FloatArray,
    ) {
        val d = b - a
        val length = d.length()
        // A unit cube's local Y axis is aligned with the 3D segment.
        val transform = beamTransform
        System.arraycopy(parent, 0, transform, 0, 16)
        Matrix.translateM(transform, 0, (a.x + b.x) * .5f, (a.y + b.y) * .5f, (a.z + b.z) * .5f)
        val angle = Math.toDegrees(kotlin.math.acos((d.y / length).coerceIn(-1f, 1f)).toDouble()).toFloat()
        if (angle > .001f) {
            Matrix.rotateM(transform, 0, angle,
                if (d.x == 0f && d.z == 0f) 1f else d.z, 0f, -d.x)
        }
        part(c, transform, cube, 0f, 0f, 0f, radius, length * .5f, radius, color)
    }

    private val beamTransform = FloatArray(16)

    private fun roofTiles(): List<MeshGeometry> {
        val batches = Array(4) { MeshBuilder() }
        for (side in intArrayOf(-1, 1)) {
            val apex = roofApex()
            val eave = roofEave(side)
            val ridge = roofFront()
            // Rows converge exactly toward the shared apex. Insets reveal dark tile seams.
            for (row in 0 until 7) {
                val t0 = row / 7f
                val t1 = (row + 1) / 7f
                val columns = row + 1
                for (column in 0 until columns) {
                    val u0 = column.toFloat() / columns
                    val u1 = (column + 1f) / columns
                    fun point(t: Float, u: Float) = apex +
                        ((eave - apex) * (1f - u) + (ridge - apex) * u) * t
                    val points = listOf(point(t0, u0), point(t1, u0), point(t1, u1), point(t0, u1))
                    val center = points.reduce { a, b -> a + b } * .25f
                    val inset = points.map { center + (it - center) * .94f + v(0f, 0f, .004f) }
                    val batch = batches[(row + column + if (side < 0) 1 else 0) % 4]
                    // The first row starts at an apex, so its first and last corners coincide.
                    batch.triangle(inset[0], inset[1], inset[2])
                    if (row > 0) batch.triangle(inset[0], inset[2], inset[3])
                }
            }
        }
        return batches.map { it.build() }
    }

    /** Rounded ridge tiles catch an upper-left highlight instead of reading as a dark crease. */
    private fun raisedRidge(): MeshGeometry = MeshBuilder().apply {
        val apex = roofApex()
        val front = roofFront()
        fun point(row: Int, segment: Int): V {
            val t = row / 8f
            val center = apex + (front - apex) * t
            val angle = segment * PI.toFloat() / 8f
            // The tapered center joins all four colored caps at one exact point.
            val radius = if (row == 0) 0f else .025f - t * .004f
            return center + v(cos(angle) * radius, 0f, .020f + sin(angle) * radius)
        }
        for (row in 0 until 8) {
            for (segment in 0 until 8) {
                quad(point(row, segment), point(row + 1, segment),
                    point(row + 1, segment + 1), point(row, segment + 1))
            }
            // Side skirts seat the rounded cap into both roof planes, without a floating gap.
            for (edge in intArrayOf(0, 8)) {
                val a = point(row, edge)
                val b = point(row + 1, edge)
                val down = v(0f, 0f, -.045f)
                quad(a, b, b + down, a + down)
            }
        }
        // Close the exposed gable end so the ridge reads as solid roof trim.
        for (segment in 0 until 8) {
            triangle(front + v(0f, 0f, -.025f), point(8, segment + 1), point(8, segment))
        }
    }.build()

    private data class V(val x: Float, val y: Float, val z: Float) {
        operator fun plus(other: V) = V(x + other.x, y + other.y, z + other.z)
        operator fun minus(other: V) = V(x - other.x, y - other.y, z - other.z)
        operator fun times(scale: Float) = V(x * scale, y * scale, z * scale)
        fun length() = sqrt(x * x + y * y + z * z)
    }

    private class MeshBuilder {
        private val vertices = ArrayList<Float>()
        private val indices = ArrayList<Short>()
        fun triangle(a: V, b: V, c: V) {
            val u = b - a
            val w = c - a
            var normal = V(u.y * w.z - u.z * w.y, u.z * w.x - u.x * w.z, u.x * w.y - u.y * w.x)
            val length = normal.length()
            if (length < .000001f) return
            normal = normal * (1f / length)
            // No face culling; orient visible front normals consistently for both roof halves.
            if (normal.z < 0f) normal = normal * -1f
            for (point in arrayOf(a, b, c)) {
                indices.add((vertices.size / 6).toShort())
                vertices.add(point.x); vertices.add(point.y); vertices.add(point.z)
                vertices.add(normal.x); vertices.add(normal.y); vertices.add(normal.z)
            }
        }
        fun quad(a: V, b: V, c: V, d: V) {
            triangle(a, b, c)
            triangle(a, c, d)
        }
        fun prism(points: List<V>, back: Float, front: Float) {
            val center = points.reduce { a, b -> a + b } * (1f / points.size)
            points.indices.forEach { index ->
                val a = points[index]
                val b = points[(index + 1) % points.size]
                val af = V(a.x, a.y, front)
                val bf = V(b.x, b.y, front)
                val ab = V(a.x, a.y, back)
                val bb = V(b.x, b.y, back)
                triangle(V(center.x, center.y, front), af, bf)
                triangle(V(center.x, center.y, back), bb, ab)
                quad(af, ab, bb, bf)
            }
        }
        fun build() = MeshGeometry(vertices.toFloatArray(), indices.toShortArray())
    }

    private companion object {
        fun v(x: Float, y: Float, z: Float = 0f) = V(x, y, z)
        // One geometry definition keeps the body, tiles, edging and cap aligned.
        fun roofApex() = v(0f, 0f, .70f)
        // Pull the entire gable edge inward; its Z stays on the extended facade plane.
        fun roofEave(side: Int) = v(side * .90f, .90f, .135f)
        fun roofFront() = v(0f, .54f, .549f)
        fun rgb(hex: Int) = floatArrayOf(((hex shr 16) and 255) / 255f,
            ((hex shr 8) and 255) / 255f, (hex and 255) / 255f, 1f)
        fun tint(base: FloatArray, factor: Float) = floatArrayOf(
            (base[0] * factor).coerceAtMost(1f), (base[1] * factor).coerceAtMost(1f),
            (base[2] * factor).coerceAtMost(1f), 1f)
        val CREAM = rgb(0xFFE6B5)
        val GOLD = rgb(0xF9B63D)
        val WOOD = rgb(0xA95A24)
        val WOOD_GRAIN = rgb(0x713817)
        val INTERIOR = rgb(0x302018)
        val GLASS = rgb(0x328ED2)
        val TERRACOTTA = rgb(0xB96B32)
        val LEAF = rgb(0x409D35)
        val WHITE = rgb(0xFFF5D9)
        val STONE = rgb(0xEAD9B8)
        val GRASS = rgb(0x8ABD63)
        val GRASS_FLECK = rgb(0x9BCB73)
        val COURTYARD_GRASS = rgb(0x95C66D)
        val PATH_EDGE = rgb(0xBBA782)
        val BED_SOIL = rgb(0x768C46)
        val FLOWER_PINK = rgb(0xF18CA0)
    }
}
