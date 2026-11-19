package com.jrweb.wcmonitor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class StoreEvent(
    val id: Int,
    val eventType: String,
    val title: String,
    val message: String,
    val orderId: Int,
    var orderStatus: String,
    var actionable: Boolean,
    val createdAt: String
)

class PrefsManager(context: Context) {
    private val prefs = context.getSharedPreferences("jrweb_wc_prefs", Context.MODE_PRIVATE)

    var siteUrl: String
        get() = prefs.getString("site_url", "") ?: ""
        set(value) = prefs.edit().putString("site_url", value.trim().trimEnd('/')).apply()

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) = prefs.edit().putString("api_key", value.trim()).apply()

    var siteName: String
        get() = prefs.getString("site_name", "فروشگاه من") ?: "فروشگاه من"
        set(value) = prefs.edit().putString("site_name", value).apply()

    var lastEventId: Int
        get() = prefs.getInt("last_event_id", 0)
        set(value) = prefs.edit().putInt("last_event_id", value).apply()

    fun isConfigured(): Boolean = siteUrl.isNotEmpty() && apiKey.isNotEmpty()

    fun saveEvents(events: List<StoreEvent>) {
        val arr = JSONArray()
        events.take(100).forEach { ev ->
            val obj = JSONObject().apply {
                put("id", ev.id)
                put("event_type", ev.eventType)
                put("title", ev.title)
                put("message", ev.message)
                put("order_id", ev.orderId)
                put("order_status", ev.orderStatus)
                put("actionable", ev.actionable)
                put("created_at", ev.createdAt)
            }
            arr.put(obj)
        }
        prefs.edit().putString("cached_events", arr.toString()).apply()
    }

    fun getSavedEvents(): MutableList<StoreEvent> {
        val list = mutableListOf<StoreEvent>()
        val raw = prefs.getString("cached_events", "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    StoreEvent(
                        id = o.getInt("id"),
                        eventType = o.optString("event_type"),
                        title = o.optString("title"),
                        message = o.optString("message"),
                        orderId = o.optInt("order_id", 0),
                        orderStatus = o.optString("order_status", ""),
                        actionable = o.optBoolean("actionable", false),
                        createdAt = o.optString("created_at", "")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }
}
