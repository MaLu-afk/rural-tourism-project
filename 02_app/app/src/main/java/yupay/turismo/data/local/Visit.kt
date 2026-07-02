package yupay.turismo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "visits")
data class Visit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    // Identificador estable entre dispositivos (clave de deduplicación/merge en P2P). El `id` de Room
    // es local y se regenera en cada equipo; el `uuid` viaja con la visita y la identifica siempre.
    val uuid: String = java.util.UUID.randomUUID().toString(),
    // id de esta visita en el servidor. null = aún no subida a la nube.
    val remoteId: Long? = null,
    val deviceId: String = "",
    val nationality: String,
    val nationalityFlag: String,
    val selectedProducts: List<SelectedProduct> = emptyList(),
    val subtotal: Double = 0.0,
    val discountValue: Double = 0.0,
    val discountType: DiscountType = DiscountType.FIXED,
    val totalAmount: Double = 0.0,
    val currency: String = "S/",
    val registrationDate: Long = System.currentTimeMillis(),
    val isSent: Boolean = false,
    val sentDate: Long? = null
)
