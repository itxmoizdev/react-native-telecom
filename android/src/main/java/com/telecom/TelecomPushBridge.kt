package com.telecom

import android.content.Context
import android.util.Log

/**
 * Native entry for FCM / push data messages when the JS runtime may be dead.
 *
 * Expected data keys (string values):
 * - `uuid` or `callUUID` (required)
 * - `handle` or `number` (required)
 * - `callerName` or `name` (optional)
 * - `hasVideo` ("true"/"1")
 * - `autoAnswer` ("true"/"1")
 * - `avatarUrl` or `avatar` (optional https/file url)
 *
 * Call from your `FirebaseMessagingService.onMessageReceived` for high-priority
 * data messages with `type=incoming_call` (or always if your payload is call-only).
 */
object TelecomPushBridge {
  private const val TAG = "RNTelecomPush"

  @JvmStatic
  fun isIncomingCallPayload(data: Map<String, String>): Boolean {
    val type = data["type"] ?: data["callType"] ?: ""
    if (type.equals("incoming_call", ignoreCase = true) ||
      type.equals("call", ignoreCase = true)
    ) {
      return true
    }
    val uuid = data["uuid"] ?: data["callUUID"]
    val handle = data["handle"] ?: data["number"]
    return !uuid.isNullOrBlank() && !handle.isNullOrBlank()
  }

  /**
   * @return true if an incoming call UI was started
   */
  @JvmStatic
  fun handleIncomingCall(context: Context, data: Map<String, String>): Boolean {
    if (!isIncomingCallPayload(data)) {
      Log.i(TAG, "payload ignored — not an incoming call")
      return false
    }

    val uuid = (data["uuid"] ?: data["callUUID"]).orEmpty()
    val handle = (data["handle"] ?: data["number"]).orEmpty()
    if (uuid.isBlank() || handle.isBlank()) {
      Log.w(TAG, "payload missing uuid/handle")
      return false
    }

    val callerName = data["callerName"] ?: data["name"] ?: handle
    val hasVideo = truthy(data["hasVideo"])
    val autoAnswer = truthy(data["autoAnswer"])
    val avatarUrl = data["avatarUrl"] ?: data["avatar"] ?: ""

    val appContext = context.applicationContext
    val manager =
      CallSessionManager.instance
        ?: CallSessionManager(appContext) { _, _ ->
          // JS may be dead; events are no-op until module binds.
        }.also {
          it.setup(supportsVideo = true)
        }

    Log.i(TAG, "push → displayIncomingCall uuid=$uuid handle=$handle")
    manager.displayIncomingCall(
      uuid = uuid,
      handle = handle,
      callerName = callerName,
      hasVideo = hasVideo,
      autoAnswer = autoAnswer,
      avatarUrl = avatarUrl,
    )
    return true
  }

  private fun truthy(value: String?): Boolean {
    return value.equals("true", ignoreCase = true) || value == "1" || value.equals("yes", ignoreCase = true)
  }
}
