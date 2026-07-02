package yupay.turismo.utils

object CurrencyUtils {
    /**
     * Convierte un monto de una moneda origen a una moneda destino usando los tipos de cambio proporcionados.
     * @param amount Monto a convertir
     * @param from Moneda de origen (S/, $, €)
     * @param to Moneda de destino (S/, $, €)
     * @param usdRate Tipo de cambio de S/ a $ (Ej: 3.8)
     * @param eurRate Tipo de cambio de S/ a € (Ej: 4.1)
     */
    fun convert(amount: Double, from: String, to: String, usdRate: Double, eurRate: Double): Double {
        if (from == to) return amount
        
        // Primero convertimos a Soles como moneda base
        val inSoles = when (from) {
            "S/" -> amount
            "$" -> amount * usdRate
            "€" -> amount * eurRate
            else -> amount
        }
        
        // Luego convertimos de Soles a la moneda destino
        return when (to) {
            "S/" -> inSoles
            "$" -> if (usdRate > 0) inSoles / usdRate else inSoles
            "€" -> if (eurRate > 0) inSoles / eurRate else inSoles
            else -> inSoles
        }
    }
}
