package yupay.turismo.tts

/**
 * Catálogo de modelos de voz REALES de Sherpa-ONNX por idioma.
 *
 * Datos verificados (junio 2026) contra:
 *  - API de releases de GitHub `k2-fsa/sherpa-onnx`, tag `tts-models` (campo `size` exacto en
 *    bytes y `digest = sha256:...` de cada asset .tar.bz2).
 *  - API de HuggingFace para la conversión ONNX de la voz MMS de quechua.
 * No hay valores inventados: tamaños y SHA-256 son los oficiales.
 *
 * Mapeo CALIDAD → variante del fichero:
 *  - [ModelQuality.LIGHT]  = variante cuantizada `-int8` (≈ 20–35 MB, ahorra datos).
 *  - [ModelQuality.HIGH]   = modelo completo fp32 (≈ 64–115 MB, mejor calidad).
 *  Se usa la MISMA voz para LIGHT y HIGH (solo cambia la cuantización), lo que garantiza que
 *  ambas variantes tengan el mismo género; es el compromiso tamaño/calidad más limpio.
 *
 * Disponibilidad de GÉNERO por idioma en Sherpa-ONNX (importante, según requisito):
 *  - Español (es): MASCULINO (es_ES-davefx, España) y FEMENINO (es_AR-daniela, Argentina). ✔ ambos.
 *  - Inglés (en):  MASCULINO y FEMENINO (en_US-hfc_male / en_US-hfc_female). ✔ ambos.
 *  - Portugués (pt): SOLO MASCULINO. A día de hoy Piper NO tiene voz femenina en pt_BR ni pt_PT
 *    (todas las voces portuguesas oficiales son masculinas; hay solicitudes abiertas en
 *    rhasspy/piper). Por eso el catálogo de portugués solo ofrece pt_BR-faber (masc.) en LIGHT/HIGH.
 *  - Quechua (quz): NO existe en el release oficial de sherpa-onnx. Solo está la voz MMS de
 *    Facebook (`facebook/mms-tts-quz`, Cusco-Collao) convertida a ONNX por la comunidad
 *    (willwade/mms-tts-multilingual-models-onnx). Es de UN solo locutor, género NEUTRAL, y NO
 *    tiene variantes de calidad ni de género: una única opción (~109 MB, modelo completo).
 */
object TtsModelCatalog {

    /** Base de los assets del release oficial de sherpa-onnx. */
    private const val GH = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"

    /** Base de la conversión ONNX comunitaria de los modelos MMS (incluye quechua quz). */
    private const val HF_MMS = "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/main"

    // ---------------------------------------------------------------------------------------
    // ESPAÑOL — voz masculina (es_ES-davefx, España) + voz femenina (es_AR-daniela, Argentina)
    // ---------------------------------------------------------------------------------------
    private val SPANISH = listOf(
        TtsModelInfo(
            id = "es-davefx-light",
            language = SupportedLanguage.SPANISH,
            displayName = "Voz masculina · España",
            gender = VoiceGender.MALE,
            quality = ModelQuality.LIGHT,
            sizeBytes = 21_171_632L,
            engine = TtsEngineType.PIPER,
            source = ModelSource.Archive(
                url = "$GH/vits-piper-es_ES-davefx-medium-int8.tar.bz2",
                sha256 = "8bb8ac1cefb727caec9bd9c6c3185c673c8b42c53bd29bb25d5a7715dac37125",
            ),
            note = "Piper es_ES-davefx (cuantizado int8).",
        ),
        TtsModelInfo(
            id = "es-davefx-high",
            language = SupportedLanguage.SPANISH,
            displayName = "Voz masculina · España",
            gender = VoiceGender.MALE,
            quality = ModelQuality.HIGH,
            sizeBytes = 67_184_952L,
            engine = TtsEngineType.PIPER,
            source = ModelSource.Archive(
                url = "$GH/vits-piper-es_ES-davefx-medium.tar.bz2",
                sha256 = "a3f6beb54a9cb893279f72978a22f807a4d9fc9c7848157b524d5cc7b7f58b22",
            ),
            note = "Piper es_ES-davefx (completo).",
        ),
        TtsModelInfo(
            id = "es-daniela-light",
            language = SupportedLanguage.SPANISH,
            displayName = "Voz femenina · Argentina",
            gender = VoiceGender.FEMALE,
            quality = ModelQuality.LIGHT,
            sizeBytes = 35_069_782L,
            engine = TtsEngineType.PIPER,
            source = ModelSource.Archive(
                url = "$GH/vits-piper-es_AR-daniela-high-int8.tar.bz2",
                sha256 = "7218f0a119e4c16533ac187f71ab3019f2092f1594e43fef8392ae1f5b64abab",
            ),
            note = "Piper es_AR-daniela (cuantizado int8). No hay voz femenina es_ES en Sherpa-ONNX.",
        ),
        TtsModelInfo(
            id = "es-daniela-high",
            language = SupportedLanguage.SPANISH,
            displayName = "Voz femenina · Argentina",
            gender = VoiceGender.FEMALE,
            quality = ModelQuality.HIGH,
            sizeBytes = 115_562_134L,
            engine = TtsEngineType.PIPER,
            source = ModelSource.Archive(
                url = "$GH/vits-piper-es_AR-daniela-high.tar.bz2",
                sha256 = "71cbf6b7f646ab74f3c51336151abef41e6c54467ac929ffdb19ed706a07dd7b",
            ),
            note = "Piper es_AR-daniela (completo). No hay voz femenina es_ES en Sherpa-ONNX.",
        ),
    )

    // ---------------------------------------------------------------------------------------
    // INGLÉS — voz femenina (en_US-hfc_female) + voz masculina (en_US-hfc_male). El género va
    // explícito en el nombre del modelo Piper, así que la asignación es 100 % fiable.
    // ---------------------------------------------------------------------------------------
    private val ENGLISH = listOf(
        TtsModelInfo(
            id = "en-female-light",
            language = SupportedLanguage.ENGLISH,
            displayName = "Female voice · US",
            gender = VoiceGender.FEMALE,
            quality = ModelQuality.LIGHT,
            sizeBytes = 21_021_379L,
            engine = TtsEngineType.PIPER,
            source = ModelSource.Archive(
                url = "$GH/vits-piper-en_US-hfc_female-medium-int8.tar.bz2",
                sha256 = "7c467d19054ddf84056a4887a8be75f5f3b2a5d426637b526953a5e488fec5ce",
            ),
            note = "Piper en_US-hfc_female (int8).",
        ),
        TtsModelInfo(
            id = "en-female-high",
            language = SupportedLanguage.ENGLISH,
            displayName = "Female voice · US",
            gender = VoiceGender.FEMALE,
            quality = ModelQuality.HIGH,
            sizeBytes = 67_228_166L,
            engine = TtsEngineType.PIPER,
            source = ModelSource.Archive(
                url = "$GH/vits-piper-en_US-hfc_female-medium.tar.bz2",
                sha256 = "3fffdceb0c65bd9415a085d09c3cb88cc82f9d74a6ca453f8ce7fc5eaee81ff8",
            ),
            note = "Piper en_US-hfc_female (completo).",
        ),
        TtsModelInfo(
            id = "en-male-light",
            language = SupportedLanguage.ENGLISH,
            displayName = "Male voice · US",
            gender = VoiceGender.MALE,
            quality = ModelQuality.LIGHT,
            sizeBytes = 20_984_819L,
            engine = TtsEngineType.PIPER,
            source = ModelSource.Archive(
                url = "$GH/vits-piper-en_US-hfc_male-medium-int8.tar.bz2",
                sha256 = "a12daed3ba978ffb403dec4afa245d6f114668b370157a022fcc47fa6ca073d8",
            ),
            note = "Piper en_US-hfc_male (int8).",
        ),
        TtsModelInfo(
            id = "en-male-high",
            language = SupportedLanguage.ENGLISH,
            displayName = "Male voice · US",
            gender = VoiceGender.MALE,
            quality = ModelQuality.HIGH,
            sizeBytes = 67_214_049L,
            engine = TtsEngineType.PIPER,
            source = ModelSource.Archive(
                url = "$GH/vits-piper-en_US-hfc_male-medium.tar.bz2",
                sha256 = "76388f84acfca8ba5c0ed1636a26ada14c598abd52e76f110d4756fe326fc5f2",
            ),
            note = "Piper en_US-hfc_male (completo).",
        ),
    )

    // ---------------------------------------------------------------------------------------
    // PORTUGUÉS — SOLO MASCULINO (pt_BR-faber). No existe voz femenina portuguesa en Sherpa-ONNX
    // (ni pt_BR ni pt_PT). Se documenta el hueco; las tarjetas femeninas no se ofrecen.
    // ---------------------------------------------------------------------------------------
    private val PORTUGUESE = listOf(
        TtsModelInfo(
            id = "pt-faber-light",
            language = SupportedLanguage.PORTUGUESE,
            displayName = "Voz masculina · Brasil",
            gender = VoiceGender.MALE,
            quality = ModelQuality.LIGHT,
            sizeBytes = 21_335_916L,
            engine = TtsEngineType.PIPER,
            source = ModelSource.Archive(
                url = "$GH/vits-piper-pt_BR-faber-medium-int8.tar.bz2",
                sha256 = "05386120a50ee0c46e246bd0b9ad1c7b3116606b3f0c037db8ee4dd5dda712c5",
            ),
            note = "Piper pt_BR-faber (int8). Portugués no tiene voz femenina en Sherpa-ONNX.",
        ),
        TtsModelInfo(
            id = "pt-faber-high",
            language = SupportedLanguage.PORTUGUESE,
            displayName = "Voz masculina · Brasil",
            gender = VoiceGender.MALE,
            quality = ModelQuality.HIGH,
            sizeBytes = 67_209_996L,
            engine = TtsEngineType.PIPER,
            source = ModelSource.Archive(
                url = "$GH/vits-piper-pt_BR-faber-medium.tar.bz2",
                sha256 = "2227dadddfb3a2bad805d32dbace7dd4ff48053b32889dcf60d301ebac062a44",
            ),
            note = "Piper pt_BR-faber (completo). Portugués no tiene voz femenina en Sherpa-ONNX.",
        ),
    )

    // ---------------------------------------------------------------------------------------
    // QUECHUA (Cusco-Collao, quz) — única opción: voz MMS de Facebook convertida a ONNX.
    // Un solo locutor, género NEUTRAL, sin variantes de calidad/género. Modelo completo ~109 MB.
    // tokens.txt no tiene SHA-256 publicado (no es LFS): verificación blanda (se registra el hash).
    // ---------------------------------------------------------------------------------------
    private val QUECHUA = listOf(
        TtsModelInfo(
            id = "quz-mms",
            language = SupportedLanguage.QUECHUA,
            displayName = "Voz MMS · Quechua Cusco",
            gender = VoiceGender.NEUTRAL,
            quality = ModelQuality.HIGH,
            sizeBytes = 114_016_184L + 340L, // model.onnx + tokens.txt
            engine = TtsEngineType.MMS,
            source = ModelSource.Files(
                files = listOf(
                    RemoteFile(
                        url = "$HF_MMS/quz/model.onnx",
                        relativePath = "model.onnx",
                        sha256 = "441e30775d22a37b2f265e3be23fb928b393b8b39f8f3c9d07501bafa4a73d9c",
                    ),
                    RemoteFile(
                        url = "$HF_MMS/quz/tokens.txt",
                        relativePath = "tokens.txt",
                        sha256 = null, // sin hash publicado; verificación blanda
                    ),
                ),
            ),
            note = "facebook/mms-tts-quz (Cusco-Collao) convertido a ONNX por la comunidad. " +
                "Único modelo disponible; sin variantes de género ni calidad.",
        ),
    )

    private val ALL: List<TtsModelInfo> = SPANISH + ENGLISH + PORTUGUESE + QUECHUA

    /** Catálogo completo de un idioma (puede estar vacío si en el futuro algún idioma no tuviera modelos). */
    fun catalogFor(language: SupportedLanguage): List<TtsModelInfo> =
        ALL.filter { it.language == language }

    /** Todos los modelos del catálogo. */
    fun all(): List<TtsModelInfo> = ALL

    /** Busca un modelo por su [TtsModelInfo.id] (usado por el Worker, que recibe solo el id). */
    fun findById(id: String): TtsModelInfo? = ALL.firstOrNull { it.id == id }

    /** El tamaño en bytes del modelo más pesado de un idioma (para la barra comparativa de la UI). */
    fun maxSizeBytes(language: SupportedLanguage): Long =
        catalogFor(language).maxOfOrNull { it.sizeBytes } ?: 1L
}
