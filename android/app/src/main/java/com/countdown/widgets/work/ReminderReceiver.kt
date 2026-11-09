package com.countdown.widgets.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives the reminder alarm and posts whatever is due. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pr = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                ReminderScheduler.fire(app)
            } catch (e: Throwable) {
            } finally {
                pr.finish()
            }
        }.start()
    }
}
