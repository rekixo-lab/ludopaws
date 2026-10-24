package com.rekixo.ludopaws

import android.opengl.Matrix
import com.rekixo.ludopaws.feature.characters.rendering.CharacterDrawContext
import com.rekixo.ludopaws.feature.characters.rendering.CharacterSurface
import com.rekixo.ludopaws.rendering.meshes.MeshGeometry
import com.rekixo.ludopaws.rendering.meshes.SphereGeometry
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Four rigid roof wedges share one apex; only the separately hinged doors move. */
internal class LudoPawsCenterHouses3D {
    private val house = FloatArray(16)
    private val facade = FloatArray(16)
    private val plaqueTransform = FloatArray(16)
    private val finishLettering by lazy {
        listOf("WINNER", "2ND", "3RD", "4TH", "HOME").associateWith(::lettering)
    }
    private val lampTint = floatArrayOf(1f, 1f, 1f, 1f)
    private val reflectionTint = floatArrayOf(1f, 1f, 1f, .2f)
    private val lightPool = MeshBuilder().apply {
        quad(v(-1f, -1f), v(1f, -1f), v(1f, 1f), v(-1f, 1f))
    }.build()
    private val lamps = buildList {
        repeat(6) { column -> add((-.56f + column * .224f) to -.153f) }
        add(.685f to 0f)
        repeat(6) { column -> add((.56f - column * .224f) to .153f) }
        add(-.685f to 0f)
    }
    private val hinge = FloatArray(16)
    private val garden = FloatArray(16)
    private data class CornerBloom(
        val x: Float, val y: Float, val height: Float, val size: Float, val pink: Boolean,
    )
    private val cornerBlooms = listOf(
        // Low corner planting stays outside the arrival slots.
        CornerBloom(.708f, .795f, .060f, .82f, false),
        CornerBloom(.718f, .902f, .052f, .66f, true),
        CornerBloom(.806f, .705f, .072f, .94f, false),
        CornerBloom(.900f, .715f, .056f, .67f, true),
        CornerBloom(.735f, .735f, .048f, .55f, false),
    )
    private val colors = arrayOf("BLUE", "RED", "GREEN", "YELLOW")
    // Roofs and routes share the board-only team palette; character materials stay independent.
    private val roofs = arrayOf(
        rgb(LudoPawsBoardPalette.sky), rgb(LudoPawsBoardPalette.coral),
        rgb(LudoPawsBoardPalette.jade), rgb(LudoPawsBoardPalette.honey),
    )
    private val roofShades = roofs.map { base ->
        Array(4) { shade -> tint(base, .91f + shade * .055f) }
    }
    private val roofEdges = roofs.map { tint(it, .68f) }
    private val roofBase = roofs.map { tint(it, .66f) }
    private val tiledRoof = roofTiles()
    private val ridgeCap = raisedRidge()
    private val fenceMeshes = gardenFence()
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
    private val entryJamb = MeshBuilder().apply {
        val outline = mutableListOf(v(-1f, 0f), v(-1f, -.65f))
        for (step in 0..16) {
            val angle = PI.toFloat() - step * PI.toFloat() / 16f
            outline.add(v(cos(angle), -.65f - sin(angle) * .35f))
        }
        outline.add(v(1f, 0f))
        outline.indices.forEach { index ->
            val next = (index + 1) % outline.size
            fun outer(i: Int) = v(outline[i].x * .195f, outline[i].y * .76f - .025f, .041f)
            fun inner(i: Int) = v(outline[i].x * .163f, outline[i].y * .69f - .025f, .055f)
            quad(outer(index), outer(next), inner(next), inner(index))
        }
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
    private val stoneBlock = MeshBuilder().apply {
        // Cut corners and sloping bevels make the cap, plinth and masonry read as solid stone.
        val outline = listOf(v(-.78f, -1f), v(.78f, -1f), v(1f, -.78f), v(1f, .78f),
            v(.78f, 1f), v(-.78f, 1f), v(-1f, .78f), v(-1f, -.78f))
        fun ring(point: V, scale: Float, z: Float) = v(point.x * scale, point.y * scale, z)
        outline.indices.forEach { index ->
            val a = outline[index]
            val b = outline[(index + 1) % outline.size]
            val topA = ring(a, .87f, 1f)
            val topB = ring(b, .87f, 1f)
            triangle(v(0f, 0f, 1f), topA, topB)
            quad(topA, topB, ring(b, 1f, .62f), ring(a, 1f, .62f))
            quad(ring(a, 1f, .62f), ring(a, 1f, -.7f), ring(b, 1f, -.7f), ring(b, 1f, .62f))
            quad(ring(a, 1f, -.7f), ring(b, 1f, -.7f), ring(b, .92f, -1f), ring(a, .92f, -1f))
        }
    }.build()
    private val houseApron = MeshBuilder().apply {
        // A shallow cream terrace joins each facade to lawn and steps, without filling the courts.
        val inner = .66f
        val outer = .785f
        quad(v(-inner, inner, -.021f), v(inner, inner, -.021f),
            v(outer, outer, -.021f), v(-outer, outer, -.021f))
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

    private val staticArtwork by lazy {
        val recorder = StaticHouseRecorder()
        draw(recorder, 6f, 0, LudoPaws3DSceneState(), 0L, bakeStatic = true)
        recorder.batches()
    }
    private val staticTransform = FloatArray(16)

    // The roads use the very same bevelled solid as the entrance steps, baked once.
    private val roadTransform = FloatArray(16)
    private val roadShield = MeshBuilder().apply {
        prism(listOf(v(0f, -.26f), v(.22f, -.17f), v(.20f, .03f),
            v(.12f, .18f), v(0f, .27f), v(-.12f, .18f),
            v(-.20f, .03f), v(-.22f, -.17f)), -.008f, .008f)
    }.build()
    private val stoneRoadArtwork by lazy {
        val recorder = StaticHouseRecorder()
        val identity = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
        val tones = arrayOf(rgb(LudoPawsBoardPalette.stone),
            rgb(LudoPawsBoardPalette.stoneLight), rgb(LudoPawsBoardPalette.stoneShade))
        val joint = rgb(LudoPawsBoardPalette.joint)
        val shieldColor = rgb(LudoPawsBoardPalette.safe)
        val checkColor = rgb(LudoPawsBoardPalette.text)
        val safe = setOf(2 to 6, 6 to 12, 12 to 8, 8 to 2)
        fun boundary(index: Int): Float = when {
            index <= 6 -> index * 5f / 6f
            index <= 9 -> 5f + (index - 6) * 5f / 3f
            else -> 10f + (index - 9) * 5f / 6f
        }
        for (row in 0 until 15) for (col in 0 until 15) {
            val vertical = col in 6..8 && (row < 6 || row >= 9)
            val horizontal = row in 6..8 && (col < 6 || col >= 9)
            if (!vertical && !horizontal) continue
            // Preserve all coloured home lanes and the four coloured start cells.
            if ((col == 7 && row in 1..5) || (row == 7 && col in 1..5) ||
                (col == 7 && row in 9..13) || (row == 7 && col in 9..13) ||
                (row == 6 && col == 1) || (row == 1 && col == 8) ||
                (row == 8 && col == 13) || (row == 13 && col == 6)) continue
            val left = boundary(col)
            val top = boundary(row)
            val width = boundary(col + 1) - left
            val height = boundary(row + 1) - top
            val unit = minOf(width, height)
            val cx = left + width * .5f
            val cy = top + height * .5f
            val gap = unit * .065f
            val sx = width * .5f - gap
            val sy = height * .5f - gap
            val depth = unit * (.032f + ((row * 7 + col * 3) % 3) * .003f)
            recorder.surface = CharacterSurface.MATTE
            part(recorder, identity, stoneBlock, cx + unit * .012f, cy + unit * .018f,
                .004f, sx + unit * .006f, sy + unit * .006f, .003f, joint)
            recorder.surface = CharacterSurface.GARDEN_STONE
            part(recorder, identity, stoneBlock, cx, cy - unit * .012f,
                depth + .009f, sx, sy, depth, tones[(row * 7 + col * 11) % tones.size])
            if ((row to col) in safe) {
                // Keep safety readable on top of the slab instead of hiding the Canvas marker.
                val z = depth * 2f + .023f
                recorder.surface = CharacterSurface.MATTE
                part(recorder, identity, roadShield, cx, cy, z, unit, unit, unit, shieldColor)
                beam(recorder, identity, v(cx - unit * .10f, cy, z + .014f),
                    v(cx - unit * .025f, cy + unit * .075f, z + .014f), unit * .020f, checkColor)
                beam(recorder, identity, v(cx - unit * .025f, cy + unit * .075f, z + .014f),
                    v(cx + unit * .12f, cy - unit * .085f, z + .014f), unit * .020f, checkColor)
            }
        }
        recorder.batches()
    }

    fun drawStoneRoads(context: CharacterDrawContext, size: Float, quarterTurns: Int) {
        val previousSurface = context.surface
        context.life = null
        context.limbBendY = 0f
        context.limbBendZ = 0f
        Matrix.setIdentityM(roadTransform, 0)
        Matrix.translateM(roadTransform, 0, size * .5f, size * .5f, 0f)
        Matrix.rotateM(roadTransform, 0, quarterTurns * 90f, 0f, 0f, 1f)
        val scale = size / 15f
        Matrix.scaleM(roadTransform, 0, scale, scale, scale)
        Matrix.translateM(roadTransform, 0, -7.5f, -7.5f, 0f)
        stoneRoadArtwork.forEach { batch ->
            context.surface = batch.surface
            part(context, roadTransform, batch.geometry, 0f, 0f, 0f, 1f, 1f, 1f, batch.color)
        }
        context.surface = previousSurface
    }

    fun draw(
        context: CharacterDrawContext,
        size: Float,
        quarterTurns: Int,
        state: LudoPaws3DSceneState,
        nowMillis: Long,
        bakeStatic: Boolean = false,
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
        if (bakeStatic) {
            drawGarden(context, size, halfSpan, quarterTurns)
        } else {
            // Baking preserves each part's original material coordinates and normals.
            // Only this uniform group transform changes with board size/perspective.
            val batches = staticArtwork
            Matrix.setIdentityM(staticTransform, 0)
            Matrix.translateM(staticTransform, 0, size * .5f, size * .5f, 0f)
            Matrix.rotateM(staticTransform, 0, quarterTurns * 90f, 0f, 0f, 1f)
            Matrix.scaleM(staticTransform, 0, halfSpan, halfSpan, halfSpan)
            Matrix.translateM(staticTransform, 0, -3f, -3f, 0f)
            batches.forEach { batch ->
                context.surface = batch.surface
                part(context, staticTransform, batch.geometry, 0f, 0f, 0f, 1f, 1f, 1f, batch.color)
            }
        }
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
            if (bakeStatic) {
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
                // Threshold belongs to the static architecture, below the hinged leaf.
                part(context, facade, cube, 0f, -.008f, .075f, .25f, .025f, .045f, STONE)
            }
            if (bakeStatic) return@repeat
            context.surface = CharacterSurface.HOUSE_WALL
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

            val player = state.snapshot?.players?.firstOrNull { it.color == colors[index] }
            val entry = player?.let { state.homeEntries[it.playerId] }
            if (entry != null) {
                val reveal = LudoPawsHomeEntryPolicy.plaque(entry.elapsed(nowMillis))
                val age = if (entry.startedAtMillis > 0L)
                    (entry.elapsed(nowMillis) - LudoPawsHomeEntryPolicy.PLAQUE_START_MS).coerceAtLeast(0L)
                    else 3_000L
                if (reveal > 0f) drawFinishPlaque(context, house, index, entry.rank, reveal, nowMillis, age)
            } else if (state.snapshot?.status == "FINISHED" && player != null) {
                val rank = LudoPawsFinishPresentation.ranks(state.snapshot)[player.playerId] ?: 0
                // Last place is a known result even though its pawns have not all arrived.
                if (rank == 4 || (rank == 2 && state.snapshot.players.size == 2))
                    drawFinishPlaque(context, house, index, rank, 1f, nowMillis, 3_000L)
            }
        }
        context.partyEnergy = 0f
        context.surface = previousSurface
    }

    /** Foreground jamb occludes entering bodies while leaving the doorway aperture empty. */
    fun drawEntryForeground(
        c: CharacterDrawContext, size: Float, turns: Int, state: LudoPaws3DSceneState, now: Long,
    ) {
        val previous = c.surface
        c.life = null
        c.limbBendY = 0f
        c.limbBendZ = 0f
        repeat(4) { index ->
            val player = state.snapshot?.players?.firstOrNull { it.color == colors[index] } ?: return@repeat
            val entry = state.homeEntries[player.playerId] ?: return@repeat
            val elapsed = entry.elapsed(now)
            if ((0..3).none { LudoPawsHomeEntryPolicy.walk(elapsed, it) in .55f..0.9999f })
                return@repeat
            Matrix.setIdentityM(house, 0)
            Matrix.translateM(house, 0, size * .5f, size * .5f, 0f)
            Matrix.rotateM(house, 0, (turns * 90 + index * 90).toFloat(), 0f, 0f, 1f)
            Matrix.scaleM(house, 0, size / 6f * .75f, size / 6f * .75f, size / 6f * .75f)
            System.arraycopy(house, 0, facade, 0, 16)
            Matrix.translateM(facade, 0, 0f, 1f, .02f)
            Matrix.rotateM(facade, 0, -48.990913f, 1f, 0f, 0f)
            Matrix.scaleM(facade, 0, 1f, .60959f, 1f)
            c.surface = CharacterSurface.HOUSE_WALL
            part(c, facade, entryJamb, 0f, 0f, 0f, 1f, 1f, 1f, GOLD)
        }
        c.surface = previous
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
            c.surface = CharacterSurface.GARDEN_STONE
            part(c, garden, houseApron, 0f, 0f, 0f, 1f, 1f, 1f, TERRACE_STONE)
            // Broken low edging defines the building without closing the lane-to-door entrance.
            for (edge in intArrayOf(-1, 1)) {
                repeat(3) { block ->
                    part(c, garden, stoneBlock, edge * (.285f + block * .145f), .785f, -.006f,
                        .067f, .022f, .012f, if (block % 2 == 0) STONE else TERRACE_STONE)
                }
            }
            // Broad graduated steps have visible risers and shaded joints, like the reference.
            repeat(3) { step ->
                val y = .792f + step * .078f
                val stepWidth = .105f + step * .008f
                c.surface = CharacterSurface.GARDEN_GROUND
                c.drawPart(garden, .009f, y + .014f, -.020f, stepWidth + .009f, .041f, .003f, CONTACT_SHADE)
                c.surface = CharacterSurface.GARDEN_STONE
                part(c, garden, stoneBlock, 0f, y, .010f - step * .007f,
                    stepWidth, .031f, .018f, if (step == 1) PATH_STONE_LIGHT else STONE)
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
            // Low rails are inset and lean into the garden. The middle stays open at the steps.
            c.surface = CharacterSurface.GARDEN_STONE
            part(c, garden, fenceMeshes[0], 0f, 0f, 0f, 1f, 1f, 1f, FENCE_WOOD)
            part(c, garden, fenceMeshes[1], 0f, 0f, 0f, 1f, 1f, 1f, FENCE_GRAIN)
            part(c, garden, fenceMeshes[2], 0f, 0f, 0f, 1f, 1f, 1f, FENCE_PINS)
        }
    }

    /** Static, batched timber geometry for one edge; four rotations surround the square. */
    private fun gardenFence(): List<MeshGeometry> {
        val body = MeshBuilder()
        val grain = MeshBuilder()
        val pins = MeshBuilder()
        fun cross(a: V, b: V) = v(a.y * b.z - a.z * b.y,
            a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)
        fun timber(a: V, b: V, width: Float, depth: Float, post: Boolean) {
            val delta = b - a
            val direction = delta * (1f / delta.length())
            val across = if (post) v(1f, 0f) else v(0f, 1f)
            val front = cross(direction, across).let {
                // Both mirrored rail runs expose their grain toward the overhead camera.
                it * ((if (it.z < 0f) -1f else 1f) / it.length())
            }
            val profile = listOf(v(-.72f, -1f), v(.72f, -1f), v(1f, -.72f), v(1f, .72f),
                v(.72f, 1f), v(-.72f, 1f), v(-1f, .72f), v(-1f, -.72f))
            fun corner(origin: V, point: V) = origin + across * (point.x * width) + front * (point.y * depth)
            profile.indices.forEach { index ->
                val p = profile[index]
                val q = profile[(index + 1) % profile.size]
                body.quad(corner(a, p), corner(b, p), corner(b, q), corner(a, q))
                body.triangle(a, corner(a, q), corner(a, p))
                body.triangle(b, corner(b, p), corner(b, q))
            }
            // Slightly broken longitudinal grooves follow each timber's actual 3D axis.
            for (line in -1..1) {
                val offset = across * (line * width * .40f) + front * (depth + .0007f)
                val start = a + delta * (.10f + (line + 1) * .035f) + offset
                val end = a + delta * (.86f - (line + 1) * .025f) + offset
                val thin = across * .0008f
                grain.quad(start - thin, start + thin, end + thin, end - thin)
            }
            if (post) {
                for (t in floatArrayOf(.38f, .78f)) {
                    val center = a + delta * t + front * (depth + .0015f)
                    val horizontal = across * .0032f
                    val vertical = direction * .0038f
                    pins.quad(center - horizontal - vertical, center + horizontal - vertical,
                        center + horizontal + vertical, center - horizontal + vertical)
                }
            }
        }
        for (side in intArrayOf(-1, 1)) {
            // No wood crosses the +/- .214 entrance, wider than all three stone steps.
            for (x in floatArrayOf(.23f, .57f, .91f)) {
                timber(v(side * x, .971f, -.030f), v(side * x, .916f, .105f),
                    .016f, .011f, post = true)
            }
            for (height in floatArrayOf(.023f, .078f)) {
                val inwardY = .971f - (height + .030f) * (.055f / .135f)
                timber(v(side * .23f, inwardY, height), v(side * .968f, inwardY, height),
                    .009f, .011f, post = false)
            }
        }
        return listOf(body.build(), grain.build(), pins.build())
    }

    private fun drawFinishPlaque(
        c: CharacterDrawContext, parent: FloatArray, color: Int, rank: Int, reveal: Float,
        nowMillis: Long, celebrationAgeMillis: Long,
    ) {
        val metal = when (rank) { 1 -> GOLD; 2 -> SILVER; 3 -> BRONZE; else -> roofs[color] }
        val burst = (1f - celebrationAgeMillis / 3_000f).coerceIn(0f, 1f)
        c.partyEnergy = if (rank == 1) .65f + burst * .35f else if (rank in 2..3) .22f else .08f
        val clock = (nowMillis % 100_000L) / 1000f
        if (rank == 1) {
            // Two soft pools tint the actual arrival court below the illuminated sign.
            c.surface = CharacterSurface.PARTY_GLOW
            repeat(2) { side ->
                val phase = clock * .4f + side * 2
                val segment = phase.toInt()
                val blend = smooth(phase - segment)
                val from = PARTY_COLORS[segment % PARTY_COLORS.size]
                val to = PARTY_COLORS[(segment + 1) % PARTY_COLORS.size]
                for (channel in 0..2) reflectionTint[channel] = from[channel] + (to[channel] - from[channel]) * blend
                reflectionTint[3] = (.15f + .08f * sin(clock * PI.toFloat() + side)) * reveal
                part(c, parent, lightPool, if (side == 0) -.40f else .40f, 1.13f, .008f,
                    .35f, .16f, 1f, reflectionTint)
            }
        }
        System.arraycopy(parent, 0, plaqueTransform, 0, 16)
        Matrix.translateM(plaqueTransform, 0, 0f, 1.13f, .02f + reveal * .04f)
        val pop = .82f + .18f * reveal
        Matrix.scaleM(plaqueTransform, 0, pop, pop, pop)
        c.surface = CharacterSurface.GARDEN_STONE
        part(c, plaqueTransform, stoneBlock, 0f, .014f, -.018f, .74f, .19f, .035f, CONTACT_SHADE)
        c.surface = CharacterSurface.FINISH_METAL
        part(c, plaqueTransform, stoneBlock, 0f, 0f, .015f, .72f, .18f, .034f, metal)
        c.surface = CharacterSurface.HOUSE_WALL
        part(c, plaqueTransform, stoneBlock, 0f, 0f, .052f, .67f, .137f, .012f, roofEdges[color])
        c.surface = CharacterSurface.FINISH_METAL
        part(c, plaqueTransform, finishLettering.getValue(LudoPawsFinishPresentation.label(rank)),
            0f, 0f, .068f, 1f, 1f, 1f, metal)
        if (rank == 1) {
            lamps.forEachIndexed { index, position ->
                val base = PARTY_COLORS[index % PARTY_COLORS.size]
                val chase = .5f + .5f * sin(clock * 2f * PI.toFloat() - index * .90f)
                for (channel in 0..2) lampTint[channel] = base[channel] * (.48f + chase * .52f)
                lampTint[3] = (.17f + chase * .20f) * reveal
                c.surface = CharacterSurface.PARTY_GLOW
                part(c, plaqueTransform, lightPool, position.first, position.second, .106f,
                    .052f, .052f, 1f, lampTint)
                lampTint[3] = 1f
                c.surface = CharacterSurface.PARTY_BULB
                c.drawPart(plaqueTransform, position.first, position.second, .094f,
                    .022f, .022f, .019f, lampTint)
            }
        } else {
            c.surface = CharacterSurface.FINISH_METAL
            for (side in intArrayOf(-1, 1)) {
                c.drawPart(plaqueTransform, side * .62f, .095f, .072f, .014f, .014f, .012f, metal)
            }
        }
        c.partyEnergy = 0f
    }

    /** Batched solid letter blocks; thickness and normals are actual mesh geometry. */
    private fun lettering(text: String): MeshGeometry {
        val glyphs = mapOf(
            'W' to listOf("10001","10001","10001","10101","10101","11011","10001"),
            'I' to listOf("11111","00100","00100","00100","00100","00100","11111"),
            'N' to listOf("10001","11001","11001","10101","10011","10011","10001"),
            'E' to listOf("11111","10000","10000","11110","10000","10000","11111"),
            'R' to listOf("11110","10001","10001","11110","10100","10010","10001"),
            '2' to listOf("01110","10001","00001","00010","00100","01000","11111"),
            '3' to listOf("11110","00001","00001","01110","00001","00001","11110"),
            '4' to listOf("10010","10010","10010","11111","00010","00010","00010"),
            'D' to listOf("11110","10001","10001","10001","10001","10001","11110"),
            'T' to listOf("11111","00100","00100","00100","00100","00100","00100"),
            'H' to listOf("10001","10001","10001","11111","10001","10001","10001"),
            'O' to listOf("01110","10001","10001","10001","10001","10001","01110"),
            'M' to listOf("10001","11011","10101","10101","10001","10001","10001"),
        )
        val pixel = .033f
        val width = (text.length * 6 - 1) * pixel
        return MeshBuilder().apply {
            text.forEachIndexed { letter, ch ->
                glyphs.getValue(ch).forEachIndexed { row, line ->
                    line.forEachIndexed { column, filled ->
                        if (filled == '1') {
                            val x = (letter * 6 + column) * pixel - width * .5f
                            val y = (row - 3.5f) * pixel
                            prism(listOf(v(x, y), v(x + pixel, y),
                                v(x + pixel, y + pixel), v(x, y + pixel)), 0f, .032f)
                        }
                    }
                }
            }
        }.build()
    }

    private fun doorOpenness(index: Int, state: LudoPaws3DSceneState, now: Long): Float {
        val finished = state.snapshot?.players?.firstOrNull { it.color == colors[index] }
            ?.let { state.homeEntries[it.playerId] }
        if (finished != null) return LudoPawsHomeEntryPolicy.door(finished.elapsed(now))
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
            for (row in 0 until 6) {
                val t0 = row / 6f
                val t1 = (row + 1) / 6f
                val columns = row + 1
                for (column in 0 until columns) {
                    val u0 = column.toFloat() / columns
                    val u1 = (column + 1f) / columns
                    fun point(t: Float, u: Float) = apex +
                        ((eave - apex) * (1f - u) + (ridge - apex) * u) * t
                    val points = listOf(point(t0, u0), point(t1, u0), point(t1, u1), point(t0, u1))
                    val center = points.reduce { a, b -> a + b } * .25f
                    val lower = points.map { center + (it - center) * .93f + v(0f, 0f, .003f) }
                    // Raised tile lips and sloping bevels have real normals, so the
                    // light follows each tile instead of just alternating painted colors.
                    val upper = lower.mapIndexed { index, point ->
                        center + (point - center) * .88f +
                            v(0f, 0f, if (index == 1 || index == 2) .024f else .012f)
                    }
                    val shade = (row * 3 + column + if (side < 0) 1 else 0) % 4
                    val batch = batches[shade]
                    batch.triangle(upper[0], upper[1], upper[2])
                    if (row > 0) batch.triangle(upper[0], upper[2], upper[3])
                    for (edge in 0 until 4) {
                        val next = (edge + 1) % 4
                        batches[if (edge == 1) 0 else shade].quad(
                            lower[edge], lower[next], upper[next], upper[edge],
                        )
                    }
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

    companion object {
        @Volatile private var prepared: LudoPawsCenterHouses3D? = null
        private var preparing = false
        private val artworkWorker = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
            Thread(task, "LudoPawsArchitecture").apply { isDaemon = true }
        }

        @Synchronized
        fun prepare() {
            if (prepared != null || preparing) return
            preparing = true
            artworkWorker.execute {
                val result = runCatching {
                    LudoPawsCenterHouses3D().also { it.staticArtwork; it.stoneRoadArtwork }
                }
                synchronized(this) {
                    result.onSuccess { prepared = it }
                    preparing = false
                }
                result.onFailure { android.util.Log.w("LudoPawsArchitecture", "Could not prewarm architecture", it) }
            }
        }

        @Synchronized
        fun acquire(): LudoPawsCenterHouses3D = prepared?.also { prepared = null } ?: LudoPawsCenterHouses3D()

        private fun v(x: Float, y: Float, z: Float = 0f) = V(x, y, z)
        // One geometry definition keeps the body, tiles, edging and cap aligned.
        private fun roofApex() = v(0f, 0f, .70f)
        // Pull the entire gable edge inward; its Z stays on the extended facade plane.
        private fun roofEave(side: Int) = v(side * .90f, .90f, .135f)
        private fun roofFront() = v(0f, .54f, .549f)
        fun rgb(hex: Int) = floatArrayOf(((hex shr 16) and 255) / 255f,
            ((hex shr 8) and 255) / 255f, (hex and 255) / 255f, 1f)
        fun tint(base: FloatArray, factor: Float) = floatArrayOf(
            (base[0] * factor).coerceAtMost(1f), (base[1] * factor).coerceAtMost(1f),
            (base[2] * factor).coerceAtMost(1f), 1f)
        val CREAM = rgb(0xF2DFBA)
        val GOLD = rgb(0xE7BD6A)
        private val SILVER = rgb(0xDFE9F4)
        private val BRONZE = rgb(0xDEA16B)
        private val PARTY_COLORS = arrayOf(rgb(0xFFE784), rgb(0x7BEAF5), rgb(0xFF9ECC), rgb(0xFFF2C0))
        val WOOD = rgb(0xA46D3F)
        val WOOD_GRAIN = rgb(0x70472D)
        val INTERIOR = rgb(0x342A25)
        val GLASS = rgb(0x459FCB)
        val TERRACOTTA = rgb(0xB17B58)
        val LEAF = rgb(0x4F9169)
        val WHITE = rgb(0xF8EFD8)
        val STONE = rgb(0xE6DDC9)
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
        val GRASS = rgb(0x78A960)
        val GRASS_TIPS = rgb(0xB4CC84)
        val TURF_EDGE = rgb(0x4C7752)
        val EARTH = rgb(0x79614A)
        val CONTACT_SHADE = rgb(0x3D5844)
        val LEAF_BASE = rgb(0x679C68)
        val LEAF_DARK = rgb(0x356C4E)
        val LEAF_LIGHT = rgb(0x8AB676)
        val PATH_STONE_LIGHT = rgb(0xF1E6CF)
        val TERRACE_STONE = rgb(0xD9D3BB)
        val FENCE_WOOD = rgb(0xB98B58)
        val FENCE_GRAIN = rgb(0x79583B)
        val FENCE_PINS = rgb(0xD6BE8C)
        val FLOWER_CREAM = rgb(0xF7EEDB)
        val FLOWER_PINK = rgb(0xE6A0A1)
    }
}

/** Records immutable architecture once; translucent pieces retain their original draw order. */
private class StaticHouseRecorder : CharacterDrawContext() {
    data class Batch(val geometry: MeshGeometry, val color: FloatArray, val surface: CharacterSurface)
    private data class Key(val color: List<Float>, val surface: CharacterSurface)
    private class Chunk(val color: FloatArray, val surface: CharacterSurface) {
        val vertices = ArrayList<Float>()
        val locals = ArrayList<Float>()
        val indices = ArrayList<Short>()
    }
    private val opaque = linkedMapOf<Key, MutableList<Chunk>>()
    private val transparent = ArrayList<Chunk>()
    private val transform = FloatArray(16)
    private val inverse = FloatArray(16)

    override fun drawPart(parent: FloatArray, translateX: Float, translateY: Float, translateZ: Float,
        scaleX: Float, scaleY: Float, scaleZ: Float, color: FloatArray,
        rotateX: Float, rotateY: Float, rotateZ: Float) =
        drawGeometry(parent, SphereGeometry.standard, translateX, translateY, translateZ,
            scaleX, scaleY, scaleZ, color, rotateX, rotateY, rotateZ)

    override fun drawGeometry(parent: FloatArray, geometry: MeshGeometry,
        translateX: Float, translateY: Float, translateZ: Float,
        scaleX: Float, scaleY: Float, scaleZ: Float, color: FloatArray,
        rotateX: Float, rotateY: Float, rotateZ: Float) {
        System.arraycopy(parent, 0, transform, 0, 16)
        Matrix.translateM(transform, 0, translateX, translateY, translateZ)
        if (rotateX != 0f) Matrix.rotateM(transform, 0, rotateX, 1f, 0f, 0f)
        if (rotateY != 0f) Matrix.rotateM(transform, 0, rotateY, 0f, 1f, 0f)
        if (rotateZ != 0f) Matrix.rotateM(transform, 0, rotateZ, 0f, 0f, 1f)
        Matrix.scaleM(transform, 0, scaleX, scaleY, scaleZ)
        check(Matrix.invertM(inverse, 0, transform, 0))
        val count = geometry.vertices.size / 6
        val chunk = if (color[3] < 1f) {
            Chunk(color.copyOf(), surface).also { transparent.add(it) }
        } else {
            val chunks = opaque.getOrPut(Key(color.toList(), surface)) { mutableListOf() }
            chunks.lastOrNull()?.takeIf { it.vertices.size / 6 + count < 65536 }
                ?: Chunk(color.copyOf(), surface).also { chunks.add(it) }
        }
        val offset = chunk.vertices.size / 6
        for (i in geometry.vertices.indices step 6) {
            val x = geometry.vertices[i]; val y = geometry.vertices[i + 1]; val z = geometry.vertices[i + 2]
            chunk.vertices.add(transform[0] * x + transform[4] * y + transform[8] * z + transform[12])
            chunk.vertices.add(transform[1] * x + transform[5] * y + transform[9] * z + transform[13])
            chunk.vertices.add(transform[2] * x + transform[6] * y + transform[10] * z + transform[14])
            val nx = geometry.vertices[i + 3]; val ny = geometry.vertices[i + 4]; val nz = geometry.vertices[i + 5]
            val a = inverse[0] * nx + inverse[1] * ny + inverse[2] * nz
            val b = inverse[4] * nx + inverse[5] * ny + inverse[6] * nz
            val c = inverse[8] * nx + inverse[9] * ny + inverse[10] * nz
            val length = sqrt(a * a + b * b + c * c).coerceAtLeast(.000001f)
            chunk.vertices.add(a / length); chunk.vertices.add(b / length); chunk.vertices.add(c / length)
            chunk.locals.add(x); chunk.locals.add(y); chunk.locals.add(z)
        }
        geometry.indices.forEach { chunk.indices.add(((it.toInt() and 65535) + offset).toShort()) }
    }

    fun batches(): List<Batch> = (opaque.values.flatten() + transparent).map {
        Batch(MeshGeometry(it.vertices.toFloatArray(), it.indices.toShortArray(), it.locals.toFloatArray()),
            it.color, it.surface)
    }
}
