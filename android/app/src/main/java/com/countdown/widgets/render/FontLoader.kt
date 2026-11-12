package com.countdown.widgets.render

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import java.io.File

/**
 * Loads fonts from res/font by name. Missing font files fall back to a system family,
 * so the app still builds and runs before the .ttf files are added.
 */
object FontLoader {
    private val RES = mapOf(
        "Instrument Sans" to "instrument_sans", "Fraunces" to "fraunces", "Playfair Display" to "playfair_display",
        "Lora" to "lora", "Caveat" to "caveat", "Bebas Neue" to "bebas_neue", "Space Mono" to "space_mono",
        "Orbitron" to "orbitron", "VT323" to "vt323", "Share Tech Mono" to "share_tech_mono",
        "Audiowide" to "audiowide", "Oxanium" to "oxanium", "Geist Mono" to "geist_mono",
                "Inter" to "inter", "Poppins" to "poppins", "Montserrat" to "montserrat", "Roboto" to "roboto",
                "Nunito" to "nunito", "DM Sans" to "dm_sans", "Manrope" to "manrope", "Oswald" to "oswald",
                "Merriweather" to "merriweather", "JetBrains Mono" to "jetbrains_mono", "Roboto Mono" to "roboto_mono",
                "Rajdhani" to "rajdhani"
    )
    private val cache = HashMap<String, Typeface>()
        private val vcache = HashMap<String, Typeface>()

        /** Variable fonts: apply the wght axis directly so every slider weight renders like the editor. */
        @Synchronized
        private fun variable(ctx: Context, name: String, weight: Int, italic: Boolean): Typeface? {
            if (Build.VERSION.SDK_INT < 26) return null
            val res = RES[name] ?: return null
            val key = name + "@" + weight + (if (italic) "i" else "")
            vcache[key]?.let { return it }
            return try {
                val id = ctx.resources.getIdentifier(res, "font", ctx.packageName)
                if (id == 0) return null
                val f = File(ctx.cacheDir, "font_$res.ttf")
                if (!f.exists() || f.length() == 0L) {
                    ctx.resources.openRawResource(id).use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                }
                val w = weight.coerceIn(1, 1000)
                val tf = Typeface.Builder(f)
                    .setFontVariationSettings("'wght' $w")
                    .setWeight(w)
                    .setItalic(italic)
                    .build()
                if (tf != null) vcache[key] = tf
                tf
            } catch (e: Throwable) {
                null
            }
        }

    private fun fallback(name: String): Typeface = when (name) {
        "Fraunces", "Playfair Display", "Lora", "Merriweather" -> Typeface.SERIF
        "Caveat" -> Typeface.create("cursive", Typeface.NORMAL)
        "Space Mono", "VT323", "Share Tech Mono", "Geist Mono", "JetBrains Mono", "Roboto Mono" -> Typeface.MONOSPACE
        else -> Typeface.SANS_SERIF
    }

    @Synchronized
    private fun base(ctx: Context, name: String): Typeface = cache.getOrPut(name) {
        val id = ctx.resources.getIdentifier(RES[name] ?: "", "font", ctx.packageName)
        val tf = if (id != 0) try { ResourcesCompat.getFont(ctx, id) } catch (e: Exception) { null } else null
        tf ?: fallback(name)
    }

    fun get(ctx: Context, name: String, weight: Int, italic: Boolean): Typeface {
        val b = base(ctx, name)
                variable(ctx, name, weight, italic)?.let { return it }
                return if (Build.VERSION.SDK_INT >= 28) {
            Typeface.create(b, weight.coerceIn(1, 1000), italic)
        } else {
            val bold = weight >= 600
            Typeface.create(b, when {
                bold && italic -> Typeface.BOLD_ITALIC
                bold -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            })
        }
    }
}
