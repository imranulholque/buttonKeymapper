package com.example.buttonremapper

import android.content.Context

object Prefs {
    const val NONE = 0        // normal button behaviour
    const val POWER = 1       // lock screen / wake screen
    const val SHOT = 2        // screenshot
    const val HOME = 3
    const val BACK = 4
    const val RECENTS = 5
    const val NOTIF = 6

    val labels = arrayOf(
        "Normal (change volume)",
        "Power button (lock / wake)",
        "Take screenshot",
        "Home",
        "Back",
        "Recent apps",
        "Notifications"
    )

    const val KEY_UP = "up"
    const val KEY_DOWN = "down"
    const val KEY_BOTH = "both"

    private fun sp(c: Context) = c.getSharedPreferences("remap", Context.MODE_PRIVATE)

    fun enabled(c: Context) = sp(c).getBoolean("enabled", true)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    fun keepAlive(c: Context) = sp(c).getBoolean("keepalive", true)
    fun setKeepAlive(c: Context, v: Boolean) = sp(c).edit().putBoolean("keepalive", v).apply()

    fun get(c: Context, key: String): Int {
        val def = when (key) {
            KEY_UP -> POWER
            KEY_BOTH -> SHOT
            else -> NONE
        }
        return sp(c).getInt(key, def)
    }

    fun set(c: Context, key: String, v: Int) = sp(c).edit().putInt(key, v).apply()
}
