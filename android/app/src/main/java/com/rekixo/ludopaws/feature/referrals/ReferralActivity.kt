package com.rekixo.ludopaws.feature.referrals

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.CheckBox
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.feature.store.data.local.CosmeticInventoryStore
import com.rekixo.ludopaws.ui.dialogs.showSettingsDialog
import com.rekixo.ludopaws.feature.safety.FamilyAgeScreen
import com.rekixo.ludopaws.ui.dialogs.PlywoodAlertDialogBuilder
import org.json.JSONObject

class ReferralActivity : Activity() {
    private lateinit var showcase: FriendshipBundleView
    private lateinit var progress: TextView
    private lateinit var ownership: TextView
    private lateinit var action: Button
    private lateinit var refresh: Button
    private var verify: Button? = null
    private lateinit var stop: Button
    private lateinit var verification: TextView
    private var invitationDialog: android.app.Dialog? = null
    private var consentDialog: android.app.Dialog? = null
    private data class ConsentGrant(val accountId: String, val termsVersion: String)
    private var status: JSONObject? = null
    private var generation = 0
    private var busy = false
    private var resumed = false
    private val handler = Handler(Looper.getMainLooper())
    private var pollsRemaining = 20
    private val poll = Runnable { if (resumed && !busy) load() }
    private val repository by lazy { ReferralRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LudoProofTheme.configureWindow(this)
        val (root, host) = LudoProofTheme.arcadeRoot(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(12), dp(18), dp(16))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF123B38.toInt(), 0xFF102B31.toInt())).apply { cornerRadius = dp(24).toFloat() }
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(quietButton("‹") { finish() }.apply { contentDescription = "Back" }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(copy("Invite friends", 19f, true), LinearLayout.LayoutParams(0, -2, 1f))
        refresh = quietButton("↻") { load() }.apply { contentDescription = "Refresh invitation progress"; textSize = 23f }
        header.addView(refresh, LinearLayout.LayoutParams(dp(48), dp(48)))
        content.addView(header, LinearLayout.LayoutParams(-1, -2))
        content.addView(copy("Better with a friend.", 25f, true).apply { setPadding(0, dp(12), 0, dp(4)) })
        content.addView(copy("Invite a new player. Get this bundle\nafter their first qualifying match.", 13f))
        showcase = FriendshipBundleView(this)
        if (FamilyAgeScreen.hasChoice(this) && !FamilyAgeScreen.optionalGoogleAllowed(this)) showcase.visibility = android.view.View.GONE
        content.addView(showcase, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16); bottomMargin = dp(10) })
        ownership = copy("", 12f)
        ownership.visibility = android.view.View.GONE
        content.addView(ownership)
        progress = copy("Checking your bundle…", 13f)
        progress.accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        content.addView(progress, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        action = Button(this).apply {
            text = "CHECKING…"; isEnabled = false
            setTextColor(0xFF163E37.toInt()); typeface = Typeface.DEFAULT_BOLD
            minHeight = dp(54); isAllCaps = false; textSize = 16f
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = buttonSurface(primary = true)
            setOnClickListener { act() }
        }
        content.addView(action, LinearLayout.LayoutParams(-1, -2))
        content.addView(copy("Only you earn the reward. Sharing or installing alone doesn't count.", 11f).apply {
            setPadding(dp(8), dp(10), dp(8), dp(8))
        })
        verification = copy("Verify before playing to help your friend earn their bundle. This reward goes to them.", 12f)
        val links = LinearLayout(this)
        links.addView(quietButton("Got an invite?") { showIncomingInvitation() }, LinearLayout.LayoutParams(0, -2, 1f))
        links.addView(quietButton("Rules & privacy") { showInvitationDetails() }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(links, LinearLayout.LayoutParams(-1, -2))
        stop = quietButton("Stop verification") {
            PlywoodAlertDialogBuilder(this@ReferralActivity).setTitle("Stop verification?")
                .setMessage("Pending incoming attribution will be cancelled. A previously bound invitation cannot be reused. Earned rewards stay yours. No contact list is accessed.")
                .setNegativeButton("Keep it", null).setPositiveButton("Stop") { _, _ -> withdraw() }.show()
        }.apply {
            visibility = android.view.View.GONE
        }
        content.addView(stop, LinearLayout.LayoutParams(-1, -2))
        content.addView(quietButton("Share without rewards  ↗") {
            openExternal(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Play Ludo Paws with me!\nhttps://play.google.com/store/apps/details?id=com.rekixo.ludopaws")
            }, "Share Ludo Paws"))
        }, LinearLayout.LayoutParams(-1, -2))
        host.addView(ScrollView(this).apply {
            isFillViewport = true; isVerticalScrollBarEnabled = false
            setPadding(dp(6), dp(6), dp(6), dp(6)); addView(content)
        })
        setContentView(root)
    }

    private fun showIncomingInvitation() {
        if (invitationDialog?.isShowing == true) return
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        (verification.parent as? android.view.ViewGroup)?.removeView(verification)
        verification.setTextColor(0xFF583717.toInt())
        body.addView(verification)
        val button = secondaryButton("Verify my invitation") { verifyInvitation() }.apply {
            isEnabled = status?.let { it.optBoolean("enabled") && it.optBoolean("googleLinked") } == true && !busy &&
                !(FamilyAgeScreen.hasChoice(this@ReferralActivity) && !FamilyAgeScreen.optionalGoogleAllowed(this@ReferralActivity))
        }
        verify = button
        body.addView(button, LinearLayout.LayoutParams(-1, -2))
        invitationDialog = PlywoodAlertDialogBuilder(this).setTitle("Were you invited?")
            .setView(body).setPositiveButton("Close", null)
            .setOnDismissListener { verify = null; invitationDialog = null }.show()
    }

    private fun requireConsent(snapshot: JSONObject, grant: ConsentGrant?, onAccepted: (ConsentGrant) -> Unit): Boolean {
        if (snapshot.optBoolean("consented") && snapshot.optBoolean("localConsent")) return true
        val expected = ConsentGrant(snapshot.optString("localAccountId"), snapshot.optString("termsVersion"))
        if (expected.accountId.isBlank() || expected.termsVersion.isBlank()) { load(); return false }
        if (grant == expected) return true
        if (consentDialog?.isShowing == true) return false
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        body.addView(copy("Optional Google Play verification uses your linked game account, Google-identity anti-replay hash, click/install times and app/device integrity signals. Google verifies temporary tokens; we do not store those tokens. Our backend stores consent and eligible match/reward records.\n\nNo contacts or advertising ID are requested. Decline and keep playing, or stop verification later. Minimal security hashes remain until 14 days after the campaign ends. Only the inviter receives the reward.", 13f).apply {
            setTextColor(0xFF583717.toInt()); gravity = Gravity.START
        })
        body.addView(secondaryButton("Read rules & privacy") { showInvitationDetails() })
        val checkbox = CheckBox(this).apply {
            text = "I accept the invitation terms and privacy disclosure and choose optional Google Play verification."
            textSize = 13f; setTextColor(0xFF583717.toInt()); minHeight = dp(48)
        }
        body.addView(checkbox)
        val confirm = secondaryButton("Agree & continue") {
            val current = (application as com.rekixo.ludopaws.LudoProofApplication).accountRepository.currentSession()
            if (status?.optString("localAccountId") != expected.accountId || status?.optString("termsVersion") != expected.termsVersion || current?.accountId != expected.accountId) {
                consentDialog?.dismiss(); load(); return@secondaryButton
            }
            consentDialog?.dismiss()
            onAccepted(expected)
        }.apply { isEnabled = false }
        checkbox.setOnCheckedChangeListener { _, checked -> confirm.isEnabled = checked }
        body.addView(confirm, LinearLayout.LayoutParams(-1, -2))
        consentDialog = PlywoodAlertDialogBuilder(this).setTitle("Your choice")
            .setView(body).setNegativeButton("Not now", null)
            .setOnDismissListener { consentDialog = null }.show()
        return false
    }

    private fun showInvitationDetails() {
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(12))
        }
        status?.optLong("endsAt")?.takeIf { it > 0L }?.let { end ->
            details.addView(copy("Campaign ends " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(end)), 13f, true))
        }
        details.addView(copy("1  Share your personal invitation\n2  A new friend installs and links Google\n3  They verify in Home → Share before playing\n4  They finish a real Online or Friends match\n5  You receive the bundle + 100 Gems", 14f))
        details.addView(copy("Only the inviter gets rewards, once per campaign. Sharing or downloading alone does not earn anything. Your friend does not have to win. No purchase, ad view, rating or review is required. Bots, forfeits, offline matches and self-invites do not count. A qualifying match takes at least 3 minutes and includes at least one legal move by the invited player.\n\nInstall within 7 days of the download-button click; finish within 14 days of installation and before the campaign ends. The campaign lasts 90 days. Already-owned items stay yours; there is no duplicate item or gem compensation. The extra 100 Gems are awarded once, including when you already own some or all items. Verification may take a few minutes.\n\nOptional verification uses your linked game account, existing Google-identity anti-replay hash, Play click/install timestamps and app/device integrity signals. Google verifies temporary integrity tokens; referral code does not store them. Our backend stores consent and eligible match/reward records. No contacts or advertising ID are requested. You can decline and keep playing, or use Stop verification later. Account deletion removes referral links; minimal abuse-prevention hashes are retained until 14 days after the campaign ends. Integrity cannot guarantee a unique human or prevent every form of farming.", 13f))
        details.addView(secondaryButton("Privacy policy  ↗") {
            openExternal(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://ludopaws.rekixo.com/privacy-policy.html")))
        })
        details.addView(secondaryButton("Full invitation terms & support  ↗") {
            openExternal(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://ludopaws.rekixo.com/referral-terms.html")))
        })
        for (index in 0 until details.childCount) {
            (details.getChildAt(index) as? TextView)?.takeUnless { it is Button }?.apply {
                setTextColor(0xFF583717.toInt())
                gravity = Gravity.START
                setTextIsSelectable(true)
            }
        }
        PlywoodAlertDialogBuilder(this).setTitle("Invitation guide & privacy")
            .setView(ScrollView(this).apply { addView(details) })
            .setPositiveButton("Done", null).show()
    }

    private fun buttonSurface(primary: Boolean = false): android.graphics.drawable.Drawable {
        val enabled = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (primary) intArrayOf(0xFFD8FFE9.toInt(), 0xFF91DDBA.toInt())
            else intArrayOf(0xFF315A56.toInt(), 0xFF214340.toInt()),
        ).apply {
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), if (primary) 0xFFE0FFE9.toInt() else 0xFF719D88.toInt())
        }
        val disabled = GradientDrawable().apply {
            setColor(if (primary) 0xFFA5B9AF.toInt() else 0xFF405552.toInt())
            cornerRadius = dp(16).toFloat()
        }
        val states = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), disabled)
            addState(intArrayOf(), enabled)
        }
        return android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x33FFFFFF), states, null,
        )
    }

    private fun styleSecondaryButton(button: Button) = button.apply {
        isAllCaps = false; textSize = 13f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFFE4F6EA.toInt()); minHeight = dp(48)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = buttonSurface()
    }

    private fun secondaryButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; styleSecondaryButton(this); setOnClickListener { onClick() }
    }

    private fun quietButton(label: String, onClick: () -> Unit) = secondaryButton(label, onClick).apply {
        background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22FFFFFF), null, null)
        setTextColor(0xFFBBD7CA.toInt())
        minWidth = 0; minimumWidth = 0
    }

    private fun openExternal(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, "No compatible app is available. Please try another browser or sharing app.", Toast.LENGTH_LONG).show()
        }
    }

    private fun copy(value: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; gravity = Gravity.CENTER
        setTextColor(if (bold) 0xFFFFE8B7.toInt() else 0xFFBBD7CA.toInt())
        if (bold) typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        setLineSpacing(dp(2).toFloat(), 1f)
        setPadding(0, dp(5), 0, dp(5))
    }

    override fun onResume() {
        super.onResume(); resumed = true; showcase.setActive(true)
        pollsRemaining = 20
        repository.refreshAttribution()
        load()
    }
    override fun onPause() {
        resumed = false; generation++; busy = false; showcase.setActive(false)
        handler.removeCallbacks(poll)
        super.onPause()
    }

    override fun onDestroy() {
        consentDialog?.dismiss()
        invitationDialog?.dismiss()
        super.onDestroy()
    }

    private fun load() {
        if (busy) return
        handler.removeCallbacks(poll)
        busy = true; action.isEnabled = false; refresh.isEnabled = false
        val requestGeneration = ++generation
        ReferralRepository.executor.execute {
            val result = runCatching { repository.status(createAccount = true) }
            runOnUiThread {
                if (!resumed || isFinishing || isDestroyed || generation != requestGeneration) return@runOnUiThread
                busy = false; refresh.isEnabled = true
                result.onSuccess { snapshot ->
                    val current = (application as com.rekixo.ludopaws.LudoProofApplication).accountRepository.currentSession()
                    if (snapshot.has("localAccountId") && snapshot.optString("localAccountId") != current?.accountId) {
                        status = null; progress.text = "Account changed. Tap Retry to continue."
                        action.text = "RETRY"; action.isEnabled = true
                        return@onSuccess
                    }
                    status = snapshot; render(snapshot)
                    if (snapshot.optBoolean("enabled")) repository.refreshAttribution()
                }.onFailure {
                    status = null
                    progress.text = "Couldn't load invitations. Tap Retry."
                    action.text = "RETRY"; action.isEnabled = true
                }
            }
        }
    }

    private fun render(snapshot: JSONObject) {
        val owned = CosmeticInventoryStore(this).ownedCosmeticIds()
        ownership.text = listOf("dice_pearl_paw" to "Pearl Paw", "board_blossom_garden" to "Blossom Garden", "character_rabbit" to "Rabbit")
            .filter { it.first in owned }.joinToString(" · ", prefix = "Already yours: ") { it.second }
        if (owned.none { it in setOf("dice_pearl_paw", "board_blossom_garden", "character_rabbit") }) ownership.text = ""
        ownership.visibility = if (ownership.text.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
        val enabled = snapshot.optBoolean("enabled")
        val linked = snapshot.optBoolean("googleLinked")
        val state = snapshot.optString("state")
        val privacyBlocked = FamilyAgeScreen.hasChoice(this) && !FamilyAgeScreen.optionalGoogleAllowed(this)
        showcase.visibility = if (privacyBlocked) android.view.View.GONE else android.view.View.VISIBLE
        progress.text = when {
            state == "REWARDED" -> if (snapshot.optBoolean("walletSynced")) "Bundle received!\nYour items and 100 Gems are in your account." else "Reward granted on the server. Refresh to sync your items and Gems."
            !enabled -> "Rewards are currently unavailable."
            !linked -> "Connect Google to create your invitation."
            state == "VERIFYING" -> "Match verified · delivering your reward…"
            state == "FRIEND_JOINED" -> "Friend joined! Next: a completed real match."
            else -> "Ready for your first invitation."
        }
        action.text = when { state == "REWARDED" -> "Bundle received ✓"; !enabled -> "Currently unavailable"; !linked -> "Connect Google & invite"; state == "VERIFYING" -> "Check my reward"; else -> "Invite a friend  ↗" }
        action.isEnabled = enabled && state != "REWARDED" && !privacyBlocked
        verify?.isEnabled = enabled && linked && !busy && !privacyBlocked
        stop.visibility = if (snapshot.optBoolean("localConsent") || snapshot.optBoolean("consented")) android.view.View.VISIBLE else android.view.View.GONE
        val attribution = snapshot.optJSONObject("attribution")
        verification.text = when {
            privacyBlocked -> "Optional invitation rewards are unavailable for this privacy setting. You can share the game without rewards; gameplay is unchanged."
            attribution?.optString("state") == "QUALIFIED" -> "Your qualifying match was received. Only your inviter receives the bundle."
            attribution?.optString("state") == "BOUND" -> "Invitation verified. Finish a qualifying Online or Friends match before " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(attribution.optLong("deadline"))) + ". Your inviter gets the reward."
            snapshot.optString("verificationState") == "VERIFYING" -> "Checking your Google Play installation… You can keep playing; no local reward will be granted."
            snapshot.optString("verificationState") == "NO_INVITATION" -> "No valid invitation was found for this installation. An existing install cannot be converted into a new referral."
            snapshot.optString("verificationState") == "INSTALL_ALREADY_USED" -> "This installation was already used or its pending attribution was cancelled. It cannot be bound again to a different account."
            snapshot.optString("verificationState") == "NOT_ELIGIBLE" -> "This installation is not eligible for this invitation. See the rules or contact AI@rekixo.com."
            snapshot.optString("verificationState") == "PLAY_NOT_VERIFIED" -> "Google Play could not verify this installation. Use the genuine licensed Play release on a supported device, then retry."
            snapshot.optString("verificationState") == "REVIEW_TERMS" -> "Review and accept the updated invitation terms before verifying."
            snapshot.optString("verificationState") in setOf("STORE_UNAVAILABLE", "RETRY_LATER", "UNAVAILABLE") -> "Verification is temporarily unavailable. Your pending invitation is saved; use Verify to retry."
            snapshot.optString("verificationState") == "STOPPED" -> "Optional verification stopped. Existing earned rewards remain unchanged."
            else -> "Were you invited? Accept the disclosure and verify before playing. Only your inviter receives the reward."
        }
        handler.removeCallbacks(poll)
        if (resumed && pollsRemaining > 0 && (state in setOf("VERIFYING", "FRIEND_JOINED") || snapshot.optString("verificationState") == "VERIFYING")) {
            pollsRemaining--; handler.postDelayed(poll, 15_000L)
        }
    }

    private fun act(grant: ConsentGrant? = null) {
        val snapshot = status ?: return load()
        if (!snapshot.optBoolean("googleLinked")) {
            showSettingsDialog(this) { repository.refreshAttribution(); load() }
            return
        }
        if (snapshot.optString("state") == "VERIFYING") { load(); return }
        if (!requireConsent(snapshot, grant) { act(it) }) return
        if (!FamilyAgeScreen.optionalGoogleAllowed(this)) { FamilyAgeScreen.withOptionalGoogle(this) { act(grant) }; return }
        if (busy) return
        busy = true; action.isEnabled = false
        val requestGeneration = ++generation
        ReferralRepository.executor.execute {
            val result = runCatching {
                val accountId = snapshot.getString("localAccountId")
                if (!snapshot.optBoolean("consented") || !snapshot.optBoolean("localConsent")) repository.enroll(accountId, snapshot.getString("termsVersion"))
                repository.invite(accountId)
            }
            runOnUiThread {
                if (!resumed || isFinishing || isDestroyed || generation != requestGeneration) return@runOnUiThread
                busy = false; render(snapshot)
                result.onSuccess { url ->
                    openExternal(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Play Ludo Paws with me")
                        putExtra(Intent.EXTRA_TEXT, "Come play Ludo Paws with me! Install through this invitation and finish a real Online or Friends match. I can earn a friendship bundle + 100 Gems when you qualify; invited players do not receive this reward.\n\n$url")
                    }, "Invite a friend"))
                }.onFailure { progress.text = it.message ?: "Could not create an invitation. Please retry." }
            }
        }
    }

    private fun verifyInvitation(grant: ConsentGrant? = null) {
        val snapshot = status ?: return load()
        if (!snapshot.optBoolean("enabled") || !snapshot.optBoolean("googleLinked")) return
        if (!requireConsent(snapshot, grant) { verifyInvitation(it) }) return
        FamilyAgeScreen.withOptionalGoogle(this) {
            if (busy) return@withOptionalGoogle
            busy = true; verify?.isEnabled = false; action.isEnabled = false
            val requestGeneration = ++generation
            ReferralRepository.executor.execute {
                val result = runCatching { repository.enroll(snapshot.getString("localAccountId"), snapshot.getString("termsVersion")) }
                runOnUiThread {
                    if (!resumed || generation != requestGeneration || isFinishing || isDestroyed) return@runOnUiThread
                    busy = false
                    result.onSuccess {
                        verification.text = "Verifying your invitation…"
                        repository.refreshAttribution(force = true) { if (resumed && !isFinishing && !isDestroyed) load() }
                        load()
                    }.onFailure { render(snapshot); verification.text = it.message ?: "Could not verify. Retry later." }
                }
            }
        }
    }

    private fun withdraw() {
        val accountId = status?.optString("localAccountId") ?: return
        if (busy) return
        busy = true; action.isEnabled = false; verify?.isEnabled = false
        val requestGeneration = ++generation
        ReferralRepository.executor.execute {
            val result = runCatching { repository.withdraw(accountId) }
            runOnUiThread {
                if (!resumed || generation != requestGeneration || isFinishing || isDestroyed) return@runOnUiThread
                busy = false
                result.onSuccess { load() }.onFailure {
                    verification.text = "Local verification stopped. Server cleanup could not be confirmed; retry Stop or contact AI@rekixo.com."
                    action.isEnabled = true; verify?.isEnabled = true
                }
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
