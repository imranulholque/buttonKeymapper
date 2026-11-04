package com.example.buttonremapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Path
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class ButtonService : AccessibilityService() {

    companion object {
        @Volatile var instance: ButtonService? = null
    }

    private val handler = Handler(Looper.getMainLooper())

    // index 0 = Volume UP, index 1 = Volume DOWN
    private val isDown = BooleanArray(2)
    private val downAt = LongArray(2)
    private val consumed = BooleanArray(2)   // we swallowed this key's DOWN
    private val fired = BooleanArray(2)      // single-press action already done
    private val comboUsed = BooleanArray(2)  // this press became part of a combo
    private val pend = arrayOfNulls<Runnable>(2)

    private val comboMs = 150L   // both buttons must go down within this time
    private val waitMs = 180L    // how long we wait for the 2nd button

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        if (Prefs.enabled(this) && Prefs.keepAlive(this)) {
            try {
                startForegroundService(Intent(this, KeepAliveService::class.java))
            } catch (_: Exception) { }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        stopService(Intent(this, KeepAliveService::class.java))
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun keyOf(i: Int) = if (i == 0) Prefs.KEY_UP else Prefs.KEY_DOWN

    override fun onKeyEvent(e: KeyEvent): Boolean {
        val code = e.keyCode
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        // Master switch off -> buttons behave exactly like stock
        if (!Prefs.enabled(this)) return false

        val i = if (code == KeyEvent.KEYCODE_VOLUME_UP) 0 else 1
        val o = 1 - i
        val comboAction = Prefs.get(this, Prefs.KEY_BOTH)

        if (e.action == KeyEvent.ACTION_DOWN) {
            // key is being held down (auto-repeat)
            if (e.repeatCount > 0) {
                if (!consumed[i]) return false
                // holding a "normal volume" key: repeat the volume change ourselves
                if (fired[i] && !comboUsed[i] && Prefs.get(this, keyOf(i)) == Prefs.NONE) {
                    adjustVolume(i == 0)
                }
                return true
            }

            val now = SystemClock.elapsedRealtime()
            isDown[i] = true
            downAt[i] = now
            fired[i] = false
            comboUsed[i] = false

            // Both buttons together (2nd one pressed within comboMs of the 1st)
            if (comboAction != Prefs.NONE && isDown[o] && consumed[o] && now - downAt[o] <= comboMs) {
                cancel(o)
                cancel(i)
                consumed[i] = true
                comboUsed[i] = true
                comboUsed[o] = true
                fired[i] = true
                fired[o] = true
                runAction(comboAction)
                return true
            }

            val action = Prefs.get(this, keyOf(i))

            // nothing to wait for -> leave the key completely alone
            if (action == Prefs.NONE && comboAction == Prefs.NONE) {
                consumed[i] = false
                return false
            }

            consumed[i] = true

            if (comboAction == Prefs.NONE) {
                fired[i] = true
                runAction(action)
            } else {
                // wait briefly: maybe the other button follows (combo)
                cancel(i)
                val r = Runnable { pend[i] = null; fireSingle(i) }
                pend[i] = r
                handler.postDelayed(r, waitMs)
            }
            return true
        }

        if (e.action == KeyEvent.ACTION_UP) {
            isDown[i] = false
            val c = consumed[i]
            // quick tap: released before the wait ended -> do the single action now
            if (c && !fired[i] && !comboUsed[i]) {
                cancel(i)
                fireSingle(i)
            }
            consumed[i] = false
            return c
        }
        return false
    }

    private fun cancel(i: Int) {
        pend[i]?.let { handler.removeCallbacks(it) }
        pend[i] = null
    }

    private fun fireSingle(i: Int) {
        if (fired[i] || comboUsed[i]) return
        fired[i] = true
        val a = Prefs.get(this, keyOf(i))
        if (a == Prefs.NONE) adjustVolume(i == 0) else runAction(a)
    }

    /** Same as a normal volume key press (shows the volume panel). */
    private fun adjustVolume(up: Boolean) {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        am.adjustSuggestedStreamVolume(
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.USE_DEFAULT_STREAM_TYPE,
            AudioManager.FLAG_SHOW_UI
        )
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
