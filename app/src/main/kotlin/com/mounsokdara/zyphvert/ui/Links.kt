package com.mounsokdara.zyphvert.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.compositionLocalOf

object Links {
    const val REPO = "https://github.com/mounsokdara/zyphvert"
    const val ISSUES = "$REPO/issues"
    const val AUTHOR = "https://github.com/mounsokdara"
    /** Replace with your sponsor / donation page. */
    const val SUPPORT = AUTHOR
}

fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "No app can open this link", Toast.LENGTH_SHORT).show()
    }
}

val LocalHaptics = compositionLocalOf { true }
