package com.variza.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the forwarder after reboot or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            ForwarderService.start(context)
        } catch (_: Exception) {
        }
    }
}
