package com.chikchik.posecamera.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.chikchik.posecamera.R

/**
 * Stage 28: "Contact Me" in Settings - opens a Telegram chat with [username].
 *
 * Tries the Telegram app first via its `tg://resolve` deep link (explicitly targeted at the
 * Telegram package so a device with several apps that can theoretically handle `tg://` never
 * shows an unrelated chooser). If the app is not installed - or fails to launch for any reason -
 * this falls back to the public `https://t.me/<username>` page, which any browser opens as the
 * Telegram Web version. A last Toast covers the rare case where the device has no browser either.
 */
fun openTelegramContact(context: Context, username: String) {
    val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$username")).apply {
        setPackage("org.telegram.messenger")
    }
    try {
        context.startActivity(appIntent)
        return
    } catch (e: ActivityNotFoundException) {
        // Telegram app not installed (or does not own that package name) - fall through to web.
    }

    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$username"))
    try {
        context.startActivity(webIntent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.contact_me_open_failed, Toast.LENGTH_LONG).show()
    }
}
