package com.example.service

import android.content.Context
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.util.Log

object SoundAlertManager {

    private const val PREFS_NAME = "focus_sound_prefs"
    private const val KEY_SOUND_ENABLED = "sound_enabled"

    fun isSoundEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SOUND_ENABLED, true)
    }

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
        Log.d("SoundAlertManager", "Sound configurations updated. Enabled = $enabled")
    }

    fun playWorkToBreakSound(context: Context) {
        if (!isSoundEnabled(context)) {
            Log.d("SoundAlertManager", "Skip Work -> Break sound (sound is disabled)")
            return
        }
        Log.d("SoundAlertManager", "Playing Work -> Break alert")
        Thread {
            try {
                // 1. Play synthetic dual-beep alert over notification stream for polite volumes
                val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75)
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 250)
                Thread.sleep(300)
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 250)
                toneGenerator.release()

                // 2. Play default notification tone (polite chime)
                val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)
                ringtone?.play()
            } catch (e: Exception) {
                Log.e("SoundAlertManager", "Error playing Work To Break sound", e)
            }
        }.start()
    }

    fun playBreakToWorkSound(context: Context) {
        if (!isSoundEnabled(context)) {
            Log.d("SoundAlertManager", "Skip Break -> Work sound (sound is disabled)")
            return
        }
        Log.d("SoundAlertManager", "Playing Break -> Work alert")
        Thread {
            try {
                // 1. Play synthetic focus chord beep over notification stream to prevent phone alarm activation
                val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75)
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
                toneGenerator.release()

                // 2. Play default notification tone instead of loud alarm tone
                val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)
                ringtone?.play()
            } catch (e: Exception) {
                Log.e("SoundAlertManager", "Error playing Break To Work sound", e)
            }
        }.start()
    }

    fun playCompletionSound(context: Context) {
        if (!isSoundEnabled(context)) {
            Log.d("SoundAlertManager", "Skip completion sound (sound is disabled)")
            return
        }
        Log.d("SoundAlertManager", "Playing completion sound")
        Thread {
            try {
                // 1. Play synthetic triplet beep
                val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75)
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                Thread.sleep(200)
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                Thread.sleep(200)
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 250)
                toneGenerator.release()

                // 2. Play notification tone
                val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)
                ringtone?.play()
            } catch (e: Exception) {
                Log.e("SoundAlertManager", "Error playing Completion sound", e)
            }
        }.start()
    }
}
