package yupay.turismo.tts.audio

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Escribe PCM float mono (muestras en [-1, 1], como las produce
 * [yupay.turismo.tts.TtsEngine.synthesize]) a un fichero **WAV PCM 16-bit** little-endian.
 *
 * El WAV resultante lo reproduce [android.media.MediaPlayer], que nos da gratis posición,
 * duración, `seekTo` y velocidad de reproducción ([android.media.PlaybackParams]).
 */
object WavFileWriter {

    private const val BITS_PER_SAMPLE = 16
    private const val CHANNELS = 1
    private const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8

    fun write(out: File, samples: FloatArray, sampleRate: Int) {
        out.parentFile?.mkdirs()
        val dataSize = samples.size * BYTES_PER_SAMPLE
        val byteRate = sampleRate * CHANNELS * BYTES_PER_SAMPLE
        val blockAlign = CHANNELS * BYTES_PER_SAMPLE

        BufferedOutputStream(FileOutputStream(out)).use { os ->
            // ── Cabecera RIFF ──
            os.writeAscii("RIFF")
            os.writeIntLE(36 + dataSize)      // tamaño total - 8
            os.writeAscii("WAVE")
            // ── Sub-chunk "fmt " ──
            os.writeAscii("fmt ")
            os.writeIntLE(16)                 // tamaño del sub-chunk fmt (PCM)
            os.writeShortLE(1)                // formato de audio = PCM
            os.writeShortLE(CHANNELS)
            os.writeIntLE(sampleRate)
            os.writeIntLE(byteRate)
            os.writeShortLE(blockAlign)
            os.writeShortLE(BITS_PER_SAMPLE)
            // ── Sub-chunk "data" ──
            os.writeAscii("data")
            os.writeIntLE(dataSize)

            val buffer = ByteArray(dataSize)
            var i = 0
            for (s in samples) {
                val v = (s.coerceIn(-1f, 1f) * 32767f).toInt()
                buffer[i++] = (v and 0xFF).toByte()
                buffer[i++] = ((v shr 8) and 0xFF).toByte()
            }
            os.write(buffer)
        }
    }

    private fun OutputStream.writeAscii(s: String) = write(s.toByteArray(Charsets.US_ASCII))

    private fun OutputStream.writeIntLE(v: Int) {
        write(v and 0xFF)
        write((v shr 8) and 0xFF)
        write((v shr 16) and 0xFF)
        write((v shr 24) and 0xFF)
    }

    private fun OutputStream.writeShortLE(v: Int) {
        write(v and 0xFF)
        write((v shr 8) and 0xFF)
    }
}
