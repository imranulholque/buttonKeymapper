package com.example.buttonremapper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (!Prefs.enabled(c) || !Prefs.keepAlive(c)) return
        try {
            val s = Intent(c, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s) else c.startService(s)
        } catch (_: Exception) { }
    }
}
