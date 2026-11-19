package com.jrweb.wcmonitor

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object ApiClient {

    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private fun openConfiguredConnection(targetUrl: String, method: String, timeout: Int = 15000): HttpURLConnection {
        val url = URL(targetUrl)
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeout
            readTimeout = timeout
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("Cache-Control", "no-cache")
        }
    }

    fun ping(siteUrl: String, apiKey: String): Pair<Boolean, String> {
        return try {
            var cleanUrl = siteUrl.trim().trimEnd('/')
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                cleanUrl = "https://$cleanUrl"
            }
            val fullUrl = "$cleanUrl/wp-json/jrweb-sync/v1/ping?api_key=$apiKey"
            val conn = openConfiguredConnection(fullUrl, "GET", 15000)

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.let { BufferedReader(InputStreamReader(it, "UTF-8")).use { r -> r.readText() } } ?: ""

            if (code == 200) {
                val json = JSONObject(body)
                if (json.optBoolean("success", false)) {
                    Pair(true, json.optString("site_name", "اتصال موفق"))
                } else {
                    Pair(false, "پاسخ نامعتبر از افزونه")
                }
            } else {
                Pair(false, "خطای سرور ($code): ${body.take(80)}")
            }
        } catch (e: Exception) {
            Pair(false, "خطای ارتباط: ${e.javaClass.simpleName} - ${e.localizedMessage ?: "بدون جزئیات"}")
        }
    }

    fun fetchEvents(siteUrl: String, apiKey: String, lastId: Int): List<StoreEvent> {
        val result = mutableListOf<StoreEvent>()
        try {
            var cleanUrl = siteUrl.trim().trimEnd('/')
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                cleanUrl = "https://$cleanUrl"
            }
            val fullUrl = "$cleanUrl/wp-json/jrweb-sync/v1/events?last_id=$lastId&api_key=$apiKey"
            val conn = openConfiguredConnection(fullUrl, "GET", 15000)

            if (conn.responseCode == 200) {
                val body = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
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
            var cleanUrl = siteUrl.trim().trimEnd('/')
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                cleanUrl = "https://$cleanUrl"
            }
            val fullUrl = "$cleanUrl/wp-json/jrweb-sync/v1/order-action?api_key=$apiKey"
            val conn = openConfiguredConnection(fullUrl, "POST", 15000).apply {
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
            val body = stream?.let { BufferedReader(InputStreamReader(it, "UTF-8")).use { r -> r.readText() } } ?: ""
            val json = JSONObject(body)

            if (code == 200 && json.optBoolean("success")) {
                Pair(true, json.optString("message", "انجام شد"))
            } else {
                Pair(false, json.optString("message", "خطا در انجام عملیات ($code)"))
            }
        } catch (e: Exception) {
            Pair(false, "خطا: ${e.localizedMessage}")
        }
    }
}
