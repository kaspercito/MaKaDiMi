package org.makadimi.crashlytics

import android.content.Context
import com.kaspercito.domain.data.player.PlayerError
import com.kaspercito.logger.Logger

// MaKaDiMi no envía reportes de fallos a ningún servicio externo: todo queda en el log local.
fun reportCrash(throwable: Throwable) {
    Logger.e("Crashlytics", "Crash: ${throwable.localizedMessage}")
}

fun configCrashlytics(applicationContext: Context) {
    Logger.d("Crashlytics", "App start")
}

fun pushPlayerError(error: PlayerError) {
    Logger.e("Crashlytics", "Player Error: ${error.message}, code: ${error.errorCode}, code name: ${error.errorCodeName}")
}
