package com.rekixo.ludopaws.feature.sharing

import android.net.Uri

/** Navigation hints only. The server's existing invite authorization remains mandatory. */
internal data class RoomLink(val room: String, val mode: String, val host: String?) {
    fun url(): String = Uri.Builder().scheme("https").authority(PUBLIC_SHARE_HOST).appendPath("play")
        .appendPath(mode.lowercase(java.util.Locale.ROOT)).appendPath(room).apply {
            host?.let { appendQueryParameter("host", it) }
        }.build().toString()

    fun shareMessage(): String = buildString {
        append("Join my private Ludo Paws room: ").append(room).append('\n')
        host?.let { append("Host Friend ID: ").append(it).append('\n') }
        append("After opening the game, go to Friends and ask the host to send you the room invite.")
        if (host != null) append(" New friends can add the host using the Friend ID above.")
        append("\n\nGet Ludo Paws on Google Play:\n").append(url())
    }

    companion object {
        const val EXTRA_LINK = "ludopaws_room_link"
        private val roomPattern = Regex("LP[A-Z2-9]{8}")
        private val friendPattern = Regex("LPF-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}")
        fun parse(uri: Uri?): RoomLink? {
            if (uri == null || uri.toString().length > 512 ||
                uri.encodedAuthority != uri.host || uri.fragment != null) return null
            val validOrigin = when (uri.scheme) {
                "https" -> uri.host == PUBLIC_SHARE_HOST || uri.host == LEGACY_SHARE_HOST
                "ludopaws" -> uri.host == LEGACY_SHARE_HOST
                else -> false
            }
            if (!validOrigin) return null
            val parts = uri.pathSegments
            if (parts.size != 3 || parts[0] != "play" || parts[1] != "friends" || !roomPattern.matches(parts[2])) return null
            val host = uri.getQueryParameter("host")
            if (host != null && !friendPattern.matches(host)) return null
            return RoomLink(parts[2], parts[1].uppercase(java.util.Locale.ROOT), host)
        }
        fun create(room: String, mode: String, host: String? = null): RoomLink? =
            if (roomPattern.matches(room) && mode == "FRIENDS" && (host == null || friendPattern.matches(host))) RoomLink(room, mode, host) else null
    }
}
