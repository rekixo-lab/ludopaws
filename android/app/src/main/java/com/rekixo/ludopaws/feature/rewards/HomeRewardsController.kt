package com.rekixo.ludopaws.feature.rewards

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.rekixo.ludopaws.LudoProofApplication
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.feature.growth.AutomaticPromptGate
import com.rekixo.ludopaws.feature.growth.EngagementStore
import com.rekixo.ludopaws.feature.growth.GrowthUi
import java.util.concurrent.Executors

/** A read-only Home indicator. The Rewards screen owns claims and wallet updates. */
internal class HomeRewardsController(
    private val activity: Activity,
    private val canShow: () -> Boolean,
    private val canHint: () -> Boolean,
) {
    private data class Availability(val owner: String, val resetsAt: Long, val count: Int)

    private val repository = (activity.application as LudoProofApplication).accountRepository
    private val worker = Executors.newSingleThreadExecutor()
    private val prefs = activity.getSharedPreferences(DISCOVERY_PREFS, Context.MODE_PRIVATE)
    private var anchor: View? = null
    private var badge: View? = null
    private var state: Availability? = null
    private var pendingOwner: String? = null
    private var generation = 0
    private var checkedOwner: String? = null
    private var checkedAt = 0L
    private var hint: PopupWindow? = null
    private var closed = false
    private val showHint = Runnable { offerHint() }
    private val refreshAtReset = Runnable { refresh(force = true) }

    fun bind(button: View, dot: View) {
        anchor?.removeCallbacks(showHint)
        anchor?.removeCallbacks(refreshAtReset)
        anchor = button
        badge = dot
        render()
    }

    fun refresh(force: Boolean = false) {
        if (closed) return
        val owner = repository.currentSession()?.accountId
        if (!canShow() || owner == null) {
            pause()
            return
        }
        if (pendingOwner == owner) return
        val now = SystemClock.elapsedRealtime()
        if (!force && checkedOwner == owner && now - checkedAt < 30_000L) return
        checkedOwner = owner
        checkedAt = now
        pendingOwner = owner
        state = state?.takeIf { it.owner == owner }
        render()
        val expected = ++generation
        worker.execute {
            val status = runCatching {
                val result = repository.rewardAvailability(owner)
                val reset = result.getLong("resetsAt")
                check(result.getLong("day") >= 0 && reset > System.currentTimeMillis())
                val count = (if (result.getBoolean("dailyClaimed")) 0 else 1) +
                    (if (result.getBoolean("spinClaimed")) 0 else 1)
                Availability(owner, reset, count)
            }.getOrNull()
            activity.runOnUiThread {
                if (generation != expected || closed) return@runOnUiThread
                pendingOwner = null
                state = status?.takeIf { canShow() && repository.currentSession()?.accountId == owner }
                render()
            }
        }
    }

    fun openRewards() {
        pause()
        activity.startActivity(Intent(activity, RewardsActivity::class.java))
    }

    fun pause() {
        generation++
        pendingOwner = null
        checkedOwner = null
        state = null
        hint?.dismiss()
        render()
    }

    fun close() {
        pause()
        closed = true
        worker.shutdownNow()
    }

    private fun render() {
        val button = anchor ?: return
        button.removeCallbacks(showHint)
        button.removeCallbacks(refreshAtReset)
        val available = state?.takeIf { it.resetsAt > System.currentTimeMillis() }
        val count = available?.count ?: 0
        if (count == 0) hint?.dismiss()
        badge?.visibility = if (count > 0) View.VISIBLE else View.GONE
        button.contentDescription = if (count > 0) "Rewards. $count free rewards available."
        else "Rewards. Daily gifts, free spin, goals and collections."
        if (available != null) {
            button.postDelayed(refreshAtReset, (available.resetsAt - System.currentTimeMillis()).coerceAtLeast(1L))
        }
        if (count > 0 && !prefs.getBoolean(GIFTS_SEEN, false) && !prefs.getBoolean(HINT_SEEN, false)) {
            button.postDelayed(showHint, 1400L)
        }
    }

    private fun offerHint() {
        val button = anchor ?: return
        val current = state ?: return
        if (hint != null || !canShow() || !canHint() || !button.isAttachedToWindow || !activity.hasWindowFocus() ||
            current.resetsAt <= System.currentTimeMillis() || current.count == 0 ||
            repository.currentSession()?.accountId != current.owner ||
            prefs.getBoolean(GIFTS_SEEN, false) || prefs.getBoolean(HINT_SEEN, false) ||
            EngagementStore(activity).completedMatches() == 0) return
        if (!AutomaticPromptGate.reserve(activity, PROMPT)) return

        val popup = runCatching {
            val width = minOf(dp(258), button.resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(1)
            val panel = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                background = LudoProofTheme.rounded(0xFF213B3D.toInt(), 14f, 0xFFD6AE70.toInt(), 1f, activity)
                setPadding(dp(12), dp(10), dp(12), dp(8))
                addView(TextView(activity).apply {
                    text = if (current.count == 2) "Your daily gift and free spin are ready" else "You have a free reward ready"
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(0xFFFFECCA.toInt())
                })
                addView(LinearLayout(activity).apply {
                    gravity = Gravity.END
                    addView(GrowthUi.button(activity, "Later", primary = false) { hint?.dismiss() },
                        LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
                    addView(GrowthUi.button(activity, "View rewards") { openRewards() },
                        LinearLayout.LayoutParams(0, -2, 1.6f))
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            }
            panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            PopupWindow(panel, width, -2, false).apply {
                isOutsideTouchable = true
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                elevation = dp(5).toFloat()
                setOnDismissListener {
                    hint = null
                    AutomaticPromptGate.release(PROMPT)
                }
                showAsDropDown(button, 0, -button.height - panel.measuredHeight - dp(8), Gravity.END)
            }
        }.getOrNull()
        if (popup?.isShowing == true) {
            hint = popup
            prefs.edit().putBoolean(HINT_SEEN, true).apply()
            AutomaticPromptGate.presented(activity, PROMPT)
        } else {
            AutomaticPromptGate.release(PROMPT)
        }
    }

    private fun dp(value: Int) = LudoProofTheme.dp(activity, value)

    companion object {
        const val DISCOVERY_PREFS = "rewards_discovery"
        const val GIFTS_SEEN = "gifts_seen"
        private const val HINT_SEEN = "hint_seen"
        private const val PROMPT = "daily-rewards"
    }
}
