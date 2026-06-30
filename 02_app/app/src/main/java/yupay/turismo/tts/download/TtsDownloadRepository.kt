package yupay.turismo.tts.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import yupay.turismo.data.local.TtsPreference
import yupay.turismo.data.local.TtsPreferenceDao
import yupay.turismo.data.prefs.DevicePreferences
import yupay.turismo.tts.SupportedLanguage
import yupay.turismo.tts.TtsModelCatalog
import yupay.turismo.tts.TtsModelInfo
import yupay.turismo.tts.TtsModelState
import yupay.turismo.utils.NetworkMonitor
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Orquesta la descarga/borrado/activación de modelos de voz y expone su estado observable.
 *
 * Registrado como singleton en [yupay.turismo.di.ServiceLocator] (el proyecto usa ServiceLocator,
 * no Hilt). Cada modelo del catálogo tiene un `StateFlow<TtsModelState>` (vía [stateOf]) que combina:
 *  - el progreso de WorkManager (descarga en curso / error),
 *  - la presencia en disco,
 *  - la preferencia de "modelo activo" por idioma (Room, tabla tts_preferences).
 */
class TtsDownloadRepository(
    private val appContext: Context,
    private val ttsPreferenceDao: TtsPreferenceDao,
    private val networkMonitor: NetworkMonitor,
    private val devicePrefs: DevicePreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val workManager = WorkManager.getInstance(appContext)

    /** IDs de modelos presentes en disco. Se refresca en segundo plano tras cada operación. */
    private val installed = MutableStateFlow<Set<String>>(emptySet())

    /** IDs marcados como activos (uno por idioma). Derivado de Room. */
    private val activeIds: Flow<Set<String>> =
        ttsPreferenceDao.getAll().map { prefs -> prefs.map { it.activeModelId }.toSet() }

    /** Cache de StateFlows por modelId (uno por modelo, compartido entre observadores). */
    private val stateFlows = ConcurrentHashMap<String, StateFlow<TtsModelState>>()

    init {
        scope.launch { refreshInstalled() }
    }

    /** StateFlow del estado de un modelo concreto (requisito 4). Cacheado por modelId. */
    fun stateOf(info: TtsModelInfo): StateFlow<TtsModelState> =
        stateFlows.getOrPut(info.id) {
            val workFlow = workManager.getWorkInfosForUniqueWorkFlow(
                TtsDownloadWorker.uniqueWorkName(info.id),
            )
            combine(
                workFlow,
                installed,
                activeIds,
                networkMonitor.isOnline,
                devicePrefs.pausedDownloadsFlow,
            ) { workInfos, inst, act, online, paused ->
                computeState(
                    work = workInfos.firstOrNull(),
                    isInstalled = info.id in inst,
                    isActive = info.id in act,
                    isOnline = online,
                    pausedPct = paused[info.id],
                )
            }.stateIn(scope, SharingStarted.Eagerly, TtsModelState.NotDownloaded)
        }

    private fun computeState(
        work: WorkInfo?,
        isInstalled: Boolean,
        isActive: Boolean,
        isOnline: Boolean,
        pausedPct: Int?,
    ): TtsModelState {
        val disk = when {
            isInstalled && isActive -> TtsModelState.Active
            isInstalled -> TtsModelState.Downloaded
            else -> TtsModelState.NotDownloaded
        }
        // Un modelo instalado siempre gana (aunque hubiera un work/marcador viejo colgando).
        if (isInstalled) return disk

        // Pausa MANUAL (marcador en disco): tiene prioridad sobre el estado del work. El usuario
        // reanuda a mano; por eso `waitingForNetwork = false`.
        if (pausedPct != null) {
            return TtsModelState.Paused((pausedPct / 100f).coerceIn(0f, 1f), waitingForNetwork = false)
        }

        return when (work?.state) {
            WorkInfo.State.RUNNING -> {
                val pct = work.progress.getInt(TtsDownloadWorker.KEY_PROGRESS, 0)
                TtsModelState.Downloading((pct / 100f).coerceIn(0f, 1f))
            }
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                val frac = (work.progress.getInt(TtsDownloadWorker.KEY_PROGRESS, 0) / 100f).coerceIn(0f, 1f)
                // Encolada: con internet va a arrancar (Downloading); sin internet está esperando la
                // conexión (Paused waitingForNetwork → en la UI solo se ofrece "Anular").
                if (isOnline) TtsModelState.Downloading(frac)
                else TtsModelState.Paused(frac, waitingForNetwork = true)
            }
            WorkInfo.State.FAILED -> {
                val err = work.outputData.getString(TtsDownloadWorker.KEY_ERROR)
                // Detenido sin marcador de pausa = anulado por el usuario → vuelve a NotDownloaded.
                if (err == TtsDownloadWorker.ERROR_STOPPED) disk
                else TtsModelState.Error(err ?: "Error de descarga")
            }
            // SUCCEEDED / CANCELLED / null → mandar el estado de disco.
            else -> disk
        }
    }

    /** Encola la descarga del modelo con restricción de red (WiFi o datos). */
    fun download(info: TtsModelInfo) {
        // Por si venía de una pausa manual: quitar el marcador (el worker reanuda del checkpoint).
        scope.launch { devicePrefs.clearPausedDownload(info.id) }
        enqueue(info)
        observeUntilFinished(info.id)
    }

    /** Reanuda una descarga pausada por el usuario. Mismo flujo que [download] (continúa el checkpoint). */
    fun resume(info: TtsModelInfo) = download(info)

    private fun enqueue(info: TtsModelInfo) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request: OneTimeWorkRequest = OneTimeWorkRequestBuilder<TtsDownloadWorker>()
            .setInputData(TtsDownloadWorker.inputFor(info.id))
            .setConstraints(constraints)
            // Reintentos ante cortes de red (el worker reanuda con HTTP Range).
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
            .addTag(TAG_TTS)
            .build()
        workManager.enqueueUniqueWork(
            TtsDownloadWorker.uniqueWorkName(info.id),
            ExistingWorkPolicy.KEEP, // no reinicia si ya hay una descarga viva del mismo modelo
            request,
        )
    }

    private fun observeUntilFinished(modelId: String) {
        // Refresca "installed" cuando la descarga termina (éxito → aparece en disco). `first`
        // suspende hasta que el trabajo finaliza y luego completa la corrutina (sin fuga).
        scope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(TtsDownloadWorker.uniqueWorkName(modelId))
                .first { infos -> infos.firstOrNull()?.state?.isFinished == true }
            refreshInstalled()
        }
    }

    /**
     * Pausa una descarga en curso CONSERVANDO el checkpoint (no borra parciales). Guarda [progressPct]
     * (0–100) para mostrar el avance y distinguir "pausa manual" de "esperando red". Escribe el
     * marcador ANTES de cancelar el work para que la tarjeta no parpadee a NotDownloaded.
     */
    fun pause(modelId: String, progressPct: Int) {
        scope.launch {
            devicePrefs.setPausedDownload(modelId, progressPct)
            workManager.cancelUniqueWork(TtsDownloadWorker.uniqueWorkName(modelId))
        }
    }

    /** Anula una descarga: cancela el work, BORRA los parciales (checkpoint) y el marcador de pausa. */
    fun cancel(modelId: String) {
        workManager.cancelUniqueWork(TtsDownloadWorker.uniqueWorkName(modelId))
        scope.launch {
            devicePrefs.clearPausedDownload(modelId)
            TtsModelCatalog.findById(modelId)?.let { info ->
                withContext(Dispatchers.IO) { TtsFileUtils.deletePartials(appContext, info) }
            }
            refreshInstalled()
        }
    }

    /**
     * Elimina un modelo descargado y libera espacio. Si era el activo de su idioma, lo desactiva.
     * @return bytes liberados.
     */
    suspend fun delete(info: TtsModelInfo): Long = withContext(Dispatchers.IO) {
        // Cancela cualquier descarga viva y limpia el marcador de pausa. No usamos cancel() para
        // evitar una carrera con deleteModelDir (que es quien calcula los bytes liberados).
        workManager.cancelUniqueWork(TtsDownloadWorker.uniqueWorkName(info.id))
        devicePrefs.clearPausedDownload(info.id)
        val freed = TtsFileUtils.deleteModelDir(appContext, info)
        runCatching { File(TtsFileUtils.tmpDir(appContext), "${info.id}.tar.bz2").delete() }
        ttsPreferenceDao.clearIfActive(info.language.code, info.id)
        refreshInstalled()
        freed
    }

    /**
     * Activa un modelo para su idioma (desactiva automáticamente el anterior, porque la clave
     * primaria de tts_preferences es el idioma). Solo debe llamarse con modelos ya descargados.
     */
    suspend fun setActive(info: TtsModelInfo) {
        ttsPreferenceDao.upsert(TtsPreference(language = info.language.code, activeModelId = info.id))
    }

    /** Modelo activo de un idioma como Flow (null si no hay voz configurada). */
    fun activeModelId(language: SupportedLanguage): Flow<String?> =
        ttsPreferenceDao.observe(language.code).map { it?.activeModelId }

    /** [TtsModelInfo] del modelo activo de un idioma (null si no hay/­no es válido). */
    fun activeModelInfo(language: SupportedLanguage): Flow<TtsModelInfo?> =
        activeModelId(language).map { id -> id?.let { TtsModelCatalog.findById(it) } }

    /** Resuelve el directorio en disco de un modelo (para que el motor lo cargue). */
    fun modelDir(info: TtsModelInfo): File = TtsFileUtils.modelDir(appContext, info)

    /** Lectura puntual: ¿está instalado el modelo activo de este idioma? (para el AudioButton). */
    suspend fun activeInstalledModel(language: SupportedLanguage): TtsModelInfo? =
        withContext(Dispatchers.IO) {
            val id = ttsPreferenceDao.getOnce(language.code)?.activeModelId ?: return@withContext null
            val info = TtsModelCatalog.findById(id) ?: return@withContext null
            if (TtsFileUtils.isInstalled(appContext, info)) info else null
        }

    private suspend fun refreshInstalled() = withContext(Dispatchers.IO) {
        val present = TtsModelCatalog.all()
            .filter { TtsFileUtils.isInstalled(appContext, it) }
            .map { it.id }
            .toSet()
        installed.value = present
    }

    private companion object {
        const val TAG_TTS = "tts_download"
    }
}
