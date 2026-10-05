package com.telecom

import android.graphics.Color
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.modules.core.DeviceEventManagerModule

@ReactModule(name = TelecomModule.NAME)
class TelecomModule(reactContext: ReactApplicationContext) :
  NativeTelecomSpec(reactContext) {

  private val sessionManager: CallSessionManager =
    CallSessionManager.instance?.also { existing ->
      existing.rebindEmitter { eventName, params -> emitEvent(eventName, params) }
    }
      ?: CallSessionManager(reactContext.applicationContext) { eventName, params ->
        emitEvent(eventName, params)
      }

  override fun setup(options: ReadableMap, promise: Promise) {
    try {
      val supportsVideo =
        if (options.hasKey("supportsVideo")) {
          options.getBoolean("supportsVideo")
        } else {
          true
        }

      val ui = parseUiConfig(options)
      val accepted = sessionManager.setup(supportsVideo, ui)
      promise.resolve(accepted)
    } catch (error: Exception) {
      promise.reject("E_TELECOM_SETUP", error.message, error)
    }
  }

  override fun displayIncomingCall(
    uuid: String,
    handle: String,
    callerName: String,
    hasVideo: Boolean,
    autoAnswer: Boolean,
    avatarUrl: String,
  ) {
    sessionManager.displayIncomingCall(
      uuid,
      handle,
      callerName,
      hasVideo,
      autoAnswer,
      avatarUrl,
    )
  }

  override fun startCall(
    uuid: String,
    handle: String,
    callerName: String,
    hasVideo: Boolean,
    avatarUrl: String,
  ) {
    sessionManager.startCall(uuid, handle, callerName, hasVideo, avatarUrl)
  }

  override fun answerCall(uuid: String) {
    sessionManager.answerCall(uuid)
  }

  override fun endCall(uuid: String) {
    sessionManager.endCall(uuid)
  }

  override fun endAllCalls() {
    sessionManager.endAllCalls()
  }

  override fun setMuted(uuid: String, muted: Boolean) {
    sessionManager.setMuted(uuid, muted)
  }

  override fun setOnHold(uuid: String, hold: Boolean) {
    sessionManager.setOnHold(uuid, hold)
  }

  override fun setAudioRoute(uuid: String, route: String) {
    sessionManager.setAudioRoute(uuid, route)
  }

  override fun getAvailableAudioRoutes(promise: Promise) {
    try {
      promise.resolve(sessionManager.getAvailableAudioRoutes())
    } catch (error: Exception) {
      promise.reject("E_AUDIO_ROUTES", error.message, error)
    }
  }

  override fun sendDtmf(uuid: String, digits: String) {
    sessionManager.sendDtmf(uuid, digits)
  }

  override fun handlePushMessage(data: ReadableMap, promise: Promise) {
    try {
      val map = HashMap<String, String>()
      val iterator = data.keySetIterator()
      while (iterator.hasNextKey()) {
        val key = iterator.nextKey()
        when (data.getType(key)) {
          com.facebook.react.bridge.ReadableType.String ->
            map[key] = data.getString(key).orEmpty()
          com.facebook.react.bridge.ReadableType.Boolean ->
            map[key] = if (data.getBoolean(key)) "true" else "false"
          com.facebook.react.bridge.ReadableType.Number ->
            map[key] = data.getDouble(key).toString()
          else -> Unit
        }
      }
      val handled =
        TelecomPushBridge.handleIncomingCall(reactApplicationContext, map)
      promise.resolve(handled)
    } catch (error: Exception) {
      promise.reject("E_PUSH", error.message, error)
    }
  }

  override fun addListener(eventName: String) {
    // Required for NativeEventEmitter / RCTDeviceEventEmitter.
  }

  override fun removeListeners(count: Double) {
    // Required for NativeEventEmitter / RCTDeviceEventEmitter.
  }

  private fun parseUiConfig(options: ReadableMap): TelecomUiConfig? {
    if (!options.hasKey("ui") || options.isNull("ui")) {
      return null
    }
    val map = options.getMap("ui") ?: return null
    val base = TelecomUiConfig.current

    return base.copy(
      incomingTitle = map.stringOr(base.incomingTitle, "incomingTitle"),
      answerLabel = map.stringOr(base.answerLabel, "answerLabel"),
      declineLabel = map.stringOr(base.declineLabel, "declineLabel"),
      backgroundColor = map.colorOr(base.backgroundColor, "backgroundColor"),
      titleColor = map.colorOr(base.titleColor, "titleColor"),
      nameColor = map.colorOr(base.nameColor, "nameColor"),
      handleColor = map.colorOr(base.handleColor, "handleColor"),
      answerColor = map.colorOr(base.answerColor, "answerColor"),
      declineColor = map.colorOr(base.declineColor, "declineColor"),
      launchFullScreenOnIncoming =
        if (map.hasKey("launchFullScreenOnIncoming") && !map.isNull("launchFullScreenOnIncoming")) {
          map.getBoolean("launchFullScreenOnIncoming")
        } else {
          base.launchFullScreenOnIncoming
        },
    )
  }

  private fun ReadableMap.stringOr(fallback: String, key: String): String {
    return if (hasKey(key) && !isNull(key)) getString(key) ?: fallback else fallback
  }

  private fun ReadableMap.colorOr(fallback: Int, key: String): Int {
    if (!hasKey(key) || isNull(key)) return fallback
    val raw = getString(key) ?: return fallback
    return try {
      Color.parseColor(raw)
    } catch (_: Exception) {
      fallback
    }
  }

  private fun emitEvent(eventName: String, params: WritableMap) {
    if (!reactApplicationContext.hasActiveReactInstance()) {
      return
    }

    reactApplicationContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      .emit(eventName, params)
  }

  companion object {
    const val NAME = "Telecom"
  }
}
