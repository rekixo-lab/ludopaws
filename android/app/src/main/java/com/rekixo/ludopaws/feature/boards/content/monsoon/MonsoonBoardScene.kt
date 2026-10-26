package com.rekixo.ludopaws.feature.boards.content.monsoon

import android.opengl.Matrix
import com.rekixo.ludopaws.LudoPathEncoding
import com.rekixo.ludopaws.LudoPaws3DCharacterPolicy
import com.rekixo.ludopaws.LudoPaws3DRenderCadencePolicy
import com.rekixo.ludopaws.LudoPaws3DSceneState
import com.rekixo.ludopaws.LudoPawsFxBoardGeometry
import com.rekixo.ludopaws.LudoPawsPawnMotion
import com.rekixo.ludopaws.feature.characters.domain.model.AnimalSpecies
import com.rekixo.ludopaws.feature.characters.rendering.CharacterDrawContext
import com.rekixo.ludopaws.feature.characters.rendering.CharacterHopPlayback
import com.rekixo.ludopaws.feature.characters.rendering.CharacterMotion
import com.rekixo.ludopaws.feature.characters.rendering.CharacterRegistry
import com.rekixo.ludopaws.feature.characters.rendering.CharacterSurface
import com.rekixo.ludopaws.rendering.meshes.MeshGeometry
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal class MonsoonBoardScene {
    private val transform = FloatArray(16)
    private val prints = Array(48) { Print() }
    private val stepImpacts = Array(12) { Impact() }
    private var nextPrint = 0
    private var nextImpact = 0
    private var matchId: String? = null
    private var observedMotion: LudoPawsPawnMotion? = null
    private var observedStart = -1L
    private var lastLanding = 0
    private val printColor = floatArrayOf(.30f, .20f, .12f, 1f)
    private val slicks = buildList {
        for (row in 0..14) for (column in 0..14) {
            if ((row + column) % 4 != 0) continue
            if (column in 6..8 && (row < 6 || row > 8) ||
                row in 6..8 && (column < 6 || column > 8)) {
                add(Patch(axis(column + 1) - 2f, axis(row) + 2f, 1.2f, .72f))
            }
        }
    }
    private val puddleX = floatArrayOf(10f, 39f, 110f, 139f, 110f, 139f, 10f, 39f)
    private val puddleY = floatArrayOf(37f, 11f, 37f, 11f, 137f, 111f, 137f, 111f)
    private val mudColor = floatArrayOf(.39f, .28f, .19f, 1f)
    private val waterColor = floatArrayOf(.32f, .49f, .51f, 1f)
    private val rainColor = floatArrayOf(.72f, .84f, .81f, 1f)
    private val impactColor = floatArrayOf(.60f, .74f, .71f, 1f)
    private val mudSplashColor = floatArrayOf(.62f, .45f, .29f, 1f)
    private val rimColor = floatArrayOf(.39f, .43f, .36f, 1f)
    private val banks = Builder().apply {
        puddleX.indices.forEach { index ->
            val wide = index % 2 == 0
            ovalRing(puddleX[index], puddleY[index], if (wide) 6.4f else 5.4f,
                if (wide) 4.1f else 3.5f, .84f, .18f, 1.05f)
        }
    }.build()
    private val pools = Builder().apply {
        puddleX.indices.forEach { index ->
            val wide = index % 2 == 0
            disc(puddleX[index], puddleY[index], if (wide) 5.4f else 4.5f,
                if (wide) 3.4f else 2.9f, .38f)
        }
        slicks.forEach { disc(it.x, it.y, it.radiusX, it.radiusY, .38f) }
    }.build()
    private val pawPrint = Builder().apply {
        disc(0f, .15f, .48f, .42f, 0f, 12)
        repeat(4) { toe -> disc(-.48f + toe * .32f, -.40f, .17f, .22f, 0f, 8) }
    }.build()
    private val hoofPrint = Builder().apply {
        disc(-.21f, 0f, .17f, .55f, 0f, 12)
        disc(.21f, 0f, .17f, .55f, 0f, 12)
    }.build()
    private val birdPrint = Builder().apply {
        repeat(3) { toe ->
            val targetX = (toe - 1) * .48f
            quad(Point(-.08f, .35f, 0f), Point(.08f, .35f, 0f),
                Point(targetX + .08f, -.65f, 0f), Point(targetX - .08f, -.65f, 0f))
        }
    }.build()
    private val smear = Builder().apply { disc(0f, 0f, .5f, 1f, 0f, 12) }.build()
    private val roadRims = Builder().apply {
        for (row in 0..14) for (column in 0..14) {
            if (!(column in 6..8 && (row < 6 || row > 8)) &&
                !(row in 6..8 && (column < 6 || column > 8))) continue
            val left = axis(column) + .8f
            val top = axis(row) + .8f
            val right = axis(column + 1) - .8f
            val bottom = axis(row + 1) - .8f
            val outer = arrayOf(Point(left, top, .2f), Point(right, top, .2f),
                Point(right, bottom, .2f), Point(left, bottom, .2f))
            val inner = arrayOf(Point(left + .24f, top + .24f, .64f), Point(right - .24f, top + .24f, .64f),
                Point(right - .24f, bottom - .24f, .64f), Point(left + .24f, bottom - .24f, .64f))
            repeat(4) { side -> quad(outer[side], outer[(side + 1) % 4], inner[(side + 1) % 4], inner[side]) }
        }
    }.build()
    private val ripple = Builder().apply { ovalRing(0f, 0f, 1f, 1f, .85f, 0f, .08f) }.build()
    private val crown = Builder().apply {
        repeat(12) { side ->
            val angle = side * 2f * PI.toFloat() / 12f
            val next = (side + 1) * 2f * PI.toFloat() / 12f
            val middle = (angle + next) * .5f
            triangle(Point(cos(angle) * .55f, sin(angle) * .55f, 0f),
                Point(cos(next) * .55f, sin(next) * .55f, 0f),
                Point(cos(middle) * .82f, sin(middle) * .82f, if (side % 2 == 0) 1.1f else .65f))
        }
    }.build()
    private val drop = Builder().apply {
        val tip = Point(0f, 1f, 0f)
        val tail = Point(0f, -1f, .2f)
        triangle(tip, Point(-.16f, .3f, .1f), tail)
        triangle(tip, tail, Point(.16f, .3f, .1f))
    }.build()

    fun draw(context: CharacterDrawContext, size: Float, turns: Int, nowMillis: Long,
        animate: Boolean, state: LudoPaws3DSceneState) {
        if (matchId != state.snapshot?.matchId) {
            reset()
            matchId = state.snapshot?.matchId
        }
        if (animate) observeSteps(state, nowMillis) else resetTrails()
        val previousSurface = context.surface
        context.life = null
        context.limbBendY = 0f
        context.limbBendZ = 0f
        Matrix.setIdentityM(transform, 0)
        Matrix.translateM(transform, 0, size * .5f, size * .5f, 0f)
        Matrix.rotateM(transform, 0, turns * 90f, 0f, 0f, 1f)
        Matrix.scaleM(transform, 0, size / 150f, size / 150f, size / 150f)
        Matrix.translateM(transform, 0, -75f, -75f, 0f)
        context.surface = CharacterSurface.MATTE
        context.drawGeometry(transform, banks, 0f, 0f, 0f, 1f, 1f, 1f, mudColor)
        context.drawGeometry(transform, roadRims, 0f, 0f, 0f, 1f, 1f, 1f, rimColor)
        context.surface = CharacterSurface.EYE
        context.drawGeometry(transform, pools, 0f, 0f, 0f, 1f, 1f, 1f, waterColor)
        if (animate) {
            drawTrails(context, nowMillis)
        }
        context.surface = previousSurface
    }

    fun drawWeather(context: CharacterDrawContext, nowMillis: Long) {
        val previousSurface = context.surface
        context.life = null
        context.limbBendY = 0f
        context.limbBendZ = 0f
        context.surface = CharacterSurface.EYE
        repeat(36) { index ->
            val clock = nowMillis + index * 137L
            val cycle = clock / 1600L
            val phase = (clock % 1600L) / 1600f
            val jitterX = ((cycle * 5L + index * 13L) % 17L) / 17f
            val jitterY = ((cycle * 11L + index * 7L) % 19L) / 19f
            var targetX = (index % 6) * 25f + 4f + jitterX * 17f
            var targetY = (index / 6) * 25f + 4f + jitterY * 17f
            if ((index + cycle) % 9L == 0L) {
                val poolIndex = ((cycle + index) % puddleX.size).toInt()
                targetX = puddleX[poolIndex]
                targetY = puddleY[poolIndex]
            }
            if (phase < .62f) {
                val fall = 1f - phase / .62f
                context.surface = CharacterSurface.EYE
                context.drawGeometry(transform, drop, (targetX + fall * 3f).coerceIn(1f, 149f),
                    (targetY - fall * 9f).coerceIn(1f, 149f),
                    1.3f + fall * 12f, 1f, 1.2f, 1f, rainColor, rotateZ = 18f)
            } else {
                val impact = (phase - .62f) / .38f
                drawImpact(context, targetX, targetY, impact, .85f)
            }
        }
        stepImpacts.forEach { impact ->
            val age = nowMillis - impact.started
            if (impact.started >= 0L && age in 0L..549L) {
                drawImpact(context, impact.x, impact.y, age / 550f, 1.35f)
            }
        }
        context.surface = previousSurface
    }

    private fun surfaceAt(positionX: Float, positionY: Float): Int {
        for (index in puddleX.indices) {
            val radiusX = if (index % 2 == 0) 5.4f else 4.5f
            val radiusY = if (index % 2 == 0) 3.4f else 2.9f
            val deltaX = (positionX - puddleX[index]) / radiusX
            val deltaY = (positionY - puddleY[index]) / radiusY
            if (deltaX * deltaX + deltaY * deltaY <= 1f) return 0
        }
        slicks.forEach { patch ->
            val deltaX = (positionX - patch.x) / patch.radiusX
            val deltaY = (positionY - patch.y) / patch.radiusY
            if (deltaX * deltaX + deltaY * deltaY <= 1f) return 0
        }
        return if (positionX in 50f..100f || positionY in 50f..100f) 1 else 2
    }

    private fun drawImpact(context: CharacterDrawContext, positionX: Float, positionY: Float,
        progress: Float, scale: Float) {
        if (progress !in 0f..0.92f) return
        val surface = surfaceAt(positionX, positionY)
        val splashColor = if (surface == 2) mudSplashColor else impactColor
        splashColor[3] = (1f - progress) * if (surface == 1) .65f else .9f
        val radius = (.35f + progress * 2.2f) * scale * if (surface == 1) .65f else 1f
        val height = if (surface == 2) 1.16f else .76f
        context.surface = if (surface == 2) CharacterSurface.MATTE else CharacterSurface.EYE
        context.drawGeometry(transform, ripple, positionX, positionY, height,
            radius, radius * .70f, (1f - progress).coerceAtLeast(.08f), splashColor)
        if (progress < .48f) {
            val lift = sin(progress / .48f * PI.toFloat()).coerceAtLeast(.02f)
            context.drawGeometry(transform, crown, positionX, positionY, height + .02f,
                (.6f + progress) * scale, (.6f + progress) * scale, lift * scale,
                splashColor, rotateX = -28f)
        }
    }

    private fun observeSteps(state: LudoPaws3DSceneState, nowMillis: Long) {
        val motion = state.forwardMotion ?: return
        if (state.forwardDurationMillis <= 0L) return
        val elapsed = (nowMillis - state.forwardStartedAtMillis).coerceAtLeast(0L)
        val travelDuration = (state.forwardDurationMillis -
            LudoPaws3DRenderCadencePolicy.FORWARD_LANDING_SETTLE_MILLIS).coerceAtLeast(1L)
        val steps = motion.visualSteps.coerceAtLeast(1)
        val visualProgress = (elapsed.toFloat() / travelDuration * steps).coerceAtMost(steps.toFloat())
        val player = state.snapshot?.players?.firstOrNull { it.playerId == motion.playerId } ?: return
        val character = CharacterRegistry.resolve(LudoPaws3DCharacterPolicy.characterIdForSeat(
            state.characterIdsBySeat, player.seat, player.color))
        var landed = visualProgress.toInt()
        val fraction = visualProgress - landed
        if (landed < steps && fraction > .5f &&
            character.renderer.stepTravelProgress(fraction) >= .98f &&
            character.renderer.sample(CharacterMotion.HOP, fraction,
                if (landed == 0) CharacterHopPlayback.START else CharacterHopPlayback.CHAIN).liftY <= .04f) {
            landed++
        }
        if (observedMotion != motion || observedStart != state.forwardStartedAtMillis) {
            observedMotion = motion
            observedStart = state.forwardStartedAtMillis
            lastLanding = if (elapsed > state.forwardDurationMillis + 250L) steps else (landed - 1).coerceAtLeast(0)
        }
        while (lastLanding < landed) {
            lastLanding++
            val position = LudoPathEncoding.positionAtVisualStep(motion.fromPosition, lastLanding) ?: continue
            val previousPosition = LudoPathEncoding.positionAtVisualStep(motion.fromPosition, lastLanding - 1) ?: continue
            val center = LudoPawsFxBoardGeometry.tokenCenter(player.color, motion.tokenIndex, position, 10f, 0) ?: continue
            val previous = LudoPawsFxBoardGeometry.tokenCenter(player.color, motion.tokenIndex, previousPosition, 10f, 0) ?: continue
            if (position == LudoPathEncoding.HOME_POSITION) continue
            val heading = atan2(center.second - previous.second, center.first - previous.first) * 180f / PI.toFloat() + 90f
            val shape = when (character.definition.character.species) {
                AnimalSpecies.GOAT, AnimalSpecies.DONKEY, AnimalSpecies.HORSE, AnimalSpecies.PIG,
                AnimalSpecies.COW, AnimalSpecies.SHEEP, AnimalSpecies.DEER, AnimalSpecies.ZEBRA -> 1
                AnimalSpecies.COCK, AnimalSpecies.DUCK, AnimalSpecies.CHICK, AnimalSpecies.OWL -> 2
                AnimalSpecies.TURTLE -> 3
                else -> 0
            }
            repeat(2) { side ->
                val offset = if (side == 0) -.65f else .65f
                val angle = heading * PI.toFloat() / 180f
                val mark = prints[nextPrint]
                nextPrint = (nextPrint + 1) % prints.size
                mark.x = center.first + cos(angle) * offset
                mark.y = center.second + sin(angle) * offset
                mark.heading = heading
                mark.shape = shape
                mark.started = nowMillis
            }
            val impact = stepImpacts[nextImpact]
            nextImpact = (nextImpact + 1) % stepImpacts.size
            impact.x = center.first
            impact.y = center.second
            impact.started = nowMillis
        }
    }

    private fun drawTrails(context: CharacterDrawContext, nowMillis: Long) {
        context.surface = CharacterSurface.MATTE
        prints.forEach { mark ->
            val age = nowMillis - mark.started
            if (mark.started >= 0L && age in 0L..5999L) {
                printColor[3] = if (age <= 2000L) .72f else .72f * (6000L - age) / 4000f
                val geometry = when (mark.shape) { 1 -> hoofPrint; 2 -> birdPrint; 3 -> smear; else -> pawPrint }
                context.drawGeometry(transform, geometry, mark.x, mark.y, .82f,
                    1.1f, if (mark.shape == 3) 1.5f else 1.1f, 1f, printColor, rotateZ = mark.heading)
            }
        }
    }

    fun reset() {
        resetTrails()
        matchId = null
    }

    private fun resetTrails() {
        prints.forEach { it.started = -1L }
        stepImpacts.forEach { it.started = -1L }
        observedMotion = null
        observedStart = -1L
        lastLanding = 0
        nextPrint = 0
        nextImpact = 0
    }

    private class Print {
        var x = 0f
        var y = 0f
        var heading = 0f
        var shape = 0
        var started = -1L
    }

    private class Impact {
        var x = 0f
        var y = 0f
        var started = -1L
    }

    private data class Patch(val x: Float, val y: Float, val radiusX: Float, val radiusY: Float)

    private data class Point(val x: Float, val y: Float, val z: Float)

    private class Builder {
        private val vertices = ArrayList<Float>()
        private val indices = ArrayList<Short>()

        fun triangle(first: Point, second: Point, third: Point) {
            val edgeX = second.x - first.x
            val edgeY = second.y - first.y
            val edgeZ = second.z - first.z
            val otherX = third.x - first.x
            val otherY = third.y - first.y
            val otherZ = third.z - first.z
            var normalX = edgeY * otherZ - edgeZ * otherY
            var normalY = edgeZ * otherX - edgeX * otherZ
            var normalZ = edgeX * otherY - edgeY * otherX
            val length = sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ)
            if (length < .00001f) return
            val direction = if (normalZ < 0f) -1f else 1f
            normalX *= direction / length
            normalY *= direction / length
            normalZ *= direction / length
            for (point in arrayOf(first, second, third)) {
                indices.add((vertices.size / 6).toShort())
                vertices.add(point.x); vertices.add(point.y); vertices.add(point.z)
                vertices.add(normalX); vertices.add(normalY); vertices.add(normalZ)
            }
        }

        fun quad(first: Point, second: Point, third: Point, fourth: Point) {
            triangle(first, second, third)
            triangle(first, third, fourth)
        }

        fun ovalRing(centerX: Float, centerY: Float, radiusX: Float, radiusY: Float,
            inner: Float, low: Float, high: Float) {
            repeat(24) { side ->
                val angle = side * 2f * PI.toFloat() / 24f
                val next = (side + 1) * 2f * PI.toFloat() / 24f
                fun point(at: Float, radius: Float, height: Float) = Point(
                    centerX + cos(at) * radiusX * radius,
                    centerY + sin(at) * radiusY * radius, height)
                quad(point(angle, 1f, low), point(next, 1f, low),
                    point(next, inner, high), point(angle, inner, high))
                quad(point(angle, inner, high), point(next, inner, high),
                    point(next, inner * .92f, low), point(angle, inner * .92f, low))
            }
        }

        fun disc(centerX: Float, centerY: Float, radiusX: Float, radiusY: Float, height: Float, segments: Int = 32) {
            repeat(segments) { side ->
                val angle = side * 2f * PI.toFloat() / segments
                val next = (side + 1) * 2f * PI.toFloat() / segments
                triangle(Point(centerX, centerY, height),
                    Point(centerX + cos(angle) * radiusX, centerY + sin(angle) * radiusY, height),
                    Point(centerX + cos(next) * radiusX, centerY + sin(next) * radiusY, height))
            }
        }

        fun build() = MeshGeometry(vertices.toFloatArray(), indices.toShortArray())
    }

    private fun axis(index: Int): Float = when {
        index <= 6 -> index * 50f / 6f
        index <= 9 -> 50f + (index - 6) * 50f / 3f
        else -> 100f + (index - 9) * 50f / 6f
    }
}
