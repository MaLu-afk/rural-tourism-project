package yupay.turismo.tts.download

import android.content.Context
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import yupay.turismo.tts.SupportedLanguage
import yupay.turismo.tts.TtsModelInfo
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest

/** Utilidades de disco para los modelos TTS: rutas, SHA-256, tamaño y extracción de .tar.bz2. */
object TtsFileUtils {

    /** Raíz de los modelos: `filesDir/tts/models`. */
    fun modelsRoot(context: Context): File = File(context.filesDir, "tts/models")

    /** Carpeta de un modelo: `filesDir/tts/models/<langCode>/<modelId>/`. */
    fun modelDir(context: Context, info: TtsModelInfo): File =
        File(modelsRoot(context), "${info.language.code}/${info.id}")

    /** Carpeta de descargas temporales (archivos .tar.bz2 en curso): `filesDir/tts/tmp`. */
    fun tmpDir(context: Context): File = File(context.filesDir, "tts/tmp").apply { mkdirs() }

    /**
     * Un modelo se considera presente si su carpeta existe y contiene al menos un `.onnx` y un
     * `tokens.txt` (lo mínimo que necesita el motor).
     */
    fun isInstalled(context: Context, info: TtsModelInfo): Boolean {
        val dir = modelDir(context, info)
        if (!dir.isDirectory) return false
        val hasOnnx = dir.walkTopDown().any { it.isFile && it.extension == "onnx" }
        val hasTokens = dir.walkTopDown().any { it.isFile && it.name == "tokens.txt" }
        return hasOnnx && hasTokens
    }

    /** Tamaño total en bytes de un directorio (recursivo). 0 si no existe. */
    fun dirSizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** SHA-256 de un fichero, en hex minúsculas. */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = fis.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Extrae un `.tar.bz2` preservando la estructura dentro de [destDir]. No "aplana" la carpeta
     * raíz del archivo: el motor localiza luego `*.onnx` / `tokens.txt` / `espeak-ng-data`
     * recursivamente, así que da igual a qué profundidad queden.
     *
     * [shouldCancel] permite abortar (descarga cancelada); [onBytes] informa de bytes extraídos.
     * Protegido contra "zip slip" (rutas que se salgan de destDir).
     */
    fun extractTarBz2(
        archive: File,
        destDir: File,
        shouldCancel: () -> Boolean = { false },
        onBytes: (Long) -> Unit = {},
    ) {
        destDir.mkdirs()
        val canonicalDest = destDir.canonicalFile
        var extracted = 0L
        FileInputStream(archive).use { fis ->
            BufferedInputStream(fis).use { bis ->
                BZip2CompressorInputStream(bis).use { bz ->
                    TarArchiveInputStream(bz).use { tar ->
                        var entry = tar.nextTarEntry
                        val buffer = ByteArray(64 * 1024)
                        while (entry != null) {
                            if (shouldCancel()) throw InterruptedException("Extracción cancelada")
                            val outFile = File(destDir, entry.name).canonicalFile
                            if (!outFile.path.startsWith(canonicalDest.path)) {
                                throw SecurityException("Entrada de tar fuera de destino: ${entry.name}")
                            }
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                outFile.outputStream().use { out ->
                                    copy(tar, out, buffer, shouldCancel) { n ->
                                        extracted += n
                                        onBytes(extracted)
                                    }
                                }
                            }
                            entry = tar.nextTarEntry
                        }
                    }
                }
            }
        }
    }

    private inline fun copy(
        input: InputStream,
        output: java.io.OutputStream,
        buffer: ByteArray,
        shouldCancel: () -> Boolean,
        onChunk: (Int) -> Unit,
    ) {
        while (true) {
            if (shouldCancel()) throw InterruptedException("Cancelado")
            val read = input.read(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
            onChunk(read)
        }
    }

    /** Borra un directorio de modelo y devuelve los bytes liberados. */
    fun deleteModelDir(context: Context, info: TtsModelInfo): Long {
        val dir = modelDir(context, info)
        val freed = dirSizeBytes(dir)
        dir.deleteRecursively()
        return freed
    }

    /**
     * Borra los ficheros PARCIALES de una descarga (checkpoint): el `.tar.bz2` temporal y, si el
     * modelo NO está instalado todavía, su carpeta de destino. No toca un modelo ya usable (para no
     * eliminarlo por error ni interferir con [deleteModelDir]). Se usa al ANULAR una descarga.
     */
    fun deletePartials(context: Context, info: TtsModelInfo) {
        runCatching { File(tmpDir(context), "${info.id}.tar.bz2").delete() }
        if (!isInstalled(context, info)) {
            runCatching { modelDir(context, info).deleteRecursively() }
        }
    }

    /** Limpieza opcional de toda la caché de modelos (p.ej. en un reset de la app). */
    fun deleteAll(context: Context) {
        modelsRoot(context).deleteRecursively()
        tmpDir(context).deleteRecursively()
    }

    @Suppress("unused")
    fun languageFromCode(code: String): SupportedLanguage =
        SupportedLanguage.entries.firstOrNull { it.code == code } ?: SupportedLanguage.SPANISH
}
