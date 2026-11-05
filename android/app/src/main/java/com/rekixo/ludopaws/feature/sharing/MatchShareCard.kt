package com.rekixo.ludopaws.feature.sharing

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.*
import android.widget.Toast
import androidx.core.content.FileProvider
import com.rekixo.ludopaws.MatchSnapshot
import com.rekixo.ludopaws.LudoPawsCharacterPortraitBitmapCache
import com.rekixo.ludopaws.core.ui.art.PlywoodTextureDrawable
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
        LudoPawsCharacterPortraitBitmapCache.initialize(activity)
        if (LudoPawsCharacterPortraitBitmapCache.peek(characterId, null) == null) {
            Toast.makeText(activity, "Preparing your Paws card…", Toast.LENGTH_SHORT).show()
        }
        LudoPawsCharacterPortraitBitmapCache.request(characterId, null) { portrait ->
            if (activity.isFinishing || activity.isDestroyed || !activity.hasWindowFocus()) { pending.remove(activity); return@request }
            if (portrait == null) {
                pending.remove(activity)
                Toast.makeText(activity, "Your character image is still getting ready. Please try sharing again.", Toast.LENGTH_SHORT).show()
                return@request
            }
            drawCard(activity, headline, subtitle, tagline, message, portrait)
        }
    }
    private fun drawCard(activity: Activity, headline: String, subtitle: String, tagline: String, message: String, portrait: Bitmap) {
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
                    PlywoodTextureDrawable(3f, cornerRadiusDp = 20f, rimThicknessDp = 5f).apply {
                        setBounds(0, 0, 1080, 1080); draw(canvas)
                    }
                    paint.shader = LinearGradient(0f, 280f, 0f, 720f, 0xFF397EB1.toInt(), 0xFF1D4571.toInt(), Shader.TileMode.CLAMP)
                    canvas.drawRoundRect(250f, 280f, 830f, 720f, 150f, 150f, paint); paint.shader = null
                    paint.color = 0xFFFFD875.toInt(); paint.style = Paint.Style.STROKE; paint.strokeWidth = 4f
                    canvas.drawRoundRect(35f, 35f, 1045f, 1045f, 45f, 45f, paint); paint.style = Paint.Style.FILL
                    fun line(text: String, y: Float, size: Float, color: Int, maxWidth: Float = 940f) {
                        paint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD); paint.textAlign = Paint.Align.CENTER
                        paint.textSize = size; paint.color = color
                        while (paint.measureText(text) > maxWidth && paint.textSize > 20f) paint.textSize -= 2f
                        paint.setShadowLayer(3f, 0f, 2f, 0x663A220F)
                        canvas.drawText(text, 540f, y, paint); paint.clearShadowLayer()
                    }
                    line("LUDO PAWS", 132f, 57f, 0xFFFFE3A0.toInt())
                    line(headline, 236f, 66f, Color.WHITE)
                    canvas.drawBitmap(portrait, null, RectF(310f, 280f, 770f, 740f), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    paint.color = 0xFFF8E4BC.toInt()
                    canvas.drawRoundRect(100f, 753f, 980f, 831f, 39f, 39f, paint)
                    line(subtitle, 807f, 40f, 0xFF422B1C.toInt(), 800f)
                    line(tagline, 900f, 42f, Color.WHITE)
                    line("Play with friends · Find Ludo Paws on Google Play", 994f, 29f, 0xFFFFE9BB.toInt())
                    file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                } finally { bitmap.recycle() }
                val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.sharecards", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "$headline · $subtitle\n$message\nhttps://play.google.com/store/apps/details?id=${activity.packageName}")
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
