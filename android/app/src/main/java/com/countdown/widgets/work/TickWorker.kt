package com.countdown.widgets.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.countdown.widgets.data.WidgetConfig
import com.countdown.widgets.data.WidgetStore
import com.countdown.widgets.widgets.WidgetActions
import java.util.concurrent.TimeUnit

/**
 * Keeps widgets fresh. Android cannot redraw widgets every second, so:
 * - countdowns refresh once per minute (inexact alarm chain, runs while the device is awake)
 * - photo widgets advance on their interval (clamped to >= 15 s by the OS in practice)
 * - a 15 minute WorkManager job is the safety net
 */
object Ticker {
    const val ACTION = "com.countdown.widgets.action.TICK"

    private fun due(cfg: WidgetConfig, now: Long): Boolean =
        cfg.bool("auto", true) && !cfg.bool("paused") && cfg.photos.size > 1 &&
            now - cfg.long("last", 0L) >= cfg.int("interval", 5) * 1000L - 1500L

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        var next = Long.MAX_VALUE
        for (id in WidgetActions.allIds(ctx)) {
            val cfg = WidgetStore.forAppWidget(ctx, id) ?: continue
            when (cfg.type) {
                "count" -> next = minOf(next, (now / 60_000L + 1) * 60_000L)
                "photo" -> if (cfg.bool("auto", true) && !cfg.bool("paused") && cfg.photos.size > 1) {
                    next = minOf(next, maxOf(cfg.long("last", 0L) + cfg.int("interval", 5) * 1000L, now + 15_000L))
                }
            }
        }
        val pi = PendingIntent.getBroadcast(ctx, 0,
            Intent(ctx, BootReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (next == Long.MAX_VALUE) am.cancel(pi) else am.setAndAllowWhileIdle(AlarmManager.RTC, next, pi)
    }

    fun tick(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val now = System.currentTimeMillis()
        for (id in WidgetActions.allIds(ctx)) {
            val cfg = WidgetStore.forAppWidget(ctx, id) ?: continue
            if (cfg.type == "count") {
                WidgetActions.update(ctx, mgr, id)
            } else if (cfg.type == "photo" && due(cfg, now)) {
                if (WidgetActions.photoStep(cfg, 1)) {
                    WidgetStore.save(ctx, cfg)
                    WidgetActions.update(ctx, mgr, id)
                } else {
                    cfg.raw.put("last", now)
                    WidgetStore.save(ctx, cfg)
                }
            }
        }
        schedule(ctx)
    }

    fun ensureWork(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<TickWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("widget_tick", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

class TickWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        Ticker.tick(applicationContext)
        return Result.success()
    }
}
