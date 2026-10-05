package com.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Native CallStyle notification + optional full-screen Activity.
 *
 * Required CallStyle pieces (Android docs):
 * - [Person] with non-empty name + [Person.Builder.setImportant] true
 * - [NotificationCompat.CallStyle.forIncomingCall] (decline, answer) OR forOngoingCall
 * - [NotificationCompat.Builder.addPerson]
 * - [NotificationCompat.Builder.setOngoing] true (Android 14+ non-dismissible)
 * - [NotificationCompat.Builder.setCategory] CATEGORY_CALL
 * - Foreground service type phoneCall + MANAGE_OWN_CALLS
 * - fullScreenIntent must be an **Activity** PendingIntent
 */
class TelecomCallService : Service() {
  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val action = intent?.action
    val uuid = intent?.getStringExtra(EXTRA_UUID).orEmpty()
    val callerName = intent?.getStringExtra(EXTRA_CALLER_NAME) ?: "Unknown"
    val handle = intent?.getStringExtra(EXTRA_HANDLE).orEmpty()
    val direction = intent?.getStringExtra(EXTRA_DIRECTION) ?: "incoming"
    val avatarUrl = intent?.getStringExtra(EXTRA_AVATAR_URL).orEmpty()
    val ringing = direction == "incoming"
    val skipIncomingUi = intent?.getBooleanExtra(EXTRA_SKIP_INCOMING_UI, false) == true

    when (action) {
      ACTION_STOP -> {
        TelecomRingtonePlayer.stop()
        TelecomCallUi.finishAll(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        return START_NOT_STICKY
      }
      ACTION_ANSWER -> {
        TelecomRingtonePlayer.stop()
        CallSessionManager.instance?.answerCall(uuid)
        return START_STICKY
      }
      ACTION_DECLINE, ACTION_END -> {
        TelecomRingtonePlayer.stop()
        CallSessionManager.instance?.endCall(uuid)
        return START_NOT_STICKY
      }
      ACTION_UPDATE_ONGOING -> {
        TelecomRingtonePlayer.stop()
        ensureChannel()
        postNotification(uuid, callerName, handle, ringing = false, avatarUrl = avatarUrl)
        return START_STICKY
      }
      else -> {
        ensureChannel()
        logFullScreenCapability()

        val notification =
          buildCallStyleNotification(
            uuid,
            callerName,
            handle,
            ringing && !skipIncomingUi,
            avatarUrl,
          )
        val type =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
          } else {
            0
          }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)

        if (ringing && !skipIncomingUi) {
          // OEM often mutes channel sound while app is foreground — play ourselves.
          TelecomRingtonePlayer.start(this)
          if (TelecomUiConfig.current.launchFullScreenOnIncoming) {
            launchFullScreenActivity(uuid, callerName, handle, avatarUrl)
          }
        } else if (!ringing) {
          TelecomRingtonePlayer.stop()
          openInCall(this, uuid, callerName, handle, direction, avatarUrl)
        }
        return START_STICKY
      }
    }
  }

  private fun launchFullScreenActivity(
    uuid: String,
    callerName: String,
    handle: String,
    avatarUrl: String,
  ) {
    val fullScreen =
      Intent(this, TelecomIncomingCallActivity::class.java).apply {
        addFlags(
          Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP,
        )
        putExtra(EXTRA_UUID, uuid)
        putExtra(EXTRA_CALLER_NAME, callerName)
        putExtra(EXTRA_HANDLE, handle)
        putExtra(EXTRA_DIRECTION, "incoming")
        putExtra(EXTRA_AVATAR_URL, avatarUrl)
      }
    try {
      startActivity(fullScreen)
      Log.i(TAG, "full-screen Activity launched uuid=$uuid")
    } catch (error: Exception) {
      Log.e(TAG, "full-screen launch failed: ${error.message}", error)
    }
  }

  private fun postNotification(
    uuid: String,
    callerName: String,
    handle: String,
    ringing: Boolean,
    avatarUrl: String = "",
  ) {
    val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    if (!ringing) {
      // Channel/FLAG_INSISTENT alerts keep ringing on some OEMs until the
      // notification is cancelled — kill it, then post quiet ongoing.
      TelecomRingtonePlayer.stop()
      manager.cancel(NOTIFICATION_ID)
    }
    manager.notify(
      NOTIFICATION_ID,
      buildCallStyleNotification(uuid, callerName, handle, ringing, avatarUrl),
    )
  }

  private fun logFullScreenCapability() {
    if (Build.VERSION.SDK_INT < 34) return
    val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    Log.i(TAG, "canUseFullScreenIntent=${manager.canUseFullScreenIntent()}")
  }

  private fun ensureChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    // Remove older channels that still had ringtone/vibration attached.
    listOf(
      "rn_telecom_calls",
      "rn_telecom_calls_v2",
      "rn_telecom_calls_v3",
    ).forEach { manager.deleteNotificationChannel(it) }

    // Silent channel: ringtone/vibrate are owned by TelecomRingtonePlayer so
    // answering can stop sound immediately without waiting for notification dismiss.
    val channel =
      NotificationChannel(
        CHANNEL_ID,
        "Calls",
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = "CallStyle incoming / ongoing (silent — app plays ringtone)"
        enableVibration(false)
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        setBypassDnd(true)
        setShowBadge(false)
        setSound(null, null)
      }
    manager.createNotificationChannel(channel)
  }

  private fun buildCallStyleNotification(
    uuid: String,
    callerName: String,
    handle: String,
    ringing: Boolean,
    avatarUrl: String = "",
  ): Notification {
    val displayName = callerName.ifBlank { handle }.ifBlank { "Unknown" }

    // Required: non-empty name + important=true for CallStyle ranking.
    val personBuilder =
      Person.Builder()
        .setName(displayName)
        .setImportant(true)

    if (handle.isNotBlank()) {
      personBuilder.setUri(Uri.fromParts("tel", handle, null).toString())
    }
    val person = personBuilder.build()

    val contentActivityIntent =
      if (ringing) {
        Intent(this, TelecomIncomingCallActivity::class.java)
      } else {
        Intent(this, TelecomInCallActivity::class.java)
      }.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        putExtra(EXTRA_UUID, uuid)
        putExtra(EXTRA_CALLER_NAME, callerName)
        putExtra(EXTRA_HANDLE, handle)
        putExtra(EXTRA_DIRECTION, if (ringing) "incoming" else "outgoing")
        putExtra(EXTRA_AVATAR_URL, avatarUrl)
      }

    val contentPending =
      PendingIntent.getActivity(
        this,
        REQUEST_FULL_SCREEN,
        contentActivityIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    // CallStyle action intents (service keeps Telecom session in sync).
    val answerIntent =
      PendingIntent.getService(
        this,
        REQUEST_ANSWER,
        Intent(this, TelecomCallService::class.java).apply {
          action = ACTION_ANSWER
          putExtra(EXTRA_UUID, uuid)
          putExtra(EXTRA_CALLER_NAME, callerName)
          putExtra(EXTRA_HANDLE, handle)
          putExtra(EXTRA_DIRECTION, "outgoing")
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val declineOrEndIntent =
      PendingIntent.getService(
        this,
        REQUEST_DECLINE,
        Intent(this, TelecomCallService::class.java).apply {
          action = if (ringing) ACTION_DECLINE else ACTION_END
          putExtra(EXTRA_UUID, uuid)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    // Parameter order for incoming: (person, declineIntent, answerIntent)
    val style =
      if (ringing) {
        NotificationCompat.CallStyle.forIncomingCall(person, declineOrEndIntent, answerIntent)
      } else {
        NotificationCompat.CallStyle.forOngoingCall(person, declineOrEndIntent)
      }

    val builder =
      NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.sym_call_incoming)
        .setContentTitle(displayName)
        .setContentText(handle)
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setOngoing(true) // Android 14+: non-dismissible call notification
        .setOnlyAlertOnce(true)
        .setSilent(true) // never use notification sound — RingtonePlayer owns audio
        .setStyle(style)
        .addPerson(person)
        .setContentIntent(contentPending)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

    // API 30 and below: colorized helps CallStyle-like ranking.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      builder.setColorized(true).setColor(0xFF1B5E20.toInt())
    }

    if (ringing) {
      // MUST be Activity PendingIntent — never a Service.
      builder.setFullScreenIntent(contentPending, true)
    }

    return builder.build().also { notification ->
      notification.flags = notification.flags or Notification.FLAG_NO_CLEAR
    }
  }

  companion object {
    private const val TAG = "RNTelecomCallUI"
    const val CHANNEL_ID = "rn_telecom_calls_v4"
    const val NOTIFICATION_ID = 71001

    private const val REQUEST_FULL_SCREEN = 10
    private const val REQUEST_ANSWER = 11
    private const val REQUEST_DECLINE = 12

    const val ACTION_START = "com.telecom.action.START_CALL_UI"
    const val ACTION_STOP = "com.telecom.action.STOP_CALL_UI"
    const val ACTION_ANSWER = "com.telecom.action.ANSWER"
    const val ACTION_DECLINE = "com.telecom.action.DECLINE"
    const val ACTION_END = "com.telecom.action.END"
    const val ACTION_UPDATE_ONGOING = "com.telecom.action.UPDATE_ONGOING"

    const val EXTRA_UUID = "uuid"
    const val EXTRA_CALLER_NAME = "callerName"
    const val EXTRA_HANDLE = "handle"
    const val EXTRA_DIRECTION = "direction"
    const val EXTRA_SKIP_INCOMING_UI = "skipIncomingUi"
    const val EXTRA_AVATAR_URL = "avatarUrl"

    fun start(
      context: Context,
      uuid: String,
      callerName: String,
      handle: String,
      direction: String,
      skipIncomingUi: Boolean = false,
      avatarUrl: String = "",
    ) {
      val intent =
        Intent(context, TelecomCallService::class.java).apply {
          action = ACTION_START
          putExtra(EXTRA_UUID, uuid)
          putExtra(EXTRA_CALLER_NAME, callerName)
          putExtra(EXTRA_HANDLE, handle)
          putExtra(EXTRA_DIRECTION, direction)
          putExtra(EXTRA_SKIP_INCOMING_UI, skipIncomingUi)
          putExtra(EXTRA_AVATAR_URL, avatarUrl)
        }
      ContextCompat.startForegroundService(context, intent)
    }

    fun updateOngoing(
      context: Context,
      uuid: String,
      callerName: String,
      handle: String,
      avatarUrl: String = "",
    ) {
      val intent =
        Intent(context, TelecomCallService::class.java).apply {
          action = ACTION_UPDATE_ONGOING
          putExtra(EXTRA_UUID, uuid)
          putExtra(EXTRA_CALLER_NAME, callerName)
          putExtra(EXTRA_HANDLE, handle)
          putExtra(EXTRA_DIRECTION, "outgoing")
          putExtra(EXTRA_AVATAR_URL, avatarUrl)
        }
      context.startService(intent)
    }

    fun stop(context: Context) {
      TelecomRingtonePlayer.stop()
      TelecomCallUi.finishAll(context)
      val intent =
        Intent(context, TelecomCallService::class.java).apply {
          action = ACTION_STOP
        }
      context.startService(intent)
    }

    fun openInCall(
      context: Context,
      uuid: String,
      callerName: String,
      handle: String,
      direction: String,
      avatarUrl: String = "",
    ) {
      val intent =
        Intent(context, TelecomInCallActivity::class.java).apply {
          addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
              Intent.FLAG_ACTIVITY_CLEAR_TOP or
              Intent.FLAG_ACTIVITY_SINGLE_TOP,
          )
          putExtra(EXTRA_UUID, uuid)
          putExtra(EXTRA_CALLER_NAME, callerName)
          putExtra(EXTRA_HANDLE, handle)
          putExtra(EXTRA_DIRECTION, direction)
          putExtra(EXTRA_AVATAR_URL, avatarUrl)
        }
      try {
        context.startActivity(intent)
        Log.i(TAG, "in-call Activity launched uuid=$uuid direction=$direction")
      } catch (error: Exception) {
        Log.e(TAG, "in-call launch failed: ${error.message}", error)
      }
    }
  }
}
