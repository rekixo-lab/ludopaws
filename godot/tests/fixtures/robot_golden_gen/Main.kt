import com.rekixo.ludopaws.*
import com.rekixo.ludopaws.feature.offline.domain.ai.LudoPawsComputerMovePolicy

var seed = 20261110L
fun rand(): Double { seed = (seed * 1103515245L + 12345L) % 2147483648L; return seed / 2147483648.0 }
fun ri(n: Int) = (rand() * n).toInt()
val COLORS = listOf("RED", "GREEN", "YELLOW", "BLUE")
val POOL = listOf(-1, -1, -1, 57) + (0..50) + (0..50) + (52..56)

fun main() {
    val sb = StringBuilder("{\"source\":\"LudoPawsComputerMovePolicy.kt\",\"cases\":[")
    var written = 0
    var attempts = 0
    while (written < 4000 && attempts < 100000) {
        attempts++
        val n = 2 + ri(3)
        val team = n == 4 && rand() < 0.3
        val players = (0 until n).map { s ->
            PlayerSnapshot("p$s", "P$s", COLORS[s], s, List(4) { POOL[ri(POOL.size)] }, if (team) listOf("A", "B", "A", "B")[s] else null)
        }
        val seat = ri(n)
        val outcome = if (rand() < 0.3) 6 else 1 + ri(6)
        val comeback = rand() < 0.05
        val me = players[seat]
        val legal = (0..3).filter { i ->
            val t = me.tokens[i]
            (comeback && t == -1) || (!comeback && LudoPathEncoding.destinationForRoll(t, outcome) != null)
        }.toSet()
        if (legal.size < 2) continue
        val state = MatchSnapshot(players, PendingRollSnapshot(comeback))
        val choice = LudoPawsComputerMovePolicy.chooseToken(state, me, legal, outcome)
        val scores = legal.sorted().map { LudoPawsComputerMovePolicy.evaluate(state, me, it, outcome)!!.score }
        if (written > 0) sb.append(',')
        sb.append("{\"k\":").append(players.map { it.tokens }).append(",\"team\":").append(team)
          .append(",\"seat\":").append(seat).append(",\"o\":").append(outcome).append(",\"cb\":").append(comeback)
          .append(",\"l\":").append(legal.sorted()).append(",\"sc\":").append(scores).append(",\"pick\":").append(choice).append('}')
        written++
    }
    sb.append("]}")
    println(sb)
    System.err.println("cases=$written")
}
