plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "yupay.turismo"
    compileSdk = 36

    defaultConfig {
        applicationId = "yupay.turismo"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Solo empaquetar arm64-v8a (móviles modernos, 2019+). Evita duplicar el motor TTS de
            // Sherpa-ONNX (~30 MB de .so por arquitectura) en ABIs que no se usan: x86/x86_64 son
            // solo para el emulador y armeabi-v7a es ARM de 32 bits antiguo. Reduce el APK ~90 MB.
            // Para incluir más ABIs: añádelas aquí Y bájalas con scripts/fetch-sherpa-onnx-libs.ps1 -Abis ...
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Firmar el release con el keystore DEBUG estándar (~/.android/debug.keystore).
            // Mantiene el MISMO SHA-1 que los builds debug, que es el registrado en Google
            // Cloud, así el inicio de sesión con Google sigue funcionando. Sin esto Gradle
            // genera "app-release-unsigned.apk" (no instalable) y el pipeline no lo encuentra.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    lint {
        // El "lint vital" del build release crea .jar en intermediates/lint-cache que un
        // daemon de Gradle deja con candado en Windows, haciendo fallar el siguiente
        // `:app:clean` ("Unable to delete directory ...\app\build"). Como esto es un build
        // de DISTRIBUCIÓN de una app ya probada, se omite el lint del release (sigue
        // corriendo en debug y en el IDE). Esto elimina la causa del bloqueo y acelera el build.
        checkReleaseBuilds = false
    }
    compileOptions {
        // Habilita java.time (Instant/OffsetDateTime) en minSdk 24 vía desugaring.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.osmdroid.android)
    // Vico: gráficos profesionales para Compose (Home + Dashboard)
    implementation(libs.vico.compose.m3)
    implementation(libs.play.services.code.scanner)
    implementation(libs.barcode.scanning)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    // ProcessLifecycleOwner: detecta si la app está en primer/segundo plano (notificaciones).
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // --- Text-to-Speech (Sherpa-ONNX) ---
    // WorkManager: descarga bajo demanda de los modelos de voz (con restricción de red,
    // progreso observable y cancelación). Coroutine-friendly vía work-runtime-ktx.
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // Apache Commons Compress: descomprime los paquetes .tar.bz2 de los modelos Piper de
    // sherpa-onnx (bzip2 + tar son Java puro y funcionan en Android). La v1.21 está
    // ampliamente probada en Android (no usa java.nio.file en el camino de streaming).
    implementation("org.apache.commons:commons-compress:1.21")
    // NOTA: la librería NATIVA de Sherpa-ONNX (libsherpa-onnx-jni.so + libonnxruntime.so) NO
    // se declara aquí como dependencia Maven. Hay que copiar los .so oficiales en
    // app/src/main/jniLibs/<abi>/ (ver com/k2fsa/sherpa/onnx/Tts.kt para instrucciones).
    // La API Kotlin (com.k2fsa.sherpa.onnx) va vendorizada como fuente en este módulo, así que
    // NO debe añadirse además el AAR oficial (provocaría clases duplicadas).

    // --- Integración con la API en la nube ---
    // Cliente HTTP (OkHttp) + (de)serialización con kotlinx.serialization (ya presente).
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    // Almacén seguro de sesión (tokens) fuera de Room.
    implementation(libs.androidx.datastore.preferences)
    // Login con Google nativo (Credential Manager / One Tap) → idToken → /auth/google/idtoken.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    // java.time para minSdk 24.
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}