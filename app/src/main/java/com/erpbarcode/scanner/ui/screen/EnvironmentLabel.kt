package com.erpbarcode.scanner.ui.screen

import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.data.auth.ExtSystemEnvironment

/**
 * The environment's name in the operator's language, as a string resource id.
 *
 * A UI-layer extension rather than a property on the enum, for the same reason
 * `DocumentType.localizedDisplay()` is one: `data/auth` has no business holding Android resource
 * ids. Its own file because three screens need it — the two that load configurations, and the
 * main menu that warns about one of them.
 */
internal val ExtSystemEnvironment.labelRes: Int
    get() = when (this) {
        ExtSystemEnvironment.TEST -> R.string.ext_config_environment_test
        ExtSystemEnvironment.PRODUCTION -> R.string.ext_config_environment_production
    }
