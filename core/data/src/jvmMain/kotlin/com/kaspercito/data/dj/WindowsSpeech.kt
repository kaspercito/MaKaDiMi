package com.kaspercito.data.dj

import com.kaspercito.logger.Logger
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "WindowsSpeech"

/**
 * Voz del locutor DJ: síntesis de voz **offline** de Windows (SAPI, `System.Speech`), así que no
 * gasta datos ni depende de ningún servicio externo.
 *
 * Se genera un WAV en un proceso de PowerShell efímero que baja su propia prioridad a
 * `BelowNormal`: nunca compite con el hilo de audio de mpv ni con la interfaz. El texto y las
 * rutas viajan por variables de entorno / fichero temporal, no dentro de la línea de comandos,
 * así que ninguna comilla de la frase de la IA puede romper (ni inyectar nada en) el script.
 */
internal object WindowsSpeech {
    val isSupported: Boolean =
        System.getProperty("os.name", "").contains("Windows", ignoreCase = true)

    // `§` hace de signo de dólar para no llenar el script de `${'$'}`; se sustituye abajo.
    private val script: String =
        """
        try {
          §p = [System.Diagnostics.Process]::GetCurrentProcess()
          §p.PriorityClass = 'BelowNormal'
          Add-Type -AssemblyName System.Speech
          §s = New-Object System.Speech.Synthesis.SpeechSynthesizer
          §lang = §env:DJ_LANG
          §v = §s.GetInstalledVoices() | Where-Object { §_.Enabled -and §_.VoiceInfo.Culture.TwoLetterISOLanguageName -eq §lang } | Select-Object -First 1
          if (§v) { §s.SelectVoice(§v.VoiceInfo.Name) }
          §s.SetOutputToWaveFile(§env:DJ_WAV)
          §s.Speak([System.IO.File]::ReadAllText(§env:DJ_TXT, [System.Text.Encoding]::UTF8))
          §s.Dispose()
        } catch { exit 1 }
        """.trimIndent().replace('§', '$')

    /**
     * Escribe [text] hablado en [wavFile]. Devuelve false si no es Windows, si SAPI falla o si
     * tarda más de 30 s. Bloqueante: llamar desde un hilo de IO.
     */
    fun synthesizeToWav(
        text: String,
        wavFile: File,
        languageTwoLetters: String,
    ): Boolean {
        if (!isSupported) return false
        val textFile = File.createTempFile("makadimi-dj", ".txt")
        return try {
            textFile.writeText(text, Charsets.UTF_8)
            val process =
                ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-WindowStyle",
                    "Hidden",
                    "-Command",
                    script,
                ).apply {
                    environment()["DJ_TXT"] = textFile.absolutePath
                    environment()["DJ_WAV"] = wavFile.absolutePath
                    environment()["DJ_LANG"] = languageTwoLetters
                    redirectErrorStream(true)
                    redirectOutput(ProcessBuilder.Redirect.DISCARD)
                }.start()
            process.outputStream.close()
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                Logger.w(TAG, "SAPI tardó demasiado; se descarta la frase")
                false
            } else {
                val ok = process.exitValue() == 0 && wavFile.length() > 1_000L
                if (!ok) Logger.w(TAG, "SAPI terminó con código ${process.exitValue()}")
                ok
            }
        } catch (e: Exception) {
            Logger.w(TAG, "No se pudo sintetizar la voz: ${e.message}")
            false
        } finally {
            textFile.delete()
        }
    }
}
