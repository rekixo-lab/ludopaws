package com.rekixo.ludopaws.feature.safety

import android.app.Dialog
import android.app.DialogFragment
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.rekixo.ludopaws.LudoProofTheme
import com.rekixo.ludopaws.ui.dialogs.PRIVACY_POLICY_TEXT
import com.rekixo.ludopaws.ui.dialogs.baseDialog

/** One restorable popup. Privacy text replaces its content, including offline. */
class FamilyPrivacyDialog : DialogFragment() {
    private var selectedAge = 0 // Unset is distinct from age 1.
    private var showingPolicy = false
    private var submitting = false
    private lateinit var panel: LinearLayout
    private fun requireHost() = requireNotNull(activity)
    private fun dp(value: Int) = LudoProofTheme.dp(requireHost(), value)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedAge = savedInstanceState?.getInt("age_draft", 0)?.takeIf { it in 1..100 } ?: 0
        showingPolicy = savedInstanceState?.getBoolean("showing_policy", false) ?: false
        isCancelable = arguments?.getBoolean("required", false) != true
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val host = requireHost()
        panel = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF08378F.toInt(), 0xFF031B50.toInt())).apply {
                cornerRadius = dp(20).toFloat()
                setStroke(dp(2), LudoProofTheme.GOLD)
            }
        }
        val scroll = object : ScrollView(host) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val limit = (resources.displayMetrics.heightPixels * .72f).toInt()
                val offered = if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED)
                    limit else minOf(limit, View.MeasureSpec.getSize(heightMeasureSpec))
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(offered, View.MeasureSpec.AT_MOST))
            }
        }.apply { isFillViewport = false; addView(panel) }
        render()
        return baseDialog(host).apply { setContentView(scroll); setCanceledOnTouchOutside(false) }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(minOf((resources.displayMetrics.widthPixels * .90f).toInt(), dp(440)), -2)
    }

    private fun text(value: String, size: Float = 15f) = TextView(requireHost()).apply {
        text = value
        textSize = size
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setLineSpacing(0f, 1.12f)
    }

    private fun action(value: String) = Button(requireHost()).apply {
        text = value
        isAllCaps = false
        minHeight = dp(48)
        setTextColor(Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(0xFF1758A4.toInt())
    }

    private fun row(view: View, top: Int = 12) {
        panel.addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }

    private fun render() {
        panel.removeAllViews()
        val content = panel
        content.post { (content.parent as? ScrollView)?.scrollTo(0, 0) }
        if (showingPolicy) {
            row(text("Privacy Policy", 22f).apply { setTypeface(typeface, Typeface.BOLD) }, 0)
            row(action("Back to age selection").apply {
                setOnClickListener { showingPolicy = false; render() }
            })
            row(text(PRIVACY_POLICY_TEXT, 13.5f).apply { gravity = Gravity.START })
            row(action("Open online Privacy Policy").apply {
                setOnClickListener {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(POLICY_URL))) }
                    catch (_: ActivityNotFoundException) {
                        Toast.makeText(requireHost(), "No browser available. The policy is shown here offline.", Toast.LENGTH_LONG).show()
                    } catch (_: SecurityException) {
                        Toast.makeText(requireHost(), "The online page couldn't open. You can read the policy here.", Toast.LENGTH_LONG).show()
                    }
                }
            })
            row(action("Back to age selection").apply {
                setOnClickListener { showingPolicy = false; render() }
            })
            return
        }

        row(text("How old are you?", 24f).apply { setTypeface(typeface, Typeface.BOLD) }, 0)
        val number = text(if (selectedAge == 0) "—" else selectedAge.toString(), 32f).apply {
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            minHeight = dp(64)
            minWidth = dp(112)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            background = GradientDrawable().apply {
                setColor(0xFF041B48.toInt()); cornerRadius = dp(12).toFloat()
                setStroke(dp(1), LudoProofTheme.GOLD)
            }
        }
        panel.addView(number, LinearLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(18)
        })
        row(text("Slide to select your age · 1–100", 13f))
        val next = action("Continue")
        val slider = SeekBar(requireHost()).apply {
            max = 100
            progress = selectedAge
            minHeight = dp(48)
            progressTintList = ColorStateList.valueOf(0xFF68B5FF.toInt())
            thumbTintList = ColorStateList.valueOf(LudoProofTheme.GOLD)
        }
        fun updateSelection() {
            number.text = if (selectedAge == 0) "—" else selectedAge.toString()
            number.contentDescription = if (selectedAge == 0) "Age not selected" else "Age $selectedAge years"
            slider.contentDescription = number.contentDescription
            next.isEnabled = selectedAge in 1..100 && !submitting
            next.alpha = if (next.isEnabled) 1f else .5f
        }
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                selectedAge = progress.coerceIn(1, 100)
                if (progress == 0) seekBar.progress = 1
                updateSelection()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // A deliberate tap on the initial left endpoint selects age 1,
                // even when the platform emits no changed-progress callback.
                selectedAge = seekBar.progress.coerceIn(1, 100)
                seekBar.progress = selectedAge
                updateSelection()
            }
        })
        row(slider)
        row(next)
        next.setOnClickListener {
            if (submitting || selectedAge !in 1..100) return@setOnClickListener
            submitting = true
            updateSelection()
            val host = requireHost()
            if (!FamilyAgeScreen.saveChoice(host, selectedAge)) {
                submitting = false
                updateSelection()
                Toast.makeText(host, "Couldn't save your choice. Please try again.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            FamilyAgeScreen.completed(host) { dismiss() }
        }
        val copy = "By continuing, you confirm that you have read and agree to our Privacy Policy."
        val start = copy.indexOf("Privacy Policy")
        val footer = SpannableString(copy).apply {
            setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) { showingPolicy = true; render() }
            }, start, start + "Privacy Policy".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        row(text("", 12f).apply {
            text = footer
            movementMethod = LinkMovementMethod.getInstance()
            setLinkTextColor(LudoProofTheme.GOLD)
            minHeight = dp(48)
        })
        updateSelection()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("age_draft", selectedAge)
        outState.putBoolean("showing_policy", showingPolicy)
        super.onSaveInstanceState(outState)
    }

    override fun onDismiss(dialog: DialogInterface) {
        activity?.let(FamilyAgeScreen::detach)
        super.onDismiss(dialog)
    }

    override fun onDetach() {
        activity?.let(FamilyAgeScreen::detach)
        super.onDetach()
    }

    companion object { private const val POLICY_URL = "https://ludopaws.rekixo.com/privacy-policy.html" }
}
