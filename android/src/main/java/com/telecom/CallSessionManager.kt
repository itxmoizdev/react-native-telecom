package com.telecom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.telecom.DisconnectCause
import androidx.core.content.ContextCompat
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallsManager
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class CallSessionManager(
  private val context: Context,
  private var emit: (eventName: String, params: WritableMap) -> Unit,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val callsManager = CallsManager(context)
  private val sessions = ConcurrentHashMap<String, CallSession>()
  private val starting = ConcurrentHashMap.newKeySet<String>()
  private val pendingAutoAnswer = ConcurrentHashMap.newKeySet<String>()
  private var registered = false
  private val audioRouter = TelecomAudioRouter(context) { name, params -> emit(name, params) }

  init {
    instance = this
  }

  fun rebindEmitter(next: (eventName: String, params: WritableMap) -> Unit) {
    emit = next
  }

  fun setup(supportsVideo: Boolean, ui: TelecomUiConfig? = null): Boolean {
    if (ui != null) {
      TelecomUiConfig.current = ui
    }
    ensureManageOwnCallsPermission()

    var capabilities = CallsManager.CAPABILITY_BASELINE
    if (supportsVideo) {
      capabilities = capabilities or CallsManager.CAPABILITY_SUPPORTS_VIDEO_CALLING
    }

    callsManager.registerAppWithTelecom(capabilities)
    registered = true
    return true
  }

  fun displayIncomingCall(
    uuid: String,
    handle: String,
    callerName: String,
    hasVideo: Boolean,
    autoAnswer: Boolean,
    avatarUrl: String = "",
  ) {
    if (autoAnswer) {
      pendingAutoAnswer.add(uuid)
    }
    addCall(
      uuid = uuid,
      handle = handle,
      callerName = callerName,
      hasVideo = hasVideo,
      direction = CallAttributesCompat.DIRECTION_INCOMING,
      avatarUrl = avatarUrl,
    )
  }

  fun startCall(
    uuid: String,
    handle: String,
    callerName: String,
    hasVideo: Boolean,
    avatarUrl: String = "",
  ) {
    addCall(
      uuid = uuid,
      handle = handle,
      callerName = callerName,
      hasVideo = hasVideo,
      direction = CallAttributesCompat.DIRECTION_OUTGOING,
      avatarUrl = avatarUrl,
    )
  }

  fun answerCall(uuid: String) {
    scope.launch {
      val session = sessions[uuid]
      if (session == null) {
        android.util.Log.w(TAG, "answerCall ignored — no session for $uuid")
        return@launch
      }

      TelecomRingtonePlayer.stop()
      TelecomCallUi.finishIncoming(context, uuid)

      val result = session.answer()
      android.util.Log.i(TAG, "answerCall uuid=$uuid result=$result")
      audioRouter.activate(uuid)
      emit(
        EVENT_ANSWER_CALL,
        Arguments.createMap().apply {
          putString("callUUID", uuid)
        },
      )

      TelecomCallService.updateOngoing(
        context = context,
        uuid = uuid,
        callerName = session.displayName,
        handle = session.handle,
        avatarUrl = session.avatarUrl,
      )
      TelecomCallService.openInCall(
        context = context,
        uuid = uuid,
        callerName = session.displayName,
        handle = session.handle,
        direction = "incoming",
        avatarUrl = session.avatarUrl,
      )
    }
  }

  fun endCall(uuid: String) {
    scope.launch {
      TelecomRingtonePlayer.stop()
      TelecomCallUi.finishAll(context, uuid)
      val session = sessions.remove(uuid)
      starting.remove(uuid)
      pendingAutoAnswer.remove(uuid)
      if (session != null) {
        session.disconnect(DisconnectCause(DisconnectCause.LOCAL))
      }
      if (sessions.isEmpty()) {
        audioRouter.deactivate(uuid)
      }
      maybeStopCallUi()
      emit(
        EVENT_END_CALL,
        Arguments.createMap().apply {
          putString("callUUID", uuid)
        },
      )
    }
  }

  fun endAllCalls() {
    scope.launch {
      TelecomRingtonePlayer.stop()
      TelecomCallUi.finishAll(context)
      sessions.keys.toList().forEach { uuid ->
        val session = sessions.remove(uuid)
        starting.remove(uuid)
        pendingAutoAnswer.remove(uuid)
        session?.disconnect(DisconnectCause(DisconnectCause.LOCAL))
        emit(
          EVENT_END_CALL,
          Arguments.createMap().apply {
            putString("callUUID", uuid)
          },
        )
      }
      audioRouter.deactivate()
      TelecomCallService.stop(context)
    }
  }

  fun setMuted(uuid: String, muted: Boolean) {
    if (!sessions.containsKey(uuid)) {
      android.util.Log.w(TAG, "setMuted ignored — no active session for $uuid")
      return
    }

    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    audioManager.isMicrophoneMute = muted
    android.util.Log.i(TAG, "setMuted uuid=$uuid muted=$muted actual=${audioManager.isMicrophoneMute}")

    emit(
      EVENT_DID_CHANGE_MUTE_STATE,
      Arguments.createMap().apply {
        putString("callUUID", uuid)
        putBoolean("muted", muted)
      },
    )
  }

  fun setAudioRoute(uuid: String, route: String) {
    if (!sessions.containsKey(uuid)) return
    audioRouter.setRoute(uuid, route)
  }

  fun getAvailableAudioRoutes(): WritableArray = audioRouter.getAvailableRoutes()

  fun cycleAudioRoute(uuid: String): String {
    if (!sessions.containsKey(uuid)) return audioRouter.currentRouteId()
    return audioRouter.cycleRoute(uuid)
  }

  fun currentAudioRoute(): String = audioRouter.currentRouteId()

  fun sendDtmf(uuid: String, digits: String) {
    if (!sessions.containsKey(uuid)) return
    scope.launch(Dispatchers.Default) {
      var tone: ToneGenerator? = null
      try {
        tone = ToneGenerator(AudioManager.STREAM_DTMF, 80)
        digits.forEach { ch ->
          val digit = ch.toString()
          val toneType = dtmfTone(digit) ?: return@forEach
          try {
            tone.startTone(toneType, 160)
          } catch (_: Exception) {
          }
          emit(
            EVENT_DTMF,
            Arguments.createMap().apply {
              putString("callUUID", uuid)
              putString("digit", digit)
            },
          )
          delay(180)
        }
      } catch (_: Exception) {
      } finally {
        tone?.release()
      }
    }
  }

  fun emitSpeakerChanged(uuid: String, speakerOn: Boolean) {
    setAudioRoute(uuid, if (speakerOn) "speaker" else "earpiece")
  }

  fun emitDtmf(uuid: String, digit: String) {
    emit(
      EVENT_DTMF,
      Arguments.createMap().apply {
        putString("callUUID", uuid)
        putString("digit", digit)
      },
    )
  }

  fun setOnHold(uuid: String, hold: Boolean) {
    scope.launch {
      val session = sessions[uuid]
      if (session == null) {
        android.util.Log.w(TAG, "setOnHold ignored — no active session for $uuid")
        return@launch
      }

      val result =
        if (hold) {
          session.setInactive()
        } else {
          session.setActive()
        }
      android.util.Log.i(TAG, "setOnHold uuid=$uuid hold=$hold result=$result")

      emit(
        EVENT_DID_CHANGE_HOLD_STATE,
        Arguments.createMap().apply {
          putString("callUUID", uuid)
          putBoolean("hold", hold)
        },
      )
    }
  }

  private fun addCall(
    uuid: String,
    handle: String,
    callerName: String,
    hasVideo: Boolean,
    direction: Int,
    avatarUrl: String,
  ) {
    if (!registered) {
      setup(supportsVideo = true)
    }

    ensureManageOwnCallsPermission()

    if (sessions.containsKey(uuid) || !starting.add(uuid)) {
      return
    }

    val address = Uri.parse("tel:${handle.ifBlank { "unknown" }}")
    val callType =
      if (hasVideo) {
        CallAttributesCompat.CALL_TYPE_VIDEO_CALL
      } else {
        CallAttributesCompat.CALL_TYPE_AUDIO_CALL
      }

    val attributes =
      CallAttributesCompat(
        displayName = callerName.ifBlank { handle }.ifBlank { "Unknown" },
        address = address,
        direction = direction,
        callType = callType,
        callCapabilities = CallAttributesCompat.SUPPORTS_SET_INACTIVE,
      )

    val directionLabel =
      if (direction == CallAttributesCompat.DIRECTION_INCOMING) {
        "incoming"
      } else {
        "outgoing"
      }

    scope.launch {
      try {
        callsManager.addCall(
          callAttributes = attributes,
          onAnswer = { _ ->
            emit(
              EVENT_ANSWER_CALL,
              Arguments.createMap().apply {
                putString("callUUID", uuid)
              },
            )
          },
          onDisconnect = { _ ->
            sessions.remove(uuid)
            starting.remove(uuid)
            pendingAutoAnswer.remove(uuid)
            TelecomRingtonePlayer.stop()
            TelecomCallUi.finishAll(context, uuid)
            if (sessions.isEmpty()) {
              audioRouter.deactivate(uuid)
            }
            maybeStopCallUi()
            emit(
              EVENT_END_CALL,
              Arguments.createMap().apply {
                putString("callUUID", uuid)
              },
            )
          },
          onSetActive = {
            emit(
              EVENT_DID_CHANGE_HOLD_STATE,
              Arguments.createMap().apply {
                putString("callUUID", uuid)
                putBoolean("hold", false)
              },
            )
          },
          onSetInactive = {
            emit(
              EVENT_DID_CHANGE_HOLD_STATE,
              Arguments.createMap().apply {
                putString("callUUID", uuid)
                putBoolean("hold", true)
              },
            )
          },
        ) {
          val session = CallSession(uuid, this, hasVideo, callerName, handle, avatarUrl)
          sessions[uuid] = session
          starting.remove(uuid)

          TelecomCallService.start(
            context = context,
            uuid = uuid,
            callerName = callerName.ifBlank { handle }.ifBlank { "Unknown" },
            handle = handle,
            direction = directionLabel,
            skipIncomingUi = pendingAutoAnswer.contains(uuid),
            avatarUrl = avatarUrl,
          )

          emit(
            EVENT_DID_DISPLAY_INCOMING_CALL,
            Arguments.createMap().apply {
              putString("callUUID", uuid)
              putString("handle", handle)
              putString("callerName", callerName)
              putBoolean("hasVideo", hasVideo)
              putString("direction", directionLabel)
              putString("avatarUrl", avatarUrl)
            },
          )

          if (direction == CallAttributesCompat.DIRECTION_OUTGOING) {
            launch {
              setActive()
              audioRouter.activate(uuid)
            }
          }

          if (pendingAutoAnswer.remove(uuid)) {
            launch {
              delay(350)
              answerCall(uuid)
            }
          }
        }
      } catch (error: Exception) {
        sessions.remove(uuid)
        starting.remove(uuid)
        pendingAutoAnswer.remove(uuid)
        maybeStopCallUi()
        emit(
          EVENT_END_CALL,
          Arguments.createMap().apply {
            putString("callUUID", uuid)
            putString("error", error.message ?: "Failed to add call")
          },
        )
      }
    }
  }

  private fun maybeStopCallUi() {
    if (sessions.isEmpty()) {
      TelecomCallService.stop(context)
    }
  }

  private fun ensureManageOwnCallsPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      throw IllegalStateException("react-native-telecom requires Android 8.0 (API 26)+")
    }

    val granted =
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.MANAGE_OWN_CALLS,
      ) == PackageManager.PERMISSION_GRANTED

    if (!granted) {
      throw SecurityException(
        "Missing android.permission.MANAGE_OWN_CALLS. See docs/android-setup.md"
      )
    }
  }

  private fun dtmfTone(digit: String): Int? {
    return when (digit) {
      "0" -> ToneGenerator.TONE_DTMF_0
      "1" -> ToneGenerator.TONE_DTMF_1
      "2" -> ToneGenerator.TONE_DTMF_2
      "3" -> ToneGenerator.TONE_DTMF_3
      "4" -> ToneGenerator.TONE_DTMF_4
      "5" -> ToneGenerator.TONE_DTMF_5
      "6" -> ToneGenerator.TONE_DTMF_6
      "7" -> ToneGenerator.TONE_DTMF_7
      "8" -> ToneGenerator.TONE_DTMF_8
      "9" -> ToneGenerator.TONE_DTMF_9
      "*" -> ToneGenerator.TONE_DTMF_S
      "#" -> ToneGenerator.TONE_DTMF_P
      else -> null
    }
  }

  companion object {
    private const val TAG = "RNTelecom"

    @Volatile
    var instance: CallSessionManager? = null

    const val EVENT_ANSWER_CALL = "answerCall"
    const val EVENT_END_CALL = "endCall"
    const val EVENT_DID_DISPLAY_INCOMING_CALL = "didDisplayIncomingCall"
    const val EVENT_DID_CHANGE_HOLD_STATE = "didChangeHoldState"
    const val EVENT_DID_CHANGE_MUTE_STATE = "didChangeMuteState"
    const val EVENT_DID_CHANGE_SPEAKER = "didChangeSpeaker"
    const val EVENT_DTMF = "dtmfTone"
  }
}
