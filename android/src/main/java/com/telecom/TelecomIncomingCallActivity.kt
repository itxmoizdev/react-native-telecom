package com.telecom

import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Full-screen incoming host (Pixel-style layout + clear Decline / Answer taps).
 * Shows over the lock screen via showWhenLocked + turnScreenOn.
 */
class TelecomIncomingCallActivity : Activity() {
  private var uuid: String = ""
  private var handle: String = ""
  private var finishReceiver: BroadcastReceiver? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    applyLockScreenFlags()
    setContentView(R.layout.rn_telecom_incoming_call)

    val cfg = TelecomUiConfig.current
    uuid = intent.getStringExtra(TelecomCallService.EXTRA_UUID).orEmpty()
    val callerName =
      intent.getStringExtra(TelecomCallService.EXTRA_CALLER_NAME) ?: "Unknown"
    handle = intent.getStringExtra(TelecomCallService.EXTRA_HANDLE).orEmpty()
    val avatarUrl = intent.getStringExtra(TelecomCallService.EXTRA_AVATAR_URL).orEmpty()

    finishReceiver =
      TelecomCallUi.registerFinishReceiver(
        this,
        TelecomCallUi.ACTION_FINISH_INCOMING,
        uuid,
      ) {
        if (!isFinishing) finish()
      }

    findViewById<FrameLayout>(R.id.rn_telecom_root).setBackgroundColor(cfg.backgroundColor)

    findViewById<TextView>(R.id.rn_telecom_caller_name).apply {
      text = callerName
      setTextColor(cfg.nameColor)
    }

    findViewById<TextView>(R.id.rn_telecom_handle).apply {
      text =
        when {
          handle.isBlank() -> cfg.incomingTitle
          else -> "Mobile  ·  $handle"
        }
      setTextColor(cfg.handleColor)
    }

    findViewById<TextView>(R.id.rn_telecom_title).apply {
      text = cfg.incomingTitle
      setTextColor(cfg.titleColor)
    }

    val avatarHost =
      findViewById<TextView>(R.id.rn_telecom_avatar_initials).parent as View
    avatarHost.post { clipCircle(avatarHost) }
    TelecomAvatarLoader.bind(
      imageView = findViewById(R.id.rn_telecom_avatar_image),
      initialsView = findViewById(R.id.rn_telecom_avatar_initials),
      avatarUrl = avatarUrl,
      callerName = callerName,
    )

    findViewById<TextView>(R.id.rn_telecom_message).apply {
      text = cfg.messageLabel
      setCompoundDrawablesRelativeWithIntrinsicBounds(
        R.drawable.rn_telecom_ic_message,
        0,
        0,
        0,
      )
      setOnClickListener { openMessage() }
    }

    findViewById<TextView>(R.id.rn_telecom_decline_label).text = cfg.declineLabel
    findViewById<TextView>(R.id.rn_telecom_answer_label).text = cfg.answerLabel

    findViewById<View>(R.id.rn_telecom_decline).setOnClickListener { onDecline() }
    findViewById<View>(R.id.rn_telecom_answer).setOnClickListener { onAnswer() }
  }

  private fun clipCircle(view: View) {
    view.outlineProvider =
      object : ViewOutlineProvider() {
        override fun getOutline(v: View, outline: Outline) {
          outline.setOval(0, 0, v.width, v.height)
        }
      }
    view.clipToOutline = true
  }

  private fun applyLockScreenFlags() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
    }
    @Suppress("DEPRECATION")
    window.addFlags(
      WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
        WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
    )

    WindowCompat.setDecorFitsSystemWindows(window, true)
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT
    WindowInsetsControllerCompat(window, window.decorView).apply {
      isAppearanceLightStatusBars = true
      isAppearanceLightNavigationBars = true
    }
  }

  private fun onAnswer() {
    TelecomRingtonePlayer.stop()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      val keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
      keyguard.requestDismissKeyguard(this, null)
    }
    CallSessionManager.instance?.answerCall(uuid)
    finish()
  }

  private fun onDecline() {
    TelecomRingtonePlayer.stop()
    CallSessionManager.instance?.endCall(uuid)
    finish()
  }

  private fun openMessage() {
    if (handle.isBlank()) return
    try {
      val intent =
        Intent(Intent.ACTION_SENDTO).apply {
          data = Uri.parse("smsto:${Uri.encode(handle)}")
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      startActivity(intent)
    } catch (_: Exception) {
    }
  }

  override fun onDestroy() {
    finishReceiver?.let {
      try {
        unregisterReceiver(it)
      } catch (_: Exception) {
      }
    }
    finishReceiver = null
    super.onDestroy()
  }
}
