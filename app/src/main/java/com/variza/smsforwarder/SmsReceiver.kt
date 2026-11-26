package com.variza.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.variza.smsforwarder.data.MessageStore
import com.variza.smsforwarder.data.Settings
import com.variza.smsforwarder.util.PhoneMatch

/** Receives every incoming SMS (all SIMs). Matching ones are queued and forwarded. */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return

        val settings = Settings(context)
        if (!settings.isLoggedIn) return
        val numbers = settings.numbers()

        val subId = intent.getIntExtra("subscription", -1)
        val slot = listOf("slot", "simSlot", "android.telephony.extra.SLOT_INDEX")
            .map { intent.getIntExtra(it, -1) }
            .firstOrNull { it >= 0 } ?: -1

        // Join multipart messages per sender
        val bySender = LinkedHashMap<String, StringBuilder>()
        var ts = System.currentTimeMillis()
        for (p in parts) {
            val from = p.originatingAddress ?: continue
            bySender.getOrPut(from) { StringBuilder() }.append(p.messageBody ?: "")
            if (p.timestampMillis > 0) ts = p.timestampMillis
        }

        val store = MessageStore.get(context)
        var queued = false
        for ((from, body) in bySender) {
            val matched = PhoneMatch.findMatch(from, numbers)
            // Log every incoming SMS so the user can see the exact sender name/number.
            store.insertIncoming(from, body.toString().take(80), ts, slot, matched)
            if (matched != null) {
                store.insertPending(from, body.toString(), ts, slot, subId)
                queued = true
            }
        }
        if (!queued) return

        try {
            ForwarderService.start(context)
        } catch (e: Exception) {
            // Could not start the service from the background: send right here instead.
            val pending = goAsync()
            Thread {
                try {
                    ForwarderService.processPending(context.applicationContext)
                } finally {
                    pending.finish()
                }
            }.start()
        }
    }
}
