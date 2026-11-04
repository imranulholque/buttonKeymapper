package com.example.buttonremapper

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        setContentView(ScrollView(this).apply { addView(root) })

        root.addView(TextView(this).apply {
            text = "Button Remapper"
            textSize = 24f
        })

        status = TextView(this).apply { setPadding(0, pad, 0, pad) }
        root.addView(status)

        root.addView(Button(this).apply {
            text = "Open Accessibility settings (turn service ON / OFF)"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        root.addView(Switch(this).apply {
            text = "Remapping enabled"
            isChecked = Prefs.enabled(this@MainActivity)
            setPadding(0, pad, 0, pad)
            setOnCheckedChangeListener { _, v -> Prefs.setEnabled(this@MainActivity, v) }
        })

        root.addView(Switch(this).apply {
            text = "Wake screen with button when screen is OFF (silent media trick, shows a notification)"
            isChecked = Prefs.keepAlive(this@MainActivity)
            setPadding(0, 0, 0, pad)
            setOnCheckedChangeListener { _, v ->
                Prefs.setKeepAlive(this@MainActivity, v)
                val i = Intent(this@MainActivity, KeepAliveService::class.java)
                if (v) {
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
                    }
                    startForegroundService(i)
                } else stopService(i)
            }
        })

        addRow(root, "Volume UP press does:", Prefs.KEY_UP)
        addRow(root, "Volume DOWN press does:", Prefs.KEY_DOWN)
        addRow(root, "Volume UP + DOWN together does:", Prefs.KEY_BOTH)

        root.addView(TextView(this).apply {
            setPadding(0, pad, 0, 0)
            text = "To restore normal buttons: switch 'Remapping enabled' off, " +
                "or turn the service off in Accessibility settings, or uninstall the app."
        })
    }

    private fun addRow(root: LinearLayout, title: String, key: String) {
        val pad = (12 * resources.displayMetrics.density).toInt()
        root.addView(TextView(this).apply { text = title; setPadding(0, pad, 0, 0) })
        root.addView(Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                Prefs.labels
            )
            setSelection(Prefs.get(this@MainActivity, key))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    Prefs.set(this@MainActivity, key, pos)
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.enabled(this) && Prefs.keepAlive(this)) {
            try { startForegroundService(Intent(this, KeepAliveService::class.java)) } catch (_: Exception) {}
        }
        val list = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val on = list.contains(packageName) && list.contains("ButtonService")
        status.text = if (on) "Service status: ON ✅ (starts automatically after restart)"
        else "Service status: OFF ❌ — open Accessibility settings, find 'Button Remapper' and turn it on."
    }
}
