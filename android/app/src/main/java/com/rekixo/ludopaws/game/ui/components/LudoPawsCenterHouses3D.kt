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
    private data class CornerBloom(
        val x: Float, val y: Float, val height: Float, val size: Float, val pink: Boolean,
    )
    private val cornerBlooms = listOf(
        CornerBloom(.765f, .785f, .068f, .76f, false),
        CornerBloom(.825f, .845f, .096f, .94f, true),
        CornerBloom(.885f, .795f, .078f, .82f, false),
        CornerBloom(.875f, .890f, .060f, .62f, true),
        CornerBloom(.775f, .885f, .050f, .57f, false),
    )
    private val colors = arrayOf("BLUE", "RED", "GREEN", "YELLOW")
    // Match the red yard's soft coral-pink perimeter instead of saturated scarlet.
    private val roofs = arrayOf(
        rgb(0x268DE2), rgb(requireNotNull(LudoPawsYardPalettePolicy.forTeam("RED")).bandLight),
        rgb(0x41B854), rgb(0xF3C43B),
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
    private val terrain = MeshBuilder().apply {
        // Real surface relief with flat arrival courts, not a painted rectangle.
        val subdivisions = 32
        fun point(row: Int, col: Int): V {
            val x = -.96f + col * 1.92f / subdivisions
            val y = -.96f + row * 1.92f / subdivisions
            return v(x, y, gardenHeight(x, y))
        }
        for (row in 0 until subdivisions) {
            for (col in 0 until subdivisions) {
                quad(point(row, col), point(row, col + 1),
                    point(row + 1, col + 1), point(row + 1, col))
            }
        }
    }.build()
    private val turfEdge = MeshBuilder().apply {
        val corners = listOf(v(-1f, -1f), v(1f, -1f), v(1f, 1f), v(-1f, 1f))
        corners.indices.forEach { index ->
            val a = corners[index]
            val b = corners[(index + 1) % corners.size]
            // A sloped turf lip exposes depth even with the board's overhead camera.
            quad(v(a.x * .96f, a.y * .96f, -.025f),
                v(b.x * .96f, b.y * .96f, -.025f),
                v(b.x * .98f, b.y * .98f, -.075f),
                v(a.x * .98f, a.y * .98f, -.075f))
        }
    }.build()
    private val pathStone = MeshBuilder().apply {
        val outline = listOf(v(-.75f, -1f), v(.65f, -.95f), v(1f, -.5f),
            v(.95f, .7f), v(.5f, 1f), v(-.65f, .95f), v(-1f, .4f), v(-.95f, -.45f))
        outline.indices.forEach { index ->
            val a = outline[index]
            val b = outline[(index + 1) % outline.size]
            val topA = v(a.x * .82f, a.y * .82f, 1f)
            val topB = v(b.x * .82f, b.y * .82f, 1f)
            triangle(v(0f, 0f, 1f), topA, topB)
            quad(topA, topB, v(b.x, b.y, -.5f), v(a.x, a.y, -.5f))
        }
    }.build()
    private val grassBlades = MeshBuilder().apply {
        // One cached mesh per corner: curled blades stay out of paths and arrival slots.
        for (row in 0..4) {
            for (col in 0..4) {
                val x = .735f + col * .045f
                val y = .735f + row * .045f
                val base = v(x, y, gardenHeight(x, y) + .003f)
                repeat(3) { blade ->
                    val angle = (row * 17 + col * 31 + blade * 113) * PI.toFloat() / 180f
                    val spread = v(cos(angle) * .020f, sin(angle) * .020f, 0f)
                    val width = v(-sin(angle) * .004f, cos(angle) * .004f, 0f)
                    val height = .045f + (row + col + blade) % 3 * .012f
                    val middle = base + spread * .45f + v(0f, -.011f, height * .65f)
                    val tip = base + spread + v(0f, -.025f, height)
                    quad(base - width, base + width, middle + width * .6f, middle - width * .6f)
                    triangle(middle - width * .6f, middle + width * .6f, tip)
                }
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
                c.surface = CharacterSurface.GARDEN_GROUND
                part(c, garden, cube, 0f, 0f, -.13f, .995f, .995f, .04f, EARTH)
                part(c, garden, turfEdge, 0f, 0f, 0f, 1f, 1f, 1f, TURF_EDGE)
                part(c, garden, terrain, 0f, 0f, 0f, 1f, 1f, 1f, GRASS)
            }
            // Low irregular slabs: chamfered tops, visible side faces and contact shade.
            repeat(3) { step ->
                val y = .785f + step * .082f
                c.surface = CharacterSurface.GARDEN_GROUND
                c.drawPart(garden, .012f, y + .012f, -.020f, .111f, .043f, .003f, CONTACT_SHADE)
                c.surface = CharacterSurface.GARDEN_STONE
                part(c, garden, pathStone, if (step == 1) -.008f else .004f, y, -.004f,
                    .103f, .037f, .018f, if (step == 1) PATH_STONE_LIGHT else STONE)
            }
            c.surface = CharacterSurface.GARDEN_GROUND
            part(c, garden, grassBlades, 0f, 0f, 0f, 1f, 1f, 1f, GRASS_TIPS)
            // One continuous planted bed: low leaves overlap the turf and neighbouring plants.
            // Individual dark oval bases made the old flowers look like separate placed objects.
            c.surface = CharacterSurface.GARDEN_LEAF
            cornerBlooms.forEachIndexed { index, plant ->
                val ground = gardenHeight(plant.x, plant.y)
                c.drawPart(garden, plant.x, plant.y, ground - .006f,
                    .065f, .058f, .027f, LEAF_BASE)
                repeat(4) { leaf ->
                    val angle = (index * 37 + leaf * 93) * PI.toFloat() / 180f
                    val x = plant.x + cos(angle) * .034f
                    val y = plant.y + sin(angle) * .028f
                    // Each spreading leaf is planted in the actual terrain, not suspended above it.
                    c.drawPart(garden, x, y, gardenHeight(x, y) + .010f,
                        .039f, .020f, .013f, if (leaf % 2 == 0) LEAF_BASE else LEAF,
                        rotateZ = angle * 180f / PI.toFloat())
                }
            }
            cornerBlooms.forEachIndexed { index, plant ->
                val ground = gardenHeight(plant.x, plant.y)
                val flowerY = plant.y - .012f - index % 2 * .009f
                val bloomZ = ground + plant.height
                // Short, differently sized stems emerge through the shared foliage.
                beam(c, garden, v(plant.x, plant.y, ground - .004f),
                    v(plant.x, flowerY, bloomZ - .006f), .0035f, LEAF_DARK)
                c.drawPart(garden, plant.x - .014f, plant.y - .003f,
                    ground + plant.height * .45f, .026f, .012f, .009f, LEAF_LIGHT,
                    rotateZ = -32f + index * 11f)
                // A green calyx joins petals directly to the stem; smaller blooms nest in leaves.
                c.drawPart(garden, plant.x, flowerY, bloomZ - .010f,
                    .020f * plant.size, .018f * plant.size, .012f, LEAF)
                repeat(5) { petal ->
                    val angle = (petal * 72 + index * 29) * PI.toFloat() / 180f
                    c.drawPart(garden, plant.x + cos(angle) * .015f * plant.size,
                        flowerY + sin(angle) * .013f * plant.size, bloomZ,
                        .015f * plant.size, .013f * plant.size, .008f * plant.size,
                        if (plant.pink) FLOWER_PINK else FLOWER_CREAM,
                        rotateX = sin(angle) * 18f, rotateY = cos(angle) * 18f,
                        rotateZ = angle * 180f / PI.toFloat())
                }
                c.drawPart(garden, plant.x, flowerY, bloomZ + .007f,
                    .008f * plant.size, .008f * plant.size, .006f * plant.size, GOLD)
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
        // Courts stay level; only the corner beds gain terrain relief.
        fun gardenHeight(x: Float, y: Float): Float {
            val ax = kotlin.math.abs(x)
            val ay = kotlin.math.abs(y)
            if (ax < .69f || ay < .69f) return -.025f
            val dx = ax - .825f
            val dy = ay - .825f
            val edgeFade = ((.96f - maxOf(ax, ay)) / .06f).coerceIn(0f, 1f)
            return -.025f + kotlin.math.exp(-(dx * dx + dy * dy) / .012f) * .085f * edgeFade
        }
        val GRASS = rgb(0x79AE4B)
        val GRASS_TIPS = rgb(0x9BCB5C)
        val TURF_EDGE = rgb(0x527D31)
        val EARTH = rgb(0x76513A)
        val CONTACT_SHADE = rgb(0x405C2C)
        val LEAF_BASE = rgb(0x57943B)
        val LEAF_DARK = rgb(0x286B29)
        val LEAF_LIGHT = rgb(0x69B646)
        val PATH_STONE_LIGHT = rgb(0xF0DFC0)
        val FLOWER_CREAM = rgb(0xF4EBCB)
        val FLOWER_PINK = rgb(0xDF96A0)
    }
}
