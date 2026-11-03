package com.countdown.widgets.data

import android.content.Context

/** Stores one JSON string per widget config (key "w_<id>"). A config is bound to at most one appWidgetId. */
object WidgetStore {
    private const val PREFS = "widget_store"
    private const val PREFIX = "w_"
    private const val PIN = "pending_pin"

    private fun p(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(ctx: Context): List<WidgetConfig> =
        p(ctx).all.entries
            .filter { it.key.startsWith(PREFIX) }
            .mapNotNull { (it.value as? String)?.let { s -> WidgetConfig.parse(s) } }
            .sortedBy { it.id }

    fun get(ctx: Context, id: Int): WidgetConfig? =
        p(ctx).getString(PREFIX + id, null)?.let { WidgetConfig.parse(it) }

    /** Called by the editor. Keeps the native-owned appWidgetId if the editor copy has none. */
    @Synchronized
    fun put(ctx: Context, json: String): WidgetConfig? {
        val cfg = WidgetConfig.parse(json) ?: return null
        if (cfg.id <= 0) return null
        val old = get(ctx, cfg.id)
        if (cfg.appWidgetId == 0 && old != null && old.appWidgetId != 0) {
            cfg.raw.put("appWidgetId", old.appWidgetId)
        }
        if (cfg.appWidgetId != 0) clearBinding(ctx, cfg.appWidgetId, cfg.id)
        save(ctx, cfg)
        return cfg
    }

    @Synchronized
    fun save(ctx: Context, cfg: WidgetConfig) {
        p(ctx).edit().putString(PREFIX + cfg.id, cfg.raw.toString()).apply()
    }

    fun delete(ctx: Context, id: Int) {
        p(ctx).edit().remove(PREFIX + id).apply()
    }

    fun forAppWidget(ctx: Context, appWidgetId: Int): WidgetConfig? =
        all(ctx).firstOrNull { it.appWidgetId == appWidgetId }

    private fun clearBinding(ctx: Context, appWidgetId: Int, exceptId: Int) {
        all(ctx).filter { it.appWidgetId == appWidgetId && it.id != exceptId }.forEach {
            it.raw.put("appWidgetId", 0)
            save(ctx, it)
        }
    }

    fun bind(ctx: Context, id: Int, appWidgetId: Int) {
        val cfg = get(ctx, id) ?: return
        clearBinding(ctx, appWidgetId, id)
        cfg.raw.put("appWidgetId", appWidgetId)
        save(ctx, cfg)
    }

    fun unbind(ctx: Context, appWidgetId: Int) {
        forAppWidget(ctx, appWidgetId)?.let {
            it.raw.put("appWidgetId", 0)
            save(ctx, it)
        }
    }

    fun setPendingPin(ctx: Context, id: Int) {
        p(ctx).edit().putInt(PIN, id).apply()
    }

    fun takePendingPin(ctx: Context): Int {
        val v = p(ctx).getInt(PIN, 0)
        if (v != 0) p(ctx).edit().remove(PIN).apply()
        return v
    }

}
