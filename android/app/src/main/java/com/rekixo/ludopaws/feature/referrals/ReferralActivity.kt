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
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.feature.store.data.local.CosmeticInventoryStore
import com.rekixo.ludopaws.ui.dialogs.showSettingsDialog
import org.json.JSONObject

class ReferralActivity : Activity() {
    private lateinit var showcase: FriendshipBundleView
    private lateinit var progress: TextView
    private lateinit var ownership: TextView
    private lateinit var action: Button
    private lateinit var refresh: Button
    private var status: JSONObject? = null
    private var generation = 0
    private var busy = false
    private var resumed = false
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
        content.addView(showcase, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        ownership = copy("", 12f)
        content.addView(ownership)
        progress = copy("Checking your friendship bundle…", 16f, true)
        progress.accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        content.addView(progress)
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
        content.addView(Button(this).apply {
            text = "Share game without a reward"
            setOnClickListener {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Play Ludo Paws with me!\nhttps://play.google.com/store/apps/details?id=com.rekixo.ludopaws")
                }, "Share Ludo Paws"))
            }
        }, LinearLayout.LayoutParams(-1, -2))
        content.addView(copy("1  Share your personal invitation\n2  A new friend installs and links Google\n3  They finish a real Online or Friends match\n4  You receive the bundle + 100 Gems", 15f))
        content.addView(Button(this).apply {
            text = "Privacy policy"
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://ludopaws.rekixo.com/privacy-policy.html")))
            }
        }, LinearLayout.LayoutParams(-1, -2))
        content.addView(copy("Only the inviter gets rewards, once per campaign. Sharing or downloading alone does not earn anything. Your friend does not have to win. Bots, forfeits, offline matches and self-invites do not count. A qualifying match takes at least 3 minutes.\n\nInstall within 7 days of the download-button click; finish within 14 days of installation and before the campaign ends. The campaign lasts 90 days. Already-owned items stay yours; there is no duplicate item or gem compensation. The extra 100 Gems are awarded once, including when you already own some or all items. Verification may take a few minutes.\n\nSharing is optional. We use account links, installation timestamps and Google Play integrity checks to prevent duplicate or fraudulent claims. No contact list or advertising ID is requested. Account deletion removes referral links; minimal abuse-prevention hashes are retained until 14 days after the campaign ends. Integrity cannot guarantee a unique human or prevent every form of farming.", 12f))
        host.addView(ScrollView(this).apply { isFillViewport = true; addView(content) })
        setContentView(root)
    }

    private fun copy(value: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; gravity = Gravity.CENTER
        setTextColor(if (bold) 0xFFFFE1A0.toInt() else Color.WHITE)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(10), 0, dp(10))
    }

    override fun onResume() {
        super.onResume(); resumed = true; showcase.setActive(true)
        repository.refreshAttribution()
        load()
    }
    override fun onPause() {
        resumed = false; generation++; busy = false; showcase.setActive(false)
        super.onPause()
    }

    private fun load() {
        if (busy) return
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
                    progress.text = "Could not check progress. Your rewards are safe; please retry."
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
        progress.text = when {
            state == "REWARDED" -> "Bundle received!\nYour items and 100 Gems are in your account."
            !enabled -> "Friendship rewards are not available yet or the campaign has ended."
            !linked -> "Link your Google account in Settings first."
            state == "VERIFYING" -> "Match verified · delivering your reward…"
            state == "FRIEND_JOINED" -> "Friend joined! Next: a completed real match."
            else -> "Invite 1 new friend to earn this bundle."
        }
        if (enabled) {
            val end = snapshot.optLong("endsAt")
            if (end > 0L) progress.append("\nEnds " + java.text.DateFormat.getDateInstance().format(java.util.Date(end)))
        }
        action.text = when { state == "REWARDED" -> "BUNDLE RECEIVED"; !enabled -> "CURRENTLY UNAVAILABLE"; !linked -> "LINK GOOGLE IN SETTINGS"; state == "VERIFYING" -> "REFRESH REWARD"; else -> "INVITE A FRIEND" }
        action.isEnabled = enabled && state != "REWARDED"
    }

    private fun act() {
        val snapshot = status ?: return load()
        if (!snapshot.optBoolean("googleLinked")) {
            showSettingsDialog(this) { repository.refreshAttribution(); load() }
            return
        }
        if (snapshot.optString("state") == "VERIFYING") { load(); return }
        if (busy) return
        busy = true; action.isEnabled = false
        val requestGeneration = ++generation
        ReferralRepository.executor.execute {
            val result = runCatching { repository.invite(snapshot.getString("localAccountId")) }
            runOnUiThread {
                if (!resumed || isFinishing || isDestroyed || generation != requestGeneration) return@runOnUiThread
                busy = false; render(snapshot)
                result.onSuccess { url ->
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Play Ludo Paws with me")
                        putExtra(Intent.EXTRA_TEXT, "Come play Ludo Paws with me! Install through this invitation and finish a real Online or Friends match. I can earn a friendship bundle + 100 Gems when you qualify; invited players do not receive this reward.\n\n$url")
                    }, "Invite a friend"))
                }.onFailure { progress.text = it.message ?: "Could not create an invitation. Please retry." }
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}
