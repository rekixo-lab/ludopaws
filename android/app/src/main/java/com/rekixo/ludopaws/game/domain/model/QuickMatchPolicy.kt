package com.rekixo.ludopaws

/** Explicit local variant: two players race two paws each; the other slots are inactive/home. */
object QuickMatchPolicy {
    const val RULESET_ID = "ludopaws-quick-two-v1"
    val initialTokens: List<Int> get() = listOf(-1, -1, 57, 57)
}
