package com.countdown.widgets.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.View
import android.widget.RemoteViews
import com.countdown.widgets.R
import com.countdown.widgets.config.WidgetConfigActivity
import com.countdown.widgets.data.WidgetConfig
import com.countdown.widgets.data.WidgetStore
import com.countdown.widgets.render.WidgetRenderer
import com.countdown.widgets.viewer.PhotoViewerActivity
import com.countdown.widgets.work.ReminderScheduler
import com.countdown.widgets.work.Ticker
import kotlin.random.Random

/** Shared logic for all three providers: rendering into RemoteViews and tap handling. */
object WidgetActions {
    const val ACT_NEXT = "com.countdown.widgets.action.NEXT"
    const val ACT_PREV = "com.countdown.widgets.action.PREV"
    const val ACT_PAUSE = "com.countdown.widgets.action.PAUSE"

    private val PROVIDERS = listOf(
        TextWidgetProvider::class.java, CountdownWidgetProvider::class.java, PhotoWidgetProvider::class.java,
                TasksWidgetProvider::class.java, ClockWidgetProvider::class.java)

    fun allIds(ctx: Context): List<Int> {
        val m = AppWidgetManager.getInstance(ctx)
        return PROVIDERS.flatMap { m.getAppWidgetIds(ComponentName(ctx, it)).toList() }
    }

    fun providerFor(type: String): Class<*> = when (type) {
        "photo" -> PhotoWidgetProvider::class.java
        "count" -> CountdownWidgetProvider::class.java
                "tasks" -> TasksWidgetProvider::class.java
                        "clock" -> ClockWidgetProvider::class.java
        else -> TextWidgetProvider::class.java
    }

    fun typeOf(ctx: Context, mgr: AppWidgetManager, id: Int): String {
        val cn = mgr.getAppWidgetInfo(id)?.provider?.className ?: return "text"
        return when {
            cn.endsWith("PhotoWidgetProvider") -> "photo"
            cn.endsWith("CountdownWidgetProvider") -> "count"
                        cn.endsWith("TasksWidgetProvider") -> "tasks"
                                    cn.endsWith("ClockWidgetProvider") -> "clock"
            else -> "text"
        }
    }

    private fun areaPx(ctx: Context, mgr: AppWidgetManager, id: Int): Pair<Int, Int> {
        val o = mgr.getAppWidgetOptions(id)
        val portrait = ctx.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val wDp = o.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
        val hDp = o.getInt(if (portrait) AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val d = ctx.resources.displayMetrics.density
        return Pair(((if (wDp > 0) wDp else 180) * d).toInt(), ((if (hDp > 0) hDp else 110) * d).toInt())
    }

    fun editIntent(ctx: Context, id: Int): PendingIntent {
        val i = Intent(ctx, WidgetConfigActivity::class.java)
            .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(ctx, id * 10, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun actionIntent(ctx: Context, id: Int, act: String, slot: Int, configId: Int): PendingIntent? {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return when (act) {
            "edit" -> editIntent(ctx, id)
            "view" -> PendingIntent.getActivity(ctx, id * 10 + slot + 1,
                Intent(ctx, PhotoViewerActivity::class.java).putExtra("widgetId", configId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
            "next", "prev", "pause" -> {
                val a = when (act) { "next" -> ACT_NEXT; "prev" -> ACT_PREV; else -> ACT_PAUSE }
                PendingIntent.getBroadcast(ctx, id * 10 + slot + 1,
                    Intent(ctx, PhotoWidgetProvider::class.java).setAction(a)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id), flags)
            }
            else -> null
        }
    }

    fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
        try {
            val type = typeOf(ctx, mgr, id)
            val cfg = WidgetStore.forAppWidget(ctx, id)
            val (aw, ah) = areaPx(ctx, mgr, id)
            val bmp = WidgetRenderer.render(ctx, cfg, aw, ah)
            val layout = when (type) {
                "photo" -> R.layout.widget_photo
                "count" -> R.layout.widget_countdown
                else -> R.layout.widget_text
            }
            val rv = RemoteViews(ctx.packageName, layout)
            rv.setImageViewBitmap(R.id.img, bmp)
            if (type == "photo") {
                val tap = cfg?.str("tap", "zones") ?: "edit"
                val acts = when (tap) {
                    "zones" -> listOf("prev", "edit", "next")
                    "next", "prev", "pause", "view", "edit" -> listOf(tap, tap, tap)
                    else -> listOf("none", "none", "none")
                }
                val zones = intArrayOf(R.id.zone_l, R.id.zone_c, R.id.zone_r)
                for (i in 0..2) {
                    val pi = actionIntent(ctx, id, acts[i], i, cfg?.id ?: 0)
                    if (pi != null) rv.setOnClickPendingIntent(zones[i], pi)
                }
                if (cfg == null) rv.setOnClickPendingIntent(R.id.zone_c, editIntent(ctx, id))
            } else if (type == "tasks" && cfg != null) {
                            bindTaskZones(ctx, rv, id, cfg, ah)
                        } else {
                            rv.setOnClickPendingIntent(R.id.root, editIntent(ctx, id))
            }
            mgr.updateAppWidget(id, rv)
        } catch (e: Throwable) {
            // Never crash the launcher callback because of one bad widget.
        }
    }

    const val ACT_TOGGLE = "com.countdown.widgets.action.TOGGLE"

        /** Places one invisible tap zone over every drawn task row (heights set through padding). */
        private fun bindTaskZones(ctx: Context, rv: RemoteViews, id: Int, cfg: WidgetConfig, ah: Int) {
            val zones = intArrayOf(
                R.id.tz0, R.id.tz1, R.id.tz2, R.id.tz3, R.id.tz4, R.id.tz5, R.id.tz6, R.id.tz7,
                R.id.tz8, R.id.tz9, R.id.tz10, R.id.tz11, R.id.tz12, R.id.tz13, R.id.tz14, R.id.tz15,
                R.id.tz16, R.id.tz17, R.id.tz18, R.id.tz19, R.id.tz20, R.id.tz21, R.id.tz22, R.id.tz23
            )
            val g = WidgetRenderer.taskGeo(cfg, ah, ctx.resources.displayMetrics.density)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val edit = editIntent(ctx, id)
            rv.setViewPadding(R.id.tz_top, 0, g.firstTop.toInt(), 0, 0)
            rv.setOnClickPendingIntent(R.id.tz_top, edit)
            rv.setOnClickPendingIntent(R.id.tz_bot, edit)
            for (i in zones.indices) {
                val r = g.rows.getOrNull(i)
                if (r == null) {
                    rv.setViewVisibility(zones[i], View.GONE)
                    continue
                }
                rv.setViewVisibility(zones[i], View.VISIBLE)
                rv.setViewPadding(zones[i], 0, r.h.toInt(), 0, 0)
                val pi = PendingIntent.getBroadcast(ctx, 1_000_000 + id * 100 + i,
                    Intent(ctx, TasksWidgetProvider::class.java).setAction(ACT_TOGGLE)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        .putExtra("task", r.index), flags)
                rv.setOnClickPendingIntent(zones[i], pi)
            }
        }

        /** Ticks / unticks one task from the home screen. */
        fun toggleTask(ctx: Context, id: Int, index: Int) {
            val cfg = WidgetStore.forAppWidget(ctx, id) ?: return
            val a = cfg.raw.optJSONArray("items") ?: return
            val o = a.optJSONObject(index) ?: return
            o.put("d", !o.optBoolean("d", false))
            WidgetStore.save(ctx, cfg)
            ReminderScheduler.scheduleAll(ctx)
            update(ctx, AppWidgetManager.getInstance(ctx), id)
        }

        fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        allIds(ctx).forEach { update(ctx, mgr, it) }
    }

    /** Port of photoStep() from the editor. Mutates cfg (idx, last); caller saves. */
    fun photoStep(cfg: WidgetConfig, dir: Int): Boolean {
        val n = cfg.photos.size
        if (n < 2) return false
        val cur = cfg.int("idx", 0)
        var i = cur
        if (cfg.bool("shuffle")) {
            do { i = Random.nextInt(n) } while (i == cur)
        } else {
            i += dir
            if (i >= n || i < 0) {
                if (!cfg.bool("loop", true)) return false
                i = (i + n) % n
            }
        }
        cfg.raw.put("idx", i)
        cfg.raw.put("last", System.currentTimeMillis())
        return true
    }

    fun photoAction(ctx: Context, id: Int, act: String) {
        val cfg = WidgetStore.forAppWidget(ctx, id) ?: return
        when (act) {
            ACT_NEXT -> photoStep(cfg, 1)
            ACT_PREV -> photoStep(cfg, -1)
            ACT_PAUSE -> {
                cfg.raw.put("paused", !cfg.bool("paused"))
                cfg.raw.put("last", System.currentTimeMillis())
            }
        }
        WidgetStore.save(ctx, cfg)
        update(ctx, AppWidgetManager.getInstance(ctx), id)
        Ticker.schedule(ctx)
    }

}
