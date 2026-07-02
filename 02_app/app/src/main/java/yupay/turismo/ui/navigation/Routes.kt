package yupay.turismo.ui.navigation

/**
 * Defines all navigation routes used in the app as constants to avoid hardcoding strings.
 */
object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val PROFILE_SETUP = "profile_setup"
    const val MAIN_PAGER = "main_pager"
    
    // Bottom Bar / Pager Routes
    const val HOME = "home"
    const val VISITS = "visits"
    const val MAP = "map"
    const val PROFILE = "profile"
    
    // Auth Routes
    const val REGISTER = "register"
    const val LOGIN = "login"
    const val ACCOUNT_INFO = "account_info"
    const val FORGOT_PASSWORD = "forgot_password"
    // source = "reset" (desde olvidé contraseña) | "account" (desde info de cuenta, logueado)
    const val RESET_PASSWORD = "reset_password?source={source}"
    const val VERIFY_OTP = "verify_otp?email={email}&flow={flow}"
    
    // Secondary / Detail Routes
    const val VISIT_DETAIL = "visit_detail/{visitId}"
    const val FULLSCREEN_MAP = "fullscreen_map"
    const val PROFILE_EDIT = "profile_edit"
    const val SHOW_QR = "show_qr"
    const val SCAN_QR = "scan_qr"
    const val LINKED_DEVICES = "linked_devices"
    const val SYNC_STATUS = "sync_status?role={role}&deviceName={deviceName}&ip={ip}&port={port}&sessionId={sessionId}"
    const val PROFILE_LANGUAGE = "profile_language"
    const val PROFILE_VOICE_MODELS = "profile_voice_models"
    const val PROFILE_HELP = "profile_help"
    const val PROFILE_PRIVACY = "profile_privacy"
    const val ADD_VISIT = "add_visit"
    const val TIP_DETAIL = "tip_detail"
    const val DASHBOARD = "dashboard"
    const val PRODUCT_CATALOG = "product_catalog"
    const val PRODUCT_CATALOG_SETUP = "product_catalog_setup"
    const val PRODUCT_EDITOR = "product_editor?productId={productId}"
    const val CURRENCY_SELECTION = "currency_selection"

    /**
     * Helper to create verify OTP route.
     */
    fun verifyOtp(email: String, flow: String) = "verify_otp?email=$email&flow=$flow"

    /**
     * Helper to create reset password route. source: "reset" (olvidé) o "account" (logueado).
     */
    fun resetPassword(source: String = "reset") = "reset_password?source=$source"

    /**
     * Helper to create product editor route with ID.
     */
    fun productEditor(id: Int?) = if (id != null) "product_editor?productId=$id" else "product_editor"

    /**
     * Helper to create visit detail route with ID.
     */
    fun visitDetail(id: Int) = "visit_detail/$id"

    /**
     * Helper to create sync status route with arguments.
     */
    fun syncStatus(role: String, deviceName: String, ip: String, port: Int, sessionId: String): String {
        return "sync_status?role=$role&deviceName=$deviceName&ip=$ip&port=$port&sessionId=$sessionId"
    }
}
