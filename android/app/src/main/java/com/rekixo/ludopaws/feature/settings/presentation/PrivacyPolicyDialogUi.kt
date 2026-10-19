package com.rekixo.ludopaws.ui.dialogs

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

internal fun showPrivacyPolicyDialog(
    context: Context,
) {
    val dialog = baseDialog(context)
    val panel =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                settingsDp(context, 16),
                settingsDp(context, 14),
                settingsDp(context, 16),
                settingsDp(context, 14),
            )
            background =
                GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        0xFF08378F.toInt(),
                        0xFF031B50.toInt(),
                    ),
                ).apply {
                    cornerRadius = settingsDp(context, 18).toFloat()
                    setStroke(
                        settingsDp(context, 2),
                        0xFFFFC735.toInt(),
                    )
                }

            addView(
                TextView(context).apply {
                    text = "PRIVACY POLICY"
                    textSize = 20f
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setTextColor(0xFFFFD34E.toInt())
                    gravity = Gravity.CENTER
                },
            )
        }

    val scroll =
        ScrollView(context).apply {
            isFillViewport = false
            overScrollMode = ScrollView.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
    val body =
        TextView(context).apply {
            text = PRIVACY_POLICY_TEXT
            textSize = 13.5f
            setTextColor(Color.WHITE)
            setLineSpacing(0f, 1.18f)
            setPadding(
                settingsDp(context, 4),
                settingsDp(context, 14),
                settingsDp(context, 4),
                settingsDp(context, 14),
            )
        }
    scroll.addView(body)
    panel.addView(
        scroll,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            settingsDp(context, 430),
        ),
    )

    panel.addView(
        TextView(context).apply {
            text = "OPEN FULL ONLINE PRIVACY POLICY"
            textSize = 13f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(0xFFFFD34E.toInt())
            gravity = Gravity.CENTER
            setPadding(
                0,
                settingsDp(context, 8),
                0,
                settingsDp(context, 8),
            )
            isClickable = true
            isFocusable = true
            contentDescription = "Open the full online Ludo Paws Privacy Policy"
            setOnClickListener {
                try {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://ludopaws.rekixo.com/privacy-policy.html"),
                        ),
                    )
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, "No browser is available to open this page.", Toast.LENGTH_LONG).show()
                }
            }
        },
    )

    panel.addView(
        TextView(context).apply {
            text = "OPEN PRICING & IN-APP PURCHASE POLICY"
            textSize = 13f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(0xFFFFD34E.toInt())
            gravity = Gravity.CENTER
            setPadding(
                0,
                settingsDp(context, 8),
                0,
                settingsDp(context, 8),
            )
            isClickable = true
            isFocusable = true
            contentDescription = "Open the Ludo Paws pricing and in-app purchase policy"
            setOnClickListener {
                try {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://ludopaws.rekixo.com/pricing.html"),
                        ),
                    )
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, "No browser is available to open this page.", Toast.LENGTH_LONG).show()
                }
            }
        },
    )

    panel.addView(
        TextView(context).apply {
            text = "OPEN TERMS OF SERVICE"
            textSize = 13f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(0xFFFFD34E.toInt())
            gravity = Gravity.CENTER
            setPadding(
                0,
                settingsDp(context, 8),
                0,
                settingsDp(context, 8),
            )
            isClickable = true
            isFocusable = true
            contentDescription = "Open the Ludo Paws Terms of Service"
            setOnClickListener {
                try {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://ludopaws.rekixo.com/terms-of-service.html"),
                        ),
                    )
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, "No browser is available to open this page.", Toast.LENGTH_LONG).show()
                }
            }
        },
    )

    panel.addView(
        TextView(context).apply {
            text = "CLOSE"
            textSize = 14f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(0xFFFFD34E.toInt())
            gravity = Gravity.CENTER
            setPadding(
                0,
                settingsDp(context, 12),
                0,
                settingsDp(context, 8),
            )
            isClickable = true
            isFocusable = true
            setOnClickListener {
                dialog.dismiss()
            }
        },
    )

    dialog.setContentView(panel)
    sizeDialog(dialog, .88f)
    dialog.show()
}

private const val PRIVACY_POLICY_TEXT =
    """Last updated: October 15, 2026

Ludo Paws is a mobile Ludo-style game published under the Rekixo brand. The data processed depends on the features you use and the services enabled in your version of the game.

INFORMATION THAT MAY BE PROCESSED
• Account information: an internal account ID, display name, account state, and links to Google, Friend ID or leaderboard identities when you use those features. Ludo Paws does not need your Google password.
• Security information: session identifiers/expiry status, refresh-token hashes, sign-in nonces, request identifiers, and limited anti-abuse/rate-limit records.
• Gameplay and fairness: online match and pseudonymous player IDs, game state, moves, results, timestamps, ruleset IDs, server dice receipts and event history. Offline game state may remain on your device.
• Optional voice chat during Friends matches: during an active Friends match, if you start voice chat and grant microphone permission, your microphone audio is sent to participating friends over WebRTC. The game service relays connection setup data (session descriptions and ICE candidates); Google's public STUN service helps establish the connection and may process IP/network information. The current client does not upload the voice stream to the game API for storage; network details may be exposed to other participants depending on the connection.
• Social and leaderboard information: Friend IDs, display names, relationships, requests, invitations, recent players, friend conversations when used, submitted results and scores.
• Store data: gem balance, selected/owned cosmetics, cosmetic/reward progress when enabled, Play product IDs and hashed purchase references used to verify entitlements and prevent duplicate grants.
• Device data: local settings, profile statistics, offline game state and account-scoped cache. Hosting or network providers may process IP address and request/device-network information to deliver and protect the service. The API derives a one-way IP hash for rate limiting and does not intentionally save raw IP addresses in match or rate-limit state.

WHY DATA IS USED AND WHO MAY PROCESS IT
Data is used to run the game and accounts, authenticate sessions, provide online/social/leaderboard features, sync entitlements, verify purchases and rewarded ads, protect match integrity, prevent abuse, troubleshoot errors and respond to privacy requests. Depending on features enabled, providers include Cloudflare hosting/storage, Google sign-in services, Google Play Games Services for supported achievements/leaderboard integration, Google Play Billing, Google's public STUN service for optional voice-chat setup, and Google Mobile Ads/AdMob with Google's consent tools. Providers apply their own privacy notices and terms. Ludo Paws does not sell personal information.

PURCHASES AND ADS
The one-time product contract is described at https://ludopaws.rekixo.com/pricing.html. Google Play supplies the localized checkout price; the website does not take payment. The server checks each one-time purchase before granting its configured Gem amount or non-consumable entitlement. Google Play separately keeps its order/payment history. When advertising is enabled, Google Mobile Ads/AdMob may process ad requests, device or advertising identifiers, consent signals and ad-interaction information under Google's policies. A player chooses whether to watch an opt-in rewarded ad; the configured reward is five Gems and is granted only after server-side verification succeeds. The Remove Ads purchase suppresses eligible non-rewarded ads but does not prevent voluntary rewarded ads.

LOCAL STORAGE AND SECURITY
The app uses Android Keystore-backed protection for account credentials and selected sensitive local records where implemented. API requests use HTTPS, and local wallet caches are not trusted as proof of paid entitlement. If local cleanup fails after account deletion, clear Ludo Paws app storage on that device before handing it over.

RETENTION AND ACCOUNT DELETION
Open Profile → menu → Delete account and data to delete your Ludo Paws account, or use the official web page:
https://ludopaws.rekixo.com/account-deletion.html
The web page lets a Google-linked player verify the previously linked Google identity or lets an eligible unlinked guest use the delete-only code created before uninstalling. The guest code can delete an account only; it cannot sign in or restore progress. Never send it to support.

When the request completes, the service deletes the account profile, sessions, wallet, cosmetics/reward progress, app-side purchase-ledger records, linked social data and leaderboard profile; the app also attempts to clear local account caches. The current online protocol creates no separate long-term match-proof archive. Match room state and recent command receipts follow scheduled activity-based cleanup. Minimal hashed/pseudonymous deletion and anti-replay security markers may remain as long as needed to prevent deleted accounts or codes from being reused; the current service does not assign a fixed expiry to every such marker. Google Play order history and independent providers' records are not deleted by this request. Offline/local data may remain until app data is cleared or the app is uninstalled.

CONTACT AND AUDIENCE
For privacy questions or help with account deletion, contact AI@rekixo.com. The publisher must confirm the target age group/children's treatment and align the final public policy with service providers and Google Play Data safety answers before release."""
