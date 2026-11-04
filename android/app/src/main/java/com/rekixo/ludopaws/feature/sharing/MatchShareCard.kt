package com.rekixo.ludopaws.feature.sharing

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.*
import android.widget.Toast
import androidx.core.content.FileProvider
import com.rekixo.ludopaws.MatchSnapshot
import com.rekixo.ludopaws.feature.characters.rendering.*
import java.io.File
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper

/** Deliberately drawn card: never captures player names, IDs, chat, wallet or room credentials. */
internal object MatchShareCard {
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "PawsShareCard").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    private val pending = WeakHashMap<Activity, Boolean>()
    fun share(activity: Activity, state: MatchSnapshot, mode: String, playerId: String? = null) {
        if (state.status != "FINISHED") return
        val winner = state.players.firstOrNull { it.playerId == state.winnerPlayerId }
            ?: state.players.firstOrNull { state.winnerTeamId != null && it.teamId == state.winnerTeamId }
            ?: return
        val me = state.players.firstOrNull { it.playerId == playerId }
        val won = me != null && (me.playerId == state.winnerPlayerId ||
            state.winnerTeamId != null && me.teamId == state.winnerTeamId)
        val headline = when { playerId == null -> "A PAWSOME FINISH!"; won -> "I WON!"; else -> "REMATCH TIME!" }
        shareCard(activity, headline, mode, me?.characterId ?: winner.characterId, "Think you can beat me?", "Come play Ludo Paws with me!")
    }

    fun weekly(activity: Activity, title: String, completed: Boolean) = shareCard(activity,
        if (completed) "CHALLENGE COMPLETE!" else "PLAY THIS WEEK!", title,
        com.rekixo.ludopaws.feature.characters.data.local.CharacterSelectionStore(activity).load().characterId,
        "Little paws. Big fun.", "Join this week's Ludo Paws challenge!")

    private fun shareCard(activity: Activity, headline: String, subtitle: String, characterId: String?, tagline: String, message: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (pending.put(activity, true) != null) return
        worker.execute {
            val result = runCatching {
                val directory = File(activity.cacheDir, "share_cards").apply { check(isDirectory || mkdirs()) }
                // Keep recent chooser attachments valid; remove old cards only on a later explicit share.
                directory.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 7 * 86_400_000L }?.forEach { it.delete() }
                val bitmap = Bitmap.createBitmap(1080, 1080, Bitmap.Config.ARGB_8888)
                val file = File(directory, "paws-${UUID.randomUUID()}.png")
                try {
                    val canvas = Canvas(bitmap)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    paint.shader = LinearGradient(0f, 0f, 1080f, 1080f, 0xFF143F43.toInt(), 0xFF102536.toInt(), Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, 1080f, 1080f, paint); paint.shader = null
                    paint.color = 0x223FE2BC; canvas.drawCircle(540f, 490f, 300f, paint)
                    paint.color = 0xFFFFD875.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f
                    canvas.drawRoundRect(35f, 35f, 1045f, 1045f, 45f, 45f, paint); paint.style = Paint.Style.FILL
                    fun line(text: String, y: Float, size: Float, color: Int) {
                        paint.typeface = Typeface.DEFAULT_BOLD; paint.textAlign = Paint.Align.CENTER
                        paint.textSize = size; paint.color = color
                        while (paint.measureText(text) > 940f && paint.textSize > 20f) paint.textSize -= 2f
                        canvas.drawText(text, 540f, y, paint)
                    }
                    line("LUDO PAWS", 145f, 64f, 0xFFFFD875.toInt())
                    line(headline, 250f, 70f, Color.WHITE)
                    canvas.save(); canvas.translate(290f, 275f); canvas.scale(5f, 5f)
                    CharacterRegistry.resolve(characterId).avatar.draw(CharacterAvatarContext(
                        Paint(Paint.ANTI_ALIAS_FLAG), Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }), canvas)
                    canvas.restore()
                    line(subtitle, 800f, 42f, 0xFFFFD875.toInt())
                    line(tagline, 890f, 46f, Color.WHITE)
                    line("Play with friends • Find Ludo Paws on Google Play", 990f, 29f, Color.WHITE)
                    file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                } finally { bitmap.recycle() }
                val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.sharecards", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "$message\nhttps://play.google.com/store/apps/details?id=${activity.packageName}")
                    clipData = ClipData.newUri(activity.contentResolver, "Ludo Paws card", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                send
            }
            main.post {
                pending.remove(activity)
                if (activity.isFinishing || activity.isDestroyed || !activity.hasWindowFocus()) return@post
                result.onSuccess { send ->
                    runCatching { activity.startActivity(Intent.createChooser(send, "Share your Ludo Paws moment")) }
                        .onSuccess { com.rekixo.ludopaws.feature.growth.GrowthMetrics.increment(activity, "share_card_opened") }
                        .onFailure { Toast.makeText(activity, "No sharing app is available.", Toast.LENGTH_SHORT).show() }
                }.onFailure { Toast.makeText(activity, "Couldn't prepare the card. Please try again.", Toast.LENGTH_SHORT).show() }
            }
        }
    }
}
