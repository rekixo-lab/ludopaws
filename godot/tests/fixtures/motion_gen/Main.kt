import com.rekixo.ludopaws.LudoPathEncoding
import com.rekixo.ludopaws.LudoPawsPawnAnimationPolicy

// Prints, for every (from, to) token change, the UNCHANGED live animation decision
// (kind + visual steps) and the cells a forward move walks through.
fun main() {
    val rows = mutableListOf<String>()
    for (from in -1..57) for (to in -1..57) {
        val m = LudoPawsPawnAnimationPolicy.transition("p", 0, from, to)
        val kind = m?.kind?.name ?: "NONE"
        val steps = m?.visualSteps ?: 0
        val path = if (m?.kind?.name == "FORWARD")
            (1..steps).map { LudoPathEncoding.positionAtVisualStep(from, it) } else emptyList()
        rows += "[$from,$to,\"$kind\",$steps,[${path.joinToString(",")}]]"
    }
    println("[\n" + rows.joinToString(",\n") + "\n]")
}
