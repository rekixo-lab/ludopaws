package com.rekixo.ludopaws.ui.dialogs

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.LinearLayout
import com.rekixo.ludopaws.DiceView
import com.rekixo.ludopaws.LudoBoardView
import com.rekixo.ludopaws.LudoPawsBoardView
import com.rekixo.ludopaws.LudoPawsBoardChromeView
import com.rekixo.ludopaws.LudoPawsReactiveBoardView
import com.rekixo.ludopaws.feature.profile.data.local.ProfileStore
import com.rekixo.ludopaws.feature.profile.domain.ProfileProgression
import com.rekixo.ludopaws.feature.settings.data.local.GameMusicController
import com.rekixo.ludopaws.feature.settings.data.local.GameSettingsStore
import com.rekixo.ludopaws.feature.settings.data.local.GameSoundFeedback
import com.rekixo.ludopaws.feature.store.data.local.CosmeticInventoryStore
import com.rekixo.ludopaws.feature.store.domain.StoreCosmeticCatalog
import com.rekixo.ludopaws.feature.store.domain.model.CosmeticCategory

internal fun showSettingsDialog(
    context: Context,
    onChanged: (() -> Unit)? = null,
) {
    val activity =
        context as? Activity
            ?: return
    if (
        activity.isFinishing ||
        activity.isDestroyed
    ) {
        return
    }

    val dialog = baseDialog(context)
    val panel = settingsCompactPanel(
        context = context,
        dialog = dialog,
    )
    val loadingLabel =
        TextView(context).apply {
            text = "Opening settings…"
            textSize = 12f
            setTextColor(0xFFFFD34E.toInt())
            gravity = Gravity.CENTER
            setPadding(
                settingsDp(context, 12),
                settingsDp(context, 12),
                settingsDp(context, 12),
                settingsDp(context, 12),
            )
        }
    panel.addView(
        loadingLabel,
        fullWidthParams(context, 12),
    )
    val overflowGutter = settingsDp(context, 6)
    val scroll =
        ScrollView(context).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipChildren = false
            clipToPadding = false
            setPadding(overflowGutter, overflowGutter, overflowGutter, overflowGutter)
            addView(
                panel,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    dialog.setContentView(
        scroll,
        ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ),
    )
    sizeDialog(
        dialog,
        if (context.resources.configuration.screenWidthDp < 380) .94f else .88f,
        overflowGutterDp = 6,
    )
    dialog.show()

    // Show the lightweight dialog shell before building rows and reading cosmetic
    // inventory. Previously all content creation happened before dialog.show(),
    // so the entire cost was visible as a delayed response to the settings tap.
    activity.window.decorView.postDelayed({
        if (activity.isFinishing || activity.isDestroyed || !dialog.isShowing) {
            return@postDelayed
        }
        panel.removeView(loadingLabel)
        if (com.rekixo.ludopaws.BuildConfig.DEBUG) {
            panel.addView(settingsDropdownField(context, "Developer tools").apply {
                text = "Developer tools"
                contentDescription = "Open developer previews and feature diagnostics"
                setOnClickListener {
                    dialog.dismiss()
                    activity.startActivity(android.content.Intent().setClassName(
                        context.packageName, "com.rekixo.ludopaws.DeveloperToolsActivity",
                    ))
                }
            }, fullWidthParams(context, 8))
        }
        val settingsStore =
            GameSettingsStore(context)
        val cosmetics =
            CosmeticInventoryStore(context)
        val profileLevel =
            ProfileProgression
                .levelProgress(
                    ProfileStore(context)
                        .snapshot()
                        .totalXp,
                )
                .level

        fun notifyChanged(
            refreshCosmetics: Boolean = false,
        ) {
            if (refreshCosmetics) {
                reloadVisibleCosmetics(
                    activity.window
                        .decorView,
                )
            }
            onChanged?.invoke()
        }

        var settings =
            settingsStore.snapshot()

        val musicControl =
            SettingsAudioIconView(
                context,
            ).apply {
                kind =
                    SettingsAudioIconKind.MUSIC
                isOn =
                    settings.musicEnabled
                contentDescription =
                    if (isOn) {
                        "Music on"
                    } else {
                        "Music muted"
                    }
                setOnClickListener {
                    val enabled =
                        !settingsStore
                            .snapshot()
                            .musicEnabled
                    settingsStore
                        .setMusicEnabled(
                            enabled,
                        )
                    GameMusicController
                        .onPreferenceChanged(
                            context,
                        )
                    isOn = enabled
                    contentDescription =
                        if (enabled) {
                            "Music on"
                        } else {
                            "Music muted"
                        }
                    GameSoundFeedback.click(
                        context,
                    )
                    settings =
                        settingsStore
                            .snapshot()
                    notifyChanged()
                }
            }
        panel.addView(
            settingsCompactRow(
                context = context,
                label = "Music",
                control = musicControl,
            ),
        )

        val soundControl =
            SettingsAudioIconView(
                context,
            ).apply {
                kind =
                    SettingsAudioIconKind.SOUND
                isOn =
                    settings.soundEnabled
                contentDescription =
                    if (isOn) {
                        "Sound on"
                    } else {
                        "Sound muted"
                    }
                setOnClickListener {
                    val enabled =
                        !settingsStore
                            .snapshot()
                            .soundEnabled
                    settingsStore
                        .setSoundEnabled(
                            enabled,
                        )
                    settingsStore
                        .setAnimalVoicesEnabled(
                            enabled,
                        )
                    isOn = enabled
                    contentDescription =
                        if (enabled) {
                            "Sound on"
                        } else {
                            "Sound muted"
                        }
                    if (enabled) {
                        GameSoundFeedback.click(
                            context,
                        )
                    }
                    settings =
                        settingsStore
                            .snapshot()
                    notifyChanged()
                }
            }
        panel.addView(
            settingsCompactRow(
                context = context,
                label = "Sound",
                control = soundControl,
            ),
        )

        val quickChatControl =
            SettingsBooleanControl(
                context = context,
                initialValue =
                    settings.quickChatEnabled,
            ) { enabled ->
                settingsStore
                    .setQuickChatEnabled(
                        enabled,
                    )
                settings =
                    settingsStore
                        .snapshot()
                GameSoundFeedback.click(
                    context,
                )
                notifyChanged()
            }
        panel.addView(
            settingsCompactRow(
                context = context,
                label = "Quick chat",
                control = quickChatControl,
            ),
        )

        val speedField =
            settingsDropdownField(
                context,
                settings.gameSpeed.label,
            )
        speedField.setOnClickListener {
            val latest =
                settingsStore
                    .snapshot()
            showSettingsChoiceDialog(
                context = context,
                title = "GAME SPEED",
                options =
                    listOf(
                        "FAST",
                        "NORMAL",
                        "SLOW",
                    ),
                selected =
                    latest.gameSpeed.label,
            ) { selected ->
                val speed =
                    com.rekixo.ludopaws
                        .feature.settings.data.local
                        .GameSpeed.entries
                        .first {
                            it.label ==
                                selected
                        }
                settingsStore
                    .setGameSpeed(
                        speed,
                    )
                speedField.text =
                    settingsDropdownLabel(
                        selected,
                    )
                settings =
                    settingsStore
                        .snapshot()
                GameSoundFeedback.click(
                    context,
                )
                notifyChanged()
            }
        }
        panel.addView(
            settingsCompactRow(
                context = context,
                label = "Game Speed",
                control = speedField,
            ),
        )

        val ownedCosmeticIds = cosmetics.ownedCosmeticIds()
        val ownedBoards =
            StoreCosmeticCatalog
                .forCategory(
                    CosmeticCategory.BOARD,
                )
                .filter { it.contentAvailable && it.id in ownedCosmeticIds }
        val selectedBoard =
            StoreCosmeticCatalog
                .find(
                    cosmetics.selectedId(
                        CosmeticCategory.BOARD,
                    ),
                )
        val boardField =
            settingsDropdownField(
                context,
                selectedBoard
                    ?.title
                    ?.uppercase()
                    ?: "CLASSIC",
            )
        boardField.setOnClickListener {
            val currentTitle =
                StoreCosmeticCatalog
                    .find(
                        cosmetics.selectedId(
                            CosmeticCategory.BOARD,
                        ),
                    )
                    ?.title
                    ?.uppercase()
                    ?: "CLASSIC"
            showSettingsChoiceDialog(
                context = context,
                title = "BOARDS",
                options =
                    ownedBoards.map {
                        it.title.uppercase()
                    },
                selected = currentTitle,
            ) { selected ->
                val cosmetic =
                    ownedBoards
                        .firstOrNull {
                            it.title
                                .uppercase() ==
                                selected
                        }
                        ?: return@showSettingsChoiceDialog
                if (cosmetics.selectedId(CosmeticCategory.BOARD) == cosmetic.id) return@showSettingsChoiceDialog
                boardField.isEnabled = false
                selectSettingsCosmetic(activity, cosmetic, onApplied = {
                    boardField.isEnabled = true
                    boardField.text = settingsDropdownLabel(
                        StoreCosmeticCatalog.find(cosmetics.selectedId(CosmeticCategory.BOARD))
                            ?.title?.uppercase() ?: "CLASSIC",
                    )
                    GameSoundFeedback.click(context)
                    notifyChanged(refreshCosmetics = true)
                }, onFailure = {
                    boardField.isEnabled = true
                })
            }
        }
        panel.addView(
            settingsCompactRow(
                context = context,
                label = "Boards",
                control = boardField,
            ),
        )

        val ownedDice =
            StoreCosmeticCatalog
                .forCategory(
                    CosmeticCategory.DICE,
                )
                .filter { it.contentAvailable && it.id in ownedCosmeticIds }
        val selectedDice =
            StoreCosmeticCatalog
                .find(
                    cosmetics.selectedId(
                        CosmeticCategory.DICE,
                    ),
                )
        val diceField =
            settingsDropdownField(
                context,
                selectedDice
                    ?.title
                    ?.uppercase()
                    ?: "CLASSIC",
            )
        diceField.setOnClickListener {
            val currentTitle =
                StoreCosmeticCatalog
                    .find(
                        cosmetics.selectedId(
                            CosmeticCategory.DICE,
                        ),
                    )
                    ?.title
                    ?.uppercase()
                    ?: "CLASSIC"
            showSettingsChoiceDialog(
                context = context,
                title = "DICE",
                options =
                    ownedDice.map {
                        it.title.uppercase()
                    },
                selected = currentTitle,
            ) { selected ->
                val cosmetic =
                    ownedDice
                        .firstOrNull {
                            it.title
                                .uppercase() ==
                                selected
                        }
                        ?: return@showSettingsChoiceDialog
                if (cosmetics.selectedId(CosmeticCategory.DICE) == cosmetic.id) return@showSettingsChoiceDialog
                diceField.isEnabled = false
                selectSettingsCosmetic(activity, cosmetic, onApplied = {
                    diceField.isEnabled = true
                    diceField.text = settingsDropdownLabel(
                        StoreCosmeticCatalog.find(cosmetics.selectedId(CosmeticCategory.DICE))
                            ?.title?.uppercase() ?: "CLASSIC",
                    )
                    GameSoundFeedback.click(context)
                    notifyChanged(refreshCosmetics = true)
                }, onFailure = {
                    diceField.isEnabled = true
                })
            }
        }
        panel.addView(
            settingsCompactRow(
                context = context,
                label = "Dice",
                control = diceField,
            ),
        )

        // One compact bottom row: both informational actions share the same
        // width, styling and touch target instead of stretching into full rows.
        val privacyAction = settingsDropdownField(context, "Privacy Policy").apply {
            text = "Privacy Policy"
            setTextColor(android.graphics.Color.WHITE)
            textSize = if (resources.configuration.screenWidthDp < 380) 11.5f else 13.5f
            setPadding(settingsDp(context, 4), 0, settingsDp(context, 4), 0)
            setSingleLine(true)
            contentDescription = "Open Privacy Policy"
            setOnClickListener {
                GameSoundFeedback.click(context)
                showPrivacyPolicyDialog(context)
            }
        }
        val guideAction = settingsDropdownField(context, "How to Play").apply {
            text = "How to Play"
            setTextColor(android.graphics.Color.WHITE)
            textSize = if (resources.configuration.screenWidthDp < 380) 11.5f else 13.5f
            setPadding(settingsDp(context, 4), 0, settingsDp(context, 4), 0)
            setSingleLine(true)
            contentDescription = "How to play Ludo Paws"
            setOnClickListener {
                GameSoundFeedback.click(context)
                com.rekixo.ludopaws.ui.home.showLudoPawsGuide(activity)
            }
        }
        panel.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, settingsDp(context, 7), 0, settingsDp(context, 1))
                addView(
                    privacyAction,
                    LinearLayout.LayoutParams(
                        0,
                        settingsDp(context, 42),
                        1f,
                    ).apply { marginEnd = settingsDp(context, 6) },
                )
                addView(
                    guideAction,
                    LinearLayout.LayoutParams(
                        0,
                        settingsDp(context, 42),
                        1f,
                    ),
                )
            },
        )

        panel.addView(TextView(context).apply {
            text = "Quick Chat artwork credits"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(0xFFE7CC9B.toInt())
            setPadding(0, settingsDp(context, 12), 0, settingsDp(context, 8))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val notice = context.assets.open("quickchat/NOTICE.txt").bufferedReader().use { it.readText() }
                PlywoodAlertDialogBuilder(context)
                    .setTitle("Quick Chat artwork")
                    .setMessage(notice)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }, fullWidthParams(context, 8))

    }, SETTINGS_CONTENT_BUILD_DELAY_MS)
}

private const val SETTINGS_CONTENT_BUILD_DELAY_MS = 24L

private fun reloadVisibleCosmetics(
    view: View,
) {
    when (view) {
        is LudoPawsReactiveBoardView -> {
            view.reloadStyle()
            return
        }
        is LudoPawsBoardView -> {
            view.reloadStyle()
            return
        }
        is LudoPawsBoardChromeView ->
            view.reloadStyle()
        is LudoBoardView ->
            view.reloadStyle()
        is DiceView ->
            view.reloadStyle()
    }

    if (view is ViewGroup) {
        for (
            index in
            0 until
                view.childCount
        ) {
            reloadVisibleCosmetics(
                view.getChildAt(
                    index,
                ),
            )
        }
    }
}

private val settingsCosmeticExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { work ->
    Thread(work, "LudoPawsSettingsCosmetics").apply { isDaemon = true }
}

private fun selectSettingsCosmetic(
    activity: Activity,
    cosmetic: com.rekixo.ludopaws.feature.store.domain.model.StoreCosmetic,
    onApplied: () -> Unit,
    onFailure: () -> Unit,
) {
    val app = activity.application as com.rekixo.ludopaws.LudoProofApplication
    val inventory = CosmeticInventoryStore(activity.applicationContext)
    if (inventory.selectOfflineFreeCosmetic(cosmetic)) {
        onApplied()
        return
    }
    settingsCosmeticExecutor.execute {
        val result = runCatching {
            val session = app.accountRepository.ensureGuestSession()
            val response = app.accountRepository.selectCosmetic(cosmetic.id)
            val wallet = response.getJSONObject("wallet")
            check(inventory.applyServerSnapshot(wallet, session.accountId)) {
                "The selected account or wallet changed. Please try again."
            }
            inventory.clearOfflineSelection(cosmetic.category)
        }
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
            result.fold(onSuccess = { onApplied() }, onFailure = { error ->
                onFailure()
                PlywoodAlertDialogBuilder(activity)
                    .setTitle("Could not change appearance")
                    .setMessage(error.message ?: "Please check your connection and try again.")
                    .setPositiveButton("OK", null).show()
            })
        }
    }
}
