package yupay.turismo.data.remote

/** Configuración del cliente de la API en la nube (Yupay Turismo API en Render). */
object ApiConfig {
    /**
     * URL base de la API desplegada. Sin barra final.
     * El plan free de Render se suspende por inactividad: la PRIMERA petición tras un
     * periodo inactivo puede tardar varios segundos (cold start). Los timeouts del cliente
     * están dimensionados para tolerarlo (ver [HttpModule]).
     */
    const val BASE_URL = "https://yupay-turismo-api.onrender.com"
}
