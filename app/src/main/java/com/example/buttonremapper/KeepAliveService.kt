package com.example.buttonremapper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.view.KeyEvent

class KeepAliveService : Service() {

    private var session: MediaSession? = null
    private var track: AudioTrack? = null
    @Volatile private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (!running) startKeepAlive()
        return START_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("keep", "Button wake service", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val n = Notification.Builder(this, "keep")
            .setContentTitle("Button Remapper active")
            .setContentText("Volume button can wake the screen")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, n)
        }
    }

    private fun startKeepAlive() {
        running = true

        // Silent audio so Android treats us as "music playing" and keeps
        // delivering volume keys to us even when the screen is off.
        val rate = 8000
        val min = AudioTrack.getMinBufferSize(
            rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(min, rate / 2) * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        t.play()
        Thread {
            val buf = ShortArray(rate / 4)
            while (running) {
                try { t.write(buf, 0, buf.size) } catch (_: Exception) { break }
            }
        }.start()

        // Media session with a remote volume provider = we receive volume key presses
        val s = MediaSession(this, "ButtonRemapper")
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)
                .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                .build()
        )
        s.setPlaybackToRemote(object : android.media.VolumeProvider(VOLUME_CONTROL_RELATIVE, 100, 50) {
            override fun onAdjustVolume(direction: Int) {
                handleKey(direction)
            }
        })
        s.isActive = true
        session = s
    }

    private fun handleKey(direction: Int) {
        if (direction == 0) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isInteractive) {
            // screen is on -> behave like a normal volume key
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
            return
        }
        if (!Prefs.enabled(this)) return
        val key = if (direction > 0) Prefs.KEY_UP else Prefs.KEY_DOWN
        if (Prefs.get(this, key) == Prefs.POWER) Waker.wake(this)
    }

    override fun onDestroy() {
        running = false
        try { session?.isActive = false; session?.release() } catch (_: Exception) {}
        try { track?.stop(); track?.release() } catch (_: Exception) {}
        super.onDestroy()
    }
}
