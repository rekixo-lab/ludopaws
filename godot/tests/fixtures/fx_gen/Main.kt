package com.rekixo.ludopaws

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Recorder
import com.rekixo.ludopaws.feature.characters.domain.model.AnimationCue

// Records the UNCHANGED live LudoPawsFxPainter draw calls (density 2) for every cue and
// effect over a progress sweep. ONE painter instance is reused in this exact order, so its
// stateful paint widths carry over between calls as they do in the app.
fun main() {
    val painter = LudoPawsFxPainter(Context())
    val canvas = Canvas()
    val cell = 40f
    val blocks = mutableListOf<String>()
    fun block(label: String, body: () -> Unit) {
        Recorder.commands.clear()
        body()
        blocks += "{\"label\":\"$label\",\"commands\":[${Recorder.commands.joinToString(",")}]}"
    }
    val progresses = listOf(0f, .1f, .25f, .5f, .73f, .9f, 1f)
    for (p in progresses) {
        block("trail $p") { painter.drawPawTrail(canvas, 100f to 220f, 260f to 180f, Color.rgb(229, 57, 53), cell, p, .55f) }
        block("home-trail $p") { painter.drawPawTrail(canvas, 300f to 300f, 300f to 260f, Color.rgb(30, 136, 229), cell, p, 1f) }
        block("capture $p") { painter.drawCaptureReturn(canvas, 420f to 140f, 70f to 70f, Color.rgb(46, 160, 67), cell, p) }
        block("stars $p") { painter.drawHomeStars(canvas, cell, p) }
        for (cue in AnimationCue.values()) {
            for (particles in listOf(0, 8, 32)) {
                block("${cue.name} $particles $p") {
                    painter.drawReaction(canvas, 250f to 330f, cue, cell, p, particles, particles == 32)
                }
            }
        }
    }
    println("[\n" + blocks.joinToString(",\n") + "\n]")
}
