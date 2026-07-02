package yupay.turismo.di

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import yupay.turismo.data.local.AppDatabase
import yupay.turismo.data.prefs.DevicePreferences
import yupay.turismo.data.remote.HttpModule
import yupay.turismo.data.remote.YupayApiService
import yupay.turismo.data.repository.AuthRepository
import yupay.turismo.data.repository.CloudSyncRepository
import yupay.turismo.data.session.SessionManager
import yupay.turismo.data.sync.CloudSyncEngine
import yupay.turismo.notifications.SyncEventBus
import yupay.turismo.sync.P2pSyncController
import yupay.turismo.tts.TtsManager
import yupay.turismo.tts.audio.AudioPlaybackController
import yupay.turismo.tts.download.TtsDownloadRepository
import yupay.turismo.utils.NetworkMonitor

/**
 * Localizador de servicios simple (sin framework de DI), acorde al estilo del proyecto
 * (`AppDatabase.getDatabase`). Construye y mantiene los singletons de la capa de nube.
 *
 * [init] es idempotente y barato; lo llaman tanto [yupay.turismo.YupayApp] como los
 * ViewModels de forma defensiva. [startBackgroundSync] arranca los observadores una sola vez.
 */
object ServiceLocator {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var initialized = false
    @Volatile private var started = false

    lateinit var sessionManager: SessionManager
        private set
    lateinit var apiService: YupayApiService
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var cloudSyncRepository: CloudSyncRepository
        private set
    lateinit var cloudSyncEngine: CloudSyncEngine
        private set
    lateinit var networkMonitor: NetworkMonitor
        private set
    lateinit var devicePrefs: DevicePreferences
        private set
    lateinit var syncEventBus: SyncEventBus
        private set
    lateinit var p2pController: P2pSyncController
        private set

    // ───────── Text-to-Speech (Sherpa-ONNX) ─────────
    lateinit var ttsDownloadRepository: TtsDownloadRepository
        private set
    lateinit var ttsManager: TtsManager
        private set

    /** Reproductor "ligado a página" (tips/mapas/dashboard) con seek + velocidad + caché WAV. */
    lateinit var audioPlaybackController: AudioPlaybackController
        private set

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            val db = AppDatabase.getDatabase(app)

            sessionManager = SessionManager(app)
            apiService = HttpModule.create(sessionManager)
            devicePrefs = DevicePreferences(app)
            syncEventBus = SyncEventBus()
            authRepository = AuthRepository(apiService, sessionManager, db.appSettingsDao())
            cloudSyncRepository = CloudSyncRepository(
                api = apiService,
                session = sessionManager,
                appSettingsDao = db.appSettingsDao(),
                productDao = db.productDao(),
                visitDao = db.visitDao(),
                pendingOpDao = db.pendingOpDao(),
                syncEventBus = syncEventBus
            )
            networkMonitor = NetworkMonitor(app)
            cloudSyncEngine = CloudSyncEngine(cloudSyncRepository, sessionManager, networkMonitor, apiService)
            p2pController = P2pSyncController(app)

            // TTS: repositorio de descargas (WorkManager + Room) y manager de reproducción.
            // Recibe networkMonitor (gating offline) y devicePrefs (checkpoints de pausa).
            ttsDownloadRepository = TtsDownloadRepository(app, db.ttsPreferenceDao(), networkMonitor, devicePrefs)
            ttsManager = TtsManager(app, ttsDownloadRepository)
            audioPlaybackController = AudioPlaybackController(app, ttsDownloadRepository)

            initialized = true
        }
    }

    /** Arranca el monitor de red y los disparadores automáticos de sync (una sola vez). */
    fun startBackgroundSync() {
        if (started) return
        synchronized(this) {
            if (started) return
            networkMonitor.start()
            cloudSyncEngine.start(appScope)
            p2pController.start()
            started = true
        }
    }
}
