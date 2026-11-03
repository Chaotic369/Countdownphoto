package com.countdown.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.util.Base64
import com.countdown.widgets.config.WidgetConfigActivity
import com.countdown.widgets.data.PhotoStorage
import com.countdown.widgets.data.WidgetStore
import com.countdown.widgets.widgets.WidgetActions
import com.countdown.widgets.work.Ticker
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "WidgetBridge")
class WidgetBridgePlugin : Plugin() {

    @PluginMethod
    fun `init`(call: PluginCall) {
        val ctx = context
        val arr = JSArray()
        WidgetStore.all(ctx).forEach { arr.put(it.raw.toString()) }
        val intent = activity.intent
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val c = JSObject()
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_CONFIGURE && id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            c.put("mode", "config")
            c.put("appWidgetId", id)
            c.put("type", WidgetActions.typeOf(ctx, AppWidgetManager.getInstance(ctx), id))
        } else {
            c.put("mode", "app")
            c.put("widgetId", intent.getIntExtra("widgetId", 0))
        }
        val res = JSObject()
        res.put("widgets", arr)
        res.put("ctx", c)
        call.resolve(res)
    }

    @PluginMethod
    fun saveWidget(call: PluginCall) {
        val json = call.getString("config")
        if (json == null) { call.reject("config missing"); return }
        val cfg = WidgetStore.put(context, json)
        if (cfg == null) { call.reject("invalid config"); return }
        val bound = WidgetStore.get(context, cfg.id)?.appWidgetId ?: 0
        if (bound != 0) {
            WidgetActions.update(context, AppWidgetManager.getInstance(context), bound)
            Ticker.schedule(context)
        }
        call.resolve()
    }

    @PluginMethod
    fun deleteWidget(call: PluginCall) {
        val id = call.getInt("id") ?: 0
        val old = WidgetStore.get(context, id)
        PhotoStorage.deleteAll(context, id)
        WidgetStore.delete(context, id)
        if (old != null && old.appWidgetId != 0) {
            WidgetActions.update(context, AppWidgetManager.getInstance(context), old.appWidgetId)
        }
        call.resolve()
    }

    @PluginMethod
    fun savePhoto(call: PluginCall) {
        val widgetId = call.getInt("widgetId") ?: 0
        val b64 = call.getString("base64")
        if (b64 == null || widgetId <= 0) { call.reject("bad args"); return }
        try {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            val path = PhotoStorage.save(context, widgetId, bytes)
            if (path == null) { call.reject("decode failed"); return }
            val r = JSObject()
            r.put("path", path)
            call.resolve(r)
        } catch (e: Exception) {
            call.reject("save failed")
        }
    }

    @PluginMethod
    fun deletePhoto(call: PluginCall) {
        PhotoStorage.delete(context, call.getString("path") ?: "")
        call.resolve()
    }

    @PluginMethod
    fun pinWidget(call: PluginCall) {
        val type = call.getString("type") ?: "text"
        val id = call.getInt("widgetId") ?: 0
        val mgr = AppWidgetManager.getInstance(context)
        if (!mgr.isRequestPinAppWidgetSupported) { call.reject("Launcher does not support pinning"); return }
        WidgetStore.setPendingPin(context, id)
        mgr.requestPinAppWidget(ComponentName(context, WidgetActions.providerFor(type)), null, null)
        call.resolve()
    }

    @PluginMethod
    fun finishConfig(call: PluginCall) {
        val a = activity
        if (a is WidgetConfigActivity) a.runOnUiThread { a.finishOk() }
        call.resolve()
    }

}
