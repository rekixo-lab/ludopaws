import android.graphics.Canvas
import android.graphics.Recorder
import com.rekixo.ludopaws.feature.boards.domain.BoardRegistry
import com.rekixo.ludopaws.feature.boards.rendering.BoardRendererRegistry

// Records the UNCHANGED live board renderers (read-only) for every live non-classic board at
// size 150 (the renderers draw in a 150-unit space) into one JSON document.
fun main(args: Array<String>) {
    val ids = listOf("board_astral", "board_cloud_lagoon", "board_autumn_forest", "board_blossom_garden",
        "board_winter_haven", "board_monsoon_mire", "board_sunstone_palace")
    val parts = mutableListOf<String>()
    for (id in ids) {
        val definition = BoardRegistry.resolve(id)
        check(definition.id == id) { "unknown board $id" }
        Recorder.out.setLength(0); Recorder.first = true
        BoardRendererRegistry.create(definition).draw(Canvas(), 150f, 2f, definition)
        parts += "\"$id\":{\"surface\":${definition.surfaceColor.toLong() and 0xFFFFFFFFL},\"neutral\":${definition.neutralCellColor.toLong() and 0xFFFFFFFFL}," +
            "\"stoneRoads\":${definition.stoneRoadOverlayEnabled},\"monsoon\":${definition.monsoonEffectsEnabled},\"commands\":[\n${Recorder.out}\n]}"
    }
    println("{\n" + parts.joinToString(",\n") + "\n}")
}
