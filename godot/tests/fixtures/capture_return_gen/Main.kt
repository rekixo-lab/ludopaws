import com.rekixo.ludopaws.LudoPawsCaptureReturnPlacement

// Prints golden capture-return placements from the UNCHANGED live policy (read-only).
fun main() {
    val routes = listOf(
        Triple(100f to 140f, 40f to 40f, 24f),
        Triple(120f to 160f, 40f to 50f, 26f),
        Triple(180f to 130f, 44f to 52f, 28f),
        Triple(100f to 100f, 40f to 100f, 30f),
        Triple(2.5f to 6.5f, 1.87f to 1.87f, 1f),
        Triple(7.5f to 13.5f, 12.13f to 13.13f, 1f),
        Triple(3f to 3f, 3f to 3f, 1f),
    )
    val rows = mutableListOf<String>()
    for ((from, to, cell) in routes) {
        for (i in 0..100) {
            val p = i / 100f
            val s = LudoPawsCaptureReturnPlacement.sample(from, to, cell, p)
            rows += "[${from.first},${from.second},${to.first},${to.second},$cell,$p,${s.x},${s.y},${s.scale},\"${s.phase}\"]"
        }
    }
    println("[\n" + rows.joinToString(",\n") + "\n]")
}
