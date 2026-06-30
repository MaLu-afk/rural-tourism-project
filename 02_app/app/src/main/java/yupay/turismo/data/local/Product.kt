package yupay.turismo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    // Identificador estable entre dispositivos (clave de merge en P2P). El `id` de Room es local y se
    // regenera en cada sincronización; el `uuid` viaja con el producto y lo identifica siempre.
    val uuid: String = java.util.UUID.randomUUID().toString(),
    // id de este producto en el servidor (BIGSERIAL). null = aún no subido a la nube.
    val remoteId: Long? = null,
    val name: String,
    val basePrice: Double,
    val currency: String = "S/",
    val category: String, // Hospedaje, Alimentación, Artesanía, Varios
    val isDefault: Boolean = false,
    val discountValue: Double = 0.0,
    val discountType: DiscountType = DiscountType.PERCENTAGE,
    val discountStartDate: Long? = null,
    val discountEndDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
) {
    fun getActivePrice(): Double {
        val now = System.currentTimeMillis()
        val isDiscountActive = discountValue > 0 && 
            (discountStartDate == null || now >= discountStartDate) && 
            (discountEndDate == null || now <= discountEndDate)
        
        if (!isDiscountActive) return basePrice
        
        return if (discountType == DiscountType.FIXED) {
            (basePrice - discountValue).coerceAtLeast(0.0)
        } else {
            (basePrice * (1 - discountValue / 100.0)).coerceAtLeast(0.0)
        }
    }
}

@Serializable
data class SelectedProduct(
    val id: Int,
    val name: String,
    val originalPrice: Double,
    val priceAtSale: Double,
    val quantity: Int,
    val hasDiscount: Boolean = false,
    val currency: String = "S/"
)

enum class DiscountType {
    FIXED, PERCENTAGE
}
