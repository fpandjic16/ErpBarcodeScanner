package com.prima.barcode.navigation

/**
 * Every route the app's NavHost knows, in one place. The patterns are what `composable(...)` is
 * registered under; the functions build a concrete route to navigate to. The strings themselves are
 * unchanged from when they were literals in MainActivity.
 */
internal object Routes {
    const val MAIN = "main"
    const val PROFILES = "profiles"
    const val RECORDINGS_LIST = "recordings_list"
    const val USER_INFO = "user_info"
    const val LOCATION_RC_PICK = "location_rc_pick"
    const val EXT_SYSTEM_CONFIG = "ext_system_config"
    const val SETTINGS = "settings"
    const val DOCS = "docs"
    const val FILTER = "filter"
    const val OVERVIEW_FILTER = "overview_filter"
    const val DOWNLOAD_FILTER = "download_filter"

    /** The dashboard, with an optional starting tab (`tab`, default 0 = errors). */
    const val DASHBOARD = "dashboard?tab={tab}"
    const val RECORDING = "recording/{documentNo}/{type}"
    const val RECORDINGS = "recordings/{documentNo}/{type}"
    const val UPLOAD_ERROR = "upload_error/{documentNo}"

    /** The dashboard on its first tab (errors). */
    const val DASHBOARD_ERRORS = "dashboard"
    /** The dashboard on its second tab. */
    const val DASHBOARD_OVERVIEW = "dashboard?tab=1"

    fun recording(documentNo: String, typeKey: String) = "recording/$documentNo/$typeKey"
    fun recordings(documentNo: String, typeKey: String) = "recordings/$documentNo/$typeKey"
    fun uploadError(documentNo: String) = "upload_error/$documentNo"
}
