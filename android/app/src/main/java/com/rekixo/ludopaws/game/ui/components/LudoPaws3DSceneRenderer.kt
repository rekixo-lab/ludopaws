package com.rekixo.ludopaws

import com.rekixo.ludopaws.feature.characters.shared.LudoPaws3DLifeMotion
import com.rekixo.ludopaws.rendering.meshes.CharacterMesh
import com.rekixo.ludopaws.rendering.meshes.SphereGeometry
import com.rekixo.ludopaws.rendering.meshes.MeshGeometry
import com.rekixo.ludopaws.rendering.shaders.CharacterShaders.VERTEX_SHADER
import com.rekixo.ludopaws.rendering.shaders.CharacterShaders.FRAGMENT_SHADER
import com.rekixo.ludopaws.rendering.shaders.GlProgram.createProgram
import android.opengl.GLES30
import android.opengl.Matrix
import com.rekixo.ludopaws.feature.characters.rendering.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Shared production renderer for all code-generated animal pawns.
 *
 * CharacterRegistry supplies anatomy and motion. All pawns share one ES3
 * program, reusable context-local meshes and one EGL context. Board
 * coordinates come only from LudoPawsFxBoardGeometry/LudoPathEncoding so the 3D
 * layer cannot invent a second game route.
 */
internal class LudoPaws3DSceneRenderer {
    private var width = 1
    private var height = 1
    private var program = 0
    private var uEntryFadeLocation = -1
    private var uEntryClipLocation = -1
    private var uMvpLocation = -1
    private var uColorLocation = -1
    private var uLightDirectionLocation = -1
    private var uNormalMatrixLocation = -1
    private lateinit var sphere: CharacterMesh
    private var uMaterialLocation = -1
    private var uPartyClockLocation = -1
    private var uPartyEnergyLocation = -1
    private var boundPartyEnergy = Float.NaN
    private var uLimbBendLocation = -1
    private var boundSurface: CharacterSurface? = null
    private var boundBendY = Float.NaN
    private var boundBendZ = Float.NaN
    private val customMeshes = mutableMapOf<MeshGeometry, CharacterMesh>()

    private val renderPawnScratch = ArrayList<RenderPawn>(16)
    private val visiblePawnKeys = HashSet<LudoPaws3DPawnKey>(16)
    private val pawnDepthOrder = compareBy<RenderPawn> { it.y }
    private var occupancySnapshot: MatchSnapshot? = null
    private var occupancyCell = 0f
    private var occupancyTurns = -1
    private var cachedOccupancy: Map<String, Int> = emptyMap()
    private val lifeByPawn = mutableMapOf<LudoPaws3DPawnKey, LudoPaws3DLifeMotion>()
    private val centerHouses by lazy { LudoPawsCenterHouses3D.acquire() }
    private val drawContext = object : CharacterDrawContext() {
        override fun drawPart(parent: FloatArray, translateX: Float, translateY: Float, translateZ: Float,
            scaleX: Float, scaleY: Float, scaleZ: Float, color: FloatArray,
            rotateX: Float, rotateY: Float, rotateZ: Float) {
            this@LudoPaws3DSceneRenderer.drawPart(parent, translateX, translateY, translateZ,
                scaleX, scaleY, scaleZ, color, rotateX, rotateY, rotateZ)
        }
        override fun drawGeometry(parent: FloatArray, geometry: MeshGeometry,
            translateX: Float, translateY: Float, translateZ: Float,
            scaleX: Float, scaleY: Float, scaleZ: Float, color: FloatArray,
            rotateX: Float, rotateY: Float, rotateZ: Float) {
            this@LudoPaws3DSceneRenderer.drawPart(parent, translateX, translateY, translateZ,
                scaleX, scaleY, scaleZ, color, rotateX, rotateY, rotateZ, geometry)
        }
    }
    private var bodyStretch = 1f
    private var groundPivotY = -.72f
    private val projection = FloatArray(16)
    private val root = FloatArray(16)
    private val model = FloatArray(16)
    private val inverseModel = FloatArray(16)
    private val normalMatrix = FloatArray(9)
    private val mvp = FloatArray(16)

    fun onSurfaceCreated() {
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(
            GLES30.GL_SRC_ALPHA,
            GLES30.GL_ONE_MINUS_SRC_ALPHA,
        )
        program =
            createProgram(
                VERTEX_SHADER.trimIndent(),
                FRAGMENT_SHADER.trimIndent(),
            )
        customMeshes.clear()
        uPartyClockLocation = GLES30.glGetUniformLocation(program, "uPartyClock")
        uPartyEnergyLocation = GLES30.glGetUniformLocation(program, "uPartyEnergy")
        check(uPartyClockLocation >= 0 && uPartyEnergyLocation >= 0) { "Missing party light uniforms" }
        boundPartyEnergy = Float.NaN
        uEntryFadeLocation = GLES30.glGetUniformLocation(program, "uEntryFade")
        check(uEntryFadeLocation >= 0) { "Missing entry fade uniform" }
        uEntryClipLocation = GLES30.glGetUniformLocation(program, "uEntryClip")
        check(uEntryClipLocation >= 0) { "Missing entry aperture uniform" }
        uLimbBendLocation = GLES30.glGetUniformLocation(program, "uLimbBend")
        check(uLimbBendLocation >= 0) { "Missing limb deformation uniform" }
        uMaterialLocation = GLES30.glGetUniformLocation(program, "uMaterial")
        check(uMaterialLocation >= 0) { "Missing character material uniform" }
        uMvpLocation = GLES30.glGetUniformLocation(program, "uMvp")
        uColorLocation = GLES30.glGetUniformLocation(program, "uColor")
        uLightDirectionLocation = GLES30.glGetUniformLocation(program, "uLightDirection")
        uNormalMatrixLocation = GLES30.glGetUniformLocation(program, "uNormalMatrix")
        check(
            uMvpLocation >= 0 &&
                uColorLocation >= 0 &&
                uLightDirectionLocation >= 0 &&
                uNormalMatrixLocation >= 0,
        ) { "3D pawn shader is missing a required uniform" }
        GLES30.glUseProgram(program)
        GLES30.glUniform3f(uLightDirectionLocation, .40f, -1f, -.65f)
        boundSurface = null
        boundBendY = Float.NaN
        boundBendZ = Float.NaN
        sphere =
            CharacterMesh(
                geometry = SphereGeometry.standard,
                uMvpLocation = uMvpLocation,
                uColorLocation = uColorLocation,
                uNormalMatrixLocation = uNormalMatrixLocation,
            )
    }

    fun release() {
        customMeshes.values.forEach { it.release() }
        customMeshes.clear()
        if (::sphere.isInitialized) sphere.release()
        if (program != 0) GLES30.glDeleteProgram(program)
        program = 0
    }

    /**
     * Initializes the dedicated portrait renderer. Both gameplay and portrait
     * render paths normalize the shared GLSL constants so strict ES3 drivers see
     * the #version directive at the beginning of the source.
     */
    fun onPortraitSurfaceCreated() = onSurfaceCreated()

    fun onSurfaceChanged(
        width: Int,
        height: Int,
    ) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        GLES30.glViewport(
            0,
            0,
            this.width,
            this.height,
        )

        // LudoPawsReactiveBoardView may give this transparent surface extra
        // pixels only above the square board. Keep world-space board Y=0 at the
        // exact visual board edge by mapping that overflow to negative world Y.
        // The board, pawn centers, path geometry and model scale therefore remain
        // unchanged while heads/ears/horns can render above the top row.
        val topOverflow =
            (this.height - this.width)
                .coerceAtLeast(0)
                .toFloat()
        val boardBottom =
            (this.height.toFloat() - topOverflow)
                .coerceAtLeast(1f)
        Matrix.orthoM(
            projection,
            0,
            0f,
            this.width.toFloat(),
            boardBottom,
            -topOverflow,
            // Models use pixel-sized world coordinates. A fixed +/-100 clips
            // muzzles/eyes in large portraits and on high-resolution game surfaces.
            -max(width, height).toFloat() * 2f,
            max(width, height).toFloat() * 2f,
        )
    }

    /**
     * Configures the same production geometry renderer for a square, transparent
     * character portrait. This is used only by the off-screen portrait cache;
     * the existing gameplay surface continues to use onSurfaceChanged/drawFrame.
     */
    fun onPortraitSurfaceChanged(
        width: Int,
        height: Int,
    ) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, this.width, this.height)
        Matrix.orthoM(
            projection,
            0,
            0f,
            this.width.toFloat(),
            this.height.toFloat(),
            0f,
            // Models use pixel-sized world coordinates. A fixed +/-100 clips
            // muzzles/eyes in large portraits and on high-resolution game surfaces.
            -max(width, height).toFloat() * 2f,
            max(width, height).toFloat() * 2f,
        )
    }

    /**
     * Draws an isolated character preview using the live pawn geometry and materials.
     * Default arguments render the static portrait used by the shared bitmap cache. No match state,
     * pawn position, movement, or animation state is read or modified.
     */
    fun drawPortrait(
        characterId: String,
        teamColorName: String,
        pose: CharacterPose = CharacterPose(),
        previewLife: LudoPaws3DLifeMotion? = null,
        previewZoom: Float = 1f,
        synchronizeGpu: Boolean = true,
    ) {
        if (program == 0) return

        GLES30.glViewport(0, 0, width, height)
        GLES30.glClear(
            GLES30.GL_COLOR_BUFFER_BIT or
                GLES30.GL_DEPTH_BUFFER_BIT,
        )
        GLES30.glUseProgram(program)

        val character = CharacterRegistry.resolve(characterId)
        val teamColor =
            LudoPawsTeamIdentityPolicy.resolve(teamColorName)
                ?: LudoPawsTeamColor.BLUE
        drawContext.surface = CharacterSurface.MATTE
        drawContext.life = previewLife
        bodyStretch = pose.bodyScaleY
        groundPivotY = character.definition.groundPivotY
        val size = min(width, height).toFloat()
        GLES30.glUniform1f(uEntryFadeLocation, 1f)
        GLES30.glUniform4f(uEntryClipLocation, 0f, 0f, 0f, 0f)
        val modelScale = size * 0.30f * previewZoom.coerceIn(.6f, 1.5f)
        prepareRoot(
            x = width * 0.5f,
            y = height * 0.62f,
            scale = modelScale,
            liftY = pose.liftY,
            bodyYawDegrees = pose.bodyYawDegrees,
        )

        character.renderer.draw(drawContext, root, pose, teamColor)
        // Bitmap readback needs completion; an animated window swaps buffers itself.
        if (synchronizeGpu) GLES30.glFinish()
    }

    fun drawFrame(
        state: LudoPaws3DSceneState,
        nowMillis: Long,
    ) {
        GLES30.glClear(
            GLES30.GL_COLOR_BUFFER_BIT or
                GLES30.GL_DEPTH_BUFFER_BIT,
        )
        val snapshot = state.snapshot ?: return
        if (program == 0) return
        GLES30.glUseProgram(program)
        // Keep precision stable after long device uptimes; all cycles divide 100 seconds.
        GLES30.glUniform1f(uPartyClockLocation, (nowMillis % 100_000L) / 1000f)

        val size = min(width, height).toFloat()
        val cell = size / LudoPawsFxBoardGeometry.BOARD_SIZE
        val turns =
            state.perspectiveColor
                ?.let(OfflinePlayerLayout::rotationQuarterTurns)
                ?: 0
        GLES30.glUniform4f(uEntryClipLocation, 0f, 0f, 0f, 0f)
        GLES30.glUniform1f(uEntryFadeLocation, 1f)
        centerHouses.drawStoneRoads(drawContext, size, turns)
        // Pawn and house depth are independent of the static road relief.
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
        // Clip decorative volume (including an outward-swinging door) to the
        // exact stretched center square so no home-lane or road cell is covered.
        val centerStart = floor(size / 3f).toInt()
        val centerEnd = kotlin.math.ceil(size * 2f / 3f).toInt()
        val boardBottom = height - (height - width).coerceAtLeast(0)
        GLES30.glEnable(GLES30.GL_SCISSOR_TEST)
        GLES30.glScissor(centerStart, boardBottom - centerEnd,
            centerEnd - centerStart, centerEnd - centerStart)
        GLES30.glUniform4f(uEntryClipLocation, 0f, 0f, 0f, 0f)
        GLES30.glUniform1f(uEntryFadeLocation, 1f)
        centerHouses.draw(drawContext, size, turns, state, nowMillis)
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        // Houses retain their internal depth, while all authoritative pawns remain
        // visible above the center artwork, including the four finish positions.
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
        val occupancy =
            occupancyByCenter(
                snapshot = snapshot,
                cell = cell,
                turns = turns,
            )
        val stackPlacements =
            LudoPawsPawnLayout.stackPlacements(
                snapshot = snapshot,
                cell = cell,
                perspectiveQuarterTurns = turns,
            )

        val renderPawns =
            renderPawnScratch.apply {
                clear()
                snapshot.players.forEach { player ->
                    val teamColor =
                        LudoPawsTeamIdentityPolicy.resolve(player.color)
                            ?: return@forEach
                    val character = CharacterRegistry.resolve(
                        LudoPaws3DCharacterPolicy.characterIdForSeat(
                state.characterIdsBySeat, player.seat, player.color,
            ),
                    )
                    player.tokens.forEachIndexed { tokenIndex, currentPosition ->
                        val key =
                            LudoPaws3DPawnKey(
                                playerId = player.playerId,
                                tokenIndex = tokenIndex,
                            )
                        val captureVisual =
                            state.captureReturns[key]
                                ?.let { capture ->
                                    captureReturnVisual(
                                        player = player,
                                        tokenIndex = tokenIndex,
                                        capture = capture,
                                        cell = cell,
                                        turns = turns,
                                        nowMillis = nowMillis,
                                    )
                                }

                        val entry = state.homeEntries[player.playerId]
                            ?.takeIf { currentPosition == LudoPathEncoding.HOME_POSITION }
                        val entryElapsed = entry?.elapsed(nowMillis) ?: -1L
                        val entryProgress = if (entry != null)
                            LudoPawsHomeEntryPolicy.walk(entryElapsed, tokenIndex) else 0f
                        if (entry != null && entryProgress >= 1f) return@forEachIndexed
                        var rawCenter =
                            captureVisual?.center
                                ?: animatedCenter(
                                    player = player,
                                    tokenIndex = tokenIndex,
                                    currentPosition = currentPosition,
                                    cell = cell,
                                    state = state,
                                    nowMillis = nowMillis,
                                )
                                ?: return@forEachIndexed
                        if (entryProgress > 0f) {
                            val normal = homeNormal(player.color)
                            val half = size / 6f
                            val approach = LudoPawsHomeEntryPolicy.smooth(entryProgress / .60f)
                            val inside = LudoPawsHomeEntryPolicy.smooth((entryProgress - .60f) / .40f)
                            val gate = size * .5f + normal.first * half * .88f to
                                size * .5f + normal.second * half * .88f
                            rawCenter = rawCenter.first + (gate.first - rawCenter.first) * approach -
                                normal.first * half * .23f * inside to
                                rawCenter.second + (gate.second - rawCenter.second) * approach -
                                normal.second * half * .23f * inside
                        }
                        val stackPlacement =
                            if (captureVisual == null) {
                                stackPlacements[
                                    LudoPawsPawnVisualKey(
                                        playerId = player.playerId,
                                        tokenIndex = tokenIndex,
                                    )
                                ]
                            } else {
                                null
                            }
                        val offset =
                            if (captureVisual == null) {
                                (stackPlacement?.offsetXFraction ?: 0f) to
                                    (stackPlacement?.offsetYFraction ?: 0f)
                            } else {
                                0f to 0f
                            }
                        val unrotatedX = rawCenter.first + offset.first * cell
                        val unrotatedY = rawCenter.second + offset.second * cell
                        val rotated =
                            rotatePoint(
                                x = unrotatedX,
                                y = unrotatedY,
                                size = size,
                                turns = turns,
                            )
                        val renderPosition =
                            captureVisual?.fromPosition
                                ?: currentPosition
                        val occupants =
                            if (captureVisual != null) {
                                1
                            } else {
                                val occupancyKey =
                                    centerKey(
                                        LudoPawsFxBoardGeometry.tokenCenter(
                                            color = player.color,
                                            tokenIndex = tokenIndex,
                                            position = currentPosition,
                                            cell = cell,
                                            perspectiveQuarterTurns = turns,
                                        ),
                                    )
                                occupancy[occupancyKey] ?: 1
                            }
                        val fit = LudoPawsPawnLayout.roadPresentation(
                            rawCenter.first, rawCenter.second, cell, renderPosition, occupants,
                            character.id, turns,
                        )
                        val radius = fit.radius
                        val facingYawDegrees =
                            if (entryProgress > 0f) {
                                val n = homeNormal(player.color)
                                val direction = rotatePoint(size * .5f - n.first * cell,
                                    size * .5f - n.second * cell, size, turns)
                                LudoPawsPawnFacingPolicy.yawForVisibleDelta(
                                    direction.first - size * .5f, direction.second - size * .5f)
                            } else if (captureVisual == null) {
                                facingYawDegrees(
                                    player = player,
                                    tokenIndex = tokenIndex,
                                    state = state,
                                    nowMillis = nowMillis,
                                    size = size,
                                    cell = cell,
                                    turns = turns,
                                )
                            } else {
                                LudoPawsPawnFacingPolicy.FRONT_YAW_DEGREES
                            }

                        add(
                            RenderPawn(
                                key = key,
                                character = character,
                                teamColor = teamColor,
                                seat = player.seat,
                                x = rotated.first,
                                y = rotated.second + fit.footDrop,
                                radius = radius,
                                facingYawDegrees = facingYawDegrees,
                                presentationScale = if (entryProgress > 0f)
                                    1f - .72f * LudoPawsHomeEntryPolicy.smooth(entryProgress)
                                    else captureVisual?.scale ?: 1f,
                                entryProgress = entryProgress,
                                entryClip = if (entryProgress > 0f)
                                    entryAperture(player.color, size, turns, entryProgress) else null,
                                attentionScale =
                                    legalAttentionScale(
                                        state = state,
                                        key = key,
                                        nowMillis = nowMillis,
                                    ),
                            ),
                        )
                    }
                }
            }.also { it.sortWith(pawnDepthOrder) }

        // Shadows are drawn as a separate background pass so one pawn's shadow
        // can never darken another pawn that was already painted in front of it.
        // The board coordinates remain authoritative; this pass is visual only.
        visiblePawnKeys.clear()
        renderPawns.forEach { visiblePawnKeys.add(it.key) }
        lifeByPawn.keys.retainAll(visiblePawnKeys)
        renderPawns.forEach { if (it.entryProgress < .60f) drawContactShadow(it) }

        renderPawns.forEach { pawn ->
            // Preserve depth inside one animal but use painter ordering between
            // animals. This prevents one pawn's nose/ear depth cutting another.
            GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
            GLES30.glUniform1f(uEntryFadeLocation,
                1f - LudoPawsHomeEntryPolicy.smooth((pawn.entryProgress - .85f) / .15f))
            pawn.entryClip?.let { clip ->
                GLES30.glUniform4f(uEntryClipLocation, clip[0], min(width, height) - clip[3],
                    clip[2], min(width, height) - clip[1])
            }
            drawPawn(
                pawn = pawn,
                state = state,
                nowMillis = nowMillis,
            )
            GLES30.glUniform4f(uEntryClipLocation, 0f, 0f, 0f, 0f)
            GLES30.glUniform1f(uEntryFadeLocation, 1f)
        }
        if (renderPawns.any { it.entryProgress in .55f..0.9999f }) {
            GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
            GLES30.glEnable(GLES30.GL_SCISSOR_TEST)
            GLES30.glScissor(centerStart, boardBottom - centerEnd,
                centerEnd - centerStart, centerEnd - centerStart)
            centerHouses.drawEntryForeground(drawContext, size, turns, state, nowMillis)
            GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        }
    }

    private fun homeNormal(color: String): Pair<Float, Float> = when (color) {
        "RED" -> -1f to 0f
        "GREEN" -> 0f to -1f
        "YELLOW" -> 1f to 0f
        else -> 0f to 1f
    }

    private fun entryAperture(color: String, size: Float, turns: Int, progress: Float): FloatArray {
        val n = homeNormal(color)
        val half = size / 6f
        val points = buildList {
            for (radial in floatArrayOf(.49f, .78f)) for (tangent in floatArrayOf(-.17f, .17f)) {
                add(rotatePoint(size * .5f + (n.first * radial - n.second * tangent) * half,
                    size * .5f + (n.second * radial + n.first * tangent) * half, size, turns))
            }
        }
        val aperture = floatArrayOf(points.minOf { it.first }, points.minOf { it.second },
            points.maxOf { it.first }, points.maxOf { it.second })
        // Gradually seat the whole silhouette behind the jamb instead of popping it off.
        val blend = LudoPawsHomeEntryPolicy.smooth((progress - .60f) / .25f)
        val full = floatArrayOf(size / 3f, size / 3f, size * 2f / 3f, size * 2f / 3f)
        return FloatArray(4) { full[it] + (aperture[it] - full[it]) * blend }
    }

    private fun drawPawn(
        pawn: RenderPawn,
        state: LudoPaws3DSceneState,
        nowMillis: Long,
    ) {
        val entry = state.homeEntries[pawn.key.playerId]
        val entryElapsedForPose = entry?.elapsed(nowMillis) ?: -1L
        val frame = if (entryElapsedForPose in 0 until LudoPawsHomeEntryPolicy.CELEBRATE_MS) {
            MotionFrame(CharacterMotion.HOME,
                entryElapsedForPose.toFloat() / LudoPawsHomeEntryPolicy.CELEBRATE_MS)
        } else if (pawn.entryProgress > 0f && pawn.entryProgress < .60f) {
            MotionFrame(CharacterMotion.HOP, (pawn.entryProgress * 5f) % 1f)
        } else if (pawn.entryProgress >= .60f) {
            idleMotionFrame(pawn, nowMillis)
        } else motionFrame(
                pawn = pawn,
                state = state,
                nowMillis = nowMillis,
            )
        val rawReactionTarget =
            reactionPose(
                pawn = pawn,
                state = state,
                nowMillis = nowMillis,
            )
        // Locomotion owns weight/contact. Keep facial reactions, but fade a competing
        // celebration bounce/scale through the existing springs before landing.
        val entryElapsed = state.homeEntries[pawn.key.playerId]?.elapsed(nowMillis)
        val reactionTarget = if (frame.kind == CharacterMotion.HOP ||
            (entryElapsed != null && entryElapsed >= LudoPawsHomeEntryPolicy.CELEBRATE_MS)) {
            rawReactionTarget.copy(liftY = 0f, scale = 1f, bodyYawDegrees = 0f, bodyPitchDegrees = 0f)
        } else rawReactionTarget
        val living = lifeByPawn.getOrPut(pawn.key) { LudoPaws3DLifeMotion() }
        drawContext.surface = CharacterSurface.MATTE
        drawContext.life = living
        // Watch the moving pawn, otherwise attend to an opponent.
        val forward = state.forwardMotion
        val targetPlayer = state.snapshot?.players?.firstOrNull {
            it.playerId == forward?.playerId
        } ?: state.snapshot?.players?.firstOrNull { it.playerId != pawn.key.playerId }
        val focus = targetPlayer?.let { player ->
            val index = if (player.playerId == forward?.playerId) forward.tokenIndex else 0
            val position = player.tokens.getOrNull(index) ?: return@let null
            animatedCenter(player, index, position, min(width, height).toFloat() / 15f, state, nowMillis)
        }?.let {
            rotatePoint(it.first, it.second, min(width, height).toFloat(),
                state.perspectiveColor?.let(OfflinePlayerLayout::rotationQuarterTurns) ?: 0)
        }
        val reaction = living.update(nowMillis, pawn.seat * 397 + pawn.key.tokenIndex * 103,
            frame.kind == CharacterMotion.IDLE && state.activeReactions[pawn.key]?.let {
                nowMillis < it.startedAtMillis + it.durationMillis
            } != true,
            ((focus?.first ?: width * .5f) - pawn.x) / (width * .35f),
            (pawn.y - (focus?.second ?: height * .5f)) / (height * .35f), reactionTarget)
        groundPivotY = pawn.character.definition.groundPivotY
        val modelScale =
            pawn.radius *
                MODEL_SCALE_PER_RADIUS *
                pawn.presentationScale *
                pawn.attentionScale *
                reaction.scale
        // The fixed board coordinate is the FOOT CONTACT, not the body origin.
        // Species-specific pivot and the *current* animated scale determine the
        // root translation, so a 32%-larger pawn cannot stand on the next square.
        val rootY =
            LudoPawsPawnLayout.rootYForFootContact(
                boardCenterY = pawn.y,
                modelScale = modelScale,
                groundPivotY = pawn.character.definition.groundPivotY,
            )

        val sampled = pawn.character.renderer.sample(frame.kind, frame.progress, frame.hopPlayback)
        val base = if(frame.kind == CharacterMotion.IDLE) living.idlePose(sampled) else sampled
        bodyStretch = base.bodyScaleY
        val pose = base.copy(
            liftY = base.liftY + reaction.liftY,
            bodyYawDegrees = pawn.facingYawDegrees + base.bodyYawDegrees + reaction.bodyYawDegrees,
            headTiltDegrees = base.headTiltDegrees + reaction.headTiltDegrees,
            primaryAppendageDegrees = living.followPrimary(base.primaryAppendageDegrees + reaction.primaryAppendageDegrees + living.shake),
            secondaryAppendageDegrees = living.followSecondary(base.secondaryAppendageDegrees + reaction.secondaryAppendageDegrees + living.shake),
            tertiaryAppendageDegrees = living.followTertiary(base.tertiaryAppendageDegrees + reaction.tertiaryAppendageDegrees + living.shake),
            bodyPitchDegrees = base.bodyPitchDegrees + reaction.bodyPitchDegrees,
            neckPitchDegrees = base.neckPitchDegrees + reaction.neckPitchDegrees,
            jawOpen = (base.jawOpen + reaction.jawOpen).coerceIn(0f, 1f),
        )
        prepareRoot(pawn.x, rootY, modelScale, pose.liftY, pose.bodyYawDegrees)
        pawn.character.renderer.draw(drawContext, root, pose, pawn.teamColor)
    }

    private fun reactionPose(
        pawn: RenderPawn,
        state: LudoPaws3DSceneState,
        nowMillis: Long,
    ): LudoPaws3DReactionPose {
        val active =
            state.activeReactions[pawn.key]
                ?: return LudoPaws3DReactionPose()
        if (active.durationMillis <= 0L) return LudoPaws3DReactionPose()
        val elapsed = (nowMillis - active.startedAtMillis).coerceAtLeast(0L)
        if (elapsed >= active.durationMillis) return LudoPaws3DReactionPose()
        return LudoPaws3DReactionMotion.sample(
            characterId = pawn.character.id,
            cue = active.cue,
            progress = elapsed.toFloat() / active.durationMillis.toFloat(),
        )
    }

    private fun motionFrame(
        pawn: RenderPawn,
        state: LudoPaws3DSceneState,
        nowMillis: Long,
    ): MotionFrame {
        val capture = state.captureReturns[pawn.key]
        if (
            capture != null &&
            capture.durationMillis > 0L &&
            nowMillis < capture.startedAtMillis + capture.durationMillis
        ) {
            // The capture route owns board translation/scale. Keep the base body
            // stable so the CAPTURED reaction can add species-specific emotion.
            return MotionFrame(
                kind = CharacterMotion.IDLE,
                progress = 0f,
            )
        }

        val forward = state.forwardMotion
        if (
            forward != null &&
            forward.playerId == pawn.key.playerId &&
            forward.tokenIndex == pawn.key.tokenIndex
        ) {
            val elapsed =
                (nowMillis - state.forwardStartedAtMillis)
                    .coerceAtLeast(0L)
            val travelDuration =
                (
                    state.forwardDurationMillis -
                        LudoPaws3DRenderCadencePolicy.FORWARD_LANDING_SETTLE_MILLIS
                ).coerceAtLeast(1L)
            if (state.forwardDurationMillis > 0L && elapsed < travelDuration) {
                // The route finishes before the settle tail; do not restart a hop
                // cycle while the pawn is already at its destination.
                val visualProgress =
                    elapsed.toFloat() /
                        travelDuration.toFloat() *
                        forward.visualSteps.toFloat()
                val stepFraction = visualProgress - floor(visualProgress)
                return MotionFrame(
                    kind = CharacterMotion.HOP,
                    progress = stepFraction.coerceIn(0f, 1f),
                    hopPlayback = if (visualProgress < 1f) CharacterHopPlayback.START else CharacterHopPlayback.CHAIN,
                )
            }

            if (elapsed < state.forwardDurationMillis) {
                return MotionFrame(
                    kind = CharacterMotion.HOP,
                    progress = (elapsed - travelDuration).toFloat() /
                        LudoPaws3DRenderCadencePolicy.FORWARD_LANDING_SETTLE_MILLIS,
                    hopPlayback = CharacterHopPlayback.SETTLE,
                )
            }

            if (forward.toPosition == LudoPathEncoding.HOME_POSITION) {
                val celebrationElapsed = elapsed - state.forwardDurationMillis
                val homeDuration = pawn.character.definition.homeDurationMillis
                if (celebrationElapsed in 0 until homeDuration) {
                    return MotionFrame(
                        kind = CharacterMotion.HOME,
                        progress =
                            celebrationElapsed.toFloat() /
                                homeDuration.toFloat(),
                    )
                }
            }
        }

        return idleMotionFrame(pawn, nowMillis)
    }

    private fun idleMotionFrame(pawn: RenderPawn, nowMillis: Long): MotionFrame {
        val duration = pawn.character.definition.idleDurationMillis
        val phaseOffset =
            ((pawn.seat * 4 + pawn.key.tokenIndex) * 173L) % duration
        return MotionFrame(
            kind = CharacterMotion.IDLE,
            progress =
                ((nowMillis + phaseOffset) % duration)
                    .toFloat() /
                    duration.toFloat(),
        )
    }

    private fun captureReturnVisual(
        player: PlayerSnapshot,
        tokenIndex: Int,
        capture: LudoPaws3DCaptureReturnState,
        cell: Float,
        turns: Int,
        nowMillis: Long,
    ): CaptureVisual? {
        if (capture.durationMillis <= 0L) return null
        val elapsed =
            (nowMillis - capture.startedAtMillis)
                .coerceAtLeast(0L)
        if (elapsed >= capture.durationMillis) return null

        val slot = tokenIndex + player.seat
        val fromBase =
            LudoPawsFxBoardGeometry.tokenCenter(
                color = player.color,
                tokenIndex = tokenIndex,
                position = capture.motion.fromPosition,
                cell = cell,
                perspectiveQuarterTurns = turns,
            ) ?: return null
        val toBase =
            LudoPawsFxBoardGeometry.tokenCenter(
                color = player.color,
                tokenIndex = tokenIndex,
                position = -1,
                cell = cell,
                perspectiveQuarterTurns = turns,
            ) ?: return null
        val fromOffset =
            LudoPawsPawnLayout.tokenOffsetFraction(
                slot = slot,
                position = capture.motion.fromPosition,
            )
        val toOffset =
            LudoPawsPawnLayout.tokenOffsetFraction(
                slot = slot,
                position = -1,
            )
        val from =
            (fromBase.first + fromOffset.first * cell) to
                (fromBase.second + fromOffset.second * cell)
        val to =
            (toBase.first + toOffset.first * cell) to
                (toBase.second + toOffset.second * cell)
        val placement =
            LudoPawsCaptureReturnPlacement.sample(
                from = from,
                to = to,
                cell = cell,
                progress =
                    elapsed.toFloat() /
                        capture.durationMillis.toFloat(),
            )
        return CaptureVisual(
            center = placement.x to placement.y,
            scale = placement.scale,
            fromPosition = capture.motion.fromPosition,
        )
    }

    private fun animatedCenter(
        player: PlayerSnapshot,
        tokenIndex: Int,
        currentPosition: Int,
        cell: Float,
        state: LudoPaws3DSceneState,
        nowMillis: Long,
    ): Pair<Float, Float>? {
        val turns = state.perspectiveColor?.let(OfflinePlayerLayout::rotationQuarterTurns) ?: 0
        val motion =
            state.forwardMotion
                ?.takeIf {
                    it.playerId == player.playerId &&
                        it.tokenIndex == tokenIndex &&
                        state.forwardDurationMillis > 0L
                }
                ?: return LudoPawsFxBoardGeometry.tokenCenter(
                    color = player.color,
                    tokenIndex = tokenIndex,
                    position = currentPosition,
                    cell = cell,
                    perspectiveQuarterTurns = turns,
                )
        val elapsed =
            (nowMillis - state.forwardStartedAtMillis)
                .coerceAtLeast(0L)
        val travelDuration =
            (
                state.forwardDurationMillis -
                    LudoPaws3DRenderCadencePolicy.FORWARD_LANDING_SETTLE_MILLIS
            ).coerceAtLeast(1L)
        if (elapsed >= travelDuration) {
            return LudoPawsFxBoardGeometry.tokenCenter(
                color = player.color,
                tokenIndex = tokenIndex,
                position = currentPosition,
                cell = cell,
                perspectiveQuarterTurns = turns,
            )
        }

        val totalSteps = motion.visualSteps.coerceAtLeast(1)
        val visualProgress =
            elapsed.toFloat() /
                travelDuration.toFloat() *
                totalSteps.toFloat()
        val whole =
            floor(visualProgress)
                .toInt()
                .coerceIn(0, totalSteps - 1)
        val fraction =
            (visualProgress - whole)
                .coerceIn(0f, 1f)
        val fromPosition =
            LudoPathEncoding.positionAtVisualStep(
                fromPosition = motion.fromPosition,
                step = whole,
            ) ?: return null
        val toPosition =
            LudoPathEncoding.positionAtVisualStep(
                fromPosition = motion.fromPosition,
                step = (whole + 1).coerceAtMost(totalSteps),
            ) ?: return null
        val from =
            LudoPawsFxBoardGeometry.tokenCenter(
                color = player.color,
                tokenIndex = tokenIndex,
                position = fromPosition,
                cell = cell,
                perspectiveQuarterTurns = turns,
            ) ?: return null
        val to =
            LudoPawsFxBoardGeometry.tokenCenter(
                color = player.color,
                tokenIndex = tokenIndex,
                position = toPosition,
                cell = cell,
                perspectiveQuarterTurns = turns,
            ) ?: return from

        // Stay on the cell-center path; characters may hold translation during planted
        // anticipation/contact phases without changing route endpoints or step duration.
        val renderer = CharacterRegistry.resolve(
            LudoPaws3DCharacterPolicy.characterIdForSeat(
                state.characterIdsBySeat, player.seat, player.color,
            ),
        ).renderer
        // The character already supplies an eased travel phase. Applying the board
        // helper's cubic ease again concentrates motion into too few FAST frames.
        val travel = renderer.stepTravelProgress(fraction)
        return lerp(from.first, to.first, travel) to lerp(from.second, to.second, travel)
    }

    private fun facingYawDegrees(
        player: PlayerSnapshot,
        tokenIndex: Int,
        state: LudoPaws3DSceneState,
        nowMillis: Long,
        size: Float,
        cell: Float,
        turns: Int,
    ): Float {
        val motion =
            state.forwardMotion
                ?.takeIf {
                    it.playerId == player.playerId &&
                        it.tokenIndex == tokenIndex &&
                        state.forwardDurationMillis > 0L
                }
                ?: return LudoPawsPawnFacingPolicy.FRONT_YAW_DEGREES
        val elapsed =
            (nowMillis - state.forwardStartedAtMillis)
                .coerceAtLeast(0L)
        val duration =
            (
                state.forwardDurationMillis -
                    LudoPaws3DRenderCadencePolicy.FORWARD_LANDING_SETTLE_MILLIS
            ).coerceAtLeast(1L)

        // Ease the path-facing contribution home too; resetting it on contact caused
        // a one-frame turn before the character's own celebration began.

        val totalSteps = motion.visualSteps.coerceAtLeast(1)
        if (elapsed < duration) {
            val visualProgress =
                elapsed.toFloat() /
                    duration.toFloat() *
                    totalSteps.toFloat()
            val whole =
                floor(visualProgress)
                    .toInt()
                    .coerceIn(0, totalSteps - 1)
            val fraction =
                (visualProgress - whole)
                    .coerceIn(0f, 1f)
            val targetYaw =
                segmentFacingYaw(
                    player = player,
                    tokenIndex = tokenIndex,
                    motion = motion,
                    step = whole,
                    size = size,
                    cell = cell,
                    turns = turns,
                )
                    ?: LudoPawsPawnFacingPolicy.FRONT_YAW_DEGREES
            val previousYaw =
                if (whole > 0) {
                    segmentFacingYaw(
                        player = player,
                        tokenIndex = tokenIndex,
                        motion = motion,
                        step = whole - 1,
                        size = size,
                        cell = cell,
                        turns = turns,
                    ) ?: targetYaw
                } else {
                    LudoPawsPawnFacingPolicy.FRONT_YAW_DEGREES
                }
            return LudoPawsPawnFacingPolicy.movingYaw(
                previousYawDegrees = previousYaw,
                targetYawDegrees = targetYaw,
                stepProgress = fraction,
            )
        }

        val lastTravelYaw =
            segmentFacingYaw(
                player = player,
                tokenIndex = tokenIndex,
                motion = motion,
                step = totalSteps - 1,
                size = size,
                cell = cell,
                turns = turns,
            ) ?: LudoPawsPawnFacingPolicy.FRONT_YAW_DEGREES
        return LudoPawsPawnFacingPolicy.settlingYaw(
            lastTravelYawDegrees = lastTravelYaw,
            elapsedAfterMoveMillis = elapsed - duration,
        )
    }

    private fun segmentFacingYaw(
        player: PlayerSnapshot,
        tokenIndex: Int,
        motion: LudoPawsPawnMotion,
        step: Int,
        size: Float,
        cell: Float,
        turns: Int,
    ): Float? {
        val fromPosition =
            LudoPathEncoding.positionAtVisualStep(
                fromPosition = motion.fromPosition,
                step = step.coerceAtLeast(0),
            ) ?: return null
        val toPosition =
            LudoPathEncoding.positionAtVisualStep(
                fromPosition = motion.fromPosition,
                step = (step + 1).coerceAtMost(motion.visualSteps.coerceAtLeast(1)),
            ) ?: return null
        val from =
            LudoPawsFxBoardGeometry.tokenCenter(
                color = player.color,
                tokenIndex = tokenIndex,
                position = fromPosition,
                cell = cell,
                perspectiveQuarterTurns = turns,
            ) ?: return null
        val to =
            LudoPawsFxBoardGeometry.tokenCenter(
                color = player.color,
                tokenIndex = tokenIndex,
                position = toPosition,
                cell = cell,
                perspectiveQuarterTurns = turns,
            ) ?: return null
        val visibleFrom =
            rotatePoint(
                x = from.first,
                y = from.second,
                size = size,
                turns = turns,
            )
        val visibleTo =
            rotatePoint(
                x = to.first,
                y = to.second,
                size = size,
                turns = turns,
            )
        return LudoPawsPawnFacingPolicy.yawForVisibleDelta(
            dx = visibleTo.first - visibleFrom.first,
            dy = visibleTo.second - visibleFrom.second,
        )
    }

    private fun occupancyByCenter(
        snapshot: MatchSnapshot,
        cell: Float,
        turns: Int,
    ): Map<String, Int> {
        if (occupancySnapshot === snapshot && occupancyCell == cell && occupancyTurns == turns) return cachedOccupancy
        val counts = mutableMapOf<String, Int>()
        snapshot.players.forEach { player ->
            player.tokens.forEachIndexed { tokenIndex, position ->
                val key =
                    centerKey(
                        LudoPawsFxBoardGeometry.tokenCenter(
                            color = player.color,
                            tokenIndex = tokenIndex,
                            position = position,
                            cell = cell,
                            perspectiveQuarterTurns = turns,
                        ),
                    )
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        occupancySnapshot = snapshot
        occupancyCell = cell
        occupancyTurns = turns
        cachedOccupancy = counts
        return counts
    }

    private fun centerKey(center: Pair<Float, Float>?): String {
        if (center == null) return "missing"
        return "${(center.first * 100f).roundToInt()}:${(center.second * 100f).roundToInt()}"
    }

    private fun rotatePoint(
        x: Float,
        y: Float,
        size: Float,
        turns: Int,
    ): Pair<Float, Float> =
        when (((turns % 4) + 4) % 4) {
            1 -> (size - y) to x
            2 -> (size - x) to (size - y)
            3 -> y to (size - x)
            else -> x to y
        }

    private fun prepareRoot(
        x: Float,
        y: Float,
        scale: Float,
        liftY: Float,
        bodyYawDegrees: Float,
    ) {
        Matrix.setIdentityM(root, 0)
        Matrix.translateM(root, 0, x, y, 0f)
        // Screen Y grows downward. Negative local-Y scale keeps the prototype's
        // positive-Y-up character geometry visually upright on the Android board.
        Matrix.scaleM(root, 0, scale, -scale, scale)
        if (liftY != 0f) {
            Matrix.translateM(root, 0, 0f, liftY, 0f)
        }
        if (bodyYawDegrees != 0f) {
            Matrix.rotateM(root, 0, bodyYawDegrees, 0f, 1f, 0f)
        }
        // Compress around the feet, preserving volume instead of resizing the animal.
        if (bodyStretch != 1f) {
            val widthScale = 1f / kotlin.math.sqrt(bodyStretch)
            Matrix.translateM(root, 0, 0f, groundPivotY, 0f)
            Matrix.scaleM(root, 0, widthScale, bodyStretch, widthScale)
            Matrix.translateM(root, 0, 0f, -groundPivotY, 0f)
        }
    }

    private fun drawContactShadow(pawn: RenderPawn) {
        // The shadow marks the authoritative occupied square, not the bottom
        // of the oversized 3D body's on-screen bounding box. Keep it within
        // the smallest physical board cells and independent of body breathing.
        val cell = min(width, height).toFloat() / LudoPawsFxBoardGeometry.BOARD_SIZE
        val contactRadius =
            LudoPawsPawnLayout.contactShadowRadius(
                cell = cell,
                pawnRadius = pawn.radius,
                presentationScale = pawn.presentationScale,
            )

        Matrix.setIdentityM(root, 0)
        Matrix.translateM(
            root,
            0,
            pawn.x,
            pawn.y,
            -0.80f,
        )

        // Keep shadows out of the depth buffer. They are board-contact cues only
        // and must never occlude or clip the 3D animal geometry drawn afterwards.
        GLES30.glDepthMask(false)
        drawPart(
            parent = root,
            translateX = 0f,
            translateY = 0f,
            translateZ = 0f,
            scaleX = contactRadius,
            scaleY = contactRadius * 0.36f,
            scaleZ = 0.02f,
            color = SHADOW_OUTER,
        )
        drawPart(
            parent = root,
            translateX = 0f,
            translateY = 0f,
            translateZ = 0.01f,
            scaleX = contactRadius * 0.75f,
            scaleY = contactRadius * 0.24f,
            scaleZ = 0.02f,
            color = SHADOW_INNER,
        )
        GLES30.glDepthMask(true)
    }

    private fun drawPart(
        parent: FloatArray,
        translateX: Float,
        translateY: Float,
        translateZ: Float,
        scaleX: Float,
        scaleY: Float,
        scaleZ: Float,
        color: FloatArray,
        rotateX: Float = 0f,
        rotateY: Float = 0f,
        rotateZ: Float = 0f,
        geometry: MeshGeometry? = null,
    ) {
        System.arraycopy(parent, 0, model, 0, 16)
        Matrix.translateM(
            model,
            0,
            translateX,
            translateY,
            translateZ,
        )
        if (rotateX != 0f) Matrix.rotateM(model, 0, rotateX, 1f, 0f, 0f)
        if (rotateY != 0f) Matrix.rotateM(model, 0, rotateY, 0f, 1f, 0f)
        if (rotateZ != 0f) Matrix.rotateM(model, 0, rotateZ, 0f, 0f, 1f)
        Matrix.scaleM(
            model,
            0,
            scaleX,
            scaleY,
            scaleZ,
        )
        Matrix.multiplyMM(
            mvp,
            0,
            projection,
            0,
            model,
            0,
        )
        // Correct inverse-transpose normals preserve lighting on non-uniformly
        // scaled ellipsoids. Calculate the matrix once per body part, not per vertex.
        check(Matrix.invertM(inverseModel, 0, model, 0)) {
            "3D pawn model matrix is not invertible"
        }
        normalMatrix[0] = inverseModel[0]
        normalMatrix[1] = inverseModel[4]
        normalMatrix[2] = inverseModel[8]
        normalMatrix[3] = inverseModel[1]
        normalMatrix[4] = inverseModel[5]
        normalMatrix[5] = inverseModel[9]
        normalMatrix[6] = inverseModel[2]
        normalMatrix[7] = inverseModel[6]
        normalMatrix[8] = inverseModel[10]
        val mesh = if (geometry == null) sphere else customMeshes.getOrPut(geometry) {
            CharacterMesh(geometry, uMvpLocation, uColorLocation, uNormalMatrixLocation)
        }
        if (boundBendY != drawContext.limbBendY || boundBendZ != drawContext.limbBendZ) {
            boundBendY = drawContext.limbBendY
            boundBendZ = drawContext.limbBendZ
            GLES30.glUniform2f(uLimbBendLocation, boundBendY, boundBendZ)
        }
        if (boundSurface != drawContext.surface) {
            boundSurface = drawContext.surface
            GLES30.glUniform2f(uMaterialLocation, drawContext.surface.kind, drawContext.surface.gloss)
        }
        if (boundPartyEnergy != drawContext.partyEnergy) {
            boundPartyEnergy = drawContext.partyEnergy
            GLES30.glUniform1f(uPartyEnergyLocation, boundPartyEnergy)
        }
        val reflectedLight = drawContext.surface == CharacterSurface.PARTY_GLOW
        if (reflectedLight) GLES30.glDepthMask(false)
        mesh.draw(
            normalMatrix = normalMatrix,
            mvp = mvp,
            color = color,
        )
        if (reflectedLight) GLES30.glDepthMask(true)
    }

    private fun lerp(
        start: Float,
        end: Float,
        progress: Float,
    ): Float =
        start + (end - start) * progress.coerceIn(0f, 1f)

    private fun smoothStep(
        progress: Float,
    ): Float {
        val t = progress.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun legalAttentionScale(
        state: LudoPaws3DSceneState,
        key: LudoPaws3DPawnKey,
        nowMillis: Long,
    ): Float {
        val snapshot = state.snapshot ?: return 1f
        val pending = snapshot.pendingRoll ?: return 1f
        if (pending.status != "RESOLVED") return 1f
        if (state.localPlayerId != key.playerId) return 1f
        val player =
            snapshot.players.firstOrNull {
                it.playerId == key.playerId
            } ?: return 1f
        if (pending.seat != player.seat) return 1f
        if (key.tokenIndex !in pending.legalTokenIndexes) return 1f
        if (state.forwardMotion != null || state.captureReturns.isNotEmpty()) return 1f

        val cycleMillis = 900L
        val phase =
            (
                nowMillis % cycleMillis
            ).toFloat() /
                cycleMillis.toFloat()
        val breath =
            (
                sin(
                    phase *
                        PI.toFloat() *
                        2f,
                ) +
                    1f
                ) *
                .5f
        return 1f + breath * .14f
    }

    private data class RenderPawn(
        val key: LudoPaws3DPawnKey,
        val character: RegisteredCharacter,
        val teamColor: LudoPawsTeamColor,
        val seat: Int,
        val x: Float,
        val y: Float,
        val radius: Float,
        val facingYawDegrees: Float = LudoPawsPawnFacingPolicy.FRONT_YAW_DEGREES,
        val presentationScale: Float = 1f,
        val entryProgress: Float = 0f,
        val entryClip: FloatArray? = null,
        val attentionScale: Float = 1f,
    )

    private data class CaptureVisual(
        val center: Pair<Float, Float>,
        val scale: Float,
        val fromPosition: Int,
    )

    private data class MotionFrame(
        val kind: CharacterMotion,
        val progress: Float,
        val hopPlayback: CharacterHopPlayback = CharacterHopPlayback.ISOLATED,
    )

    private companion object {
        // 0.72 -> 1.20 is a 1.67x visual enlargement. Logical radius, path,
        // stacking, hit testing and animation timing stay exactly as before.
        const val MODEL_SCALE_PER_RADIUS = 1.20f

        val SHADOW_OUTER = floatArrayOf(0.025f, 0.030f, 0.040f, 0.07f)
        val SHADOW_INNER = floatArrayOf(0.020f, 0.025f, 0.035f, 0.16f)

    }
}
