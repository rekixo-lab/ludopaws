package com.rekixo.ludopaws.feature.profile.presentation.components

import com.rekixo.ludopaws.ui.dialogs.PlywoodAlertDialogBuilder
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.rekixo.ludopaws.core.ui.art.plywoodBackground
import com.rekixo.ludopaws.core.ui.art.recessedPlywoodBackground
import com.rekixo.ludopaws.core.ui.art.transparentDarkBlueStrokeBackground
import com.rekixo.ludopaws.core.ui.components.AppFocusRing
import com.rekixo.ludopaws.feature.profile.presentation.ProfileActivity
import com.rekixo.ludopaws.ui.dialogs.showPrivacyPolicyDialog

/** Plywood dropdown for account actions and legal pages on the Profile screen. */
internal fun ProfileActivity.showProfileMenu(anchor: View) {
    val guestCodeAvailable =
        !accountGoogleLinked &&
            accountRepository.currentSession()?.guestRecoveryEligible == true

    lateinit var popup: PopupWindow
    val panel =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(9), dp(8), dp(9), dp(10))
            background = plywoodBackground(this@showProfileMenu, cornerRadiusDp = 18f)
            elevation = dp(12).toFloat()
            clipChildren = false
            clipToPadding = false
        }

    panel.addView(
        TextView(this).apply {
            text = "PROFILE MENU"
            textSize = 19f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(0xFFFFD34E.toInt())
            gravity = Gravity.CENTER
            setShadowLayer(2.5f, 0f, dp(1).toFloat(), 0xAA000000.toInt())
            setPadding(dp(8), dp(7), dp(8), dp(9))
            contentDescription = "Profile menu"
        },
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ),
    )

    panel.addView(
        View(this).apply {
            background =
                GradientDrawable().apply {
                    setColor(0x88532E17.toInt())
                }
        },
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(1),
        ).apply {
            leftMargin = dp(7)
            rightMargin = dp(7)
            bottomMargin = dp(5)
        },
    )

    val options =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
    var sectionCount = 0
    var itemCount = 0

    fun addSection(title: String) {
        sectionCount += 1
        options.addView(
            TextView(this).apply {
                text = title
                textSize = 11.5f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(0xFF513117.toInt())
                letterSpacing = 0.08f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(5), dp(8), dp(5), dp(3))
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    fun addItem(
        title: String,
        description: String,
        action: () -> Unit,
    ) {
        itemCount += 1
        val item =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                setPadding(dp(11), dp(8), dp(8), dp(8))
                background = transparentDarkBlueStrokeBackground(this@showProfileMenu, cornerRadiusDp = 12f)
                isClickable = true
                isFocusable = true
                contentDescription = "$title. $description"
                addView(
                    LinearLayout(this@showProfileMenu).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(
                            TextView(this@showProfileMenu).apply {
                                text = title
                                textSize = 14f
                                setTypeface(Typeface.DEFAULT_BOLD)
                                setTextColor(Color.WHITE)
                            },
                        )
                        addView(
                            TextView(this@showProfileMenu).apply {
                                text = description
                                textSize = 10.5f
                                setTextColor(0xFFFFE8C3.toInt())
                                setPadding(0, dp(2), 0, 0)
                            },
                        )
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(
                    TextView(this@showProfileMenu).apply {
                        text = "›"
                        textSize = 27f
                        setTextColor(0xFFFFD34E.toInt())
                        gravity = Gravity.CENTER
                    },
                    LinearLayout.LayoutParams(dp(24), dp(34)),
                )
            }
        AppFocusRing.apply(item, cornerRadiusDp = 12f, strokeWidthDp = 1.5f)
        item.setOnClickListener {
            popup.dismiss()
            action()
        }
        options.addView(
            item,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(4)
            },
        )
    }

    if (guestCodeAvailable) {
        addSection("GUEST ACCOUNT")
        addItem(
            "Guest deletion code",
            "Save a delete-only code before uninstalling",
        ) { showGuestDeletionCode() }
        addItem(
            "Replace guest deletion code",
            "Invalidate the previous code and create a new one",
        ) {
            PlywoodAlertDialogBuilder(this@showProfileMenu)
                .setTitle("Replace saved guest deletion code?")
                .setMessage(
                    "This invalidates all previously saved guest deletion codes for this account. Save the replacement privately before uninstalling.",
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Replace code") { _, _ ->
                    showGuestDeletionCode(rotateExisting = true)
                }
                .show()
        }
    }

    addSection("ACCOUNT & DATA")
    addItem("Age and privacy", "Manage the age group stored on this device") {
        com.rekixo.ludopaws.feature.safety.FamilyAgeScreen.show(this@showProfileMenu)
    }
    addItem(
        "Save / Restore Account",
        if (accountGoogleLinked) "Manage or restore your Google-linked account" else "Link this Guest account or restore a saved account",
    ) { openAccountActions() }
    addItem(
        "Delete account and data",
        "Permanently delete this Ludo Paws account",
    ) { confirmAccountDeletion() }
    addItem(
        "Online account deletion",
        "Use the official page if the app is unavailable",
    ) {
        openProfileWebPage("https://ludopaws.rekixo.com/account-deletion.html")
    }

    addSection("LEGAL")
    addItem(
        "Privacy Policy",
        "Data use, sharing and retention",
    ) { showPrivacyPolicyDialog(this@showProfileMenu) }
    addItem(
        "Terms & Conditions",
        "Rules for using Ludo Paws",
    ) { showTermsAndConditionsDialog() }

    val scroll =
        ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = ScrollView.OVER_SCROLL_IF_CONTENT_SCROLLS
            isVerticalScrollBarEnabled = true
            addView(
                options,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    val preferredScrollHeight = dp(itemCount * 61 + sectionCount * 25 + 8)
    val maxScrollHeight =
        minOf(
            dp(450),
            (resources.displayMetrics.heightPixels - dp(190)).coerceAtLeast(dp(220)),
        )
    val scrollHeight = minOf(preferredScrollHeight, maxScrollHeight).coerceAtLeast(dp(160))
    panel.addView(
        scroll,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            scrollHeight,
        ),
    )

    val menuWidth =
        minOf(
            dp(330),
            (resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(250)),
        )
    popup =
        PopupWindow(
            panel,
            menuWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            isOutsideTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = dp(12).toFloat()
        }
    popup.showAsDropDown(anchor, dp(1), dp(7), Gravity.END)
}

/** Opens the publisher-hosted web resource; public-site deployment is a separate release step. */
private fun ProfileActivity.openProfileWebPage(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "No browser is available to open this page.", Toast.LENGTH_LONG).show()
    }
}

/** In-app terms view, using the same plywood treatment as the Profile menu. */
private fun ProfileActivity.showTermsAndConditionsDialog() {
    val dialog = Dialog(this)
    val panel =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = plywoodBackground(this@showTermsAndConditionsDialog, cornerRadiusDp = 20f)
            clipChildren = false
            clipToPadding = false
        }
    panel.addView(
        TextView(this).apply {
            text = "TERMS & CONDITIONS"
            textSize = 20f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(0xFFFFD34E.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(12))
        },
    )

    val body =
        TextView(this).apply {
            text = TERMS_AND_CONDITIONS_TEXT
            textSize = 13.5f
            setTextColor(Color.WHITE)
            setLineSpacing(0f, 1.16f)
            setPadding(dp(5), dp(3), dp(5), dp(12))
            setTextIsSelectable(true)
        }
    val scroll =
        ScrollView(this).apply {
            overScrollMode = ScrollView.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(body)
        }
    panel.addView(
        scroll,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            minOf(
                dp(430),
                (resources.displayMetrics.heightPixels - dp(220)).coerceAtLeast(dp(200)),
            ),
        ),
    )

    val close =
        TextView(this).apply {
            text = "CLOSE"
            textSize = 14f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = recessedPlywoodBackground(this@showTermsAndConditionsDialog, outerCornerRadiusDp = 12f)
            isClickable = true
            isFocusable = true
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { dialog.dismiss() }
        }
    AppFocusRing.apply(close, cornerRadiusDp = 12f)
    panel.addView(
        close,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(44),
        ).apply {
            topMargin = dp(8)
        },
    )

    dialog.setContentView(panel)
    dialog.show()
    dialog.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setLayout(
            minOf(
                dp(430),
                (resources.displayMetrics.widthPixels - dp(32)).coerceAtLeast(dp(260)),
            ),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        setGravity(Gravity.CENTER)
    }
}

private const val TERMS_AND_CONDITIONS_TEXT =
    """Last updated: October 15, 2026

1. About Ludo Paws
Ludo Paws is a game published under the Rekixo brand. These terms apply when you use the Ludo Paws app and its connected game services.

2. Fair and acceptable use
Use the game lawfully and respectfully. Do not cheat, manipulate match outcomes, abuse other players, interfere with the service, reverse-engineer service protections, or use bots or automated methods to exploit gameplay or rewards.

3. Accounts and deletion
Guest accounts are generally tied to the installation unless linked to Google. Keep credentials and guest deletion codes private. A guest deletion code can delete an eligible unlinked guest account only; it cannot sign in or restore progress. Account deletion is permanent. Use Profile → menu → Delete account and data, or the official online deletion page.

4. Purchases and virtual items
Purchases offered through Google Play are subject to the purchase details shown at checkout and Google Play's applicable terms. Gems, cosmetics and other digital items are for use in Ludo Paws, are not cash, and have no cash value or transfer right except where applicable law requires otherwise. Entitlements may be checked against purchase records to prevent fraud or duplicate grants. Refunds and payment disputes are handled under applicable Google Play processes and law.

5. Online gameplay and availability
Online play and social features depend on network connectivity and service availability. Features may be updated, suspended or discontinued where reasonably necessary. Match, security and integrity records may be processed or retained as described in the Privacy Policy.

6. Privacy and third-party services
The Privacy Policy explains the categories of information used by Ludo Paws and how deletion requests work. Google Play, Google Play Games, sign-in providers, and advertising services when enabled may also apply their own terms and privacy notices.

7. Updates and limitations
We may update the game and these terms as features or legal requirements change. To the extent permitted by law, the game is provided on an availability basis and may be affected by outages, device limitations or network issues. Nothing in these terms removes rights that cannot legally be excluded."""
