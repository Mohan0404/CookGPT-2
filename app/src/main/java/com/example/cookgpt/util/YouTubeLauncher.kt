package com.example.cookgpt.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Opens a YouTube video in Chrome Custom Tabs (in-app browser).
 *
 * The user stays inside the app. YouTube opens as an overlay browser,
 * the video plays immediately. No embedding, no Error 152.
 *
 * @param context  Activity or Fragment context
 * @param videoId  The YouTube video ID (the part after ?v= in a YouTube URL)
 *                 Example: for https://www.youtube.com/watch?v=dQw4w9WgXcQ
 *                          the videoId is "dQw4w9WgXcQ"
 */
fun openYouTubeInAppBrowser(context: Context, videoId: String) {
    if (videoId.isBlank()) {
        Log.e("YouTubeLauncher", "videoId is blank — cannot open video")
        return
    }

    val url = Uri.parse("https://www.youtube.com/watch?v=$videoId")
    Log.d("YouTubeLauncher", "Opening in Custom Tabs: $url")

    try {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, url)
    } catch (e: Exception) {
        // Chrome not available — fall back to any installed browser
        Log.w("YouTubeLauncher", "Custom Tabs failed, trying ACTION_VIEW: ${e.message}")
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url))
        } catch (e2: Exception) {
            Log.e("YouTubeLauncher", "No browser available: ${e2.message}")
        }
    }
}
