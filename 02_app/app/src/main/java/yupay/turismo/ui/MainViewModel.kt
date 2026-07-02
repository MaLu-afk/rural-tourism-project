package yupay.turismo.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import yupay.turismo.data.AppReset
import yupay.turismo.data.DataRepository
import yupay.turismo.data.local.AppDatabase
import yupay.turismo.data.local.DefaultContent
import yupay.turismo.data.local.AppSettings
import yupay.turismo.data.local.Visit
import yupay.turismo.data.local.Product
import yupay.turismo.data.local.SelectedProduct
import yupay.turismo.data.local.DiscountType
import yupay.turismo.data.model.CountryFeature
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.ByteArrayOutputStream
import yupay.turismo.data.repository.AuthResult
import yupay.turismo.data.repository.ForgotStatus
import yupay.turismo.data.repository.RegisterStatus
import yupay.turismo.data.session.Session
import yupay.turismo.data.sync.CloudSyncEngine
import yupay.turismo.di.ServiceLocator

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: DataRepository
    val appSettings: StateFlow<AppSettings?>
    val allVisits: StateFlow<List<Visit>>
    val allProducts: StateFlow<List<Product>>

    private val _countryFeatures = MutableStateFlow<List<CountryFeature>>(emptyList())
    val countryFeatures: StateFlow<List<CountryFeature>> = _countryFeatures.asStateFlow()

    // ───────── Integración con la nube (auth + sync) ─────────
    private val authRepository by lazy { ServiceLocator.authRepository }
    private val cloudSync by lazy { ServiceLocator.cloudSyncRepository }
    private val cloudSyncEngine by lazy { ServiceLocator.cloudSyncEngine }
    private val sessionManager by lazy { ServiceLocator.sessionManager }

    val cloudSession: StateFlow<Session?>
    val syncState: StateFlow<CloudSyncEngine.SyncState>
    val pendingSyncCount: StateFlow<Int>

    private val _authState = MutableStateFlow(AuthUiState())
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()
    private var recoveryToken: String? = null

    // ───────── Deep link de notificaciones ─────────
    private val _navTarget = MutableStateFlow<String?>(null)
    /** Ruta a la que navegar cuando la app se abre/retoma desde una notificación. */
    val navTarget: StateFlow<String?> = _navTarget.asStateFlow()

    /** Lo fija [yupay.turismo.MainActivity] al leer el extra del Intent de la notificación. */
    fun setNavTarget(target: String?) { _navTarget.value = target }

    /** Lo llama la navegación tras consumir el destino, para no repetir el salto. */
    fun consumeNavTarget() { _navTarget.value = null }

    // ───────── Notificaciones (toggle por-dispositivo) ─────────
    private val devicePrefs by lazy { ServiceLocator.devicePrefs }

    /** ¿Notificaciones activadas? Persistido en DataStore local (no se sincroniza). */
    val notificationsEnabled: StateFlow<Boolean> by lazy {
        devicePrefs.notificationsEnabledFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    }

    /**
     * Activa/desactiva las notificaciones: persiste la preferencia y arranca/detiene el
     * Foreground Service de sincronización. El permiso runtime (Android 13+) se pide en la UI
     * ANTES de llamar aquí con `enabled = true`.
     */
    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            devicePrefs.setNotificationsEnabled(enabled)
            val app = getApplication<Application>()
            if (enabled) {
                yupay.turismo.service.SyncForegroundService.start(app)
            } else {
                yupay.turismo.service.SyncForegroundService.stop(app)
                yupay.turismo.notifications.NotificationHelper.cancelEventNotifications(app)
            }
        }
    }

    init {
        // Idempotente: garantiza los singletons aunque YupayApp no se haya ejecutado aún.
        ServiceLocator.init(application)

        val database = AppDatabase.getDatabase(application)
        repository = DataRepository(database.appSettingsDao(), database.visitDao(), database.productDao())
        
        appSettings = repository.appSettings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null,
        )

        allVisits = repository.allVisits.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allProducts = repository.allProducts.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        cloudSession = sessionManager.sessionFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
        syncState = cloudSyncEngine.state
        pendingSyncCount = cloudSyncEngine.pendingCountFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

        loadCountryFeatures()

        viewModelScope.launch {
            val androidId = Settings.Secure.getString(
                application.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "unknown"
            
            val currentSettings = repository.getSettingsOnce()
            if (currentSettings == null) {
                // Sin cuenta todavía: arrancamos SIN tips/mapas. Los textos (y su audio) llegan al
                // registrar la cuenta y vuelven al iniciar sesión (ver seedDefaultContentIfEmpty).
                repository.saveSettings(AppSettings(
                    deviceId = androidId,
                    hardwareDeviceId = androidId
                ))
            } else {
                var updated = currentSettings
                var changed = false

                if (currentSettings.hardwareDeviceId.isEmpty()) {
                    updated = updated.copy(hardwareDeviceId = androidId)
                    changed = true
                }

                // Si el deviceId está vacío (por ser nuevo campo) y no es servidor
                if (currentSettings.deviceId.isEmpty()) {
                    updated = updated.copy(deviceId = androidId)
                    changed = true
                }

                if (changed) {
                    repository.saveSettings(updated)
                }
            }
        }
    }

    private val defaultTips = DefaultContent.tips
    private val defaultSummaries = DefaultContent.summaries

    /**
     * Siembra los tips/resúmenes por defecto si están vacíos. Se usa al REGISTRAR una cuenta nueva
     * (antes de firstLink, para que migrate los suba) y en logins de cuentas antiguas cuyo servidor
     * no tenía contenido. Devuelve true si sembró algo.
     */
    private suspend fun seedDefaultContentIfEmpty(): Boolean {
        val s = repository.getSettingsOnce() ?: return false
        if (s.entrepreneurTips.isEmpty() || s.mapSummary.isEmpty()) {
            repository.saveSettings(
                s.copy(
                    entrepreneurTips = defaultTips,
                    mapSummary = defaultSummaries,
                    lastModified = System.currentTimeMillis()
                )
            )
            return true
        }
        return false
    }

    /** Login de cuenta existente: si el servidor no tenía tips/mapas, siémbralos y súbelos (una vez). */
    private suspend fun seedAndUploadContentIfServerEmpty() {
        if (seedDefaultContentIfEmpty()) {
            cloudSyncEngine.reconcileFromP2p()
        }
    }

    fun addVisit(
        nationality: String,
        flag: String,
        selectedProducts: List<SelectedProduct>,
        subtotal: Double,
        discountValue: Double,
        discountType: DiscountType,
        totalAmount: Double
    ) {
        viewModelScope.launch {
            val currentSettings = repository.getSettingsOnce()
            val visit = Visit(
                deviceId = currentSettings?.deviceId ?: "",
                nationality = nationality,
                nationalityFlag = flag,
                selectedProducts = selectedProducts,
                subtotal = subtotal,
                discountValue = discountValue,
                discountType = discountType,
                totalAmount = totalAmount,
                currency = currentSettings?.preferredCurrency ?: "S/"
            )
            val newId = repository.insertVisit(visit).toInt()
            if (currentSettings?.isLinked == true) cloudSync.enqueueVisitUpsert(newId)
        }
    }

    fun addProduct(product: Product) {
        viewModelScope.launch {
            val newId = repository.insertProduct(product).toInt()
            if (isLinked()) cloudSync.enqueueProductUpsert(newId)
        }
    }

    fun updateProduct(product: Product) {
        viewModelScope.launch {
            repository.updateProduct(product)
            if (isLinked()) cloudSync.enqueueProductUpsert(product.id)
        }
    }

    fun deleteProduct(product: Product) {
        viewModelScope.launch {
            if (isLinked()) cloudSync.enqueueProductDelete(product)
            repository.deleteProduct(product)
        }
    }

    suspend fun getProductById(id: Int): Product? {
        return repository.getProductById(id)
    }

    fun preloadProducts(category: String) {
        viewModelScope.launch {
            // Eliminar productos previos que sean 'isDefault' para evitar duplicados al cambiar de opinión en el setup
            val all = repository.allProducts.first()
            val defaultsToDelete = all.filter { it.isDefault }
            defaultsToDelete.forEach { repository.deleteProduct(it) }

            val products = when (category) {
                "Hospedaje" -> listOf(
                    Product(name = "Habitación Simple", basePrice = 50.0, category = "Hospedaje", isDefault = true)
                )
                "Alimentación" -> listOf(
                    Product(name = "Almuerzo del día", basePrice = 15.0, category = "Alimentación", isDefault = true)
                )
                "Artesanía" -> listOf(
                    Product(name = "Artesanía de la zona", basePrice = 25.0, category = "Artesanía", isDefault = true)
                )
                "Varios" -> listOf(
                    Product(name = "Habitación", basePrice = 50.0, category = "Hospedaje", isDefault = true),
                    Product(name = "Almuerzo", basePrice = 15.0, category = "Alimentación", isDefault = true),
                    Product(name = "Recuerdo / Artesanía", basePrice = 20.0, category = "Artesanía", isDefault = true)
                )
                else -> emptyList()
            }
            if (products.isNotEmpty()) {
                repository.insertProducts(products)
            }
        }
    }

    suspend fun getVisitDetail(id: Int): Visit? {
        return repository.getVisitById(id)
    }

    fun saveLanguage(language: String) {
        viewModelScope.launch {
            val current = repository.getSettingsOnce() ?: AppSettings()
            repository.saveSettings(current.copy(
                language = language,
                lastModified = System.currentTimeMillis()
            ))
        }
    }

    fun saveProfile(name: String, category: String, profilePicture: ByteArray? = null) {
        viewModelScope.launch {
            val current = repository.getSettingsOnce() ?: AppSettings()
            val oldCategory = current.businessCategory
            
            // Si cambia la categoría y no es "Varios", borrar productos que no pertenezcan
            if (category != oldCategory && category != "Varios") {
                val allProducts = repository.allProducts.first()
                val productsToDelete = allProducts.filter { it.category != category }
                productsToDelete.forEach { p ->
                    if (current.isLinked) cloudSync.enqueueProductDelete(p)
                    repository.deleteProduct(p)
                }
            }

            repository.saveSettings(current.copy(
                businessName = name,
                businessCategory = category,
                profilePicture = profilePicture ?: current.profilePicture,
                isOnboardingCompleted = true,
                lastModified = System.currentTimeMillis()
            ))
            if (current.isLinked) cloudSync.enqueueProfileUpdate()
        }
    }

    fun updatePreferredCurrency(currency: String) {
        viewModelScope.launch {
            val current = repository.getSettingsOnce() ?: AppSettings()
            repository.saveSettings(current.copy(
                preferredCurrency = currency,
                lastModified = System.currentTimeMillis()
            ))
        }
    }

    fun updateExchangeRates(usd: Double, eur: Double) {
        viewModelScope.launch {
            val current = repository.getSettingsOnce() ?: AppSettings()
            repository.saveSettings(current.copy(
                usdExchangeRate = usd,
                eurExchangeRate = eur,
                lastModified = System.currentTimeMillis()
            ))
        }
    }

    fun updateVoiceSpeed(speed: Float) {
        viewModelScope.launch {
            val current = repository.getSettingsOnce() ?: AppSettings()
            repository.saveSettings(current.copy(
                voiceSpeed = speed,
                lastModified = System.currentTimeMillis()
            ))
        }
    }

    @Deprecated("Mock offline. Usar register()/login() reales con la API.")
    fun linkAccount(email: String, password: String) {
        viewModelScope.launch {
            val current = repository.getSettingsOnce() ?: AppSettings()
            repository.saveSettings(current.copy(
                isLinked = true,
                accountEmail = email,
                accountPassword = password,
                lastModified = System.currentTimeMillis()
            ))
        }
    }

    fun clearAllAppData() {
        viewModelScope.launch {
            // Reset total a estado de fábrica (sesión + outbox + Room + ajustes por defecto).
            // Mismo comportamiento que el reset del servidor al cancelar el P2P (AppReset).
            AppReset.factoryReset(getApplication())
            _authState.value = AuthUiState()
        }
    }

    // ════════════════════ Autenticación con la API ════════════════════
    // Estas funciones disparan estados en [authState]; la UI las observará en la fase de UI.

    fun register(email: String, password: String) {
        runAuth {
            val s = repository.getSettingsOnce()
            when (val r = authRepository.register(email, password, s?.businessName, s?.businessCategory)) {
                is AuthResult.Ok -> when (r.value) {
                    RegisterStatus.LOGGED_IN -> {
                        seedDefaultContentIfEmpty() // cuenta nueva: tips/mapas llegan al registrar
                        cloudSyncEngine.firstLink()
                        AuthUiState(event = AuthEvent.LoggedIn)
                    }
                    RegisterStatus.NEEDS_EMAIL_CONFIRMATION ->
                        AuthUiState(
                            event = AuthEvent.NeedsEmailConfirmation,
                            info = "Revisa tu correo y confirma la cuenta para continuar."
                        )
                }
                is AuthResult.Err -> AuthUiState(error = r.message)
            }
        }
    }

    /**
     * Verifica el código de registro (OTP de signup) enviado al correo. Si es válido, queda
     * la sesión iniciada y se suben los datos que el emprendedor ya tenía en la app (migrate).
     */
    fun verifySignupCode(email: String, code: String) {
        runAuth {
            when (val r = authRepository.verifySignupCode(email, code)) {
                is AuthResult.Ok -> {
                    seedDefaultContentIfEmpty() // cuenta nueva: tips/mapas llegan al registrar
                    cloudSyncEngine.firstLink()
                    AuthUiState(event = AuthEvent.LoggedIn)
                }
                is AuthResult.Err -> AuthUiState(error = r.message)
            }
        }
    }

    fun login(email: String, password: String) {
        runAuth {
            when (val r = authRepository.login(email, password)) {
                is AuthResult.Ok -> {
                    cloudSyncEngine.firstLink()
                    seedAndUploadContentIfServerEmpty() // cuenta antigua sin contenido en la nube
                    markOnboardingCompleted()
                    AuthUiState(event = AuthEvent.LoggedIn)
                }
                is AuthResult.Err -> AuthUiState(error = r.message)
            }
        }
    }

    /**
     * LOGIN con Google (pantalla de inicio de sesión).
     * - Cuenta nueva (no existía en Supabase): inicia sesión y **siembra datos por defecto**
     *   (nombre/sector/productos) — caso 1.
     * - Cuenta existente: login normal y baja sus datos — caso 2.
     */
    fun signInWithGoogle(idToken: String, nonce: String? = null) {
        runAuth {
            when (val r = authRepository.loginWithGoogleIdToken(idToken, nonce)) {
                is AuthResult.Ok -> {
                    if (r.value) {
                        seedDefaultBusinessData()   // isNewUser → datos por defecto
                        seedDefaultContentIfEmpty() // y tips/mapas, para subirlos en firstLink
                    }
                    cloudSyncEngine.firstLink()
                    if (!r.value) seedAndUploadContentIfServerEmpty() // cuenta antigua sin contenido
                    markOnboardingCompleted()
                    AuthUiState(event = AuthEvent.LoggedIn)
                }
                is AuthResult.Err -> AuthUiState(error = r.message)
            }
        }
    }

    /**
     * REGISTRO con Google (pantalla de registro).
     * - Cuenta nueva: crea el usuario (sin contraseña) y **sube los datos** que ya tenía la app
     *   — caso 3. La contraseña se establece luego en "Información de cuenta".
     * - Cuenta ya registrada: NO registra; avisa "cuenta ya usada" — caso 4.
     */
    fun signUpWithGoogle(idToken: String, nonce: String? = null) {
        runAuth {
            when (val r = authRepository.loginWithGoogleIdToken(idToken, nonce)) {
                is AuthResult.Ok -> {
                    if (r.value) {
                        seedDefaultContentIfEmpty() // cuenta nueva: tips/mapas llegan al registrar
                        cloudSyncEngine.firstLink()
                        markOnboardingCompleted()
                        AuthUiState(event = AuthEvent.LoggedIn)
                    } else {
                        // La cuenta de Google ya existía → no se permite "registrar".
                        authRepository.discardLocalSession()
                        AuthUiState(event = AuthEvent.AccountAlreadyExists)
                    }
                }
                is AuthResult.Err -> AuthUiState(error = r.message)
            }
        }
    }

    /** Comprueba si un correo ya está registrado (para avisar antes de enviar el formulario). */
    fun checkEmail(email: String, onResult: (exists: Boolean, confirmed: Boolean) -> Unit) {
        viewModelScope.launch {
            when (val r = authRepository.checkEmail(email)) {
                is AuthResult.Ok -> onResult(r.value.exists, r.value.confirmed)
                is AuthResult.Err -> onResult(false, false)
            }
        }
    }

    fun resendVerification(email: String) {
        runAuth {
            when (val r = authRepository.resendVerification(email)) {
                is AuthResult.Ok -> AuthUiState(info = "Te reenviamos el correo de verificación.")
                is AuthResult.Err -> AuthUiState(error = r.message)
            }
        }
    }

    fun forgotPassword(email: String) {
        runAuth {
            when (val r = authRepository.forgotPassword(email)) {
                is AuthResult.Ok -> when (r.value) {
                    ForgotStatus.SENT ->
                        AuthUiState(event = AuthEvent.CodeSent, info = "Te enviamos un código a tu correo.")
                    ForgotStatus.NOT_REGISTERED ->
                        AuthUiState(error = "No hay ninguna cuenta registrada con ese correo.")
                    ForgotStatus.OAUTH_ONLY ->
                        AuthUiState(error = "Esa cuenta inicia sesión con Google; no tiene contraseña que restablecer.")
                }
                is AuthResult.Err -> AuthUiState(error = r.message)
            }
        }
    }

    /** Paso 1 del reset de dos pantallas: valida el código y guarda el token de recuperación. */
    fun verifyResetCode(email: String, code: String) {
        runAuth {
            when (val r = authRepository.verifyResetCode(email, code)) {
                is AuthResult.Ok -> {
                    recoveryToken = r.value
                    AuthUiState(event = AuthEvent.CodeValid)
                }
                is AuthResult.Err -> AuthUiState(error = r.message)
            }
        }
    }

    /** Paso 2 del reset de dos pantallas (usa el token de [verifyResetCode]). */
    fun resetPassword(newPassword: String) {
        runAuth {
            val token = recoveryToken
                ?: return@runAuth AuthUiState(error = "Primero valida el código de recuperación.")
            when (val r = authRepository.resetPassword(newPassword, recoveryAccessToken = token)) {
                is AuthResult.Ok -> {
                    recoveryToken = null
                    AuthUiState(event = AuthEvent.PasswordReset, info = "Contraseña actualizada. Inicia sesión.")
                }
                is AuthResult.Err -> AuthUiState(error = r.message)
            }
        }
    }

    /** Reset en un solo paso: código + nueva contraseña juntos (no llamar a verifyResetCode antes). */
    fun resetPassword(email: String, code: String, newPassword: String) {
        runAuth {
            when (val r = authRepository.resetPassword(newPassword, email = email, code = code)) {
                is AuthResult.Ok ->
                    AuthUiState(event = AuthEvent.PasswordReset, info = "Contraseña actualizada. Inicia sesión.")
                is AuthResult.Err -> AuthUiState(error = r.message)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            cloudSync.clearOutbox()
            _authState.value = AuthUiState(event = AuthEvent.LoggedOut)
        }
    }

    fun deleteAccount() {
        runAuth {
            when (val r = authRepository.deleteAccount()) {
                is AuthResult.Ok -> {
                    // La cuenta y sus datos ya se borraron en la API (DELETE /auth/account).
                    // Dejamos el dispositivo como recién instalado: sesión + outbox + audio +
                    // Room + ajustes por defecto (mismo reset que el logout/P2P, vía AppReset).
                    AppReset.factoryReset(getApplication())
                    AuthUiState(event = AuthEvent.AccountDeleted)
                }
                is AuthResult.Err -> AuthUiState(error = r.message)
            }
        }
    }

    /** Sincronización manual (botón "Sincronizar ahora"). */
    fun syncNow() {
        viewModelScope.launch { cloudSyncEngine.syncNow() }
    }

    fun consumeAuthEvent() {
        _authState.value = _authState.value.copy(event = null)
    }

    fun clearAuthMessages() {
        _authState.value = _authState.value.copy(error = null, info = null)
    }

    /** Establece/cambia la contraseña del usuario autenticado (p.ej. cuentas creadas con Google). */
    fun changeAccountPassword(newPassword: String) {
        runAuth {
            when (val r = authRepository.setPasswordWithSession(newPassword)) {
                is AuthResult.Ok ->
                    AuthUiState(event = AuthEvent.PasswordReset, info = "Contraseña actualizada.")
                is AuthResult.Err -> AuthUiState(error = r.message)
            }
        }
    }

    /** Surface de errores de la UI (p.ej. fallo/cancelación del selector de Google). */
    fun setAuthError(message: String) {
        _authState.value = _authState.value.copy(loading = false, googleLoading = false, error = message)
    }

    /**
     * Marca el inicio del flujo con Google (clic en "Continuar con Google"): enciende la pantalla
     * de carga de inmediato y limpia mensajes previos. La carga se mantiene durante la obtención
     * del idToken y la llamada a la API, y se apaga sola al terminar [signInWithGoogle] /
     * [signUpWithGoogle] (vía [runAuth]) o ante error/cancelación.
     */
    fun beginGoogleAuth() {
        _authState.value = _authState.value.copy(
            googleLoading = true, error = null, info = null, event = null
        )
    }

    /** El usuario canceló el selector de Google: apaga la pantalla de carga y vuelve al flujo normal. */
    fun cancelGoogleAuth() {
        _authState.value = _authState.value.copy(googleLoading = false)
    }

    /** Caso 1: cuenta de Google nueva → datos por defecto del emprendimiento (si no hay ya). */
    private suspend fun seedDefaultBusinessData() {
        val current = repository.getSettingsOnce() ?: AppSettings()
        repository.saveSettings(
            current.copy(
                businessName = current.businessName.ifBlank { "Mi Emprendimiento" },
                businessCategory = current.businessCategory.ifBlank { "Varios" },
                isOnboardingCompleted = true,
                lastModified = System.currentTimeMillis()
            )
        )
        if (repository.allProducts.first().isEmpty()) {
            repository.insertProducts(
                listOf(
                    Product(name = "Habitación", basePrice = 50.0, category = "Hospedaje", isDefault = true),
                    Product(name = "Almuerzo", basePrice = 15.0, category = "Alimentación", isDefault = true),
                    Product(name = "Recuerdo / Artesanía", basePrice = 20.0, category = "Artesanía", isDefault = true)
                )
            )
        }
    }

    private suspend fun markOnboardingCompleted() {
        val s = repository.getSettingsOnce() ?: return
        if (!s.isOnboardingCompleted) {
            repository.saveSettings(s.copy(isOnboardingCompleted = true, lastModified = System.currentTimeMillis()))
        }
    }

    private suspend fun isLinked(): Boolean = repository.getSettingsOnce()?.isLinked == true

    private fun runAuth(block: suspend () -> AuthUiState) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(loading = true, error = null, info = null, event = null)
            val result = try {
                block()
            } catch (e: Exception) {
                AuthUiState(error = e.message ?: "Error inesperado.")
            }
            _authState.value = result.copy(loading = false)
        }
    }

    fun updateProfilePicture(bitmap: Bitmap, saveImmediately: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val finalWidth = if (bitmap.width > 128) 128 else bitmap.width
                val finalHeight = if (bitmap.height > 128) 128 else bitmap.height
                
                val resizedBitmap = if (bitmap.width > 128 || bitmap.height > 128) {
                    Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true)
                } else {
                    bitmap
                }

                val outputStream = ByteArrayOutputStream()
                resizedBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                val byteArray = outputStream.toByteArray()

                if (saveImmediately) {
                    val current = repository.getSettingsOnce() ?: AppSettings()
                    repository.saveSettings(current.copy(
                        profilePicture = byteArray,
                        lastModified = System.currentTimeMillis()
                    ))
                    if (current.isLinked) cloudSync.enqueueProfileUpdate()
                }
                
                if (resizedBitmap != bitmap) resizedBitmap.recycle()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateProfilePictureFromUri(uri: Uri) {
        val contentResolver = getApplication<Application>().contentResolver
        viewModelScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    originalBitmap?.let { updateProfilePicture(it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadCountryFeatures() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val geoJsonStr = context.assets.open("countries5.json").bufferedReader().use { it.readText() }
                val geoJson = JSONObject(geoJsonStr)
                val features = geoJson.optJSONArray("features") ?: return@launch
                val parsedFeatures = mutableListOf<CountryFeature>()

                for (i in 0 until features.length()) {
                    val feature = features.optJSONObject(i) ?: continue
                    val properties = feature.optJSONObject("properties") ?: continue
                    val countryName = properties.optString("name", "Unknown")
                    val geometry = feature.optJSONObject("geometry") ?: continue
                    val type = geometry.optString("type")
                    val polygons = mutableListOf<List<GeoPoint>>()

                    if (type == "Polygon") {
                        val coordinates = geometry.optJSONArray("coordinates") ?: continue
                        val ring = coordinates.optJSONArray(0) ?: continue
                        polygons.add(parseRing(ring))
                    } else if (type == "MultiPolygon") {
                        val multiCoords = geometry.optJSONArray("coordinates") ?: continue
                        for (j in 0 until multiCoords.length()) {
                            val coords = multiCoords.optJSONArray(j) ?: continue
                            val ring = coords.optJSONArray(0) ?: continue
                            polygons.add(parseRing(ring))
                        }
                    }
                    
                    val centroid = if (polygons.isNotEmpty()) {
                        val mainRing = polygons.maxByOrNull { it.size } ?: polygons.first()
                        var sumLat = 0.0
                        var sumLon = 0.0
                        mainRing.forEach { 
                            sumLat += it.latitude
                            sumLon += it.longitude
                        }
                        GeoPoint(sumLat / mainRing.size, sumLon / mainRing.size)
                    } else null

                    val displayPoint = if (centroid != null && polygons.isNotEmpty()) {
                        val mainRing = polygons.maxByOrNull { it.size } ?: polygons.first()
                        val vertex = mainRing[mainRing.size / 3]
                        GeoPoint(
                            (centroid.latitude + vertex.latitude) / 2.0,
                            (centroid.longitude + vertex.longitude) / 2.0
                        )
                    } else centroid
                    
                    parsedFeatures.add(CountryFeature(countryName, polygons, centroid, displayPoint))
                }
                _countryFeatures.value = parsedFeatures
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun parseRing(coordinates: org.json.JSONArray): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        for (i in 0 until coordinates.length()) {
            val coord = coordinates.optJSONArray(i) ?: continue
            if (coord.length() >= 2) {
                points.add(GeoPoint(coord.optDouble(1), coord.optDouble(0)))
            }
        }
        return points
    }
}

/** Estado observable de los flujos de autenticación (para la UI). */
data class AuthUiState(
    val loading: Boolean = false,
    /**
     * Carga específica del flujo con Google: cubre TODO el proceso (obtener el idToken con
     * Credential Manager + llamada a la API), para mostrar una pantalla de carga desde el clic
     * del botón hasta la respuesta. Separado de [loading] para no alterar el flujo de correo.
     */
    val googleLoading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val event: AuthEvent? = null
)

/** Eventos de un solo uso emitidos por los flujos de autenticación. */
sealed interface AuthEvent {
    object LoggedIn : AuthEvent
    object NeedsEmailConfirmation : AuthEvent
    object CodeSent : AuthEvent
    object CodeValid : AuthEvent
    object PasswordReset : AuthEvent
    object LoggedOut : AuthEvent
    object AccountDeleted : AuthEvent
    object AccountAlreadyExists : AuthEvent
}
