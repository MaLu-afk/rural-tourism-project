package yupay.turismo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val deviceId: String = "",
    val hardwareDeviceId: String = "",
    val language: String = "Español",
    val businessName: String = "",
    val businessCategory: String = "",
    val isOnboardingCompleted: Boolean = false,
    val voiceSpeed: Float = 1.0f,
    val entrepreneurTips: Map<String, String> = emptyMap(),
    val mapSummary: Map<String, String> = emptyMap(),
    val entrepreneurTipsAudio: String = "",
    val mapSummaryAudio: String = "",
    val profilePicture: ByteArray? = null,
    val usdExchangeRate: Double = 3.8,
    val eurExchangeRate: Double = 4.1,
    val preferredCurrency: String = "S/",
    val isLinked: Boolean = false,
    val accountEmail: String = "",
    // DEPRECADO: no debe persistir la contraseña en claro. El flujo de auth con la API
    // guarda los tokens en SessionManager (DataStore), no aquí. Se conserva el campo solo
    // por compatibilidad de UI; a eliminar en la fase de integración de UI.
    val accountPassword: String = "",
    val lastModified: Long = 0L,
    // Marca de agua (epoch ms) del último GET /sync/pull aplicado, para sync incremental.
    val lastSyncAt: Long = 0L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AppSettings

        if (id != other.id) return false
        if (deviceId != other.deviceId) return false
        if (hardwareDeviceId != other.hardwareDeviceId) return false
        if (language != other.language) return false
        if (businessName != other.businessName) return false
        if (businessCategory != other.businessCategory) return false
        if (isOnboardingCompleted != other.isOnboardingCompleted) return false
        if (voiceSpeed != other.voiceSpeed) return false
        if (entrepreneurTips != other.entrepreneurTips) return false
        if (mapSummary != other.mapSummary) return false
        if (entrepreneurTipsAudio != other.entrepreneurTipsAudio) return false
        if (mapSummaryAudio != other.mapSummaryAudio) return false
        if (isLinked != other.isLinked) return false
        if (accountEmail != other.accountEmail) return false
        if (accountPassword != other.accountPassword) return false
        if (lastModified != other.lastModified) return false
        if (lastSyncAt != other.lastSyncAt) return false
        if (profilePicture != null) {
            if (other.profilePicture == null) return false
            if (!profilePicture.contentEquals(other.profilePicture)) return false
        } else if (other.profilePicture != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + deviceId.hashCode()
        result = 31 * result + hardwareDeviceId.hashCode()
        result = 31 * result + language.hashCode()
        result = 31 * result + businessName.hashCode()
        result = 31 * result + businessCategory.hashCode()
        result = 31 * result + isOnboardingCompleted.hashCode()
        result = 31 * result + voiceSpeed.hashCode()
        result = 31 * result + entrepreneurTips.hashCode()
        result = 31 * result + mapSummary.hashCode()
        result = 31 * result + entrepreneurTipsAudio.hashCode()
        result = 31 * result + mapSummaryAudio.hashCode()
        result = 31 * result + isLinked.hashCode()
        result = 31 * result + accountEmail.hashCode()
        result = 31 * result + accountPassword.hashCode()
        result = 31 * result + lastModified.hashCode()
        result = 31 * result + lastSyncAt.hashCode()
        result = 31 * result + (profilePicture?.contentHashCode() ?: 0)
        return result
    }
}
