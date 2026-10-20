package com.rekixo.ludopaws.feature.online

/** WebRTC peer allocation is NOT proof that audio has connected. */
internal enum class VoicePeerStatus { CONNECTING, CONNECTED, INTERRUPTED, FAILED }

internal object VoicePeerStatusPolicy {
    fun message(states: Collection<VoicePeerStatus>): String {
        if (states.isEmpty()) return "Waiting for a friend to join voice…"
        val connected = states.count { it == VoicePeerStatus.CONNECTED }
        if (connected > 0) return "Voice connected to $connected friend${if (connected == 1) "" else "s"}"
        if (states.any { it == VoicePeerStatus.CONNECTING }) return "Connecting to friends' voice…"
        if (states.any { it == VoicePeerStatus.INTERRUPTED }) return "Voice connection interrupted — reconnecting…"
        return "Voice connection failed on this network"
    }
}
