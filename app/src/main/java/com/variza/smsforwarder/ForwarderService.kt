package com.variza.smsforwarder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.variza.smsforwarder.data.MessageStore
import com.variza.smsforwarder.data.Settings
import com.variza.smsforwarder.net.Sender
import com.variza.smsforwarder.ui.MainActivity

/**
 * Foreground service that keeps the app alive and pushes every pending SMS to the
 * backend. Failed messages are retried every 10 seconds until the backend answers 2xx.
 */
class ForwarderService : Service() {

    private val wake = java.util.concurrent.Semaphore(0)
    @Volatile private var running = false
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        if (!running) {
            running = true
            worker = Thread({ loop() }, "sms-forwarder").also { it.start() }
        } else {
            // New SMS arrived: wake the loop right away.
            wake.release()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        wake.release()
        super.onDestroy()
    }

    private fun loop() {
        while (running) {
            try {
                processPending(applicationContext)
                updateNotification()
            } catch (_: Exception) {
                // never let the loop die
            }
            try {
                wake.tryAcquire(RETRY_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                wake.drainPermits()
            } catch (_: InterruptedException) {
            }
        }
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val pending = MessageStore.get(this).pendingCount()
        val text = if (pending > 0) "$pending پیام در صف ارسال (تلاش مجدد هر ۱۰ ثانیه)"
        else "در حال پایش پیامک‌ها"
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Variza SMS فعال است")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, "Forwarder", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    companion object {
        private const val CHANNEL_ID = "forwarder"
        private const val NOTIF_ID = 1
        const val RETRY_MS = 10_000L

        private val sendLock = Any()

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ForwarderService::class.java))
        }

        /** Sends all pending messages, oldest first. Stops at the first failure. */
        fun processPending(context: Context) {
            synchronized(sendLock) {
                val store = MessageStore.get(context)
                val list = store.pending()
                if (list.isEmpty()) return
                val url = Settings(context).backendUrl

                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "variza:send")
                wl.acquire(60_000L)
                try {
                    for (m in list) {
                        val json = Sender.buildJson(m.sender, m.body, m.receivedAt, m.simSlot, m.subscriptionId)
                        val r = Sender.send(url, json)
                        if (r.ok) {
                            store.markSent(m.id)
                            com.variza.smsforwarder.util.Notifier.notifySent(context, m)
                        } else {
                            store.markFailed(m.id, r.message)
                            break
                        }
                    }
                } finally {
                    if (wl.isHeld) wl.release()
                }
            }
        }
    }
}
