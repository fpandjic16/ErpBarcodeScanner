package com.erpbarcode.scanner.navigation

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.data.auth.AppSettings
import com.erpbarcode.scanner.data.auth.ExtSystemConfig
import com.erpbarcode.scanner.data.barcode.DataWedgeManager
import com.erpbarcode.scanner.data.extsystem.ExtSystemResult
import com.erpbarcode.scanner.data.model.DocState
import com.erpbarcode.scanner.data.model.DocTypeFilterMode
import com.erpbarcode.scanner.data.model.DocumentFilter
import com.erpbarcode.scanner.data.model.DocumentType
import com.erpbarcode.scanner.data.model.DownloadFilter
import com.erpbarcode.scanner.data.model.LineStatus
import com.erpbarcode.scanner.data.model.User
import com.erpbarcode.scanner.data.model.scanStatus
import com.erpbarcode.scanner.ui.screen.AppInfoScreen
import com.erpbarcode.scanner.ui.screen.DocTypeSummary
import com.erpbarcode.scanner.ui.screen.DocumentFilterScreen
import com.erpbarcode.scanner.ui.screen.DocumentListScreen
import com.erpbarcode.scanner.ui.screen.DocumentOverviewScreen
import com.erpbarcode.scanner.ui.screen.DownloadFilterScreen
import com.erpbarcode.scanner.ui.screen.ExtSystemConfigScreen
import com.erpbarcode.scanner.ui.screen.LocationRcPickScreen
import com.erpbarcode.scanner.ui.screen.LoginSheet
import com.erpbarcode.scanner.ui.screen.MainMenuScreen
import com.erpbarcode.scanner.ui.screen.ProfilesScreen
import com.erpbarcode.scanner.ui.screen.RecordingScreen
import com.erpbarcode.scanner.ui.screen.RecordingsListScreen
import com.erpbarcode.scanner.ui.screen.RecordingsTreeScreen
import com.erpbarcode.scanner.ui.screen.SettingsScreen
import com.erpbarcode.scanner.ui.screen.SignInScreen
import com.erpbarcode.scanner.ui.screen.UploadErrorScreen
import com.erpbarcode.scanner.ui.screen.UserInfoScreen
import com.erpbarcode.scanner.ui.screen.localizedDisplay
import com.erpbarcode.scanner.ui.theme.Language
import com.erpbarcode.scanner.ui.theme.AppTheme
import com.erpbarcode.scanner.ui.theme.TextSize
import com.erpbarcode.scanner.ui.viewmodel.AppViewModel
import com.erpbarcode.scanner.ui.viewmodel.RecordingViewModel
import com.erpbarcode.scanner.ui.viewmodel.RecordingsTreeViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * The app shell: the sign-in gate, the NavHost with every route, and the dialogs that can sit over
 * any screen. Moved out of MainActivity.kt, which keeps onCreate, the theme, the settings that
 * follow the operator, the hidden navigation bar and DataWedge.
 */

private val exportTimestampFmt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    .withZone(ZoneId.systemDefault())

@Composable
internal fun BarcodeApp(
    locationCode: String,
    rcCode: String,
    onLocationCodeChange: (String) -> Unit,
    onRcCodeChange: (String) -> Unit,
    textSize: TextSize,
    uppercaseText: Boolean,
    language: Language,
    debounceTime: Int,
    hapticEnabled: Boolean,
    soundEnabled: Boolean,
    warnOnOver: Boolean,
    backgroundSync: Boolean,
    disabledDocTypes: Set<String>,
    onDisabledDocTypesChange: (Set<String>) -> Unit,
    docTypeFilters: Map<String, DocTypeFilterMode>,
    onDocTypeFiltersChange: (Map<String, DocTypeFilterMode>) -> Unit,
    debuggerActive: Boolean,
    onDebuggerActiveChange: (Boolean) -> Unit,
    onSettingsSaved: (AppSettings) -> Unit,
) {
    val nav = rememberNavController()
    val appVm: AppViewModel = hiltViewModel()
    val context = LocalContext.current

    val credentials by appVm.credentials.collectAsState()
    val profile by appVm.currentProfile.collectAsState()

    // Identity comes from the profile, not from the credentials.
    //
    // Deriving it from credentials tied who you are to a secret that self-destructs on a timer:
    // when the TTL lapsed the user became null, and under per-operator storage that would have
    // meant an operator losing sight of their own unsent scans. The profile outlives the
    // credential, which is the whole point of keeping them apart.
    val user: User? = profile?.let {
        User(
            id          = it.id,
            username    = credentials?.username ?: it.displayName,
            displayName = it.displayName,
            initials    = it.displayName.take(2).uppercase(),
        )
    }

    val locations by appVm.locations.collectAsState()
    val rcs by appVm.responsibilityCenters.collectAsState()

    val rc = if (rcCode.isBlank()) null else rcs.find { it.code == rcCode }
    val location = if (locationCode.isNotEmpty()) locations.find { it.code == locationCode } else null

    // Auto-select first RC only when a non-blank code no longer matches any available RC (stale code recovery)
    LaunchedEffect(rcs, rcCode) {
        val match = rcs.find { it.code == rcCode }
        if (rcCode.isNotBlank() && match == null && rcs.isNotEmpty()) onRcCodeChange(rcs.first().code)
    }
    LaunchedEffect(rc, locations, locationCode) {
        if (rc != null) {
            val locMatch = locations.find { it.code == locationCode && it.rc == rc.code }
            if (locMatch == null) {
                locations.find { it.rc == rc.code }?.let { onLocationCodeChange(it.code) }
            }
        }
    }

    val documents by appVm.documents.collectAsState()
    val extSystemConfig by appVm.extSystemConfig.collectAsState()

    // What the menu and the lists show. The rules — the strict location scope, the blocked types,
    // the unfiltered RECORDINGS list — are in ShellData.kt, where they are tested on their own.
    val data = shellData(documents, location, rc, docTypeFilters, disabledDocTypes)
    val docTypes     = data.docTypes
    val recordedDocs = data.recordedDocs
    val shiftScans   = data.shiftScans
    val errorDocs    = data.errorDocs
    val readyDocs    = data.readyDocs
    val partialDocs  = data.partialDocs
    val overDocs     = data.overDocs
    // What is picked and which of the shell's dialogs is up; see AppShellState.
    val state = remember { AppShellState() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            appVm.exportDatabase(it) {
                Toast.makeText(context, context.getString(R.string.export_saved), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── The gate ──────────────────────────────────────────────────────────────
    //
    // Nothing below composes until somebody is signed in, and that is not only about privacy:
    // every recording stamps its author at the moment it is written, so the app has to know who
    // is scanning before the first scan. There is no filling it in afterwards.
    //
    // An early return rather than a branch around the NavHost, so the signed-out state cannot
    // reach a screen by any route — including a back stack left over from the previous operator.
    val activeProfile = profile
    if (activeProfile == null) {
        SignInGate(
            appVm = appVm,
            extSystemConfig = extSystemConfig,
            hapticEnabled = hapticEnabled,
            soundEnabled = soundEnabled,
            disabledDocTypes = disabledDocTypes,
            onDisabledDocTypesChange = onDisabledDocTypesChange,
            docTypeFilters = docTypeFilters,
            onDocTypeFiltersChange = onDocTypeFiltersChange,
            onDebuggerActiveChange = onDebuggerActiveChange,
        )
        return
    }

    val erpWorkInFlight by appVm.erpWorkInFlight.collectAsState()

    // Everything the routes need, as it stands in this composition; see Shell.
    val shell = Shell(
        state = state,
        nav = nav,
        appVm = appVm,
        context = context,
        exportLauncher = exportLauncher,
        activeProfile = activeProfile,
        user = user,
        location = location,
        rc = rc,
        locations = locations,
        rcs = rcs,
        documents = documents,
        extSystemConfig = extSystemConfig,
        data = data,
        erpWorkInFlight = erpWorkInFlight,
        locationCode = locationCode,
        rcCode = rcCode,
        onLocationCodeChange = onLocationCodeChange,
        onRcCodeChange = onRcCodeChange,
        textSize = textSize,
        uppercaseText = uppercaseText,
        language = language,
        debounceTime = debounceTime,
        hapticEnabled = hapticEnabled,
        soundEnabled = soundEnabled,
        warnOnOver = warnOnOver,
        backgroundSync = backgroundSync,
        disabledDocTypes = disabledDocTypes,
        onDisabledDocTypesChange = onDisabledDocTypesChange,
        docTypeFilters = docTypeFilters,
        onDocTypeFiltersChange = onDocTypeFiltersChange,
        debuggerActive = debuggerActive,
        onDebuggerActiveChange = onDebuggerActiveChange,
        onSettingsSaved = onSettingsSaved,
    )

    if (state.showSwitchBlocked) {
        SwitchBlockedDialog(onDismiss = { state.showSwitchBlocked = false })
    }

    // Asked by Shell.requestConfigSave when a configuration being saved names another installation.
    state.pendingServerChange?.let { config ->
        ServerChangeDialog(
            // Anything scanned and not yet accepted by the ERP, on any document of this operator.
            hasUnsentScans = documents.any { doc ->
                doc.orphanedScans.isNotEmpty() || doc.lines.any { it.scanned > it.sentQuantity }
            },
            onConfirm = {
                state.pendingServerChange = null
                when (appVm.saveExtSystemConfig(config)) {
                    AppViewModel.ConfigSave.RefusedTransferRunning -> state.showSwitchBlocked = true
                    AppViewModel.ConfigSave.SavedAndSignedOut ->
                        Toast.makeText(context, context.getString(R.string.server_changed_toast), Toast.LENGTH_LONG).show()
                    AppViewModel.ConfigSave.Saved -> Unit
                }
            },
            onDismiss = { state.pendingServerChange = null },
        )
    }

    // The nav controller is remembered above the sign-in gate, so its back stack outlives a change
    // of operator. Without this the next person lands wherever the last one left off — typically a
    // document route they have no copy of, which renders as a blank screen.
    LaunchedEffect(profile?.key) {
        nav.popBackStack(Routes.MAIN, inclusive = false)
    }

    NavHost(navController = nav, startDestination = Routes.MAIN) {
        mainMenuRoute(shell)
        documentRoutes(shell)
        dashboardRoutes(shell)
        recordingRoutes(shell)
        with(shell) {
        composable(Routes.PROFILES) {
            // Re-read after a deletion rather than observed: profiles change only here, and a
            // Flow over the encrypted profile store would be machinery for one screen.
            var profilesRefresh by remember { mutableStateOf(0) }
            ProfilesScreen(
                profiles = remember(profilesRefresh) { appVm.profileStore.profiles() },
                signedInKey = activeProfile.key,
                onBack = { nav.popBackStack() },
                loadFootprint = { key, onResult -> appVm.profileFootprint(key, onResult) },
                onDelete = { key -> appVm.deleteProfile(key) { profilesRefresh++ } },
            )
        }
        composable(Routes.USER_INFO) {
            UserInfoScreen(
                user = user,
                location = location,
                rc = rc,
                onBack = { nav.popBackStack() },
                onSignOut = { requestSignOut() },
            )
        }
        composable(Routes.LOCATION_RC_PICK) {
            val isRefreshing by appVm.isRefreshingLocations.collectAsState()
            val lastSyncedAt by appVm.lastLocationSyncAt.collectAsState()
            LocationRcPickScreen(
                currentRcCode = rcCode,
                currentLocationCode = locationCode,
                availableRcs = rcs,
                availableLocations = locations,
                isRefreshing = isRefreshing,
                lastSyncedAt = lastSyncedAt,
                hasCredentials = appVm.hasCredentials(),
                credentialTtlHours = extSystemConfig.credentialTtlHours,
                loginQrKey = extSystemConfig.loginQrKey,
                hapticEnabled = hapticEnabled,
                soundEnabled = soundEnabled,
                onSelect = { rc, loc ->
                    onRcCodeChange(rc)
                    onLocationCodeChange(loc)
                    nav.popBackStack()
                },
                onRefresh = {
                    launchWithDebug(
                        listOf(appVm.getLocationsUrl()).filter { it.isNotBlank() }
                    ) {
                        appVm.downloadLocations { error ->
                            if (error != null) {
                                downloadErrorMessage = error
                                showDownloadErrorDialog = true
                            }
                        }
                    }
                },
                onSaveCredentials = { u, p -> appVm.saveCredentials(u, p) },
                onTestConnection = shell::testSignIn,
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.EXT_SYSTEM_CONFIG) {
            ExtSystemConfigScreen(
                hapticEnabled = hapticEnabled,
                soundEnabled = soundEnabled,
                initial = extSystemConfig,
                // Stays on the screen, with the form as it is, while a change of installation is
                // being asked about; confirming it signs out, which leaves this screen anyway.
                onSave  = { config -> if (requestConfigSave(config)) nav.popBackStack() },
                onDiscard = { nav.popBackStack() },
                loadDefaults = { fileName -> appVm.loadExtSystemDefaults(fileName) },
                listCompanies = { appVm.listExtSystemDefaultsCompanies() },
                exportConfiguration = { appVm.exportConfigurationJson() },
                disabledDocTypes = disabledDocTypes,
                onDisabledDocTypesChange = onDisabledDocTypesChange,
                docTypeFilters = docTypeFilters,
                onDocTypeFiltersChange = onDocTypeFiltersChange,
                onDebuggerActiveChange = onDebuggerActiveChange,
                savedCredentials = appVm.savedCredentials(),
                onTestConnection = { serverUrl, username, password, cb ->
                    launchWithDebug(
                        listOf(serverUrl.trim()),
                        onCancel = { cb(false, null) },
                    ) {
                        appVm.testExtSystemConnection(serverUrl, username, password) { reportTestResult(it, cb) }
                    }
                },
                onImportJson = { json -> appVm.parseExtSystemConfigJson(json) },
            )
        }
        composable(Routes.SETTINGS) {
            val initialSettingsSnapshot = AppSettings(
                textSize = textSize,
                uppercaseText = uppercaseText,
                language = language,
                debounceTime = debounceTime,
                hapticEnabled = hapticEnabled,
                soundEnabled = soundEnabled,
                warnOnOver = warnOnOver,
                backgroundSync = backgroundSync,
                lastLocationCode = locationCode,
                lastRcCode = rcCode,
                disabledDocTypes = disabledDocTypes,
                docTypeFilters = docTypeFilters,
                debuggerActive = debuggerActive,
            )
            SettingsScreen(
                user = user,
                location = location,
                rc = rc,
                initial = initialSettingsSnapshot,
                onSave = { s -> onSettingsSaved(s); nav.popBackStack() },
                onDiscard = { nav.popBackStack() },
                onSaveExtSystemConfig = { config -> requestConfigSave(config) },
                loadExtSystemConfigDefaults = { fileName -> appVm.loadExtSystemDefaults(fileName) },
                listExtSystemDefaultsCompanies = { appVm.listExtSystemDefaultsCompanies() },
                parseExtSystemConfigJson = { json -> appVm.parseExtSystemConfigJson(json) },
                exportConfiguration = { appVm.exportConfigurationJson() },
                onExport = {
                    val ts = exportTimestampFmt.format(Instant.now())
                    exportLauncher.launch("barcode_export_${ts}.json")
                },
                onClearCache = { appVm.clearCache() },
                onDeleteAllDocuments = { appVm.deleteAllDocuments() },
                onChangeLocation = { nav.navigate(Routes.LOCATION_RC_PICK) },
                onOpenExtSystemConfig = { nav.navigate(Routes.EXT_SYSTEM_CONFIG) },
                onOpenProfiles = { nav.navigate(Routes.PROFILES) },
                onSignOut = { requestSignOut() },
                onSignInTap = { requireCredentials {} },
                environment = extSystemConfig.environment,
                companyName = extSystemConfig.companyName,
            )
        }
        } // with(shell)
    }

    state.processingMessage?.let { message ->
        ProcessingDialog(message)
    }

    if (state.showDownloadErrorDialog) {
        DownloadErrorDialog(state.downloadErrorMessage, onDismiss = { state.showDownloadErrorDialog = false })
    }

    if (state.showDebugDialog) {
        DebugUrlsDialog(
            urls = state.debugUrls,
            onProceed = { state.showDebugDialog = false; state.pendingAction?.invoke() },
            onCancel = { state.showDebugDialog = false; state.pendingCancel?.invoke() },
        )
    }

    if (state.showUploadLoginSheet) {
        LoginSheet(
            credentialTtlHours = extSystemConfig.credentialTtlHours,
            ctaLabel           = stringResource(R.string.btn_sign_in),
            initialUsername    = appVm.savedCredentials()?.username ?: "",
            initialPassword    = appVm.savedCredentials()?.password ?: "",
            loginQrKey         = extSystemConfig.loginQrKey,
            hapticEnabled      = hapticEnabled,
            soundEnabled       = soundEnabled,
            onDismiss          = { state.showUploadLoginSheet = false; state.pendingUploadAction = null },
            onTestConnection   = shell::testSignIn,
            onSubmit           = { _, _ ->
                state.showUploadLoginSheet = false
                state.pendingUploadAction?.invoke()
                state.pendingUploadAction = null
            },
        )
    }

    if (state.showMainLoginSheet) {
        LoginSheet(
            credentialTtlHours = extSystemConfig.credentialTtlHours,
            ctaLabel           = stringResource(R.string.btn_sign_in),
            loginQrKey         = extSystemConfig.loginQrKey,
            hapticEnabled      = hapticEnabled,
            soundEnabled       = soundEnabled,
            onDismiss          = { state.showMainLoginSheet = false },
            onTestConnection   = shell::testSignIn,
            onSubmit           = { _, _ -> state.showMainLoginSheet = false },
        )
    }

    if (state.showSyncErrorDialog) {
        SyncErrorDialog(
            onSeeErrors = { state.showSyncErrorDialog = false; nav.navigate(Routes.DASHBOARD_ERRORS) },
            onDismiss = { state.showSyncErrorDialog = false },
        )
    }
}
