package com.example.buttonremapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class ButtonService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var upDown = false
    private var downDown = false
    private var consumedUp = false
    private var consumedDown = false
    private var pending: Runnable? = null

    companion object {
        @Volatile var instance: ButtonService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        if (Prefs.enabled(this) && Prefs.keepAlive(this)) {
            try {
                val i = android.content.Intent(this, KeepAliveService::class.java)
                startForegroundService(i)
            } catch (_: Exception) { }
        }
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        stopService(android.content.Intent(this, KeepAliveService::class.java))
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onKeyEvent(e: KeyEvent): Boolean {
        val code = e.keyCode
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        // Master switch off -> buttons behave exactly like stock
        if (!Prefs.enabled(this)) return false

        val isUp = code == KeyEvent.KEYCODE_VOLUME_UP

        if (e.action == KeyEvent.ACTION_DOWN) {
            if (e.repeatCount > 0) return if (isUp) consumedUp else consumedDown

            if (isUp) upDown = true else downDown = true
            val otherDown = if (isUp) downDown else upDown
            val comboAction = Prefs.get(this, Prefs.KEY_BOTH)

            // Both buttons together
            if (otherDown && comboAction != Prefs.NONE) {
                cancelPending()
                consumedUp = true
                consumedDown = true
                runAction(comboAction)
                return true
            }

            val action = Prefs.get(this, if (isUp) Prefs.KEY_UP else Prefs.KEY_DOWN)
            if (action == Prefs.NONE) {
                if (isUp) consumedUp = false else consumedDown = false
                return false
            }
            if (isUp) consumedUp = true else consumedDown = true

            if (comboAction == Prefs.NONE) {
                runAction(action)
            } else {
                // short wait so a two-button press isn't mistaken for a single press
                cancelPending()
                val r = Runnable { pending = null; runAction(action) }
                pending = r
                handler.postDelayed(r, 180)
            }
            return true
        }

        if (e.action == KeyEvent.ACTION_UP) {
            val consumed = if (isUp) consumedUp else consumedDown
            if (isUp) { upDown = false; consumedUp = false } else { downDown = false; consumedDown = false }
            return consumed
        }
        return false
    }

    private fun cancelPending() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
    }

    private fun runAction(a: Int) {
        when (a) {
            Prefs.POWER -> powerToggle()
            Prefs.SHOT -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            Prefs.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            Prefs.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            Prefs.RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            Prefs.NOTIF -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        }
    }

    private fun powerToggle() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isInteractive) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            Waker.wake(this)
        }
    }

    fun swipeToUnlock() {
        val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isKeyguardLocked) return
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val p = Path().apply { moveTo(w / 2, h * 0.85f); lineTo(w / 2, h * 0.25f) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, 250))
            .build()
        dispatchGesture(g, null, null)
    }
}
