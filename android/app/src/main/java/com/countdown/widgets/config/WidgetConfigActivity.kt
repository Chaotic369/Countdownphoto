package com.countdown.widgets.config

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import com.countdown.widgets.WidgetBridgePlugin
import com.countdown.widgets.data.WidgetStore
import com.countdown.widgets.widgets.WidgetActions
import com.countdown.widgets.work.Ticker
import com.getcapacitor.BridgeActivity

/**
 * Widget settings screen. Shown by the launcher when a widget is added, and opened again when the
 * widget center is tapped. It hosts the same web editor; the plugin tells it which widget to edit.
 */
class WidgetConfigActivity : BridgeActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Cancelled unless the user presses Done.
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        registerPlugin(WidgetBridgePlugin::class.java)
        super.onCreate(savedInstanceState)

        // "Add to home screen" from the app: bind the chosen config and skip the settings screen.
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val pin = WidgetStore.takePendingPin(this)
            if (pin > 0 && WidgetStore.get(this, pin) != null) {
                WidgetStore.bind(this, pin, appWidgetId)
                finishOk()
            }
        }
    }

    fun finishOk() {
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            WidgetActions.update(this, AppWidgetManager.getInstance(this), appWidgetId)
            Ticker.schedule(this)
            Ticker.ensureWork(this)
        }
        finish()
    }
}
