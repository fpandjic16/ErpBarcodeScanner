package com.erpbarcode.scanner.navigation

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.data.auth.AppSettings
import com.erpbarcode.scanner.data.model.DocTypeFilterMode
import com.erpbarcode.scanner.data.model.User
import com.erpbarcode.scanner.ui.screen.LoginSheet
import com.erpbarcode.scanner.ui.theme.Language
import com.erpbarcode.scanner.ui.theme.TextSize
import com.erpbarcode.scanner.ui.viewmodel.AppViewModel

/*
 * The app shell: what is collected for every screen, the sign-in gate, the NavHost, and the dialogs
 * that can sit over any screen. MainActivity.kt keeps onCreate, the theme, the settings that follow
 * the operator, the hidden navigation bar and DataWedge.
 *
 * The routes themselves are in the files beside this one, one per group of screens, each an
 * extension of NavGraphBuilder that takes the Shell: MainMenuRoute, DocumentRoutes,
 * DashboardRoutes, RecordingRoutes, SettingsRoutes. What they share is in AppShell.kt (state and
 * actions) and ShellData.kt (what the lists show); the signed-out screens are in SignInGate.kt.
 */

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
        settingsRoutes(shell)
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
