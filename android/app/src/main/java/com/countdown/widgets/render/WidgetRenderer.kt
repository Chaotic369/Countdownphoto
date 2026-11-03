package com.countdown.widgets.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.countdown.widgets.data.PhotoStorage
import com.countdown.widgets.data.WidgetConfig
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Draws a widget to a Bitmap that is shown in an ImageView inside RemoteViews. */
object WidgetRenderer {
    private const val MAX_PIXELS = 1_400_000L

    fun render(ctx: Context, cfg: WidgetConfig?, areaW: Int, areaH: Int): Bitmap {
        val baseW = max(areaW, 48)
        var w = baseW
        var h = max(areaH, 48)
        val px = w.toLong() * h
        if (px > MAX_PIXELS) {
            val f = sqrt(MAX_PIXELS.toDouble() / px)
            w = (w * f).toInt()
            h = (h * f).toInt()
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val d = ctx.resources.displayMetrics.density * (w.toFloat() / baseW)
        if (cfg == null) {
            drawHint(c, w, h, d)
        } else when (cfg.type) {
            "text" -> drawText(ctx, c, cfg, w, h, d)
            "count" -> drawCount(ctx, c, cfg, w, h, d)
            "photo" -> drawPhoto(ctx, c, cfg, w, h, d)
            else -> drawHint(c, w, h, d)
        }
        return bmp
    }

    private fun col(s: String, def: Int = Color.WHITE): Int =
        try { Color.parseColor(s) } catch (e: Exception) { def }

    private fun alphaOf(c: Int, a: Float): Int =
        Color.argb((a.coerceIn(0f, 1f) * 255).roundToInt(), Color.red(c), Color.green(c), Color.blue(c))

    private fun drawCard(c: Canvas, cw: Float, ch: Float, cfg: WidgetConfig, d: Float) {
        val r = cfg.int("radius", 24) * d
        val rect = RectF(0f, 0f, cw, ch)
        if (cfg.bool("bgOn", true)) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = alphaOf(col(cfg.str("bg", "#000000"), Color.BLACK), cfg.int("bgOpacity", 40) / 100f)
            c.drawRoundRect(rect, r, r, p)
        }
        if (cfg.bool("border")) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.5f * d
            p.color = col(cfg.str("borderColor", "#ffffff"))
            val half = 0.75f * d
            c.drawRoundRect(RectF(half, half, cw - half, ch - half), r, r, p)
        }
    }

    private fun drawHint(c: Canvas, w: Int, h: Int, d: Float) {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        bg.color = Color.argb(150, 0, 0, 0)
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 20f * d, 20f * d, bg)
        val t = TextPaint(Paint.ANTI_ALIAS_FLAG)
        t.color = Color.WHITE
        t.textSize = 14f * d
        t.textAlign = Paint.Align.CENTER
        c.drawText("Tap to set up", w / 2f, h / 2f + 5f * d, t)
    }

    /* ---------------- text ---------------- */
    private fun drawText(ctx: Context, c: Canvas, cfg: WidgetConfig, aw: Int, ah: Int, d: Float) {
        val size = cfg.int("size", 34) * d
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.typeface = FontLoader.get(ctx, cfg.str("font", "Fraunces"), cfg.int("weight", 500), cfg.bool("italic"))
        paint.textSize = size
        paint.color = col(cfg.str("color", "#ffffff"))
        paint.letterSpacing = (cfg.dbl("spacing", 0.0) * d / size).toFloat()
        val pad = cfg.int("pad", 22) * d
        val text = cfg.str("text").ifEmpty { " " }
        val maxW = max(20, (aw - 2 * pad).toInt())
        val align = when (cfg.str("align", "center")) {
            "left" -> Layout.Alignment.ALIGN_NORMAL
            "right" -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_CENTER
        }
        fun build(width: Int): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(align).setIncludePad(false).build()
        var layout = build(maxW)
        var tw = 1
        for (i in 0 until layout.lineCount) tw = max(tw, ceil(layout.getLineWidth(i)).toInt())
        layout = build(tw)
        val cw = tw + 2 * pad
        val ch = layout.height + 2 * pad
        val s = min(1f, min(aw / cw, ah / ch))
        c.save()
        c.translate((aw - cw * s) / 2f, (ah - ch * s) / 2f)
        c.scale(s, s)
        drawCard(c, cw, ch, cfg, d)
        c.translate(pad, pad)
        layout.draw(c)
        c.restore()
    }

    /* ---------------- countdown ---------------- */
    private class Item(val text: String, val label: String, val w: Float, val h: Float, val numW: Float)

    private fun drawCount(ctx: Context, c: Canvas, cfg: WidgetConfig, aw: Int, ah: Int, d: Float) {
        val sz = cfg.int("size", 44) * d
        val fontName = cfg.str("font", "seg7")
        val seg = fontName == "seg7"
        val italic = cfg.bool("italic")
        val fg = col(cfg.str("color", "#ffffff"))
        val pad = cfg.int("pad", 20) * d
        val showLabels = cfg.bool("showLabels", true)
        val showSeps = cfg.bool("seps", true)
        val mono = "Geist Mono"
        val bd = CountdownMath.breakdown(cfg)
        // Same order as the editor preview (the JS reverses the UNITS list).
        val active = CountdownMath.ORDER.filter { cfg.unit(it) }.reversed()
        if (active.isEmpty()) return

        val num = TextPaint(Paint.ANTI_ALIAS_FLAG)
        num.typeface = FontLoader.get(ctx, if (seg) mono else fontName, cfg.int("weight", 600), italic && !seg)
        num.textSize = sz
        num.color = fg

        val labSz = max(9f * d, (sz * 0.26f).roundToInt().toFloat())
        val lab = TextPaint(Paint.ANTI_ALIAS_FLAG)
        lab.typeface = FontLoader.get(ctx, mono, 500, italic)
        lab.textSize = labSz
        lab.letterSpacing = 0.14f
        lab.color = alphaOf(fg, 0.72f)
        val labH = lab.fontMetrics.descent - lab.fontMetrics.ascent

        val gap = 8f * d
        val gapIn = 6f * d
        val items = active.map { k ->
            var v = (bd[k] ?: 0L).toString()
            if (v.length < 2) v = "0$v"
            val nw = if (seg) v.length * sz * SevenSegment.ASPECT + (v.length - 1) * sz * 0.06f else num.measureText(v)
            val label = k.uppercase()
            val lw = if (showLabels) lab.measureText(label) else 0f
            Item(v, label, max(nw, lw), sz + (if (showLabels) gapIn + labH else 0f), nw)
        }
        val n = items.size
        val sepW = if (!showSeps) 0f else if (seg) sz * 0.2f else num.measureText(":")
        var rowW = 0f
        for (it in items) rowW += it.w
        rowW += if (showSeps) (n - 1) * (sepW + 2 * gap) else (n - 1) * gap
        var rowH = 0f
        for (it in items) rowH = max(rowH, it.h)

        val title = if (cfg.bool("showTitle", true)) cfg.str("label").uppercase() else ""
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        tp.typeface = FontLoader.get(ctx, if (seg) mono else fontName, 400, italic)
        tp.textSize = max(11f * d, (sz * 0.32f).roundToInt().toFloat())
        tp.letterSpacing = 0.08f
        tp.color = fg
        val titleW = if (title.isEmpty()) 0f else tp.measureText(title)
        val titleBlock = if (title.isEmpty()) 0f else (tp.fontMetrics.descent - tp.fontMetrics.ascent) + 0.55f * tp.textSize

        val contentW = max(rowW, titleW)
        val contentH = titleBlock + rowH
        val cw = contentW + 2 * pad
        val ch = contentH + 2 * pad
        val s = min(1f, min(aw / cw, ah / ch))

        c.save()
        c.translate((aw - cw * s) / 2f, (ah - ch * s) / 2f)
        c.scale(s, s)
        drawCard(c, cw, ch, cfg, d)
        c.translate(pad, pad)

        if (title.isNotEmpty()) {
            c.drawText(title, (contentW - titleW) / 2f, -tp.fontMetrics.ascent, tp)
        }
        val sepPaint = TextPaint(num)
        sepPaint.color = alphaOf(fg, 0.8f)
        var x = (contentW - rowW) / 2f
        val y0 = titleBlock
        items.forEachIndexed { i, u ->
            val nx = x + (u.w - u.numW) / 2f
            if (seg) {
                u.text.forEachIndexed { j, ch2 ->
                    SevenSegment.drawDigit(c, ch2, nx + j * (sz * SevenSegment.ASPECT + sz * 0.06f), y0, sz, fg, italic)
                }
            } else {
                val fm = num.fontMetrics
                c.drawText(u.text, nx, y0 + (sz - (fm.descent - fm.ascent)) / 2f - fm.ascent, num)
            }
            if (showLabels) {
                val lx = x + (u.w - lab.measureText(u.label)) / 2f
                c.drawText(u.label, lx, y0 + sz + gapIn - lab.fontMetrics.ascent, lab)
            }
            x += u.w
            if (i < n - 1) {
                if (showSeps) {
                    x += gap
                    if (seg) {
                        SevenSegment.drawColon(c, x, y0, sz, alphaOf(fg, 0.8f), italic)
                    } else {
                        val fm = sepPaint.fontMetrics
                        c.drawText(":", x, y0 + (sz - (fm.descent - fm.ascent)) / 2f - fm.ascent, sepPaint)
                    }
                    x += sepW + gap
                } else {
                    x += gap
                }
            }
        }
        c.restore()
    }

    /* ---------------- photo ---------------- */
    private fun drawPhoto(ctx: Context, c: Canvas, cfg: WidgetConfig, aw: Int, ah: Int, d: Float) {
        val wDp = cfg.int("width", 260)
        val hDp = cfg.int("height", 260)
        val shadow = cfg.bool("shadow", true)
        val margin = if (shadow) 6f * d else 0f
                val s = 1f
                val cw = aw - 2 * margin
                val chh = ah - 2 * margin
        val left = (aw - cw) / 2f
        val top = (ah - chh) / 2f
        val rect = RectF(left, top, left + cw, top + chh)
        val rad = cfg.int("radius", 28) * d * s
        val pad = cfg.int("pad", 0) * d * s
        val opacity = cfg.int("opacity", 100) / 100f

        val outer = c.saveLayerAlpha(0f, 0f, aw.toFloat(), ah.toFloat(), (opacity * 255).roundToInt())
        if (pad > 0f || shadow) {
                    val bgP = Paint(Paint.ANTI_ALIAS_FLAG)
                    bgP.color = if (pad > 0f) col(cfg.str("bg", "#ffffff")) else Color.BLACK
                    if (shadow) bgP.setShadowLayer(12f * d, 0f, 6f * d, Color.argb(89, 0, 0, 0))
                    c.drawRoundRect(rect, rad, rad, bgP)
                    if (pad <= 0f) {
                        // keep only the shadow, no black fill behind the photo
                        val clr = Paint(Paint.ANTI_ALIAS_FLAG)
                        clr.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                        c.drawRoundRect(rect, rad, rad, clr)
                    }
                }

        val photos = cfg.photos
        val idx = if (photos.isEmpty()) 0 else cfg.int("idx", 0).coerceIn(0, photos.size - 1)
        val inner = RectF(rect.left + pad, rect.top + pad, rect.right - pad, rect.bottom - pad)
        val innerR = max(0f, rad - pad)
        val bmp = if (photos.isEmpty()) null else PhotoStorage.load(photos[idx].path, inner.width().toInt(), inner.height().toInt())

        if (bmp == null) {
            val t = TextPaint(Paint.ANTI_ALIAS_FLAG)
            t.color = Color.argb(217, 255, 255, 255)
            t.textAlign = Paint.Align.CENTER
            t.isFakeBoldText = true
            t.textSize = 14f * d * s
            c.drawText("No photos yet", rect.centerX(), rect.centerY(), t)
            t.isFakeBoldText = false
            t.textSize = 13f * d * s
            c.drawText("Add photos in the editor", rect.centerX(), rect.centerY() + 20f * d * s, t)
        } else {
            val bw = bmp.width.toFloat()
            val bh = bmp.height.toFloat()
            val dst = when (cfg.str("fit", "cover")) {
                "contain" -> {
                    val k = min(inner.width() / bw, inner.height() / bh)
                    RectF(inner.centerX() - bw * k / 2, inner.centerY() - bh * k / 2, inner.centerX() + bw * k / 2, inner.centerY() + bh * k / 2)
                }
                "fill" -> RectF(inner)
                else -> {
                    val k = max(inner.width() / bw, inner.height() / bh)
                    RectF(inner.centerX() - bw * k / 2, inner.centerY() - bh * k / 2, inner.centerX() + bw * k / 2, inner.centerY() + bh * k / 2)
                }
            }
            val b = cfg.int("brightness", 100) / 100f
            val g = cfg.int("gray", 0) / 100f
            val cm = ColorMatrix()
            cm.setSaturation(1f - g)
            cm.postConcat(ColorMatrix(floatArrayOf(
                b, 0f, 0f, 0f, 0f,
                0f, b, 0f, 0f, 0f,
                0f, 0f, b, 0f, 0f,
                0f, 0f, 0f, 1f, 0f)))
            val pp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            pp.colorFilter = ColorMatrixColorFilter(cm)
            val layer = c.saveLayer(inner, null)
            c.drawBitmap(bmp, null, dst, pp)
            val mask = Paint(Paint.ANTI_ALIAS_FLAG)
            mask.color = Color.BLACK
            mask.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            c.drawRoundRect(inner, innerR, innerR, mask)
            c.restoreToCount(layer)
            bmp.recycle()
        }
        c.restoreToCount(outer)
    }

}
