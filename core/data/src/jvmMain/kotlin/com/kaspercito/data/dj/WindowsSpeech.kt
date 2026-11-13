package com.kaspercito.data.dj

import com.kaspercito.logger.Logger
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "WindowsSpeech"

/**
 * Voz del locutor DJ: síntesis de voz **offline** de Windows, así que no gasta datos ni depende de
 * ningún servicio externo.
 *
 * Se prueban dos motores, del mejor al peor:
 *  1. **Voces modernas de Windows** (`Windows.Media.SpeechSynthesis`, las mismas de Narrador y
 *     Cortana): suenan bastante más naturales y respetan la región (es-MX, es-ES...).
 *  2. **SAPI** (`System.Speech`): el motor clásico, por si el anterior no está disponible.
 *
 * Se genera un WAV en un proceso de PowerShell efímero que baja su propia prioridad a
 * `BelowNormal`: nunca compite con el hilo de audio de mpv ni con la interfaz. El texto y las
 * rutas viajan por variables de entorno / fichero temporal, no dentro de la línea de comandos,
 * así que ninguna comilla de la frase de la IA puede romper (ni inyectar nada en) el script.
 */
internal object WindowsSpeech {
    val isSupported: Boolean =
        System.getProperty("os.name", "").contains("Windows", ignoreCase = true)

    // `§` hace de signo de dólar para no llenar los scripts de `${'$'}`; se sustituye abajo.
    private val modernScript: String =
        """
        try {
          §ErrorActionPreference = 'Stop'
          §p = [System.Diagnostics.Process]::GetCurrentProcess()
          §p.PriorityClass = 'BelowNormal'
          Add-Type -AssemblyName System.Runtime.WindowsRuntime
          §null = [Windows.Media.SpeechSynthesis.SpeechSynthesizer, Windows.Media.SpeechSynthesis, ContentType=WindowsRuntime]
          §null = [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType=WindowsRuntime]
          §asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
            §_.Name -eq 'AsTask' -and §_.GetParameters().Count -eq 1 -and §_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
          })[0]
          function Await(§op, §type) {
            §task = §asTask.MakeGenericMethod(§type).Invoke(§null, @(§op))
            §null = §task.Wait(30000)
            §task.Result
          }
          §s = New-Object Windows.Media.SpeechSynthesis.SpeechSynthesizer
          §lang = §env:DJ_LANG
          §sys = (Get-Culture).Name
          §voices = @([Windows.Media.SpeechSynthesis.SpeechSynthesizer]::AllVoices | Where-Object { §_.Language -like (§lang + '*') })
          if (§voices.Count -eq 0) { exit 3 }
          §best = §voices | Where-Object { §_.Language -eq §sys } | Select-Object -First 1
          if (-not §best) { §best = §voices | Select-Object -First 1 }
          §s.Voice = §best
          try { §s.Options.SpeakingRate = 1.04; §s.Options.AudioPitch = 1.0 } catch { }
          §text = [System.IO.File]::ReadAllText(§env:DJ_TXT, [System.Text.Encoding]::UTF8)
          §stream = Await (§s.SynthesizeTextToStreamAsync(§text)) ([Windows.Media.SpeechSynthesis.SpeechSynthesisStream])
          §size = [uint32]§stream.Size
          §reader = New-Object Windows.Storage.Streams.DataReader(§stream.GetInputStreamAt(0))
          §null = Await (§reader.LoadAsync(§size)) ([uint32])
          §bytes = New-Object byte[] §size
          §reader.ReadBytes(§bytes)
          [System.IO.File]::WriteAllBytes(§env:DJ_WAV, §bytes)
          §s.Dispose()
        } catch { exit 1 }
        """.trimIndent().replace('§', '$')

    private val sapiScript: String =
        """
        try {
          §p = [System.Diagnostics.Process]::GetCurrentProcess()
          §p.PriorityClass = 'BelowNormal'
          Add-Type -AssemblyName System.Speech
          §s = New-Object System.Speech.Synthesis.SpeechSynthesizer
          §lang = §env:DJ_LANG
          §sys = (Get-Culture).Name
          §all = @(§s.GetInstalledVoices() | Where-Object { §_.Enabled -and §_.VoiceInfo.Culture.TwoLetterISOLanguageName -eq §lang })
          §v = §all | Where-Object { §_.VoiceInfo.Culture.Name -eq §sys } | Select-Object -First 1
          if (-not §v) { §v = §all | Select-Object -First 1 }
          if (§v) { §s.SelectVoice(§v.VoiceInfo.Name) }
          §s.Rate = 1
          §s.Volume = 100
          §s.SetOutputToWaveFile(§env:DJ_WAV)
          §s.Speak([System.IO.File]::ReadAllText(§env:DJ_TXT, [System.Text.Encoding]::UTF8))
          §s.Dispose()
        } catch { exit 1 }
        """.trimIndent().replace('§', '$')

    /**
     * Escribe [text] hablado en [wavFile]. Devuelve false si no es Windows, si ningún motor
     * funciona o si tardan demasiado. Bloqueante: llamar desde un hilo de IO.
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
            run("modern", modernScript, textFile, wavFile, languageTwoLetters) ||
                run("sapi", sapiScript, textFile, wavFile, languageTwoLetters)
        } catch (e: Exception) {
            Logger.w(TAG, "No se pudo sintetizar la voz: ${e.message}")
            false
        } finally {
            textFile.delete()
        }
    }

    private fun run(
        engine: String,
        script: String,
        textFile: File,
        wavFile: File,
        languageTwoLetters: String,
    ): Boolean =
        try {
            wavFile.delete()
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
                Logger.w(TAG, "El motor de voz '$engine' tardó demasiado")
                false
            } else {
                val ok = process.exitValue() == 0 && wavFile.length() > 1_000L
                if (!ok) Logger.w(TAG, "El motor de voz '$engine' terminó con código ${process.exitValue()}")
                ok
            }
        } catch (e: Exception) {
            Logger.w(TAG, "El motor de voz '$engine' falló: ${e.message}")
            false
        }
}
