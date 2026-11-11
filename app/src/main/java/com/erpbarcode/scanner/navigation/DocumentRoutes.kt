package com.erpbarcode.scanner.navigation

import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.data.extsystem.ExtSystemResult
import com.erpbarcode.scanner.data.model.DocTypeFilterMode
import com.erpbarcode.scanner.ui.screen.DocumentFilterScreen
import com.erpbarcode.scanner.ui.screen.DocumentListScreen
import com.erpbarcode.scanner.ui.screen.DownloadFilterScreen
import com.erpbarcode.scanner.ui.screen.localizedDisplay

/**
 * One document type: its list, the list's filter, and the download from the ERP.
 *
 * Moved out of AppNavHost.kt as written. Inside `with(shell)` every name the routes use — who is
 * signed in, the settings, what is picked, the shell's actions — is a member of [Shell].
 */
internal fun NavGraphBuilder.documentRoutes(shell: Shell) {
    with(shell) {
        composable(Routes.DOCS) {
            // Same rule as `filteredDocs`, for the same reason: nothing is exempt because it has
            // been scanned into. This is also the only mode-aware filter on this path, so
            // `DocumentListScreen` does not narrow by location again.
            val typeDocs = documents.filter { doc ->
                doc.type == selectedDocType && doc.isInScope(docTypeFilters, location, rc)
            }
            DocumentListScreen(
                hapticEnabled = hapticEnabled,
                soundEnabled = soundEnabled,
                docType = selectedDocType,
                locationCode = location?.code ?: "",
                docTypeCode = extSystemConfig.docTypeCodeFor(selectedDocType),
                documents = typeDocs,
                onBack = { nav.popBackStack() },
                onDocTap = { selected -> nav.navigate(Routes.recording(selected.documentNo, selected.type.key)) },
                onDownload = { nav.navigate(Routes.DOWNLOAD_FILTER) },
                onUpload = { docs ->
                    upload(
                        docs,
                        onBackgroundStarted = { nav.popBackStack(Routes.MAIN, false) },
                        onBlockingDone = { failures ->
                            nav.popBackStack(Routes.MAIN, false)
                            if (failures > 0) showSyncErrorDialog = true
                        },
                    )
                },
                onErrorTap = { doc -> nav.navigate(Routes.uploadError(doc.documentNo)) },
                onClearErrors = { appVm.clearErrorDocs() },
                filter = docFilter,
                onOpenFilter = { nav.navigate(Routes.FILTER) },
            )
        }
        composable(Routes.FILTER) {
            DocumentFilterScreen(
                initialFilter = docFilter,
                showDocTypeFilter = false,
                onApply = { newFilter ->
                    docFilter = newFilter
                    nav.popBackStack()
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.DOWNLOAD_FILTER) {
            val dlFilterMode = docTypeFilters[selectedDocType.key] ?: selectedDocType.defaultFilterMode
            // For the debugger's list of addresses; DocumentType.display is the English constant.
            val docTypeLabel = selectedDocType.localizedDisplay()
            // A second line behind the main menu, which already blocks the list in front of this
            // screen until a location or centre is selected. Kept for what it guards: with nothing
            // selected the request goes out with no location or centre in it and fetches the type
            // for every one of them — documents that then show on no list.
            val scopeSelected = when (dlFilterMode) {
                DocTypeFilterMode.LOCATION -> location != null
                DocTypeFilterMode.RESPONSIBILITY_CENTER -> rc != null
            }
            val scopeMissingText = stringResource(R.string.main_blocked_text)
            DownloadFilterScreen(
                hasCredentials  = appVm.hasCredentials(),
                loginQrKey      = extSystemConfig.loginQrKey,
                hapticEnabled   = hapticEnabled,
                soundEnabled    = soundEnabled,
                docType         = selectedDocType,
                fixedSourceCode = if (dlFilterMode == DocTypeFilterMode.LOCATION) locationCode else null,
                fixedRcCode     = if (dlFilterMode == DocTypeFilterMode.RESPONSIBILITY_CENTER) rcCode else null,
                locations       = locations,
                rcs             = rcs,
                onTestConnection = shell::testSignIn,
                onLoadInventoryBatches = { filter, username, password, onResult ->
                    if (username != null && password != null) appVm.saveCredentials(username, password)
                    appVm.loadInventoryBatches(filter) { result ->
                        when (result) {
                            is ExtSystemResult.Success -> onResult(result.data, null)
                            is ExtSystemResult.Failure -> onResult(null, result.message)
                        }
                    }
                },
                onConfirm = { filter, username, password ->
                    if (username != null && password != null) appVm.saveCredentials(username, password)
                    if (!scopeSelected) {
                        nav.popBackStack()
                        downloadErrorMessage = scopeMissingText
                        showDownloadErrorDialog = true
                    } else {
                        val urls = appVm.buildDownloadUrls(filter, selectedDocType).map { (_, url) -> "$docTypeLabel: $url" }
                        launchWithDebug(urls) {
                            processingMessage = R.string.processing_downloading
                            appVm.realDownloadDocuments(filter, docType = selectedDocType) { failures, errors ->
                                processingMessage = null
                                nav.popBackStack()
                                if (failures > 0) {
                                    downloadErrorMessage = errors.firstOrNull() ?: ""
                                    showDownloadErrorDialog = true
                                }
                            }
                        }
                    }
                },
                onCancel = { nav.popBackStack() },
            )
        }
    }
}
