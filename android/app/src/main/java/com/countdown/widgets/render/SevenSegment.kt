package com.countdown.widgets.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

/** Seven-segment digits drawn on the same 60x100 grid as the editor's SVG. */
object SevenSegment {
    const val ASPECT = 0.6f

    private val SEG = mapOf(
        'a' to "17,10 22,5 38,5 43,10 38,15 22,15",
        'b' to "50,12 55,17 55,43 50,48 45,43 45,17",
        'c' to "50,52 55,57 55,83 50,88 45,83 45,57",
        'd' to "17,90 22,85 38,85 43,90 38,95 22,95",
        'e' to "10,52 15,57 15,83 10,88 5,83 5,57",
        'f' to "10,12 15,17 15,43 10,48 5,43 5,17",
        'g' to "17,50 22,45 38,45 43,50 38,55 22,55"
    )
    private val DIG = mapOf(
        '0' to "abcdef", '1' to "bc", '2' to "abdeg", '3' to "abcdg", '4' to "bcfg",
        '5' to "acdfg", '6' to "acdefg", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg"
    )
    private const val SKEW = -0.158f // tan(-9 degrees)

    private val paths: Map<Char, Path> by lazy {
        SEG.mapValues { e ->
            val p = Path()
            e.value.split(" ").forEachIndexed { i, pt ->
                val xy = pt.split(",")
                val x = xy[0].toFloat()
                val y = xy[1].toFloat()
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            p
        }
    }

    fun drawDigit(c: Canvas, ch: Char, x: Float, y: Float, h: Float, color: Int, italic: Boolean) {
        val on = DIG[ch] ?: ""
        val off = Paint(Paint.ANTI_ALIAS_FLAG)
        off.color = color
        off.alpha = 26
        val lit = Paint(Paint.ANTI_ALIAS_FLAG)
        lit.color = color
        c.save()
        c.translate(x, y)
        if (italic) {
            c.translate(h * ASPECT / 2f, h / 2f)
            c.skew(SKEW, 0f)
            c.translate(-h * ASPECT / 2f, -h / 2f)
        }
        c.scale(h / 100f, h / 100f)
        for ((_, p) in paths) c.drawPath(p, off)
        for ((k, p) in paths) if (on.indexOf(k) >= 0) c.drawPath(p, lit)
        c.restore()
    }

    fun drawColon(c: Canvas, x: Float, y: Float, h: Float, color: Int, italic: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        c.save()
        c.translate(x, y)
        if (italic) {
            c.translate(h * 0.1f, h / 2f)
            c.skew(SKEW, 0f)
            c.translate(-h * 0.1f, -h / 2f)
        }
        c.scale(h / 100f, h / 100f)
        c.drawCircle(10f, 33f, 6f, p)
        c.drawCircle(10f, 67f, 6f, p)
        c.restore()
    }

}
