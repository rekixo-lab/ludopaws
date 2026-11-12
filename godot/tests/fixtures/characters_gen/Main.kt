import com.rekixo.ludopaws.LudoPaws3DReactionPose
import com.rekixo.ludopaws.LudoPawsTeamColor
import com.rekixo.ludopaws.feature.characters.domain.model.AnimationCue
import com.rekixo.ludopaws.feature.characters.rendering.*
import com.rekixo.ludopaws.feature.characters.shared.*
import com.rekixo.ludopaws.rendering.meshes.MeshGeometry
import com.rekixo.ludopaws.rendering.meshes.SphereGeometry
import android.opengl.Matrix
import kotlin.math.*

/** Records every part the live renderer draws: final matrix, colour, surface, mesh name. */
internal class Rec(private val names: Map<MeshGeometry, String>) : CharacterDrawContext() {
    val parts = StringBuilder()
    private var count = 0
    private val m = FloatArray(16)
    override fun drawPart(parent: FloatArray, translateX: Float, translateY: Float, translateZ: Float,
        scaleX: Float, scaleY: Float, scaleZ: Float, color: FloatArray, rotateX: Float, rotateY: Float, rotateZ: Float) =
        drawGeometry(parent, SphereGeometry.standard, translateX, translateY, translateZ, scaleX, scaleY, scaleZ, color, rotateX, rotateY, rotateZ)
    override fun drawGeometry(parent: FloatArray, geometry: MeshGeometry, translateX: Float, translateY: Float, translateZ: Float,
        scaleX: Float, scaleY: Float, scaleZ: Float, color: FloatArray, rotateX: Float, rotateY: Float, rotateZ: Float) {
        System.arraycopy(parent, 0, m, 0, 16)
        Matrix.translateM(m, 0, translateX, translateY, translateZ)
        if (rotateX != 0f) Matrix.rotateM(m, 0, rotateX, 1f, 0f, 0f)
        if (rotateY != 0f) Matrix.rotateM(m, 0, rotateY, 0f, 1f, 0f)
        if (rotateZ != 0f) Matrix.rotateM(m, 0, rotateZ, 0f, 0f, 1f)
        Matrix.scaleM(m, 0, scaleX, scaleY, scaleZ)
        if (count++ > 0) parts.append(',')
        parts.append("[\"").append(names[geometry] ?: "sphere").append("\",\"").append(surface.name).append("\",")
        parts.append((color.toList() + m.toList() + listOf(limbBendY, limbBendZ, partyEnergy)).joinToString(",") { f(it) }).append(']')
    }
}

fun f(v: Float): String = if (v.isFinite()) "%.6f".format(java.util.Locale.ROOT, v) else "0"
fun nf(v: Float?): String = if (v == null) "null" else f(v)

internal fun poseJson(p: CharacterPose) = "{" + listOf(
    "liftY" to p.liftY, "bodyScaleY" to p.bodyScaleY, "bodyYawDegrees" to p.bodyYawDegrees,
    "headTiltDegrees" to p.headTiltDegrees, "primaryAppendageDegrees" to p.primaryAppendageDegrees,
    "secondaryAppendageDegrees" to p.secondaryAppendageDegrees, "tertiaryAppendageDegrees" to p.tertiaryAppendageDegrees,
    "legSwingDegrees" to p.legSwingDegrees, "legTuckDegrees" to p.legTuckDegrees, "legPaddleDegrees" to p.legPaddleDegrees,
    "bodyPitchDegrees" to p.bodyPitchDegrees, "bodyRollDegrees" to p.bodyRollDegrees, "neckPitchDegrees" to p.neckPitchDegrees,
    "bodyCrouch" to p.bodyCrouch, "jawOpen" to p.jawOpen, "stridePhase" to p.stridePhase, "strideWeight" to p.strideWeight,
    "frontLegTuckDegrees" to p.frontLegTuckDegrees, "rearLegTuckDegrees" to p.rearLegTuckDegrees,
    "frontLegSwingDegrees" to p.frontLegSwingDegrees, "rearLegSwingDegrees" to p.rearLegSwingDegrees,
    "gestureWeight" to p.gestureWeight, "gesturePhase" to p.gesturePhase,
).joinToString(",") { "\"${it.first}\":${nf(it.second)}" } + "}"

fun reactionJson(r: LudoPaws3DReactionPose) = "{" + listOf(
    "liftY" to r.liftY, "bodyYawDegrees" to r.bodyYawDegrees, "headTiltDegrees" to r.headTiltDegrees,
    "primaryAppendageDegrees" to r.primaryAppendageDegrees, "secondaryAppendageDegrees" to r.secondaryAppendageDegrees,
    "tertiaryAppendageDegrees" to r.tertiaryAppendageDegrees, "scale" to r.scale, "bodyPitchDegrees" to r.bodyPitchDegrees,
    "neckPitchDegrees" to r.neckPitchDegrees, "jawOpen" to r.jawOpen,
).joinToString(",") { "\"${it.first}\":${f(it.second)}" } + "}"

/** LudoPaws3DReactionMotion.sample frame math (its file depends on the whole catalog). */
fun reactionFrame(cue: AnimationCue, progress: Float): CharacterReactionFrame {
    val raw = progress.coerceIn(0f, 1f)
    val p = if (cue == AnimationCue.CAPTURED || cue == AnimationCue.NERVOUS) {
        if (raw < .18f) .5f * (raw / .18f) else .5f + .5f * (raw - .18f) / .82f
    } else raw
    return CharacterReactionFrame(p, sin(p * PI).toFloat().coerceAtLeast(0f), sin(p * PI * 2.0).toFloat(),
        sin(p * PI * 5.0).toFloat(), abs(sin(p * PI * 2.0).toFloat()) * (1f - p * .20f))
}

internal fun meshJson(g: MeshGeometry): String {
    var sx = 0.0; var sy = 0.0; var sz = 0.0; var sn = 0.0
    for (i in g.vertices.indices step 6) {
        sx += g.vertices[i]; sy += g.vertices[i + 1]; sz += g.vertices[i + 2]
        sn += g.vertices[i + 3] * 1.0 + g.vertices[i + 4] * 2.0 + g.vertices[i + 5] * 3.0
    }
    var sl = 0.0
    g.localPositions?.forEachIndexed { i, v -> sl += v * (1 + i % 3) }
    return "[${g.vertices.size / 6},${g.indices.size},${"%.5f".format(java.util.Locale.ROOT, sx)},${"%.5f".format(java.util.Locale.ROOT, sy)},${"%.5f".format(java.util.Locale.ROOT, sz)},${"%.5f".format(java.util.Locale.ROOT, sn)},${"%.5f".format(java.util.Locale.ROOT, sl)}]"
}

fun main(args: Array<String>) {
    val species = Species.all.first { it.id == args[0] }
    val names = species.meshes.entries.associate { (k, v) -> v to k }
    val out = StringBuilder("{\"species\":\"${species.id}\",\"meshes\":{")
    out.append(species.meshes.entries.joinToString(",") { "\"${it.key}\":${meshJson(it.value)}" }).append("},\"samples\":[")
    val identity = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    val sampleRows = mutableListOf<String>()
    val drawRows = mutableListOf<String>()
    for (motion in CharacterMotion.values()) {
        val playbacks = if (motion == CharacterMotion.HOP) CharacterHopPlayback.values().toList() else listOf(CharacterHopPlayback.ISOLATED)
        for (pb in playbacks) for (step in 0..10) {
            val progress = step / 10f
            val pose = species.renderer.sample(motion, progress, pb)
            sampleRows += "{\"m\":\"${motion.name}\",\"p\":${f(progress)},\"pb\":\"${pb.name}\",\"pose\":${poseJson(pose)}}"
            if (step % 3 == 0) {
                val rec = Rec(names)
                species.renderer.draw(rec, identity, pose, LudoPawsTeamColor.RED)
                drawRows += "{\"m\":\"${motion.name}\",\"p\":${f(progress)},\"pb\":\"${pb.name}\",\"life\":false,\"team\":\"RED\",\"parts\":[${rec.parts}]}"
            }
        }
    }
    out.append(sampleRows.joinToString(",")).append("],\"reactions\":[")
    val reactionRows = mutableListOf<String>()
    for (cue in AnimationCue.values()) for (step in 0..8) {
        val p = step / 8f
        reactionRows += "{\"cue\":\"${cue.name}\",\"p\":${f(p)},\"pose\":${reactionJson(species.reactions.sample(cue, reactionFrame(cue, p)))}}"
    }
    out.append(reactionRows.joinToString(",")).append("],\"life\":[")
    // Deterministic life-motion run, then a draw that uses its blink/gaze/breath state.
    val life = LudoPaws3DLifeMotion()
    val lifeRows = mutableListOf<String>()
    val target = species.reactions.sample(AnimationCue.EXCITED, reactionFrame(AnimationCue.EXCITED, .4f))
    var last = LudoPaws3DReactionPose()
    for (frame in 0 until 120) {
        val now = 5_000L + frame * 16L
        last = life.update(now, 37, frame < 80, .3f, -.2f, if (frame in 40..90) target else LudoPaws3DReactionPose())
        if (frame % 10 == 9) lifeRows += "{\"frame\":$frame,\"blink\":${f(life.blink)},\"breath\":${f(life.breath)},\"headTilt\":${f(life.headTilt)},\"headPitch\":${f(life.headPitch)},\"mouthOpen\":${f(life.mouthOpen)},\"shake\":${f(life.shake)},\"gazeX\":${f(life.gazeX)},\"gazeY\":${f(life.gazeY)},\"idleWeight\":${f(life.idleWeight)},\"reaction\":${reactionJson(last)}}"
    }
    out.append(lifeRows.joinToString(",")).append("],\"idle\":[")
    val idleRows = mutableListOf<String>()
    for (step in 0..4) {
        val base = species.renderer.sample(CharacterMotion.IDLE, step / 4f, CharacterHopPlayback.ISOLATED)
        idleRows += "{\"p\":${f(step / 4f)},\"pose\":${poseJson(life.idlePose(base, species.id))}}"
    }
    out.append(idleRows.joinToString(",")).append("],\"lifeDraw\":")
    val rec = Rec(names)
    rec.life = life
    species.renderer.draw(rec, identity, life.idlePose(species.renderer.sample(CharacterMotion.IDLE, .3f, CharacterHopPlayback.ISOLATED), species.id), LudoPawsTeamColor.BLUE)
    out.append("{\"team\":\"BLUE\",\"parts\":[${rec.parts}]}")
    out.append(",\"draws\":[").append(drawRows.joinToString(",")).append("]}")
    println(out)
}
