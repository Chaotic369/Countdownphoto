package com.countdown.widgets.work

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import com.countdown.widgets.MainActivity
import com.countdown.widgets.data.TaskItem
import com.countdown.widgets.data.WidgetConfig
import com.countdown.widgets.data.WidgetStore
import com.countdown.widgets.widgets.WidgetActions
import java.util.Calendar

/**
 * Task reminders. One exact alarm is armed for the earliest pending reminder across all task widgets.
 * When it fires, ReminderReceiver calls fire(), which posts the notifications, records the fire time
 * ("rl" in the item JSON, same field the editor uses) and re-arms the next alarm.
 */
object ReminderScheduler {
    private const val REQ = 7001
    private const val LATE_GRACE = 120_000L

    private fun two(n: Int): String = if (n < 10) "0$n" else "$n"

    private fun todayStr(): String {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR).toString() + "-" + two(c.get(Calendar.MONTH) + 1) + "-" + two(c.get(Calendar.DAY_OF_MONTH))
    }

    /** Next occurrence (ms) of a reminder that has not fired yet, or 0. Mirrors tkAt() in the editor. */
    fun nextAt(t: TaskItem, now: Long): Long {
        val dp = (if (t.date.isNotEmpty()) t.date else todayStr()).split("-")
        val y = dp.getOrNull(0)?.toIntOrNull() ?: return 0L
        val m = dp.getOrNull(1)?.toIntOrNull() ?: return 0L
        val d = dp.getOrNull(2)?.toIntOrNull() ?: return 0L
        val tp = (if (t.time.isNotEmpty()) t.time else "09:00").split(":")
        val h = tp.getOrNull(0)?.toIntOrNull() ?: 9
        val mi = tp.getOrNull(1)?.toIntOrNull() ?: 0
        val lim = maxOf(t.lastFired, now - LATE_GRACE)
        val rep = t.rep
        var step = 0
        var guard = 0
        while (guard++ < 5000) {
            val c = Calendar.getInstance()
            c.set(
                y,
                m - 1 + (if (rep == "monthly") step else 0),
                d + (if (rep == "daily") step else if (rep == "weekly") step * 7 else 0),
                h, mi, 0
            )
            c.set(Calendar.MILLISECOND, 0)
            val at = c.timeInMillis
            step++
            if (rep != "daily" && rep != "weekly" && rep != "monthly") return if (at > lim) at else 0L
            if (at > lim) return at
        }
        return 0L
    }

    private fun leadMs(cfg: WidgetConfig): Long =
        (cfg.str("rLead", "0").toLongOrNull() ?: 0L) * 60_000L

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, REQ,
            Intent(ctx, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Arms (or cancels) the single alarm for the earliest pending reminder. */
    @Synchronized
    fun scheduleAll(ctx: Context) {
        try {
            val app = ctx.applicationContext
            val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val now = System.currentTimeMillis()
            var next = Long.MAX_VALUE
            for (cfg in WidgetStore.all(app)) {
                if (cfg.type != "tasks" || !cfg.bool("rNotify", true)) continue
                val lead = leadMs(cfg)
                for (t in cfg.tasks) {
                    if (!t.rem || t.done) continue
                    val at = nextAt(t, now)
                    if (at == 0L) continue
                    next = minOf(next, at - lead)
                }
            }
            val pi = pending(app)
            if (next == Long.MAX_VALUE) {
                am.cancel(pi)
                return
            }
            val whenMs = maxOf(next, now + 1_000L)
            try {
                if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
                }
            } catch (e: Throwable) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
            }
        } catch (e: Throwable) {
        }
    }

    /** Posts every reminder that is due now, records it as fired, then re-arms. */
    @Synchronized
    fun fire(ctx: Context) {
        val app = ctx.applicationContext
        try {
            val now = System.currentTimeMillis()
            val mgr = AppWidgetManager.getInstance(app)
            for (cfg in WidgetStore.all(app)) {
                if (cfg.type != "tasks" || !cfg.bool("rNotify", true)) continue
                val lead = leadMs(cfg)
                val items = cfg.raw.optJSONArray("items") ?: continue
                val tasks = cfg.tasks
                var changed = false
                for (i in tasks.indices) {
                    val t = tasks[i]
                    if (!t.rem || t.done) continue
                    val at = nextAt(t, now)
                    if (at == 0L || at - lead > now) continue
                    items.optJSONObject(i)?.put("rl", at)
                    changed = true
                    notifyTask(app, cfg, t, i)
                }
                if (changed) {
                    WidgetStore.save(app, cfg)
                    if (cfg.appWidgetId != 0) WidgetActions.update(app, mgr, cfg.appWidgetId)
                }
            }
        } catch (e: Throwable) {
        }
        scheduleAll(app)
    }

    private fun detail(t: TaskItem): String {
        val parts = ArrayList<String>()
        if (t.time.isNotEmpty()) {
            val p = t.time.split(":")
            val h = p.getOrNull(0)?.toIntOrNull()
            val m = p.getOrNull(1)?.toIntOrNull() ?: 0
            if (h != null) parts.add((if (h % 12 == 0) 12 else h % 12).toString() + ":" + two(m) + (if (h < 12) " AM" else " PM"))
        }
        if (t.rep.isNotEmpty() && t.rep != "none") parts.add(t.rep.replaceFirstChar { it.uppercase() })
        return if (parts.isEmpty()) "Reminder" else parts.joinToString(" · ")
    }

    private fun bellRes(ctx: Context): Int = ctx.resources.getIdentifier("tick_bell", "raw", ctx.packageName)

    private fun channelFor(ctx: Context, sound: Boolean, vib: Boolean): String {
        val bell = bellRes(ctx)
        val id = "rem_s" + (if (sound) "1" else "0") + "_v" + (if (vib) "1" else "0") + (if (bell != 0) "_b" else "")
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(id) == null) {
            val ch = NotificationChannel(id, "Task reminders", NotificationManager.IMPORTANCE_HIGH)
            if (sound) {
                val uri: Uri = if (bell != 0) Uri.parse("android.resource://" + ctx.packageName + "/" + bell)
                else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                ch.setSound(uri, attrs)
            } else {
                ch.setSound(null, null)
            }
            ch.enableVibration(vib)
            if (vib) ch.vibrationPattern = longArrayOf(0, 200, 100, 200)
            nm.createNotificationChannel(ch)
        }
        return id
    }

    private fun notifyTask(ctx: Context, cfg: WidgetConfig, t: TaskItem, index: Int) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.areNotificationsEnabled()) return
        val chId = channelFor(ctx, cfg.bool("rSound", true), cfg.bool("rVib", true))
        val open = PendingIntent.getActivity(
            ctx, REQ + 1,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, chId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (t.text.isEmpty()) "Reminder" else t.text)
            .setContentText(detail(t))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        val nid = if (t.id != 0L) (t.id xor (t.id ushr 32)).toInt() else cfg.id * 1000 + index
        nm.notify(nid, n)
    }
}
