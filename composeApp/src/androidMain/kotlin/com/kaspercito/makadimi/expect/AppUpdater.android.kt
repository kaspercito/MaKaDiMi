package com.kaspercito.makadimi.expect

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Build
import android.provider.Settings
import android.content.Context
import kotlin.coroutines.cancellation.CancellationException
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.kaspercito.makadimi.utils.BrandLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform.getKoin
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val APK_MIME = "application/vnd.android.package-archive"

actual fun canInstallUpdateInApp(): Boolean = true

actual suspend fun downloadAndInstallUpdate(
    url: String,
    fileName: String,
    onProgress: (Float) -> Unit,
): UpdateInstallResult {
    // Seguridad: solo se instalan APKs que vengan de los Releases de TU repositorio.
    if (!url.startsWith("${BrandLinks.REPO_URL}/releases/download/")) return UpdateInstallResult.Failed

    return try {
        val context = getKoin().get<Context>().applicationContext

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()).addFlags(FLAG_ACTIVITY_NEW_TASK))
            return UpdateInstallResult.NeedsPermission
        }

        val apk = withContext(Dispatchers.IO) { downloadApk(context.cacheDir, url, fileName, onProgress) }
        @Suppress("DEPRECATION")
        val downloadedPackage = context.packageManager.getPackageArchiveInfo(apk.path, 0)
        check(downloadedPackage?.packageName == context.packageName)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", apk)
        val install =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME)
                .addFlags(FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        withContext(Dispatchers.Main) { context.startActivity(install) }
        UpdateInstallResult.InstallerOpened
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        UpdateInstallResult.Failed
    }
}

private fun downloadApk(
    cacheDir: File,
    url: String,
    fileName: String,
    onProgress: (Float) -> Unit,
): File {
    val dir = File(cacheDir, "updates").apply { mkdirs() }
    // Borra descargas anteriores para no llenar el almacenamiento.
    dir.listFiles()?.forEach { it.delete() }
    val safeName = File(fileName).name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "update.apk" }
    val target = File(dir, safeName)

    val connection = (URL(url).openConnection() as HttpURLConnection)
    connection.connectTimeout = 15_000
    connection.readTimeout = 30_000
    connection.instanceFollowRedirects = true
    connection.setRequestProperty("User-Agent", "${BrandLinks.APP_NAME}-updater")
    try {
        check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
        val total = connection.contentLengthLong
        var done = 0L
        connection.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var lastPercent = -1
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    done += read
                    if (total > 0) {
                        val percent = (done * 100 / total).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(done.toFloat() / total)
                        }
                    }
                }
            }
        }
        check(target.length() > 0 && (total <= 0 || target.length() == total)) { "Archivo incompleto" }
    } finally {
        connection.disconnect()
    }
    return target
}
