package com.rekixo.ludopaws.feature.characters.content.donkey

import android.graphics.Canvas
import android.graphics.RectF
import com.rekixo.ludopaws.feature.characters.rendering.*

/** Lightweight donkey fallback while the matching 3D portrait is prepared. */
internal object DonkeyAvatarRenderer: CharacterAvatarRenderer {
    override fun draw(context: CharacterAvatarContext,canvas: Canvas)=with(context) {
        fill.color=0xFF858D98.toInt()
        canvas.save();canvas.rotate(-13f,35f,36f)
        canvas.drawOval(RectF(27f,2f,42f,48f),fill)
        fill.color=0xFFB78484.toInt();canvas.drawOval(RectF(31f,7f,38f,38f),fill);canvas.restore()
        fill.color=0xFF858D98.toInt()
        canvas.save();canvas.rotate(13f,65f,36f)
        canvas.drawOval(RectF(58f,2f,73f,48f),fill)
        fill.color=0xFFB78484.toInt();canvas.drawOval(RectF(62f,7f,69f,38f),fill);canvas.restore()
        fill.color=0xFF9198A1.toInt();canvas.drawOval(RectF(24f,31f,76f,82f),fill)
        fill.color=0xFF35373D.toInt();canvas.drawOval(RectF(44f,28f,56f,42f),fill)
        drawEye(canvas,36f,52f);drawEye(canvas,64f,52f)
        fill.color=0xFFE8E3D5.toInt();canvas.drawOval(RectF(26f,61f,74f,87f),fill)
        fill.color=0xFF42454B.toInt()
        canvas.drawOval(RectF(33f,67f,40f,71f),fill);canvas.drawOval(RectF(60f,67f,67f,71f),fill)
        stroke.color=0xFF42454B.toInt();stroke.strokeWidth=1.5f
        canvas.drawArc(RectF(37f,72f,63f,81f),0f,180f,false,stroke)
    }
}
