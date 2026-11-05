package com.rekixo.ludopaws.feature.sharing

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.rekixo.ludopaws.R

internal const val PUBLIC_SHARE_HOST = "ludopaws.rekixo.com"
internal const val LEGACY_SHARE_HOST = "api.ludopaws.rekixo.com"
internal const val GAME_SHARE_URL = "https://$PUBLIC_SHARE_HOST/share"

internal fun gameShareChooser(context: Context): Intent =
    Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_subject))
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_game_invite, GAME_SHARE_URL))
    }, context.getString(R.string.share_chooser_title))

private val referralPath = Regex("/r/[a-f0-9]{64}")

internal fun Uri.isReferralShareLink(): Boolean =
    scheme == "https" &&
        (host == PUBLIC_SHARE_HOST || host == LEGACY_SHARE_HOST) &&
        encodedAuthority == host &&
        referralPath.matches(encodedPath.orEmpty()) &&
        encodedQuery == null && encodedFragment == null
