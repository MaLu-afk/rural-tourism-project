package yupay.turismo.tts.audio

import android.content.Context
import yupay.turismo.tts.SupportedLanguage
import java.io.File
import java.security.MessageDigest

/**
 * Caché en disco del audio TTS generado (WAV) para tips, mapas y resúmenes del dashboard.
 *
 * Vive en `filesDir/tts/audio_cache/` y se borra completa al cerrar sesión
 * ([yupay.turismo.data.AppReset]). La clave incluye idioma + modelo + texto, de modo que al
 * cambiar el texto o la voz activa se genera otro fichero (sin mezclas ni audio desactualizado).
 */
object AudioCache {

    /** Raíz de la caché de audio generado: `filesDir/tts/audio_cache`. */
    fun cacheRoot(context: Context): File = File(context.filesDir, "tts/audio_cache")

    /** Subcarpeta con un marcador por "dueño" (pantalla): `filesDir/tts/audio_cache/owners`. */
    private fun ownersRoot(context: Context): File = File(cacheRoot(context), "owners")

    /** Clave de caché = sha256(idioma | modelo | texto). */
    fun keyFor(text: String, language: SupportedLanguage, modelId: String): String =
        sha256("${language.code}|$modelId|$text")

    /** Fichero WAV asociado a una clave. */
    fun fileFor(context: Context, key: String): File = File(cacheRoot(context), "$key.wav")

    /**
     * Registra [newKey] como el audio actual del [owner] (p.ej. "tip:Español", "map", "dashboard").
     * Si el owner tenía otro audio (el texto cambió), borra su WAV anterior —salvo que otro owner
     * siga usándolo— de modo que el audio del texto previo se elimina y se reemplaza por el nuevo.
     */
    fun updateOwnerAudio(context: Context, owner: String, newKey: String) {
        runCatching {
            val ownersDir = ownersRoot(context).apply { mkdirs() }
            val marker = File(ownersDir, sha256(owner))
            val oldKey = if (marker.exists()) marker.readText().trim() else null
            if (!oldKey.isNullOrBlank() && oldKey != newKey) {
                val stillUsed = ownersDir.listFiles()
                    ?.any { it != marker && runCatching { it.readText().trim() }.getOrNull() == oldKey } == true
                if (!stillUsed) fileFor(context, oldKey).delete()
            }
            marker.writeText(newKey)
        }
    }

    /** Borra TODO el audio cacheado y los marcadores (logout / reset). No toca los modelos. */
    fun deleteAll(context: Context) {
        cacheRoot(context).deleteRecursively()
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
