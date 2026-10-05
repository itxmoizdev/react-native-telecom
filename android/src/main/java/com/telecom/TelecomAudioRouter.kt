package com.telecom

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap

/**
 * VoIP audio routes: earpiece, speaker, bluetooth SCO.
 * Call [activate] when a call becomes active; [deactivate] when all calls end.
 */
internal class TelecomAudioRouter(
  private val context: Context,
  private val emit: (eventName: String, params: WritableMap) -> Unit,
) {
  enum class Route(val id: String) {
    EARPIECE("earpiece"),
    SPEAKER("speaker"),
    BLUETOOTH("bluetooth"),
  }

  private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  private var current: Route = Route.EARPIECE
  private var focusRequest: android.media.AudioFocusRequest? = null

  fun activate(uuid: String) {
    am.mode = AudioManager.MODE_IN_COMMUNICATION
    requestFocus()
    applyRoute(current)
    emit(
      EVENT_AUDIO_ACTIVATED,
      Arguments.createMap().apply {
        putString("callUUID", uuid)
        putString("route", current.id)
      },
    )
    Log.i(TAG, "audio session activated uuid=$uuid route=${current.id}")
  }

  fun deactivate(uuid: String? = null) {
    stopBluetoothSco()
    @Suppress("DEPRECATION")
    am.isSpeakerphoneOn = false
    am.mode = AudioManager.MODE_NORMAL
    abandonFocus()
    emit(
      EVENT_AUDIO_DEACTIVATED,
      Arguments.createMap().apply {
        if (!uuid.isNullOrBlank()) putString("callUUID", uuid)
      },
    )
    Log.i(TAG, "audio session deactivated")
  }

  fun setRoute(uuid: String, routeId: String) {
    val route =
      Route.entries.find { it.id == routeId }
        ?: run {
          Log.w(TAG, "unknown route=$routeId")
          return
        }
    current = route
    am.mode = AudioManager.MODE_IN_COMMUNICATION
    applyRoute(route)
    emit(
      EVENT_DID_CHANGE_AUDIO_ROUTE,
      Arguments.createMap().apply {
        putString("callUUID", uuid)
        putString("route", route.id)
      },
    )
    // Back-compat for older listeners.
    emit(
      CallSessionManager.EVENT_DID_CHANGE_SPEAKER,
      Arguments.createMap().apply {
        putString("callUUID", uuid)
        putBoolean("speakerOn", route == Route.SPEAKER)
      },
    )
  }

  fun getAvailableRoutes(): WritableArray {
    val list = Arguments.createArray()
    list.pushMap(routeMap(Route.EARPIECE, "Phone", true))
    list.pushMap(routeMap(Route.SPEAKER, "Speaker", true))
    list.pushMap(routeMap(Route.BLUETOOTH, "Bluetooth", isBluetoothAvailable()))
    return list
  }

  fun currentRouteId(): String = current.id

  fun cycleRoute(uuid: String): String {
    val available =
      listOf(Route.EARPIECE, Route.SPEAKER, Route.BLUETOOTH).filter { route ->
        route != Route.BLUETOOTH || isBluetoothAvailable()
      }
    val idx = available.indexOf(current).let { if (it < 0) 0 else (it + 1) % available.size }
    val next = available[idx]
    setRoute(uuid, next.id)
    return next.id
  }

  private fun applyRoute(route: Route) {
    when (route) {
      Route.SPEAKER -> {
        stopBluetoothSco()
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = true
      }
      Route.EARPIECE -> {
        stopBluetoothSco()
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = false
      }
      Route.BLUETOOTH -> {
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = false
        startBluetoothSco()
      }
    }
  }

  private fun startBluetoothSco() {
    try {
      @Suppress("DEPRECATION")
      am.startBluetoothSco()
      @Suppress("DEPRECATION")
      am.isBluetoothScoOn = true
    } catch (error: Exception) {
      Log.w(TAG, "startBluetoothSco failed: ${error.message}")
    }
  }

  private fun stopBluetoothSco() {
    try {
      @Suppress("DEPRECATION")
      am.isBluetoothScoOn = false
      @Suppress("DEPRECATION")
      am.stopBluetoothSco()
    } catch (_: Exception) {
    }
  }

  private fun isBluetoothAvailable(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
          PackageManager.PERMISSION_GRANTED
      if (!granted) return false
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
      if (devices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }) {
        return true
      }
    }
    return try {
      val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
      adapter.isEnabled && adapter.getProfileConnectionState(BluetoothProfile.HEADSET) ==
        BluetoothAdapter.STATE_CONNECTED
    } catch (_: SecurityException) {
      false
    } catch (_: Exception) {
      false
    }
  }

  private fun requestFocus() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val attrs =
        android.media.AudioAttributes.Builder()
          .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
          .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
          .build()
      val req =
        android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
          .setAudioAttributes(attrs)
          .setAcceptsDelayedFocusGain(true)
          .setOnAudioFocusChangeListener { }
          .build()
      focusRequest = req
      am.requestAudioFocus(req)
    } else {
      @Suppress("DEPRECATION")
      am.requestAudioFocus(
        null,
        AudioManager.STREAM_VOICE_CALL,
        AudioManager.AUDIOFOCUS_GAIN,
      )
    }
  }

  private fun abandonFocus() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      focusRequest?.let { am.abandonAudioFocusRequest(it) }
      focusRequest = null
    } else {
      @Suppress("DEPRECATION")
      am.abandonAudioFocus(null)
    }
  }

  private fun routeMap(route: Route, name: String, available: Boolean): WritableMap {
    return Arguments.createMap().apply {
      putString("id", route.id)
      putString("name", name)
      putBoolean("available", available)
      putBoolean("selected", current == route)
    }
  }

  companion object {
    private const val TAG = "RNTelecomAudio"
    const val EVENT_AUDIO_ACTIVATED = "didActivateAudioSession"
    const val EVENT_AUDIO_DEACTIVATED = "didDeactivateAudioSession"
    const val EVENT_DID_CHANGE_AUDIO_ROUTE = "didChangeAudioRoute"
  }
}
