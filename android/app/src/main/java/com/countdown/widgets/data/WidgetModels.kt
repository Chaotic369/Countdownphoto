package com.countdown.widgets.data

import org.json.JSONObject

data class Photo(val path: String, val name: String)

data class TaskItem(val text: String, val done: Boolean)

/** Thin wrapper over the JSON the editor saves. Field names match www/index.html. */
class WidgetConfig(val raw: JSONObject) {
    val id: Int get() = raw.optInt("id", 0)
    val type: String get() = raw.optString("type", "text")
    val appWidgetId: Int get() = raw.optInt("appWidgetId", 0)

    fun str(k: String, d: String = ""): String = raw.optString(k, d)
    fun int(k: String, d: Int = 0): Int = raw.optInt(k, d)
    fun long(k: String, d: Long = 0L): Long = raw.optLong(k, d)
    fun dbl(k: String, d: Double = 0.0): Double = raw.optDouble(k, d)
    fun bool(k: String, d: Boolean = false): Boolean = raw.optBoolean(k, d)
    fun unit(k: String): Boolean = raw.optJSONObject("units")?.optBoolean(k, false) ?: false

    val photos: List<Photo>
        get() {
            val a = raw.optJSONArray("photos") ?: return emptyList()
            return (0 until a.length()).map {
                val o = a.optJSONObject(it)
                Photo(o?.optString("path", "") ?: "", o?.optString("name", "") ?: "")
            }
        }

    val tasks: List<TaskItem>
            get() {
                val a = raw.optJSONArray("items") ?: return emptyList()
                return (0 until a.length()).map {
                    val o = a.optJSONObject(it)
                    TaskItem(o?.optString("t", "") ?: "", o?.optBoolean("d", false) ?: false)
                }
            }

        companion object {
        fun parse(s: String): WidgetConfig? = try {
            WidgetConfig(JSONObject(s))
        } catch (e: Exception) {
            null
        }
    }

}
