package com.telecom

import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlResult
import androidx.core.telecom.CallControlScope

internal class CallSession(
  val uuid: String,
  private val controlScope: CallControlScope,
  private val hasVideo: Boolean,
  val displayName: String,
  val handle: String,
  val avatarUrl: String = "",
) {
  suspend fun answer(): CallControlResult {
    val callType =
      if (hasVideo) {
        CallAttributesCompat.CALL_TYPE_VIDEO_CALL
      } else {
        CallAttributesCompat.CALL_TYPE_AUDIO_CALL
      }
    controlScope.answer(callType)
    return controlScope.setActive()
  }

  suspend fun setActive(): CallControlResult {
    return controlScope.setActive()
  }

  suspend fun setInactive(): CallControlResult {
    return controlScope.setInactive()
  }

  suspend fun disconnect(cause: android.telecom.DisconnectCause): CallControlResult {
    return controlScope.disconnect(cause)
  }
}
