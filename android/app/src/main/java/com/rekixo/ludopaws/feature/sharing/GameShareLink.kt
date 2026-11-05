package com.rekixo.ludopaws.feature.sharing

import android.net.Uri

internal const val PUBLIC_SHARE_HOST = "ludopaws.rekixo.com"
internal const val LEGACY_SHARE_HOST = "api.ludopaws.rekixo.com"
internal const val GAME_SHARE_URL = "https://$PUBLIC_SHARE_HOST/share"

private val referralPath = Regex("/r/[a-f0-9]{64}")

internal fun Uri.isReferralShareLink(): Boolean =
    scheme == "https" &&
        (host == PUBLIC_SHARE_HOST || host == LEGACY_SHARE_HOST) &&
        encodedAuthority == host &&
        referralPath.matches(encodedPath.orEmpty()) &&
        encodedQuery == null && encodedFragment == null
