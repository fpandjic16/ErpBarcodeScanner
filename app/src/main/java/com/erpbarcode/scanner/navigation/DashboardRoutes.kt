package com.erpbarcode.scanner.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.erpbarcode.scanner.ui.screen.DocumentFilterScreen
import com.erpbarcode.scanner.ui.screen.DocumentOverviewScreen

/**
 * The dashboard across every document type, and its filter.
 *
 * Moved out of AppNavHost.kt as written. Inside `with(shell)` every name the routes use — who is
 * signed in, the settings, what is picked, the shell's actions — is a member of [Shell].
 */
internal fun NavGraphBuilder.dashboardRoutes(shell: Shell) {
    with(shell) {
        composable(
            route = Routes.DASHBOARD,
            arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
        ) { backStackEntry ->
            val initialTab = backStackEntry.arguments?.getInt("tab") ?: 0
            DocumentOverviewScreen(
                locationCode = location?.code ?: "",
                // The resolved centre, like the location above: a code no longer on the list is
                // no selection at all, and "Moja lokacija" then has nothing to show.
                rcCode = rc?.code ?: "",
                documents = documents,
                onBack = { nav.popBackStack() },
                onDocTap = { selected -> nav.navigate(Routes.recording(selected.documentNo, selected.type.key)) },
                onClearErrors = { appVm.clearErrorDocs() },
                // Stays on the dashboard either way; only a failed blocking upload says so.
                onUpload = { docs ->
                    upload(
                        docs,
                        onBlockingDone = { failures ->
                            if (failures > 0) showSyncErrorDialog = true
                        },
                    )
                },
                onErrorTap = { doc -> nav.navigate(Routes.uploadError(doc.documentNo)) },
                filter = overviewFilter,
                onOpenFilter = { src, rc ->
                    overviewLockedSource = src
                    overviewLockedRc = rc
                    nav.navigate(Routes.OVERVIEW_FILTER)
                },
                docTypeFilters = docTypeFilters,
                initialTab = initialTab,
            )
        }
        composable(Routes.OVERVIEW_FILTER) {
            DocumentFilterScreen(
                initialFilter = overviewFilter,
                lockedSourceCode = overviewLockedSource,
                lockedRcCode = overviewLockedRc,
                onApply = { newFilter ->
                    overviewFilter = newFilter
                    nav.popBackStack()
                },
                onBack = { nav.popBackStack() },
            )
        }
    }
}
