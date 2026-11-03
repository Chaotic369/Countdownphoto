package com.countdown.widgets.render

import com.countdown.widgets.data.WidgetConfig
import java.time.Duration
import java.time.LocalDateTime

/** Port of breakdown()/addM() from the editor. plusMonths clamps the day like the JS version. */
object CountdownMath {
    val ORDER = listOf("years", "months", "weeks", "days", "hours", "minutes", "seconds")

    fun parse(s: String): LocalDateTime? = try {
        LocalDateTime.parse(s.take(16))
    } catch (e: Exception) {
        null
    }

    fun breakdown(cfg: WidgetConfig, now: LocalDateTime = LocalDateTime.now()): Map<String, Long> {
        val out = linkedMapOf<String, Long>()
        ORDER.forEach { out[it] = 0L }
        val t = parse(cfg.str("target")) ?: return out
        if (!t.isAfter(now)) return out
        var c = now
        if (cfg.unit("years")) {
            var y = 0
            while (y < 200 && !c.plusMonths(12L * (y + 1)).isAfter(t)) y++
            out["years"] = y.toLong()
            c = c.plusMonths(12L * y)
        }
        if (cfg.unit("months")) {
            var m = 0
            while (m < 2400 && !c.plusMonths(m + 1L).isAfter(t)) m++
            out["months"] = m.toLong()
            c = c.plusMonths(m.toLong())
        }
        var ms = Duration.between(c, t).toMillis()
        val spans = listOf("weeks" to 604_800_000L, "days" to 86_400_000L, "hours" to 3_600_000L,
            "minutes" to 60_000L, "seconds" to 1_000L)
        for ((k, span) in spans) {
            if (cfg.unit(k)) {
                val v = ms / span
                out[k] = v
                ms -= v * span
            }
        }
        return out
    }

}
