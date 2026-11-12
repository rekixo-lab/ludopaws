import android.graphics.Canvas
import android.graphics.Recorder
import com.rekixo.ludopaws.feature.dice.domain.DiceRegistry
import com.rekixo.ludopaws.feature.dice.rendering.DiceRenderFrame
import com.rekixo.ludopaws.feature.dice.rendering.DiceRendererRegistry

// Records the UNCHANGED live dice renderers (read-only) at size 100, density 2, for every
// live dice over faces and render states (rest, attention pulse, rolling, settling, deadline).
fun main() {
    val ids = listOf("dice_classic", "dice_pearl_paw", "dice_forest_dew", "dice_lagoon_pearl",
        "dice_frost_crystal", "dice_starlight", "dice_ember_forge", "dice_prism_royal")
    val parts = mutableListOf<String>()
    // The spatial dice face textures are drawn once into bitmaps; record those drawings too.
    val surfaces = mutableListOf<String>()
    for ((name, material) in listOf("surface_forge" to com.rekixo.ludopaws.feature.dice.content.spatial.SpatialDiceMaterial.FORGE,
            "surface_prism" to com.rekixo.ludopaws.feature.dice.content.spatial.SpatialDiceMaterial.PRISM)) {
        Recorder.out.setLength(0); Recorder.first = true
        val bitmap = com.rekixo.ludopaws.feature.dice.content.spatial.SpatialDiceSurfaces.forMaterial(material)
        bitmap.key = name
        surfaces += "\"$name\":[\n${Recorder.out}\n]"
    }
    for (id in ids) {
        val definition = DiceRegistry.resolve(id)
        check(definition.id == id) { "unknown dice $id" }
        val renderer = DiceRendererRegistry.create(definition, android.content.res.Resources())
        val palette = definition.palette(0xFFE85468.toInt())
        val frames = mutableListOf<String>()
        fun frame(label: String, f: DiceRenderFrame) {
            Recorder.out.setLength(0); Recorder.first = true
            val rect = renderer.draw(Canvas(), 100f, palette, f)
            frames += "{\"label\":\"$label\",\"rect\":[${rect.j()}],\"commands\":[\n${Recorder.out}\n]}"
        }
        for (face in 1..6) {
            frame("rest $face", DiceRenderFrame(face, false, false, false, 0f, false))
            frame("attention $face", DiceRenderFrame(face, false, false, true, .5f, false))
        }
        frame("attention-low 3", DiceRenderFrame(3, false, false, true, 0f, false))
        frame("attention-high 3", DiceRenderFrame(3, false, false, true, 1f, false))
        frame("deadline 4", DiceRenderFrame(4, false, false, true, .7f, true))
        for (elapsed in listOf(0L, 60L, 150L, 230L, 400L, 700L)) {
            frame("rolling 2 $elapsed", DiceRenderFrame(2, true, false, false, .4f, false, 5, elapsed, 0f))
        }
        for (settle in listOf(0f, .3f, .7f, 1f)) {
            frame("settling 6 $settle", DiceRenderFrame(6, false, true, false, .6f, false, 1, 520L, settle))
        }
        parts += "\"$id\":{\"usesPerimeterCountdown\":${renderer.usesPerimeterCountdown},\"usesSpatialRoll\":${renderer.usesSpatialRoll},\"frames\":[\n${frames.joinToString(",\n")}\n]}"
    }
    parts += "\"surfaces\":{${surfaces.joinToString(",")}}"
    println("{\n" + parts.joinToString(",\n") + "\n}")
}
