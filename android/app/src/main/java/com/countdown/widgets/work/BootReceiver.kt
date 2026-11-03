package com.countdown.widgets.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.countdown.widgets.widgets.WidgetActions

/** Re-arms updates after boot or app update, and handles the TICK alarm. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Ticker.ensureWork(context)
                WidgetActions.updateAll(context)
                Ticker.schedule(context)
            }
            Ticker.ACTION -> Ticker.tick(context)
        }
    }
}
