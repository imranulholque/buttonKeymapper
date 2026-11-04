package com.example.buttonremapper

import android.content.Context
import android.os.PowerManager

object Waker {
    /** Turn the screen on and swipe up so the PIN / password screen appears. */
    fun wake(c: Context) {
        val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "buttonremapper:wake"
        )
        wl.acquire(4000)
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        h.postDelayed({ ButtonService.instance?.swipeToUnlock() }, 700)
        h.postDelayed({ ButtonService.instance?.swipeToUnlock() }, 1400)
    }
}
