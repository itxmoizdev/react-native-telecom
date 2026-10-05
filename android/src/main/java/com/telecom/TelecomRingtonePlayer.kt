package com.telecom

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Plays the **user's default ringtone + vibration** from system settings.
 * Notification-channel sound alone is often silenced by OEMs while the app is
 * foreground / showing a full-screen Activity — so we ring ourselves.
 */
internal object TelecomRingtonePlayer {
  private const val TAG = "RNTelecomRingtone"
  private var ringtone: Ringtone? = null
  private var vibrator: Vibrator? = null

  @Synchronized
  fun start(context: Context) {
    stop()

    try {
      val uri =
        RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
          ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

      val tone = RingtoneManager.getRingtone(context, uri)
      if (tone != null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          tone.isLooping = true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          tone.audioAttributes =
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
              .build()
        }
        // Respect silent / vibrate modes via ringer mode.
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (am.ringerMode) {
          AudioManager.RINGER_MODE_SILENT -> {
            Log.i(TAG, "ringerMode=SILENT — no sound")
          }
          AudioManager.RINGER_MODE_VIBRATE -> {
            Log.i(TAG, "ringerMode=VIBRATE — vibrate only")
            startVibrate(context)
          }
          else -> {
            tone.play()
            ringtone = tone
            startVibrate(context)
            Log.i(TAG, "playing default ringtone uri=$uri")
          }
        }
      }
    } catch (error: Exception) {
      Log.e(TAG, "start failed: ${error.message}", error)
    }
  }

  @Synchronized
  fun stop() {
    val tone = ringtone
    ringtone = null
    try {
      if (tone?.isPlaying == true) {
        tone.stop()
      } else {
        tone?.stop()
      }
    } catch (_: Exception) {
    }
    val vib = vibrator
    vibrator = null
    try {
      vib?.cancel()
    } catch (_: Exception) {
    }
    Log.i(TAG, "ringtone + vibrate stopped")
  }

  private fun startVibrate(context: Context) {
    val vib =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
      } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
      }

    vibrator = vib
    // Classic ring pattern: wait, buzz, wait, buzz...
    val pattern = longArrayOf(0, 1000, 1000, 1000)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
    } else {
      @Suppress("DEPRECATION")
      vib.vibrate(pattern, 0)
    }
  }
}
