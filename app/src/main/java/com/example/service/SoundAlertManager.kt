package com.example.service

import android.content.Context
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.util.Log

object SoundAlertManager {

    fun playWorkToBreakSound(context: Context) {
        Log.d("SoundAlertManager", "Playing Work -> Break alert")
        Thread {
            try {
                // 1. Play synthetic dual-beep alert
                val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
                Thread.sleep(350)
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
                toneGenerator.release()

                // 2. Play default notification tone
                val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)
                ringtone?.play()
            } catch (e: Exception) {
                Log.e("SoundAlertManager", "Error playing Work To Break sound", e)
            }
        }.start()
    }

    fun playBreakToWorkSound(context: Context) {
        Log.d("SoundAlertManager", "Playing Break -> Work alert")
        Thread {
            try {
                // 1. Play synthetic warning/focus chord beep
                val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_HIGH_L, 500)
                toneGenerator.release()

                // 2. Play default alarm or notification tone
                val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?:
                              RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)
                ringtone?.play()
            } catch (e: Exception) {
                Log.e("SoundAlertManager", "Error playing Break To Work sound", e)
            }
        }.start()
    }

    fun playCompletionSound(context: Context) {
        Log.d("SoundAlertManager", "Playing completion sound")
        Thread {
            try {
                // 1. Play synthetic triplet beep
                val toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
                Thread.sleep(250)
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
                Thread.sleep(250)
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
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
