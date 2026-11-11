package com.erpbarcode.scanner.navigation

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.erpbarcode.scanner.ui.screen.RecordingScreen
import com.erpbarcode.scanner.ui.screen.RecordingsListScreen
import com.erpbarcode.scanner.ui.screen.RecordingsTreeScreen
import com.erpbarcode.scanner.ui.screen.UploadErrorScreen
import com.erpbarcode.scanner.ui.viewmodel.RecordingViewModel
import com.erpbarcode.scanner.ui.viewmodel.RecordingsTreeViewModel

/**
 * Scanning a document, the list and tree of what has been scanned, and a document's upload errors.
 *
 * Moved out of AppNavHost.kt as written. Inside `with(shell)` every name the routes use — who is
 * signed in, the settings, what is picked, the shell's actions — is a member of [Shell].
 */
internal fun NavGraphBuilder.recordingRoutes(shell: Shell) {
    with(shell) {
        composable(Routes.RECORDINGS_LIST) {
            RecordingsListScreen(
                docs = recordedDocs,
                onBack = { nav.popBackStack() },
                onDocumentTap = { doc -> nav.navigate(Routes.recordings(doc.documentNo, doc.type.key)) },
            )
        }
        composable(
            route = Routes.RECORDING,
            arguments = listOf(
                navArgument("documentNo") { type = NavType.StringType },
                navArgument("type") { type = NavType.StringType },
            ),
        ) {
            val vm: RecordingViewModel = hiltViewModel()
            val doc by vm.document.collectAsState()
            doc?.let { currentDoc ->
                RecordingScreen(
                    doc = currentDoc,
                    docTypeCode = extSystemConfig.docTypeCodeFor(currentDoc.type),
                    onBack = { nav.popBackStack() },
                    onScan = { lineNo, barcode, quantity ->
                        vm.recordScan(lineNo, barcode, activeProfile.id, quantity)
                    },
                    onScanNotOnDocument = { barcode, uom, quantity ->
                        vm.recordScanNotOnDocument(barcode, uom, activeProfile.id, quantity)
                    },
                    onLineUpdate = { lineNo, newScanned, onRefused ->
                        vm.setLineScanned(lineNo, newScanned, activeProfile.id, onRefused)
                    },
                    onUpload = {
                        upload(
                            listOf(currentDoc),
                            onBackgroundStarted = { nav.popBackStack(Routes.MAIN, false) },
                            onBlockingDone = { failures ->
                                nav.popBackStack(Routes.MAIN, false)
                                if (failures > 0) showSyncErrorDialog = true
                            },
                        )
                    },
                        hapticEnabled = hapticEnabled,
                    soundEnabled = soundEnabled,
                    debounceTime = debounceTime,
                    warnOnOver = warnOnOver,
                )
            }
        }
        composable(
            route = Routes.RECORDINGS,
            arguments = listOf(
                navArgument("documentNo") { type = NavType.StringType },
                navArgument("type") { type = NavType.StringType },
            ),
        ) {
            val vm: RecordingsTreeViewModel = hiltViewModel()
            val treeState by vm.state.collectAsState()
            treeState.document?.let { currentDoc ->
                RecordingsTreeScreen(
                    document = currentDoc,
                    tree = treeState.tree,
                    onBack = { nav.popBackStack() },
                    onDeleteScan = { scan -> vm.deleteScan(scan) },
                    onDeleteAllQueued = { vm.deleteAllQueued() },
                )
            }
        }
        composable(
            route = Routes.UPLOAD_ERROR,
            arguments = listOf(navArgument("documentNo") { type = NavType.StringType }),
        ) { backStackEntry ->
            val docNo = backStackEntry.arguments?.getString("documentNo") ?: ""
            val doc = documents.find { it.documentNo == docNo }
            doc?.let { currentDoc ->
                UploadErrorScreen(
                    document = currentDoc,
                    onBack = { nav.popBackStack() },
                    onDiscardOrphans = {
                        appVm.discardOrphanedScans(currentDoc.documentNo, currentDoc.type) {
                            nav.popBackStack()
                        }
                    },
                    onDiscardFailed = {
                        appVm.discardFailedScans(currentDoc.documentNo, currentDoc.type) {
                            nav.popBackStack()
                        }
                    },
                    // Leaves this screen once the upload is handed over or has gone through; a
                    // failed blocking upload stays here, under the dialog that says so.
                    onRetryUpload = {
                        upload(
                            listOf(currentDoc),
                            onBackgroundStarted = { nav.popBackStack() },
                            onBlockingDone = { failures ->
                                if (failures > 0) { showSyncErrorDialog = true } else { nav.popBackStack() }
                            },
                        )
                    },
                )
            }
        }
    }
}
