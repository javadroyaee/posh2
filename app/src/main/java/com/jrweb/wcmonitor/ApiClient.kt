package com.jrweb.wcmonitor

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object ApiClient {

    fun ping(siteUrl: String, apiKey: String): Pair<Boolean, String> {
        return try {
            val cleanUrl = siteUrl.trimEnd('/')
            val url = URL("/wp-json/jrweb-sync/v1/ping?api_key=")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            if (code == 200) {
                val json = JSONObject(body)
                Pair(true, json.optString("site_name", "اتصال موفق"))
            } else {
                Pair(false, "خطای سرور ()")
            }
        } catch (e: Exception) {
            Pair(false, "خطای ارتباط: ")
        }
    }

    fun fetchEvents(siteUrl: String, apiKey: String, lastId: Int): List<StoreEvent> {
        val result = mutableListOf<StoreEvent>()
        try {
            val cleanUrl = siteUrl.trimEnd('/')
            val url = URL("/wp-json/jrweb-sync/v1/events?last_id=&api_key=")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 12000
            }
            if (conn.responseCode == 200) {
                val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val json = JSONObject(body)
                val arr = json.optJSONArray("events") ?: return result
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    result.add(
                        StoreEvent(
                            id = o.getInt("id"),
                            eventType = o.optString("event_type"),
                            title = o.optString("title"),
                            message = o.optString("message"),
                            orderId = o.optInt("order_id", 0),
                            orderStatus = o.optString("order_status", ""),
                            actionable = o.optInt("actionable", 0) == 1,
                            createdAt = o.optString("created_at", "")
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return result
    }

    fun sendOrderAction(siteUrl: String, apiKey: String, orderId: Int, action: String): Pair<Boolean, String> {
        return try {
            val cleanUrl = siteUrl.trimEnd('/')
            val url = URL("/wp-json/jrweb-sync/v1/order-action?api_key=")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12000
                readTimeout = 12000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            val payload = JSONObject().apply {
                put("order_id", orderId)
                put("action", action)
            }
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(payload.toString()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            val json = JSONObject(body)
            if (code == 200 && json.optBoolean("success")) {
                Pair(true, json.optString("message", "انجام شد"))
            } else {
                Pair(false, json.optString("message", "خطا در انجام عملیات"))
            }
        } catch (e: Exception) {
            Pair(false, "خطا: ")
        }
    }
}
