package com.telecom

import com.facebook.react.bridge.ReactApplicationContext

class TelecomModule(reactContext: ReactApplicationContext) :
  NativeTelecomSpec(reactContext) {

  override fun multiply(a: Double, b: Double): Double {
    return a * b
  }

  companion object {
    const val NAME = NativeTelecomSpec.NAME
  }
}
