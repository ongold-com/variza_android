package com.variza.smsforwarder.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class SmsRecord(
    val id: Long,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val simSlot: Int,
    val subscriptionId: Int,
    val status: String, // PENDING | SENT
    val attempts: Int,
    val lastError: String?,
    val sentAt: Long?,
)

/** Small SQLite queue + log of forwarded messages. */
class MessageStore private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "messages.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender TEXT NOT NULL,
                body TEXT NOT NULL,
                received_at INTEGER NOT NULL,
                sim_slot INTEGER NOT NULL,
                subscription_id INTEGER NOT NULL,
                status TEXT NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0,
                last_error TEXT,
                sent_at INTEGER
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    @Synchronized
    fun insertPending(sender: String, body: String, receivedAt: Long, simSlot: Int, subId: Int): Long {
        val v = ContentValues().apply {
            put("sender", sender)
            put("body", body)
            put("received_at", receivedAt)
            put("sim_slot", simSlot)
            put("subscription_id", subId)
            put("status", "PENDING")
        }
        return writableDatabase.insert("messages", null, v)
    }

    @Synchronized
    fun pending(): List<SmsRecord> = query("status = 'PENDING'", "id ASC", null)

    @Synchronized
    fun recent(limit: Int = 50): List<SmsRecord> = query(null, "id DESC", limit.toString())

    @Synchronized
    fun pendingCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM messages WHERE status = 'PENDING'", null).use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    @Synchronized
    fun markSent(id: Long) {
        val v = ContentValues().apply {
            put("status", "SENT")
            put("sent_at", System.currentTimeMillis())
            putNull("last_error")
        }
        writableDatabase.update("messages", v, "id = ?", arrayOf(id.toString()))
        writableDatabase.execSQL("UPDATE messages SET attempts = attempts + 1 WHERE id = ?", arrayOf(id))
        trim()
    }

    @Synchronized
    fun markFailed(id: Long, error: String) {
        writableDatabase.execSQL(
            "UPDATE messages SET attempts = attempts + 1, last_error = ? WHERE id = ?",
            arrayOf(error, id)
        )
    }

    /** Keep the log small: drop sent rows beyond the newest 200. */
    private fun trim() {
        writableDatabase.execSQL(
            "DELETE FROM messages WHERE status = 'SENT' AND id NOT IN " +
                "(SELECT id FROM messages ORDER BY id DESC LIMIT 200)"
        )
    }

    private fun query(where: String?, order: String, limit: String?): List<SmsRecord> {
        val out = ArrayList<SmsRecord>()
        readableDatabase.query("messages", null, where, null, null, null, order, limit).use { c ->
            while (c.moveToNext()) {
                out.add(
                    SmsRecord(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        sender = c.getString(c.getColumnIndexOrThrow("sender")),
                        body = c.getString(c.getColumnIndexOrThrow("body")),
                        receivedAt = c.getLong(c.getColumnIndexOrThrow("received_at")),
                        simSlot = c.getInt(c.getColumnIndexOrThrow("sim_slot")),
                        subscriptionId = c.getInt(c.getColumnIndexOrThrow("subscription_id")),
                        status = c.getString(c.getColumnIndexOrThrow("status")),
                        attempts = c.getInt(c.getColumnIndexOrThrow("attempts")),
                        lastError = c.getString(c.getColumnIndexOrThrow("last_error")),
                        sentAt = c.getLong(c.getColumnIndexOrThrow("sent_at")).takeIf { v -> v != 0L },
                    )
                )
            }
        }
        return out
    }

    companion object {
        @Volatile private var instance: MessageStore? = null
        fun get(context: Context): MessageStore =
            instance ?: synchronized(this) {
                instance ?: MessageStore(context).also { instance = it }
            }
    }
}
