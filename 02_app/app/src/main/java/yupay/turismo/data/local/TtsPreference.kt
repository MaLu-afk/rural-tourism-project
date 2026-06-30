package yupay.turismo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Modelo de voz activo por idioma. Solo puede haber UNA fila por idioma (la clave primaria es el
 * código de idioma: "es", "en", "pt", "quz"), así que activar un modelo reemplaza al anterior.
 *
 * [activeModelId] referencia [yupay.turismo.tts.TtsModelInfo.id]. Si una fila existe, ese modelo es
 * el activo para ese idioma; si no hay fila, no hay voz configurada para el idioma.
 */
@Entity(tableName = "tts_preferences")
data class TtsPreference(
    @PrimaryKey val language: String,
    val activeModelId: String,
)
