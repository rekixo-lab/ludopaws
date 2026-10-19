package com.rekixo.ludopaws

internal enum class OnlineStateSource {
    AUTHORITATIVE,
    CACHE,
}

internal object OnlineCachedStateRestorePolicy {
    fun shouldApply(
        sessionMatchId: String?,
        cachedMatchId: String?,
    ): Boolean =
        !sessionMatchId.isNullOrBlank() &&
            sessionMatchId == cachedMatchId
}
