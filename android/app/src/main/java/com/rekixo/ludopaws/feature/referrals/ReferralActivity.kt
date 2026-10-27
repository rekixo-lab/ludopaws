package com.rekixo.ludopaws.feature.referrals

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
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
    private lateinit var terms: CheckBox
    private lateinit var verify: Button
    private lateinit var stop: Button
    private lateinit var verification: TextView
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
            setPadding(dp(18), dp(12), dp(18), dp(28))
        }
        content.addView(Button(this).apply { text = "‹  BACK"; setOnClickListener { finish() } }, LinearLayout.LayoutParams(-1, dp(48)))
        content.addView(copy("A little friendship.\nA big adventure.", 24f, true))
        showcase = FriendshipBundleView(this)
        if (FamilyAgeScreen.hasChoice(this) && !FamilyAgeScreen.optionalGoogleAllowed(this)) showcase.visibility = android.view.View.GONE
        content.addView(showcase, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        ownership = copy("", 12f)
        content.addView(ownership)
        progress = copy("Checking your friendship bundle…", 16f, true)
        progress.accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        content.addView(progress)
        content.addView(copy("Optional verification uses your linked game account, existing Google-identity anti-replay hash, Play click/install timestamps and app/device integrity signals. Google verifies temporary integrity tokens; referral code does not store them. Our backend stores consent and eligible match/reward records. No contacts or advertising ID are requested. You can decline and keep playing, or use Stop verification later. Minimal security hashes remain until 14 days after the campaign ends.", 13f))
        terms = CheckBox(this).apply {
            text = "I have read the invitation terms and privacy disclosure below. I choose optional invitation verification using Google Play installation and integrity checks. Only the inviter earns the reward."
            textSize = 13f; setTextColor(Color.WHITE)
        }
        content.addView(terms, LinearLayout.LayoutParams(-1, -2))
        action = Button(this).apply {
            text = "CHECKING…"; isEnabled = false
            setTextColor(0xFF163E37.toInt()); typeface = Typeface.DEFAULT_BOLD
            minHeight = dp(52)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFB8EFD5.toInt()); cornerRadius = dp(24).toFloat()
            }
            setOnClickListener { act() }
        }
        content.addView(action, LinearLayout.LayoutParams(-1, -2))
        refresh = Button(this).apply { text = "Refresh progress"; setOnClickListener { load() } }
        content.addView(refresh, LinearLayout.LayoutParams(-1, -2))
        verification = copy("Were you invited? Verify your invitation before playing so your friend can qualify. You do not receive this reward.", 13f)
        content.addView(verification)
        verify = Button(this).apply {
            text = "VERIFY MY INVITATION"; isEnabled = false
            setOnClickListener { verifyInvitation() }
        }
        content.addView(verify, LinearLayout.LayoutParams(-1, -2))
        stop = Button(this).apply {
            text = "Stop optional invitation verification"
            visibility = android.view.View.GONE
            setOnClickListener {
                PlywoodAlertDialogBuilder(this@ReferralActivity).setTitle("Stop verification?")
                    .setMessage("Pending incoming attribution will be cancelled. A previously bound invitation cannot be reused. Earned rewards stay yours. No contact list is accessed.")
                    .setNegativeButton("Keep it", null).setPositiveButton("Stop") { _, _ -> withdraw() }.show()
            }
        }
        content.addView(stop, LinearLayout.LayoutParams(-1, -2))
        content.addView(Button(this).apply {
            text = "Share game without a reward"
            setOnClickListener {
                openExternal(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Play Ludo Paws with me!\nhttps://play.google.com/store/apps/details?id=com.rekixo.ludopaws")
                }, "Share Ludo Paws"))
            }
        }, LinearLayout.LayoutParams(-1, -2))
        content.addView(copy("1  Share your personal invitation\n2  A new friend installs and links Google\n3  They finish a real Online or Friends match\n4  You receive the bundle + 100 Gems", 15f))
        content.addView(copy("For an invited friend: after installing, open Home → Share, accept the disclosure and tap Verify my invitation before playing. Your friend receives the reward, not you. No purchase, ad view, rating or review is required. A qualifying match must include at least one legal move by the invited player.", 13f))
        content.addView(Button(this).apply {
            text = "Privacy policy"
            setOnClickListener {
                openExternal(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://ludopaws.rekixo.com/privacy-policy.html")))
            }
        }, LinearLayout.LayoutParams(-1, -2))
        content.addView(Button(this).apply {
            text = "Full invitation terms & support"
            setOnClickListener {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://ludopaws.rekixo.com/referral-terms.html"))) }
                    .onFailure { Toast.makeText(this@ReferralActivity, "Open ludopaws.rekixo.com/referral-terms.html or contact AI@rekixo.com.", Toast.LENGTH_LONG).show() }
            }
        }, LinearLayout.LayoutParams(-1, -2))
        content.addView(copy("Only the inviter gets rewards, once per campaign. Sharing or downloading alone does not earn anything. Your friend does not have to win. Bots, forfeits, offline matches and self-invites do not count. A qualifying match takes at least 3 minutes.\n\nInstall within 7 days of the download-button click; finish within 14 days of installation and before the campaign ends. The campaign lasts 90 days. Already-owned items stay yours; there is no duplicate item or gem compensation. The extra 100 Gems are awarded once, including when you already own some or all items. Verification may take a few minutes.\n\nSharing is optional. We use account links, installation timestamps and Google Play integrity checks to prevent duplicate or fraudulent claims. No contact list or advertising ID is requested. Account deletion removes referral links; minimal abuse-prevention hashes are retained until 14 days after the campaign ends. Integrity cannot guarantee a unique human or prevent every form of farming.", 12f))
        host.addView(ScrollView(this).apply { isFillViewport = true; addView(content) })
        setContentView(root)
    }

    private fun openExternal(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, "No compatible app is available. Please try another browser or sharing app.", Toast.LENGTH_LONG).show()
        }
    }

    private fun copy(value: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; gravity = Gravity.CENTER
        setTextColor(if (bold) 0xFFFFE1A0.toInt() else Color.WHITE)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(10), 0, dp(10))
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
                        status = null; progress.text = "Your account changed. Refresh progress to continue."
                        action.text = "RETRY"; action.isEnabled = true
                        return@onSuccess
                    }
                    status = snapshot; render(snapshot)
                    if (snapshot.optBoolean("enabled")) repository.refreshAttribution()
                }.onFailure {
                    status = null
                    progress.text = "Could not check progress. No reward is granted locally. Please retry or contact AI@rekixo.com."
                    action.text = "RETRY"; action.isEnabled = true
                }
            }
        }
    }

    private fun render(snapshot: JSONObject) {
        val owned = CosmeticInventoryStore(this).ownedCosmeticIds()
        ownership.text = listOf("dice_pearl_paw" to "Pearl Paw", "board_blossom_garden" to "Blossom Garden", "character_rabbit" to "Rabbit")
            .filter { it.first in owned }.joinToString(" · ") { "${it.second}: already owned" }
        val enabled = snapshot.optBoolean("enabled")
        val linked = snapshot.optBoolean("googleLinked")
        val state = snapshot.optString("state")
        val privacyBlocked = FamilyAgeScreen.hasChoice(this) && !FamilyAgeScreen.optionalGoogleAllowed(this)
        showcase.visibility = if (privacyBlocked) android.view.View.GONE else android.view.View.VISIBLE
        progress.text = when {
            state == "REWARDED" -> if (snapshot.optBoolean("walletSynced")) "Bundle received!\nYour items and 100 Gems are in your account." else "Reward granted on the server. Refresh to sync your items and Gems."
            !enabled -> "Friendship rewards are not available yet or the campaign has ended."
            !linked -> "Link your Google account in Settings first."
            state == "VERIFYING" -> "Match verified · delivering your reward…"
            state == "FRIEND_JOINED" -> "Friend joined! Next: a completed real match."
            else -> "Invite 1 new friend to earn this bundle."
        }
        if (enabled) {
            val end = snapshot.optLong("endsAt")
            if (end > 0L) progress.append("\nEnds " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(end)))
        }
        action.text = when { state == "REWARDED" -> "BUNDLE RECEIVED"; !enabled -> "CURRENTLY UNAVAILABLE"; !linked -> "LINK GOOGLE IN SETTINGS"; state == "VERIFYING" -> "REFRESH REWARD"; else -> "INVITE A FRIEND" }
        action.isEnabled = enabled && state != "REWARDED" && !privacyBlocked
        verify.isEnabled = enabled && linked && !busy && !privacyBlocked
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

    private fun act() {
        val snapshot = status ?: return load()
        if (!snapshot.optBoolean("googleLinked")) {
            showSettingsDialog(this) { repository.refreshAttribution(); load() }
            return
        }
        if (snapshot.optString("state") == "VERIFYING") { load(); return }
        if ((!snapshot.optBoolean("consented") || !snapshot.optBoolean("localConsent")) && !terms.isChecked) {
            Toast.makeText(this, "Read the disclosure and tick the optional invitation checkbox first.", Toast.LENGTH_LONG).show()
            return
        }
        if (!FamilyAgeScreen.optionalGoogleAllowed(this)) { FamilyAgeScreen.withOptionalGoogle(this) { act() }; return }
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

    private fun verifyInvitation() {
        val snapshot = status ?: return load()
        if (!snapshot.optBoolean("enabled") || !snapshot.optBoolean("googleLinked")) return
        if (!terms.isChecked && (!snapshot.optBoolean("consented") || !snapshot.optBoolean("localConsent"))) {
            Toast.makeText(this, "Read and accept the optional invitation disclosure first.", Toast.LENGTH_LONG).show()
            return
        }
        FamilyAgeScreen.withOptionalGoogle(this) {
            if (busy) return@withOptionalGoogle
            busy = true; verify.isEnabled = false; action.isEnabled = false
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
        busy = true; action.isEnabled = false; verify.isEnabled = false
        val requestGeneration = ++generation
        ReferralRepository.executor.execute {
            val result = runCatching { repository.withdraw(accountId) }
            runOnUiThread {
                if (!resumed || generation != requestGeneration || isFinishing || isDestroyed) return@runOnUiThread
                busy = false
                result.onSuccess { terms.isChecked = false; load() }.onFailure {
                    verification.text = "Local verification stopped. Server cleanup could not be confirmed; retry Stop or contact AI@rekixo.com."
                    action.isEnabled = true; verify.isEnabled = true
                }
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
