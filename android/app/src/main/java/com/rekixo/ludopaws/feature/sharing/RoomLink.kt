package com.rekixo.ludopaws.feature.sharing

import android.net.Uri

/** Navigation hints only. The server's existing invite authorization remains mandatory. */
internal data class RoomLink(val room: String, val mode: String, val host: String?) {
    fun url(): String = Uri.Builder().scheme("https").authority(HOST).appendPath("play")
        .appendPath(mode.lowercase(java.util.Locale.ROOT)).appendPath(room).apply {
            host?.let { appendQueryParameter("host", it) }
        }.build().toString()
    companion object {
        const val HOST = "api.ludopaws.rekixo.com"
        const val EXTRA_LINK = "ludopaws_room_link"
        private val roomPattern = Regex("LP[A-Z2-9]{8}")
        private val friendPattern = Regex("LPF-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}")
        fun parse(uri: Uri?): RoomLink? {
            if (uri == null || uri.toString().length > 512 || uri.scheme !in setOf("https", "ludopaws") ||
                uri.host != HOST || uri.port != -1 || uri.userInfo != null || uri.fragment != null) return null
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
