import com.rekixo.ludopaws.LudoPawsFxBoardGeometry
import com.rekixo.ludopaws.LudoPawsPawnLayout

// Prints golden pawn-layout values from the UNCHANGED live LudoPawsPawnLayout (read-only):
// roadPresentation (radius + foot drop) for every colour/position/occupancy/species/turn,
// sampled at the authoritative token centre.
fun main() {
    val species = listOf("dog", "goat", "duck", "cat", "donkey", "goat_lumi", "cock", "pig",
        "elephant", "horse", "rabbit", "panda", "monkey", "giraffe")
    val rows = mutableListOf<String>()
    val cell = 1f
    for (color in listOf("RED", "GREEN", "YELLOW", "BLUE")) {
        for (position in -1..57) {
            for (turns in 0..3) {
                val c = LudoPawsFxBoardGeometry.tokenCenter(color, 1, position, cell, turns) ?: continue
                for (occupancy in listOf(1, 2, 3, 5, 7)) {
                    for (id in species) {
                        if ((position + occupancy + turns + id.length) % 13 != 0 && !(turns == 0 && occupancy == 1 && position % 4 == 0)) continue
                        val fit = LudoPawsPawnLayout.roadPresentation(c.first, c.second, cell, position, occupancy, id, turns)
                        rows += "[\"$color\",$position,$turns,$occupancy,\"$id\",${c.first},${c.second},${fit.radius},${fit.footDrop}]"
                    }
                }
            }
        }
    }
    println("[\n" + rows.joinToString(",\n") + "\n]")
}
