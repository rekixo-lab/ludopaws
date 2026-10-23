package com.rekixo.ludopaws

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Five independently interchangeable, animal-free habitat artworks.
 *
 * Paint all four COMPLETE YARD QUADRANTS (including their former color strips).
 * Road, home lanes, center, stars, hit targets and all authoritative pawns
 * remain untouched. 3D animals draw ABOVE this board view.
 *
 * The habitat atlas includes its own garden/pond/farm edge decoration, so
 * leaving the old broad colored rim visible would create double borders.
 *
 * The five detailed illustrations stay around the habitat's perimeter. A
 * translucent biome-colored clearing calms the middle where four real 3D
 * characters stand. No fake white paw pedestals compete with the characters.
 *
 * The small scene bitmaps are lazily cached per habitat (5 * 384² RGBA < 3 MiB).
 * Dense procedural details are drawn once, not every animation frame.
 */
internal class LudoPawsYardHabitatRenderer(private val context: Context) {
    /**
     * High-quality offline illustration atlas; five equal-width panels in
     * order Dog / Goat / Duck / Cat / Donkey. Loaded once, not per frame.
     * The original cached vector scenes remain a safe fallback.
     */
    private val paintedAtlas: Bitmap? by lazy(LazyThreadSafetyMode.NONE) {
        runCatching {
            context.assets.open("yard_habitats/atlas.webp").use { stream ->
                BitmapFactory.decodeStream(stream)?.takeIf { bitmap ->
                    bitmap.width >= 5 &&
                        bitmap.width % 5 == 0 &&
                        bitmap.height > 0
                }
            }
        }.getOrNull()
    }

    private fun atlasIndex(habitat: LudoPawsYardHabitat): Int = when (habitat) {
        LudoPawsYardHabitat.DOG_GARDEN -> 0
        LudoPawsYardHabitat.GOAT_MEADOW -> 1
        LudoPawsYardHabitat.DUCK_POND -> 2
        LudoPawsYardHabitat.CAT_COURTYARD -> 3
        LudoPawsYardHabitat.DONKEY_FARM -> 4
    }

    private fun drawPremiumHabitat(
        canvas: Canvas,
        habitat: LudoPawsYardHabitat,
        destination: RectF,
    ): Boolean {
        val atlas = paintedAtlas ?: return false
        val panel = atlas.width / 5
        val index = atlasIndex(habitat)
        // Trim only the outermost 3% of the source where the old thick,
        // dark atlas edging doubled up against the four Ludo board seams.
        // This fills the same COMPLETE yard without resizing pawn positions.
        val inset = (panel * 0.03f).toInt().coerceAtLeast(1)
        canvas.drawBitmap(
            atlas,
            Rect(
                index * panel + inset,
                inset,
                (index + 1) * panel - inset,
                atlas.height - inset,
            ),
            destination,
            bitmapPaint,
        )
        return true
    }

    private val cache = LruCache<LudoPawsYardHabitat, Bitmap>(5)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val habitatBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clearingPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun draw(
        canvas: Canvas,
        cell: Float,
        habitatsByColor: Map<String, LudoPawsYardHabitat>,
    ) {
        if (cell <= 0f || habitatsByColor.isEmpty()) return
        // Stable canonical layout; parent view rotation already follows player
        // perspective, so never rotate or permute habitats a second time here.
        for (color in listOf("RED", "GREEN", "YELLOW", "BLUE")) {
            val habitat = habitatsByColor[color] ?: continue
            val face = LudoPawsYardHabitatGeometry.face(color, cell) ?: continue
            val rect = RectF(face.left, face.top, face.right, face.bottom)
            val save = canvas.save()
            // Square-to-square clip meets the road at the exact approved
            // 5-cell yard boundary. Rounded clips reveal colored triangular
            // corner slivers, so do NOT reintroduce a roundRect mask here.
            canvas.clipRect(rect)
            // Opaque scene-colored underlay protects against transparent
            // edge pixels in WebP without exposing the old team-color band.
            habitatBackgroundPaint.color = when (habitat) {
                LudoPawsYardHabitat.DOG_GARDEN -> 0xFF6DAA43.toInt()
                LudoPawsYardHabitat.GOAT_MEADOW -> 0xFFC3B46B.toInt()
                LudoPawsYardHabitat.DUCK_POND -> 0xFF149FC5.toInt()
                LudoPawsYardHabitat.CAT_COURTYARD -> 0xFFD6B4B9.toInt()
                LudoPawsYardHabitat.DONKEY_FARM -> 0xFFD4AE63.toInt()
            }
            canvas.drawRect(rect, habitatBackgroundPaint)
            if (!drawPremiumHabitat(canvas, habitat, rect)) {
                canvas.drawBitmap(bitmap(habitat), null, rect, bitmapPaint)
            }
            drawOpenPlayArea(canvas, rect, habitat)
            // NO bright permanent landingPad circles. The 3D scene already
            // provides a grounded shadow for each pawn, and the authoritative
            // yard-footprint overlay marks only slots actually vacated.
            canvas.restoreToCount(save)
        }
    }

    /**
     * A soft illustrated clearing, not a solid flat overlay. Real detail is
     * strongest around the edge, with calm ground/water under the 3D animals.
     * The center has the highest veil opacity; there is no rectangular seam,
     * border strip, white disk or fake animal in the backing art.
     */
    private fun drawOpenPlayArea(
        canvas: Canvas,
        yard: RectF,
        habitat: LudoPawsYardHabitat,
    ) {
        val tint = when (habitat) {
            LudoPawsYardHabitat.DOG_GARDEN -> 0xFF96BD6B.toInt()  // sunlit grass
            LudoPawsYardHabitat.GOAT_MEADOW -> 0xFFE8CF97.toInt() // sandy meadow
            LudoPawsYardHabitat.DUCK_POND -> 0xFF44BAE7.toInt()   // open water
            LudoPawsYardHabitat.CAT_COURTYARD -> 0xFFD8C5B5.toInt() // warm paving
            LudoPawsYardHabitat.DONKEY_FARM -> 0xFFDEBC7E.toInt() // farm earth
        }
        val red = Color.red(tint)
        val green = Color.green(tint)
        val blue = Color.blue(tint)
        clearingPaint.shader = RadialGradient(
            yard.centerX(),
            yard.centerY(),
            yard.width() * 0.55f,
            intArrayOf(
                Color.argb(177, red, green, blue),
                Color.argb(114, red, green, blue),
                Color.argb(0, red, green, blue),
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(yard, clearingPaint)
        clearingPaint.shader = null
    }

    private fun bitmap(habitat: LudoPawsYardHabitat): Bitmap {
        cache.get(habitat)?.let { return it }
        val b = Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888)
        val scene = Canvas(b)
        scene.scale(3.84f, 3.84f)
        drawScene(scene, habitat)
        cache.put(habitat, b)
        return b
    }

    private fun drawScene(canvas: Canvas, habitat: LudoPawsYardHabitat) {
        when (habitat) {
            LudoPawsYardHabitat.DOG_GARDEN -> dogGarden(canvas)
            LudoPawsYardHabitat.GOAT_MEADOW -> goatMeadow(canvas)
            LudoPawsYardHabitat.DUCK_POND -> duckPond(canvas)
            LudoPawsYardHabitat.CAT_COURTYARD -> catCourtyard(canvas)
            LudoPawsYardHabitat.DONKEY_FARM -> donkeyFarm(canvas)
        }
        vignette(canvas)
    }

    private fun dogGarden(c: Canvas) {
        background(c, 0xFFC3EE78.toInt(), 0xFF3AAB54.toInt())
        patches(c, 102, 0x304F7926, 1.7f, 4.8f)
        // A winding warm footpath beneath the garden, partly revealed between paw pads.
        val path = Path().apply {
            moveTo(-9f, 83f)
            cubicTo(26f, 72f, 24f, 32f, 69f, 28f)
            cubicTo(88f, 25f, 102f, 18f, 108f, -8f)
        }
        stroke(c, path, 0x505A7C35, 22f)
        stroke(c, path, 0xFFF5D890.toInt(), 15f)
        stroke(c, path, 0xAFFFF2C3.toInt(), 7f)
        foliageBorder(c, 0xFF2E9237.toInt(), 0xFF6FCC45.toInt(), seed = 112)
        gardenFlowers(c, seed = 121, count = 25, petal = 0xFFF8FEF2.toInt())
        gardenFlowers(c, seed = 122, count = 13, petal = 0xFFFFC9CD.toInt())
    }

    private fun goatMeadow(c: Canvas) {
        background(c, 0xFFFFE6A0.toInt(), 0xFF90CB65.toInt())
        // Sunlit hillside bands and small natural rock outcrops.
        hill(c, 62f, 0xFFB6D67A.toInt(), .24f)
        hill(c, 83f, 0xFF78B75C.toInt(), .32f)
        patches(c, 118, 0x3E81744D, 1f, 3.1f)
        for ((x,y,s) in listOf(Triple(4f,18f,1f),Triple(92f,78f,.8f),Triple(81f,7f,.65f))) {
            stone(c,x,y,s)
        }
        foliageBorder(c, 0xFF559843.toInt(), 0xFFC0DD64.toInt(), seed = 213)
        val r = Random(218)
        repeat(55) {
            val x = r.nextFloat() * 100f
            val y = r.nextFloat() * 100f
            grass(c,x,y,.5f+r.nextFloat()*.7f,0x9B487F36.toInt())
        }
        gardenFlowers(c, seed = 219, count = 22, petal = 0xFFFCF7DD.toInt())
        gardenFlowers(c, seed = 221, count = 12, petal = 0xFFFFB4E3.toInt())
    }

    private fun duckPond(c: Canvas) {
        background(c, 0xFF53DDDF.toInt(), 0xFF0784CC.toInt())
        // Soft deep-water center, bright shallow turquoise shore.
        p.color=Color.WHITE
        p.shader = RadialGradient(50f,47f,72f, intArrayOf(
            0x004DDDF5, 0x3A047DB4, 0xC429BFB9.toInt()
        ), floatArrayOf(0f,.57f,1f), Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,100f,100f,p)
        p.shader = null
        val ripples = listOf(Triple(28f,22f,17f),Triple(67f,26f,12f),
            Triple(52f,54f,21f),Triple(23f,77f,11f),Triple(78f,78f,19f))
        for ((x,y,r) in ripples) {
            ring(c,x,y,r,r*.30f,0x71DAFAFF, .90f)
            ring(c,x+2f,y+3f,r*.73f,r*.18f,0x58ECFFFF,.72f)
        }
        lilyPad(c,13f,53f,8f,225f)
        lilyPad(c,86f,42f,10f,40f)
        lilyPad(c,72f,91f,9f,310f)
        lilyPad(c,19f,9f,8f,140f)
        waterLotus(c,88f,78f,1f)
        waterLotus(c,8f,34f,.65f)
        foliageBorder(c,0xFF08755A.toInt(),0xFF4FCB70.toInt(),seed=320,amount=28)
        val r=Random(327)
        repeat(29){
            val edge=if(r.nextBoolean()) 2f else 98f
            val x=if(r.nextBoolean()) edge else r.nextFloat()*100f
            val y=if(x==edge) r.nextFloat()*100f else edge
            reed(c,x,y,.6f+r.nextFloat()*.6f)
        }
        patches(c,30,0x3ADDFBFA,1f,2f)
    }

    private fun catCourtyard(c: Canvas) {
        background(c, 0xFFFFE4E3.toInt(), 0xFFD5B2DD.toInt())
        // Cozy flower courtyard with irregular pale stepping stones.
        val r=Random(416)
        repeat(28){
            val x=r.nextFloat()*100f
            val y=r.nextFloat()*100f
            oval(c,x,y,3.3f+r.nextFloat()*3f,2.2f+r.nextFloat()*1.9f,0x4BFFF9F2)
        }
        val walkway=Path().apply{
            moveTo(-12f,72f);cubicTo(35f,60f,56f,30f,117f,29f)
        }
        stroke(c,walkway,0x337C6B7B,16f)
        stroke(c,walkway,0xB9FFF4E8.toInt(),12f)
        patches(c,74,0x2D9A638D,1f,3f)
        foliageBorder(c,0xFF55864A.toInt(),0xFFA4CC71.toInt(),seed=418)
        gardenFlowers(c,420,31,0xFFF9FBFF.toInt())
        gardenFlowers(c,421,20,0xFFFFA3D4.toInt())
        gardenFlowers(c,422,10,0xFFDDB8FF.toInt())
    }

    private fun donkeyFarm(c: Canvas) {
        background(c, 0xFFFFE8AA.toInt(), 0xFFC99E56.toInt())
        // Orchard/farm path with warm wooden edging and tall ripe wheat.
        hill(c,69f,0xFFE4BE70.toInt(),.19f)
        val field=Path().apply{
            moveTo(2f,105f);cubicTo(11f,79f,39f,44f,64f,29f)
            cubicTo(84f,16f,91f,10f,108f,-7f)
        }
        stroke(c,field,0x677E6935,24f)
        stroke(c,field,0xFFEDD29A.toInt(),16f)
        stroke(c,field,0xAFFFF0C6.toInt(),7f)
        patches(c,92,0x397B6337,1f,3.3f)
        foliageBorder(c,0xFF628542.toInt(),0xFFB3B95A.toInt(),seed=522)
        for ((x,y) in listOf(6f to 28f,10f to 71f,94f to 13f,92f to 66f,58f to 95f)) {
            wheat(c,x,y,1.0f)
        }
        gardenFlowers(c,529,14,0xFFFFF4CE.toInt())
        val r=Random(537)
        repeat(21){grass(c,r.nextFloat()*100f,r.nextFloat()*100f,.6f,0x886E9449.toInt())}
    }

    /** Low-cost granular textures are baked into each cached bitmap. */
    private fun patches(c: Canvas, seed: Int, color: Int, smallest: Float, largest: Float) {
        val r=Random(seed)
        repeat(310) {
            val x=r.nextFloat()*100f
            val y=r.nextFloat()*100f
            val s=smallest+r.nextFloat()*(largest-smallest)
            oval(c,x,y,s*(.28f+r.nextFloat()*.42f),s*.22f,color)
        }
    }

    private fun foliageBorder(c: Canvas, dark: Int, light: Int, seed: Int, amount: Int=37) {
        val r=Random(seed)
        repeat(amount){
            val edge=r.nextInt(4)
            val pos=r.nextFloat()*100f
            val x=when(edge){0->r.nextFloat()*9f;1->91f+r.nextFloat()*9f;else->pos}
            val y=when(edge){2->r.nextFloat()*9f;3->91f+r.nextFloat()*9f;else->pos}
            bush(c,x,y,.65f+r.nextFloat()*.6f,dark,light)
        }
        for ((x,y) in listOf(0f to 0f,100f to 0f,0f to 100f,100f to 100f)) {
            bush(c,x,y,1.65f,dark,light)
        }
    }

    private fun bush(c: Canvas, x: Float, y: Float, size: Float, dark: Int, light: Int) {
        oval(c,x,y+1.8f*size,8f*size,5f*size,0x470B3119)
        val shades=intArrayOf(dark,light,0xFFB7E971.toInt(),dark,light)
        for (i in 0 until 9) {
            val t=i*6.283185f/9f
            val xx=x+cos(t)*5f*size
            val yy=y+sin(t)*3.7f*size
            leaf(c,xx,yy,3.4f*size,2.4f*size,t*57.2958f,shades[i%shades.size])
        }
        oval(c,x-1.2f*size,y-1.6f*size,2.1f*size,1.6f*size,0x88BFF38C.toInt())
    }

    private fun leaf(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, angle: Float, color: Int) {
        val saved=c.save()
        c.rotate(angle,x,y)
        oval(c,x,y+.4f,rx,ry,0x42252C18)
        oval(c,x,y,rx,ry,color)
        oval(c,x-rx*.2f,y-ry*.3f,rx*.54f,ry*.28f,0x48FFFFFF)
        c.restoreToCount(saved)
    }

    private fun gardenFlowers(c: Canvas, seed: Int, count: Int, petal: Int) {
        val r=Random(seed)
        repeat(count){
            val edge=r.nextInt(4)
            val t=r.nextFloat()*100f
            val x=if(edge==0) r.nextFloat()*16f else if(edge==1) 84f+r.nextFloat()*16f else t
            val y=if(edge==2) r.nextFloat()*15f else if(edge==3) 85f+r.nextFloat()*15f else t
            flower(c,x,y,.6f+r.nextFloat()*.75f,petal)
        }
    }

    private fun flower(c: Canvas,x:Float,y:Float,s:Float,petal:Int) {
        for(i in 0..4){
            val a=i*6.283185f/5f
            oval(c,x+cos(a)*2.0f*s,y+sin(a)*2.0f*s,1.9f*s,1.5f*s,petal)
        }
        oval(c,x,y,1.55f*s,1.55f*s,0xFFFFC645.toInt())
        oval(c,x-.35f*s,y-.35f*s,.55f*s,.55f*s,0xFFFFE99D.toInt())
    }

    private fun hill(c: Canvas, y:Float, color:Int, depth:Float) {
        val path=Path().apply{
            moveTo(-10f,100f);lineTo(-10f,y)
            cubicTo(23f,y-17f,45f,y+depth*19f,67f,y-4f)
            cubicTo(87f,y-12f,100f,y+7f,110f,y)
            lineTo(110f,110f);close()
        }
        p.color=color
        c.drawPath(path,p)
    }

    private fun stone(c:Canvas,x:Float,y:Float,s:Float) {
        oval(c,x,y+2f*s,9f*s,5f*s,0x573D5538)
        oval(c,x,y,8.2f*s,5.4f*s,0xFF8F9890.toInt())
        oval(c,x-2f*s,y-1.8f*s,4.2f*s,2.1f*s,0xFFCCD1B7.toInt())
        oval(c,x+3f*s,y+1f*s,2f*s,2.3f*s,0xFF6C7976.toInt())
    }

    private fun grass(c:Canvas,x:Float,y:Float,s:Float,color:Int) {
        line(c,x,y+2f*s,x-2.5f*s,y-3f*s,color,.8f*s)
        line(c,x,y+2f*s,x+.6f*s,y-5f*s,color,.9f*s)
        line(c,x,y+2f*s,x+3f*s,y-2f*s,color,.8f*s)
    }

    private fun lilyPad(c:Canvas,x:Float,y:Float,r:Float,rotation:Float) {
        val saved=c.save()
        c.rotate(rotation,x,y)
        oval(c,x,y+1f,r*1.05f,r*.55f,0x740B6E65)
        oval(c,x,y,r,r*.55f,0xFF6EB848.toInt())
        oval(c,x-2f,y-1.7f,r*.52f,r*.22f,0xFFB7E575.toInt())
        val cut=Path().apply{
            moveTo(x,y);lineTo(x+r*1.12f,y-2f);lineTo(x+r*.78f,y+r*.46f);close()
        }
        p.color=0xFF2CB6C4.toInt();c.drawPath(cut,p)
        line(c,x-r*.6f,y,x+r*.52f,y,0x76578B3D,.5f)
        c.restoreToCount(saved)
    }

    private fun waterLotus(c:Canvas,x:Float,y:Float,s:Float) {
        oval(c,x,y+3f*s,7.5f*s,3.2f*s,0x7707778C)
        for(i in 0..5){
            val angle=i*60f
            val saved=c.save()
            c.rotate(angle,x,y)
            oval(c,x,y-3.1f*s,2.2f*s,5.4f*s,0xFFF28BC1.toInt())
            oval(c,x,y-3.4f*s,1.2f*s,3.8f*s,0xFFFFCDE6.toInt())
            c.restoreToCount(saved)
        }
        oval(c,x,y,2f*s,1.6f*s,0xFFFFDC64.toInt())
    }

    private fun reed(c:Canvas,x:Float,y:Float,s:Float) {
        line(c,x,y,x-1.6f*s,y-11f*s,0xFF337E51.toInt(),.95f*s)
        line(c,x,y,x+3.2f*s,y-8f*s,0xFF428D51.toInt(),.90f*s)
        oval(c,x-1.9f*s,y-10f*s,1.05f*s,2.6f*s,0xFFC48B41.toInt())
    }

    private fun wheat(c:Canvas,x:Float,y:Float,s:Float){
        line(c,x,y+9f*s,x+1f*s,y-11f*s,0xFF7D8A39.toInt(),1f*s)
        for(i in 0..5){
            val yy=y-9f*s+i*2.4f*s
            val xx=x+1f*s-i*.13f*s
            oval(c,xx-1.5f*s,yy,.9f*s,2f*s,0xFFE5B65A.toInt())
            oval(c,xx+1.5f*s,yy,.9f*s,2f*s,0xFFF3D17C.toInt())
        }
    }

    private fun vignette(c: Canvas) {
        p.color=Color.WHITE
        p.style=Paint.Style.FILL
        p.shader = RadialGradient(
            50f,45f,73f,
            intArrayOf(0x00FFFFFF,0x00FFFFFF,0x350E3322),
            floatArrayOf(0f,.73f,1f),
            Shader.TileMode.CLAMP,
        )
        c.drawRect(0f,0f,100f,100f,p)
        p.shader=null
    }

    private fun background(c:Canvas,top:Int,bottom:Int) {
        p.color=Color.WHITE
        p.style=Paint.Style.FILL
        p.shader=LinearGradient(0f,0f,100f,100f,top,bottom,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,100f,100f,p)
        p.shader=null
    }

    private fun oval(c:Canvas,x:Float,y:Float,rx:Float,ry:Float,color:Int){
        p.style=Paint.Style.FILL
        p.color=color
        c.drawOval(x-rx,y-ry,x+rx,y+ry,p)
    }

    private fun line(c:Canvas,x0:Float,y0:Float,x1:Float,y1:Float,color:Int,width:Float){
        p.style=Paint.Style.STROKE
        p.color=color
        p.strokeWidth=width
        c.drawLine(x0,y0,x1,y1,p)
        p.style=Paint.Style.FILL
    }

    private fun ring(c:Canvas,x:Float,y:Float,rx:Float,ry:Float,color:Int,width:Float){
        p.style=Paint.Style.STROKE
        p.color=color
        p.strokeWidth=width
        c.drawOval(x-rx,y-ry,x+rx,y+ry,p)
        p.style=Paint.Style.FILL
    }

    private fun stroke(c:Canvas,path:Path,color:Int,width:Float){
        p.style=Paint.Style.STROKE
        p.color=color
        p.strokeWidth=width
        c.drawPath(path,p)
        p.style=Paint.Style.FILL
    }
}
