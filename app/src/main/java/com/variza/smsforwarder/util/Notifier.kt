package com.variza.smsforwarder.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.variza.smsforwarder.R
import com.variza.smsforwarder.data.SmsRecord
import com.variza.smsforwarder.net.SendResult
import java.text.NumberFormat
import java.util.Locale

/** Heads-up style notification shown when a watched SMS was delivered to the backend. */
object Notifier {
    private const val CHANNEL_ID = "sms_sent"

    fun notifySent(context: Context, m: SmsRecord, r: SendResult) {
        try {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Forwarded messages", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            val title = if (r.amount != null) {
                val amount = NumberFormat.getInstance(Locale.US).format(r.amount)
                "✓ واریز $amount ثبت شد" + (r.bankId?.let { " ($it)" } ?: "")
            } else "✓ پیام از ${m.sender} ارسال شد"
            val n = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(m.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(m.body))
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .build()
            nm.notify(1000 + (m.id % 100000).toInt(), n)
        } catch (_: Exception) {
        }
    }
}
