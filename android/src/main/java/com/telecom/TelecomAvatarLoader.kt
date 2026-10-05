package com.telecom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Loads a remote/local avatar into an ImageView; falls back to initials. */
internal object TelecomAvatarLoader {
  private const val TAG = "RNTelecomAvatar"
  private val executor = Executors.newFixedThreadPool(2)
  private val main = Handler(Looper.getMainLooper())

  fun bind(
    imageView: ImageView,
    initialsView: TextView,
    avatarUrl: String?,
    callerName: String,
  ) {
    initialsView.text = initialsFor(callerName)
    initialsView.visibility = android.view.View.VISIBLE
    imageView.setImageDrawable(null)
    imageView.visibility = android.view.View.GONE

    val url = avatarUrl?.trim().orEmpty()
    if (url.isEmpty()) return

    executor.execute {
      val bitmap = decode(url)
      main.post {
        if (bitmap != null) {
          imageView.setImageBitmap(bitmap)
          imageView.visibility = android.view.View.VISIBLE
          initialsView.visibility = android.view.View.GONE
        }
      }
    }
  }

  private fun decode(url: String): Bitmap? {
    return try {
      when {
        url.startsWith("http://") || url.startsWith("https://") -> {
          val conn = URL(url).openConnection() as HttpURLConnection
          conn.connectTimeout = 8_000
          conn.readTimeout = 8_000
          conn.instanceFollowRedirects = true
          conn.inputStream.use { BitmapFactory.decodeStream(it) }
        }
        url.startsWith("file://") || url.startsWith("/") -> {
          BitmapFactory.decodeFile(url.removePrefix("file://"))
        }
        url.startsWith("content://") -> null // host app can pass https/file for now
        else -> BitmapFactory.decodeFile(url)
      }
    } catch (error: Exception) {
      Log.w(TAG, "avatar load failed: ${error.message}")
      null
    }
  }

  fun initialsFor(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
      parts.isEmpty() -> "?"
      parts.size == 1 -> parts[0].take(2).uppercase()
      else -> "${parts[0].first()}${parts[1].first()}".uppercase()
    }
  }
}
