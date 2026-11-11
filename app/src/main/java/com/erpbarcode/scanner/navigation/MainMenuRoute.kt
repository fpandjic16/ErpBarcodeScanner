package com.erpbarcode.scanner.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.erpbarcode.scanner.ui.screen.MainMenuScreen

/**
 * The main menu: document types with their counts, the shift's counters, RECORDINGS, and the ways
 * on to everything else.
 *
 * Moved out of AppNavHost.kt as written. Inside `with(shell)` every name the routes use — who is
 * signed in, the settings, what is picked, the shell's actions — is a member of [Shell].
 */
internal fun NavGraphBuilder.mainMenuRoute(shell: Shell) {
    with(shell) {
        composable(Routes.MAIN) {
            MainMenuScreen(
                user = user,
                location = location,
                rc = rc,
                environment = extSystemConfig.environment,
                docTypes = docTypes,
                recordedDocs = recordedDocs,
                shiftScans = shiftScans,
                shiftErrors = errorDocs.size,
                shiftReady = readyDocs.size,
                shiftPartial = partialDocs.size,
                shiftOver = overDocs.size,
                onChangeLocationRc = { nav.navigate(Routes.LOCATION_RC_PICK) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenExtSystemConfig = { nav.navigate(Routes.EXT_SYSTEM_CONFIG) },
                onTypeTap = { type ->
                    selectedDocType = type
                    nav.navigate(Routes.DOCS)
                },
                onDocumentOverview = { nav.navigate(Routes.DASHBOARD_OVERVIEW) },
                onShowErrors = { nav.navigate(Routes.DASHBOARD_ERRORS) },
                onUserInfoTap = {
                    if (appVm.hasCredentials()) nav.navigate(Routes.USER_INFO)
                    else showMainLoginSheet = true
                },
                onOpenRecordings = { nav.navigate(Routes.RECORDINGS_LIST) },
            )
        }
    }
}
