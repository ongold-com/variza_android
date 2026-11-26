package com.variza.smsforwarder.net

import com.variza.smsforwarder.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Result of sending one SMS. [amount] and [bankId] come from the backend's parsed result. */
data class SendResult(
    val ok: Boolean,
    val message: String,
    val amount: Long? = null,
    val bankId: String? = null,
)

data class LoginResult(
    val ok: Boolean,
    val message: String,
    val deviceToken: String = "",
    val deviceId: Long = 0,
    val fullName: String = "",
    val phone: String = "",
)

/** Talks to the Variza backend (login + send SMS). Every request carries the API-key header. */
object Sender {
    const val BASE_URL = "https://variz.aongold.ir/api/v1/app"

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

    private fun request(path: String, json: String, token: String?): Request =
        Request.Builder()
            .url("$BASE_URL/$path")
            .post(json.toRequestBody(JSON))
            .header(BuildConfig.API_KEY_HEADER, BuildConfig.API_KEY)
            .header("Content-Type", "application/json")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()

    /** Extracts a human readable error from a JSON error body, if there is one. */
    private fun errorText(code: Int, body: String): String {
        val detail = try {
            val o = JSONObject(body)
            o.optString("message").ifBlank { o.optString("error") }
        } catch (e: Exception) {
            ""
        }
        return if (detail.isBlank()) "HTTP $code" else "HTTP $code – $detail"
    }

    fun login(identifier: String, password: String): LoginResult {
        val json = JSONObject()
            .put("identifier", identifier)
            .put("password", password)
            .put("label", "phone")
            .toString()
        return try {
            client.newCall(request("login", json, null)).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (!resp.isSuccessful) return LoginResult(false, errorText(resp.code, text))
                val o = JSONObject(text)
                val token = o.optString("deviceToken")
                if (token.isBlank()) return LoginResult(false, "پاسخ سرور توکن نداشت")
                val user = o.optJSONObject("user")
                LoginResult(
                    ok = true,
                    message = "OK",
                    deviceToken = token,
                    deviceId = o.optLong("deviceId"),
                    fullName = user?.optString("fullName") ?: "",
                    phone = user?.optString("phone") ?: "",
                )
            }
        } catch (e: Exception) {
            LoginResult(false, e.javaClass.simpleName + ": " + (e.message ?: ""))
        }
    }

    /**
     * Success only when the server answers 2xx AND every item in `results`
     * has parsed = true and matched = true. Anything else must be retried.
     */
    fun send(token: String, json: String): SendResult {
        return try {
            client.newCall(request("messages", json, token)).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (!resp.isSuccessful) return SendResult(false, errorText(resp.code, text))
                checkResults(text)
            }
        } catch (e: Exception) {
            SendResult(false, e.javaClass.simpleName + ": " + (e.message ?: ""))
        }
    }

    /** Pure function (unit-tested): decides success from the response body. */
    fun checkResults(text: String): SendResult {
        val results: JSONArray = try {
            JSONObject(text).optJSONArray("results")
        } catch (e: Exception) {
            null
        } ?: return SendResult(false, "پاسخ سرور نامعتبر است")
        if (results.length() == 0) return SendResult(false, "results خالی است")
        for (i in 0 until results.length()) {
            val r = results.optJSONObject(i) ?: return SendResult(false, "results نامعتبر")
            if (!r.optBoolean("parsed", false)) return SendResult(false, "parsed=false")
            if (!r.optBoolean("matched", false)) return SendResult(false, "matched=false")
        }
        val first = results.getJSONObject(0)
        return SendResult(
            ok = true,
            message = "OK",
            amount = if (first.has("amount")) first.optLong("amount") else null,
            bankId = first.optString("bankId").ifBlank { null },
        )
    }
}
