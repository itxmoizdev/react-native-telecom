package com.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.app.Activity
import android.os.Build

/**
 * Closes call Activities when the session is answered / ended from any path
 * (auto-answer, notification, JS, other Activity).
 */
internal object TelecomCallUi {
  const val ACTION_FINISH_INCOMING = "com.telecom.action.FINISH_INCOMING"
  const val ACTION_FINISH_INCALL = "com.telecom.action.FINISH_INCALL"
  const val EXTRA_UUID = "uuid"

  fun finishIncoming(context: Context, uuid: String? = null) {
    context.sendBroadcast(
      Intent(ACTION_FINISH_INCOMING).apply {
        setPackage(context.packageName)
        if (!uuid.isNullOrBlank()) putExtra(EXTRA_UUID, uuid)
      },
    )
  }

  fun finishInCall(context: Context, uuid: String? = null) {
    context.sendBroadcast(
      Intent(ACTION_FINISH_INCALL).apply {
        setPackage(context.packageName)
        if (!uuid.isNullOrBlank()) putExtra(EXTRA_UUID, uuid)
      },
    )
  }

  fun finishAll(context: Context, uuid: String? = null) {
    finishIncoming(context, uuid)
    finishInCall(context, uuid)
  }

  fun registerFinishReceiver(
    activity: Activity,
    action: String,
    uuid: String,
    onFinish: () -> Unit,
  ): BroadcastReceiver {
    val receiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
          val target = intent?.getStringExtra(EXTRA_UUID)
          if (target.isNullOrBlank() || target == uuid) {
            onFinish()
          }
        }
      }
    val filter = IntentFilter(action)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      activity.registerReceiver(receiver, filter)
    }
    return receiver
  }
}
