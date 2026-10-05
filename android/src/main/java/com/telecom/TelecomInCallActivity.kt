package com.telecom

import android.app.Activity
import android.content.BroadcastReceiver
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Outgoing + answered in-call UI (Material You style).
 * Override [R.layout.rn_telecom_incall] in the host app to restyle.
 */
class TelecomInCallActivity : Activity() {
  private var uuid: String = ""
  private var direction: String = "outgoing"
  private var muted = false
  private var onHold = false
  private var keypadVisible = false
  private var audioRoute = "earpiece"
  private var toneGenerator: ToneGenerator? = null
  private var finishReceiver: BroadcastReceiver? = null

  private lateinit var statusView: TextView
  private lateinit var muteControl: View
  private lateinit var keypadControl: View
  private lateinit var speakerControl: View
  private lateinit var holdControl: View
  private lateinit var keypad: GridLayout
  private lateinit var controlsRow: View
  private lateinit var avatar: View

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    applyLightChrome()
    setContentView(R.layout.rn_telecom_incall)

    uuid = intent.getStringExtra(TelecomCallService.EXTRA_UUID).orEmpty()
    val callerName =
      intent.getStringExtra(TelecomCallService.EXTRA_CALLER_NAME) ?: "Unknown"
    val handle = intent.getStringExtra(TelecomCallService.EXTRA_HANDLE).orEmpty()
    direction = intent.getStringExtra(TelecomCallService.EXTRA_DIRECTION) ?: "outgoing"
    val avatarUrl = intent.getStringExtra(TelecomCallService.EXTRA_AVATAR_URL).orEmpty()

    finishReceiver =
      TelecomCallUi.registerFinishReceiver(
        this,
        TelecomCallUi.ACTION_FINISH_INCALL,
        uuid,
      ) {
        if (!isFinishing) finish()
      }

    statusView = findViewById(R.id.rn_telecom_incall_status)
    statusView.text = if (direction == "incoming") "Connected" else "Calling…"
    findViewById<TextView>(R.id.rn_telecom_incall_name).text = callerName
    findViewById<TextView>(R.id.rn_telecom_incall_handle).text =
      if (handle.isBlank()) "" else "Mobile  ·  $handle"
    avatar = findViewById(R.id.rn_telecom_incall_avatar)
    avatar.post { clipCircle(avatar) }
    TelecomAvatarLoader.bind(
      imageView = findViewById(R.id.rn_telecom_incall_avatar_image),
      initialsView = findViewById(R.id.rn_telecom_incall_initials),
      avatarUrl = avatarUrl,
      callerName = callerName,
    )

    controlsRow = findViewById(R.id.rn_telecom_controls)
    keypad = findViewById(R.id.rn_telecom_keypad)

    muteControl = findViewById(R.id.rn_telecom_btn_mute)
    keypadControl = findViewById(R.id.rn_telecom_btn_keypad)
    speakerControl = findViewById(R.id.rn_telecom_btn_speaker)
    holdControl = findViewById(R.id.rn_telecom_btn_hold)

    bindControl(muteControl, R.drawable.rn_telecom_ic_mic, "Mute")
    bindControl(keypadControl, R.drawable.rn_telecom_ic_dialpad, "Keypad")
    bindControl(speakerControl, R.drawable.rn_telecom_ic_speaker, "Audio")
    bindControl(holdControl, R.drawable.rn_telecom_ic_hold, "Hold")
    audioRoute = CallSessionManager.instance?.currentAudioRoute() ?: "earpiece"
    refreshAudioRouteLabel()

    buildKeypad(keypad)

    muteControl.setOnClickListener {
      muted = !muted
      CallSessionManager.instance?.setMuted(uuid, muted)
      setControlActive(
        muteControl,
        muted,
        if (muted) R.drawable.rn_telecom_ic_mic_off else R.drawable.rn_telecom_ic_mic,
        if (muted) "Unmute" else "Mute",
      )
    }

    speakerControl.setOnClickListener {
      audioRoute = CallSessionManager.instance?.cycleAudioRoute(uuid) ?: audioRoute
      refreshAudioRouteLabel()
    }

    holdControl.setOnClickListener {
      onHold = !onHold
      CallSessionManager.instance?.setOnHold(uuid, onHold)
      setControlActive(
        holdControl,
        onHold,
        R.drawable.rn_telecom_ic_hold,
        if (onHold) "Resume" else "Hold",
      )
      statusView.text =
        when {
          onHold -> "On hold"
          direction == "outgoing" && !onHold -> "Connected"
          else -> "Connected"
        }
    }

    keypadControl.setOnClickListener {
      keypadVisible = !keypadVisible
      keypad.visibility = if (keypadVisible) View.VISIBLE else View.GONE
      avatar.visibility = if (keypadVisible) View.GONE else View.VISIBLE
      setControlActive(
        keypadControl,
        keypadVisible,
        R.drawable.rn_telecom_ic_dialpad,
        if (keypadVisible) "Hide" else "Keypad",
      )
    }

    findViewById<View>(R.id.rn_telecom_btn_end).setOnClickListener {
      CallSessionManager.instance?.endCall(uuid)
      finish()
    }

    try {
      toneGenerator = ToneGenerator(AudioManager.STREAM_DTMF, 80)
    } catch (_: Exception) {
      toneGenerator = null
    }
  }

  private fun refreshAudioRouteLabel() {
    val label =
      when (audioRoute) {
        "speaker" -> "Speaker"
        "bluetooth" -> "Bluetooth"
        else -> "Phone"
      }
    setControlActive(
      speakerControl,
      audioRoute != "earpiece",
      R.drawable.rn_telecom_ic_speaker,
      label,
    )
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

  private fun applyLightChrome() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
    }
    @Suppress("DEPRECATION")
    window.addFlags(
      WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
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

  private fun bindControl(root: View, iconRes: Int, label: String) {
    val icon = root.findViewById<ImageView>(R.id.rn_telecom_control_icon)
    icon.setImageResource(iconRes)
    icon.clearColorFilter()
    root.findViewById<TextView>(R.id.rn_telecom_control_label).text = label
    root.findViewById<FrameLayout>(R.id.rn_telecom_control_icon_bg)
      .setBackgroundResource(R.drawable.rn_telecom_control_circle)
  }

  private fun setControlActive(root: View, active: Boolean, iconRes: Int, label: String) {
    val bg = root.findViewById<FrameLayout>(R.id.rn_telecom_control_icon_bg)
    val icon = root.findViewById<ImageView>(R.id.rn_telecom_control_icon)
    val text = root.findViewById<TextView>(R.id.rn_telecom_control_label)
    bg.setBackgroundResource(
      if (active) R.drawable.rn_telecom_control_circle_on else R.drawable.rn_telecom_control_circle,
    )
    icon.setImageResource(iconRes)
    icon.setColorFilter(if (active) Color.WHITE else Color.parseColor("#1C1B1F"))
    text.text = label
  }

  private fun buildKeypad(grid: GridLayout) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
    keys.forEach { key ->
      val button =
        TextView(this).apply {
          text = key
          textSize = 22f
          setTextColor(Color.parseColor("#1C1B1F"))
          gravity = android.view.Gravity.CENTER
          background =
            GradientDrawable().apply {
              shape = GradientDrawable.OVAL
              setColor(Color.parseColor("#E8E4DE"))
            }
          setOnClickListener { onDtmf(key) }
        }
      val size = (64 * resources.displayMetrics.density).toInt()
      val lp =
        GridLayout.LayoutParams().apply {
          width = 0
          height = size
          columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
          setMargins(10, 10, 10, 10)
        }
      grid.addView(button, lp)
    }
    grid.alignmentMode = GridLayout.ALIGN_BOUNDS
  }

  private fun onDtmf(digit: String) {
    val tone =
      when (digit) {
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
        else -> return
      }
    try {
      toneGenerator?.startTone(tone, 150)
    } catch (_: Exception) {
    }
    CallSessionManager.instance?.emitDtmf(uuid, digit)
  }

  override fun onDestroy() {
    finishReceiver?.let {
      try {
        unregisterReceiver(it)
      } catch (_: Exception) {
      }
    }
    finishReceiver = null
    toneGenerator?.release()
    toneGenerator = null
    super.onDestroy()
  }
}
