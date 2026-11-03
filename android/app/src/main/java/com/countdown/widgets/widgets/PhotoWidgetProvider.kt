package com.countdown.widgets.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.countdown.widgets.data.WidgetStore
import com.countdown.widgets.work.Ticker

class PhotoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { WidgetActions.update(context, mgr, it) }
        Ticker.schedule(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        WidgetActions.update(context, mgr, id)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { WidgetStore.unbind(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val act = intent.action ?: return
        if (act == WidgetActions.ACT_NEXT || act == WidgetActions.ACT_PREV || act == WidgetActions.ACT_PAUSE) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) WidgetActions.photoAction(context, id, act)
        }
    }

}
