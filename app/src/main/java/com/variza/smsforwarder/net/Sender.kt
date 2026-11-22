package com.variza.smsforwarder.net

import com.variza.smsforwarder.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SendResult(val ok: Boolean, val message: String)

/** Sends one SMS as JSON to the backend. Success = HTTP 2xx. */
object Sender {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun buildJson(from: String, body: String, receivedAt: Long, simSlot: Int, subscriptionId: Int): String =
        JSONObject()
            .put("from", from)
            .put("body", body)
            .put("receivedAt", receivedAt)
            .put("simSlot", simSlot)
            .put("subscriptionId", subscriptionId)
            .toString()

    fun send(url: String, json: String): SendResult {
        val target = url.trim()
        if (target.isEmpty()) return SendResult(false, "آدرس بک‌اند وارد نشده")
        val req = try {
            Request.Builder()
                .url(target)
                .post(json.toRequestBody(JSON))
                .apply {
                    if (BuildConfig.API_KEY.isNotBlank()) {
                        header(BuildConfig.API_KEY_HEADER, BuildConfig.API_KEY)
                    }
                }
                .build()
        } catch (e: IllegalArgumentException) {
            return SendResult(false, "آدرس نامعتبر است (باید با http:// یا https:// شروع شود)")
        }
        return try {
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) SendResult(true, "HTTP ${resp.code}")
                else SendResult(false, "HTTP ${resp.code}")
            }
        } catch (e: Exception) {
            SendResult(false, e.javaClass.simpleName + ": " + (e.message ?: ""))
        }
    }
}
