package com.variza.smsforwarder.data

import android.content.Context
import org.json.JSONArray

/** Backend URL and the list of watched numbers, kept in SharedPreferences. */
class Settings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("settings", Context.MODE_PRIVATE)

    var backendUrl: String
        get() = prefs.getString("backend_url", "") ?: ""
        set(v) = prefs.edit().putString("backend_url", v.trim()).apply()

    fun numbers(): List<String> {
        val raw = prefs.getString("numbers", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveNumbers(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString("numbers", arr.toString()).apply()
    }

    fun addNumber(n: String): Boolean {
        val v = n.trim()
        if (v.isEmpty()) return false
        val cur = numbers()
        if (cur.any { it.equals(v, ignoreCase = true) }) return false
        saveNumbers(cur + v)
        return true
    }

    fun removeNumber(n: String) {
        saveNumbers(numbers().filter { it != n })
    }
}
