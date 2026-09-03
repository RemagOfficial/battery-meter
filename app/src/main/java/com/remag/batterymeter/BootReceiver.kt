package com.remag.batterymeter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences(BatteryOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean(BatteryOverlayService.KEY_SERVICE_ENABLED, false)
            
            if (isEnabled && Settings.canDrawOverlays(context)) {
                val serviceIntent = Intent(context, BatteryOverlayService::class.java)
                context.startService(serviceIntent)
            }
        }
    }
}
