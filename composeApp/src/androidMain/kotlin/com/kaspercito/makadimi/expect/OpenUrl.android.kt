package com.kaspercito.makadimi.expect

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.ActivityNotFoundException
import android.content.Context
import android.widget.Toast
import androidx.core.net.toUri
import org.koin.mp.KoinPlatform.getKoin

actual fun openUrl(url: String) {
    val context = getKoin().get<Context>().applicationContext
    val browserIntent =
        Intent(
            Intent.ACTION_VIEW,
            url.toUri(),
        )
    browserIntent.setFlags(FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(browserIntent)
    } catch (error: ActivityNotFoundException) {
        Toast.makeText(context, error.localizedMessage, Toast.LENGTH_SHORT).show()
    }
}

actual fun shareUrl(
    title: String,
    url: String,
) {
    val context = getKoin().get<Context>().applicationContext
    val shareIntent = Intent(Intent.ACTION_SEND)
    shareIntent.type = "text/plain"
    shareIntent.putExtra(Intent.EXTRA_TEXT, url)
    shareIntent.setFlags(FLAG_ACTIVITY_NEW_TASK)
    val chooserIntent =
        Intent.createChooser(shareIntent, title).addFlags(FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooserIntent)
}