package com.countdown.widgets.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
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

    private var screenRx: BroadcastReceiver? = null

        /** Re-arms the 1 s chain as soon as the screen turns on. */
        private fun watchScreen(ctx: Context) {
            if (screenRx != null) return
            try {
                val r = object : BroadcastReceiver() {
                    override fun onReceive(c: Context, i: Intent) { tick(c) }
                }
                ctx.applicationContext.registerReceiver(r, IntentFilter(Intent.ACTION_SCREEN_ON))
                screenRx = r
            } catch (e: Throwable) {
            }
        }

        fun schedule(ctx: Context) {
            watchScreen(ctx)
                    try {
                        if (!SecondsService.running && WidgetActions.allIds(ctx).any { id ->
                                WidgetStore.forAppWidget(ctx, id)?.let { it.type == "count" && it.unit("seconds") } == true }) {
                            val si = Intent(ctx, SecondsService::class.java)
                            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(si) else ctx.startService(si)
                        }
                    } catch (e: Throwable) {
                    }
                    val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val now = System.currentTimeMillis()
            val awake = try { (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive } catch (e: Throwable) { true }
            val minGap = if (awake) 1_000L else 60_000L
            var next = Long.MAX_VALUE
        for (id in WidgetActions.allIds(ctx)) {
            val cfg = WidgetStore.forAppWidget(ctx, id) ?: continue
            when (cfg.type) {
                "count" -> next = minOf(next,
                                    if (awake && cfg.unit("seconds") && !SecondsService.running) maxOf((now / 1_000L + 1) * 1_000L, now + 400L)
                                    else (now / 60_000L + 1) * 60_000L)
                "photo" -> if (cfg.bool("auto", true) && !cfg.bool("paused") && cfg.photos.size > 1) {
                    next = minOf(next, maxOf(cfg.long("last", 0L) + cfg.int("interval", 5) * 1000L, now + minGap))
                }
            }
        }
        val pi = PendingIntent.getBroadcast(ctx, 0,
            Intent(ctx, BootReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (next == Long.MAX_VALUE) {
                    am.cancel(pi)
                } else {
                    try {
                        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                            am.setExactAndAllowWhileIdle(AlarmManager.RTC, next, pi)
                        } else {
                            am.setAndAllowWhileIdle(AlarmManager.RTC, next, pi)
                        }
                    } catch (e: Throwable) {
                        am.setAndAllowWhileIdle(AlarmManager.RTC, next, pi)
                    }
                }
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

/** Redraws countdown widgets once per second while the screen is on. */
class SecondsService : Service() {
    companion object {
        @Volatile var running = false
    }

    private var thread: HandlerThread? = null
    private var h: Handler? = null
    private var rx: BroadcastReceiver? = null
    @Volatile private var screenOn = true

    private val loop = object : Runnable {
        override fun run() {
            if (!screenOn) return
            try {
                val ctx = this@SecondsService
                val mgr = AppWidgetManager.getInstance(ctx)
                var any = false
                for (id in WidgetActions.allIds(ctx)) {
                    val cfg = WidgetStore.forAppWidget(ctx, id) ?: continue
                    if (cfg.type == "count" && cfg.unit("seconds")) {
                        any = true
                        WidgetActions.update(ctx, mgr, id)
                    }
                }
                if (!any) { stopSelf(); return }
            } catch (e: Throwable) {
            }
            h?.postDelayed(this, 1000L - (System.currentTimeMillis() % 1000L) + 5L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        val t = HandlerThread("seconds-tick")
        t.start()
        thread = t
        h = Handler(t.looper)
        screenOn = try { (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive } catch (e: Throwable) { true }
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action == Intent.ACTION_SCREEN_OFF) {
                    screenOn = false
                    h?.removeCallbacks(loop)
                } else if (i.action == Intent.ACTION_SCREEN_ON) {
                    screenOn = true
                    h?.removeCallbacks(loop)
                    h?.post(loop)
                }
            }
        }
        val f = IntentFilter()
        f.addAction(Intent.ACTION_SCREEN_ON)
        f.addAction(Intent.ACTION_SCREEN_OFF)
        registerReceiver(r, f)
        rx = r
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(NotificationChannel("seconds", "Live countdown", NotificationManager.IMPORTANCE_MIN))
            }
            val n = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "seconds") else Notification.Builder(this))
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Countdown is running")
                .setOngoing(true)
                .build()
            if (Build.VERSION.SDK_INT >= 34) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(7, n)
        } catch (e: Throwable) {
            stopSelf()
            return START_NOT_STICKY
        }
        h?.removeCallbacks(loop)
        if (screenOn) h?.post(loop)
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        try { rx?.let { unregisterReceiver(it) } } catch (e: Throwable) { }
        h?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        super.onDestroy()
    }
}

class TickWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        Ticker.tick(applicationContext)
                ReminderScheduler.scheduleAll(applicationContext)
        return Result.success()
    }
}
