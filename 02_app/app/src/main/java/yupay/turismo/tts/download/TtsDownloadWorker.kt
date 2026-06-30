package yupay.turismo.tts.download

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import yupay.turismo.tts.ModelSource
import yupay.turismo.tts.RemoteFile
import yupay.turismo.tts.TtsModelCatalog
import yupay.turismo.tts.TtsModelInfo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Descarga bajo demanda de un modelo de voz. Se encola con [androidx.work.WorkManager] y una
 * restricción [androidx.work.NetworkType.CONNECTED] (WiFi o datos móviles), por lo que NO va en el APK.
 *
 * Flujo:
 *  1. Descarga el `.tar.bz2` (Piper) o los ficheros sueltos (MMS) a una zona temporal/destino.
 *  2. Verifica integridad con SHA-256 (si el catálogo trae el hash; si no, lo registra en Logcat).
 *  3. Extrae el archivo (bzip2 + tar) en `filesDir/tts/models/<lang>/<id>/`.
 *  4. Publica progreso (0–100) vía [setProgress]; es cancelable (cancelUniqueWork → [isStopped]).
 */
class TtsDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            // Sin callTimeout: los modelos pueden pesar >100 MB y tardar en redes lentas.
            .build()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelId = inputData.getString(KEY_MODEL_ID)
            ?: return@withContext fail("Falta model_id")
        val info = TtsModelCatalog.findById(modelId)
            ?: return@withContext fail("Modelo desconocido: $modelId")

        // Idempotente: si ya está instalado, no re-descargamos.
        if (TtsFileUtils.isInstalled(applicationContext, info)) {
            return@withContext Result.success(workDataOf(KEY_PROGRESS to 100))
        }

        val modelDir = TtsFileUtils.modelDir(applicationContext, info)
        try {
            when (val source = info.source) {
                is ModelSource.Archive -> downloadArchive(info, source, modelDir)
                is ModelSource.Files -> downloadFiles(info, source, modelDir)
            }
            if (!TtsFileUtils.isInstalled(applicationContext, info)) {
                cleanup(info, modelDir)
                return@withContext fail("El modelo quedó incompleto tras la descarga")
            }
            setProgress(workDataOf(KEY_PROGRESS to 100))
            Result.success(workDataOf(KEY_PROGRESS to 100))
        } catch (ie: InterruptedException) {
            // Parada cooperativa (pausa o anulación): CONSERVAR los parciales como checkpoint para
            // poder reanudar. Si fue una anulación real, el repositorio borrará los parciales tras
            // cancelar el work; el worker por sí solo no distingue pausa de anulación.
            Log.i(TAG, "Descarga detenida (checkpoint conservado): $modelId")
            Result.failure(workDataOf(KEY_ERROR to ERROR_STOPPED))
        } catch (ce: CancellationException) {
            // Cancelación de la corrutina al detener el work (pausa/anulación): igual que arriba,
            // conservar parciales. Sin esto, el `catch (Throwable)` borraría el checkpoint.
            Log.i(TAG, "Descarga cancelada (checkpoint conservado): $modelId")
            Result.failure(workDataOf(KEY_ERROR to ERROR_STOPPED))
        } catch (io: IOException) {
            // Corte de red a mitad de descarga: conservar parciales y reintentar con backoff;
            // el reintento reanuda desde el checkpoint (HTTP Range). La restricción CONNECTED hace
            // que WorkManager espere a que vuelva la conexión. Tras varios intentos fallidos, se
            // reporta error (no se borran los parciales, por si el usuario reintenta).
            if (runAttemptCount < MAX_RETRIES) {
                Log.w(TAG, "Corte de red descargando $modelId (intento $runAttemptCount); se reintentará", io)
                Result.retry()
            } else {
                Log.e(TAG, "Red inestable: agotados los reintentos de $modelId", io)
                fail(io.message ?: "Error de red")
            }
        } catch (se: SecurityException) {
            // Fallo de integridad (SHA-256): el helper ya borró el fichero corrupto. Limpiar el
            // resto y reportar error (el siguiente intento descarga desde cero).
            cleanup(info, modelDir)
            Log.e(TAG, "Integridad inválida en $modelId", se)
            fail(se.message ?: "Error de verificación")
        } catch (t: Throwable) {
            cleanup(info, modelDir)
            Log.e(TAG, "Error descargando $modelId", t)
            fail(t.message ?: "Error de descarga")
        }
    }

    // ------------------------------------------------------------------ Archive (.tar.bz2, Piper)
    private suspend fun downloadArchive(info: TtsModelInfo, source: ModelSource.Archive, modelDir: File) {
        val tmp = File(TtsFileUtils.tmpDir(applicationContext), "${info.id}.tar.bz2")
        // NO se borra `tmp`: si quedó un parcial de una pausa/corte anterior, se reanuda (HTTP Range).
        // 0–85%: descarga del archivo.
        downloadTo(
            url = source.url,
            dest = tmp,
            totalForProgress = info.sizeBytes,
            baseBytes = 0L,
            startPct = 0,
            endPct = 85,
            resume = true,
        )

        try {
            verifySha256(tmp, source.sha256, info.id)
        } catch (e: SecurityException) {
            tmp.delete() // parcial corrupto/desincronizado → fuera; el próximo intento baja limpio
            throw e
        }

        // 85–100%: extracción.
        setProgress(workDataOf(KEY_PROGRESS to 85))
        modelDir.deleteRecursively()
        modelDir.mkdirs()
        TtsFileUtils.extractTarBz2(
            archive = tmp,
            destDir = modelDir,
            shouldCancel = { isStopped },
        )
        tmp.delete()
    }

    // ------------------------------------------------------------------ Files (model.onnx + tokens, MMS)
    private suspend fun downloadFiles(info: TtsModelInfo, source: ModelSource.Files, modelDir: File) {
        // NO se borra el directorio: los ficheros ya bajados son el checkpoint (se reanuda cada uno).
        modelDir.mkdirs()
        val total = info.sizeBytes.coerceAtLeast(1L)
        var doneBytes = 0L
        for (remote in source.files) {
            val dest = File(modelDir, remote.relativePath).apply { parentFile?.mkdirs() }
            val hasSha = remote.sha256 != null
            // ¿Ya está completo y verificado? (solo se puede afirmar si hay SHA y coincide).
            if (hasSha && dest.exists() && dest.length() > 0L &&
                TtsFileUtils.sha256(dest).equals(remote.sha256, ignoreCase = true)
            ) {
                doneBytes += dest.length()
                continue
            }
            // El % es global (acumulado entre ficheros): baseBytes = lo ya descargado.
            // Ficheros con SHA → reanudables (Range). Sin SHA (p.ej. tokens.txt, pequeño) → siempre
            // limpio para no arriesgar un parcial truncado indetectable.
            downloadTo(
                url = remote.url,
                dest = dest,
                totalForProgress = total,
                baseBytes = doneBytes,
                startPct = 0,
                endPct = 99,
                resume = hasSha,
            )
            try {
                verifyFileSha256(dest, remote, info.id)
            } catch (e: SecurityException) {
                dest.delete()
                throw e
            }
            doneBytes += dest.length()
        }
    }

    // ------------------------------------------------------------------ Descarga genérica con progreso
    /**
     * Descarga [url] a [dest] reportando progreso interpolado en [startPct]..[endPct]. El porcentaje
     * se calcula como `(baseBytes + escrito) / totalForProgress`, lo que permite un progreso GLOBAL
     * acumulado cuando un modelo se compone de varios ficheros (MMS). Todo el reporte ([setProgress])
     * ocurre dentro de este cuerpo suspend, así no hay callbacks que invoquen funciones suspend.
     */
    private suspend fun downloadTo(
        url: String,
        dest: File,
        totalForProgress: Long,
        baseBytes: Long,
        startPct: Int,
        endPct: Int,
        resume: Boolean,
    ) {
        // Reanudación: si hay un parcial en disco y se permite reanudar, pedimos solo el resto con
        // un header Range y abrimos el fichero en modo append.
        var existing = if (resume && dest.exists()) dest.length() else 0L
        if (!resume) dest.delete()

        val builder = Request.Builder().url(url)
        if (existing > 0) builder.header("Range", "bytes=$existing-")
        http.newCall(builder.build()).execute().use { response ->
            val code = response.code
            // 416 = "Range Not Satisfiable": el offset pedido cubre todo → el fichero ya está completo.
            if (code == 416) return
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP $code al descargar $url")
            }
            // Si pedimos rango pero el servidor respondió 200 (no soporta Range), reiniciamos limpio.
            val append = existing > 0 && code == 206
            if (existing > 0 && code != 206) {
                dest.delete()
                existing = 0L
            }
            val body = response.body ?: throw IllegalStateException("Respuesta vacía: $url")
            val total = if (totalForProgress > 0) totalForProgress else existing + body.contentLength()
            var written = existing
            var lastReportPct = -1
            body.byteStream().use { input ->
                FileOutputStream(dest, append).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (isStopped) throw InterruptedException("Descarga detenida")
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val frac = ((baseBytes + written).toDouble() / total).coerceIn(0.0, 1.0)
                            val pct = (startPct + frac * (endPct - startPct)).toInt().coerceIn(startPct, endPct)
                            if (pct != lastReportPct) {
                                lastReportPct = pct
                                reportProgress(pct)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun reportProgress(pct: Int) {
        setProgress(workDataOf(KEY_PROGRESS to pct))
    }

    // ------------------------------------------------------------------ Verificación de integridad
    private fun verifySha256(file: File, expected: String, modelId: String) {
        val actual = TtsFileUtils.sha256(file)
        if (!actual.equals(expected, ignoreCase = true)) {
            throw SecurityException(
                "SHA-256 no coincide para $modelId. esperado=$expected actual=$actual",
            )
        }
        Log.i(TAG, "SHA-256 OK para $modelId")
    }

    private fun verifyFileSha256(file: File, remote: RemoteFile, modelId: String) {
        val actual = TtsFileUtils.sha256(file)
        val expected = remote.sha256
        if (expected == null) {
            // Verificación blanda: no hay hash publicado (p.ej. tokens.txt). Lo registramos para
            // poder fijarlo en el catálogo si se desea endurecer la verificación.
            Log.i(TAG, "Sin SHA-256 esperado para ${remote.relativePath} ($modelId). Calculado=$actual")
            return
        }
        if (!actual.equals(expected, ignoreCase = true)) {
            throw SecurityException(
                "SHA-256 no coincide para ${remote.relativePath} ($modelId). esperado=$expected actual=$actual",
            )
        }
        Log.i(TAG, "SHA-256 OK para ${remote.relativePath} ($modelId)")
    }

    private fun cleanup(info: TtsModelInfo, modelDir: File) {
        runCatching { modelDir.deleteRecursively() }
        runCatching { File(TtsFileUtils.tmpDir(applicationContext), "${info.id}.tar.bz2").delete() }
    }

    private fun fail(message: String): Result = Result.failure(workDataOf(KEY_ERROR to message))

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"

        /** El worker fue detenido (pausa o anulación). Los parciales se conservan; el repositorio
         *  decide si limpiarlos (anular) o no (pausa). */
        const val ERROR_STOPPED = "__stopped__"
        private const val TAG = "TtsDownloadWorker"

        /** Máximo de reintentos automáticos ante cortes de red antes de reportar error. */
        private const val MAX_RETRIES = 5

        /** Nombre único del trabajo por modelo (para encolar/cancelar/observar). */
        fun uniqueWorkName(modelId: String): String = "tts_download_$modelId"

        fun inputFor(modelId: String): Data = workDataOf(KEY_MODEL_ID to modelId)
    }
}
