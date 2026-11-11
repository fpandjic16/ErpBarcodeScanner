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
    var selectedDocType by remember { mutableStateOf(DocumentType.WAREHOUSE_SHIPMENT) }
    var docFilter by remember { mutableStateOf(DocumentFilter()) }
    var overviewFilter by remember { mutableStateOf(DocumentFilter()) }
    var overviewLockedSource by remember { mutableStateOf<String?>(null) }
    var overviewLockedRc     by remember { mutableStateOf<String?>(null) }
    var showSyncErrorDialog     by remember { mutableStateOf(false) }
    var showDownloadErrorDialog by remember { mutableStateOf(false) }
    var downloadErrorMessage    by remember { mutableStateOf("") }
    // A string resource, resolved where it is shown: the places that set it are callbacks, not
    // composable scopes, and a literal there is how "Downloading..." stayed English in every
    // language.
    var processingMessage by remember { mutableStateOf<Int?>(null) }

    var debugUrls     by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingCancel by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showDebugDialog by remember { mutableStateOf(false) }

    fun launchWithDebug(urls: List<String>, onCancel: () -> Unit = {}, action: () -> Unit) {
        if (debuggerActive && urls.isNotEmpty()) {
            debugUrls = urls; pendingAction = action; pendingCancel = onCancel; showDebugDialog = true
        } else {
            action()
        }
    }

    var showUploadLoginSheet by remember { mutableStateOf(false) }
    var pendingUploadAction  by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showMainLoginSheet   by remember { mutableStateOf(false) }

    fun requireCredentials(action: () -> Unit) {
        if (appVm.hasCredentials()) {
            action()
        } else {
            pendingUploadAction = action
            showUploadLoginSheet = true
        }
    }

    // Shared by every LoginSheet that gates access behind a real sign-in (as opposed to
    // ExtSystemConfigScreen's own LoginSheet usage, which *is* the explicit "Test connection"
    // action and already handles testing itself) — verifies the ERP server actually accepts
    // the credentials before the sheet treats sign-in as successful.
    fun testSignIn(username: String, password: String, onResult: (success: Boolean, error: String?) -> Unit) {
        appVm.testExtSystemConnection(extSystemConfig.serverBaseUrl, username, password) { result ->
            when (result) {
                is ExtSystemResult.Success -> onResult(true, null)
                is ExtSystemResult.Failure -> onResult(false, if (result.code > 0) "HTTP ${result.code}: ${result.message}" else result.message)
            }
        }
    }

    // Shared by the configuration screen's "Test connection" on both sides of the gate, so the
    // two do not drift apart.
    fun reportTestResult(result: ExtSystemResult<Unit>, cb: (Boolean, String?) -> Unit) {
        when (result) {
            is ExtSystemResult.Success -> cb(true, context.getString(R.string.ext_config_test_ok))
            is ExtSystemResult.Failure -> cb(
                false,
                if (result.code > 0) "HTTP ${result.code}: ${result.message}" else result.message,
            )
        }
    }

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
    var signingIn   by remember { mutableStateOf(false) }
    var signInError by remember { mutableStateOf<String?>(null) }
    var configuringAtSignIn by remember { mutableStateOf(false) }
    var appInfoAtSignIn by remember { mutableStateOf(false) }

    val activeProfile = profile
    if (activeProfile == null) {
        // The one door out of the gate, and it has to exist: signing in asks the server whether
        // the password is good, and a device out of the box has no server URL to ask. Without a
        // way to set one from here, a fresh install could never be configured and so could never
        // be signed into either.
        //
        // Not wrapped in launchWithDebug the way the signed-in route is — that dialog composes
        // below this return and so would never appear.
        //
        // **Only until a server address is saved** (3.6.52). The server is what decides whether a
        // password is good, so whoever can change it before signing in can point the device at a
        // server that accepts anything and sign in as any operator enrolled here, opening that
        // person's documents and unsent scans. Once configured, the configuration is changed after
        // signing in (Settings). A device whose saved address is wrong, or whose server has moved,
        // has to be reset (clear the app's data, or reinstall) — the price of closing that door.
        val firstSetup = extSystemConfig.serverBaseUrl.isBlank()
        if (configuringAtSignIn && firstSetup) {
            ExtSystemConfigScreen(
                hapticEnabled = hapticEnabled,
                soundEnabled = soundEnabled,
                initial = extSystemConfig,
                onSave = { config ->
                    appVm.saveExtSystemConfig(config)
                    configuringAtSignIn = false
                },
                onDiscard = { configuringAtSignIn = false },
                loadDefaults = { fileName -> appVm.loadExtSystemDefaults(fileName) },
                listCompanies = { appVm.listExtSystemDefaultsCompanies() },
                exportConfiguration = { appVm.exportConfigurationJson() },
                disabledDocTypes = disabledDocTypes,
                onDisabledDocTypesChange = onDisabledDocTypesChange,
                docTypeFilters = docTypeFilters,
                onDocTypeFiltersChange = onDocTypeFiltersChange,
                onDebuggerActiveChange = onDebuggerActiveChange,
                // savedCredentials left null on purpose: nobody is signed in, so there is nothing
                // to prefill and nowhere a test's credentials would be stored.
                onTestConnection = { serverUrl, username, password, cb ->
                    appVm.testExtSystemConnection(serverUrl, username, password) { reportTestResult(it, cb) }
                },
                onImportJson = { json -> appVm.parseExtSystemConfigJson(json) },
            )
            return
        }
        // The second door out of the gate: read-only, and nothing in it is personal or secret.
        if (appInfoAtSignIn) {
            AppInfoScreen(
                environment = extSystemConfig.environment,
                companyName = extSystemConfig.companyName,
                onBack = { appInfoAtSignIn = false },
            )
            return
        }
        SignInScreen(
            onOpenConfig = if (firstSetup) ({ configuringAtSignIn = true }) else null,
            onOpenAppInfo = { appInfoAtSignIn = true },
            loginQrKey = extSystemConfig.loginQrKey,
            hapticEnabled = hapticEnabled,
            soundEnabled = soundEnabled,
            // Those enrolled on the installation the device is pointed at: a name known only on
            // another server or company would be offered here and then find none of its work.
            profiles = remember(signingIn, extSystemConfig.serverIdentity) {
                appVm.profileStore.profilesOn(extSystemConfig.serverIdentity)
            },
            busy = signingIn,
            error = signInError,
            onErrorDismiss = { signInError = null },
            onSignIn = { username, password ->
                signingIn = true
                appVm.signIn(username, password) { result ->
                    signingIn = false
                    when (result) {
                        is AppViewModel.SignInResult.Online -> Unit
                        is AppViewModel.SignInResult.Failed -> signInError = result.message
                    }
                }
            },
        )
        return
    }

    // Changing operator swaps the database underneath everything, and a transfer that is still
    // running would then be writing into the wrong person's file — an upload recording "this row
    // reached the ERP", a download replacing documents someone else is holding. So the switch
    // waits rather than the transfer being cancelled. Covers the background upload, which outlives
    // the screen that started it, and every download: those resolve the database only once they
    // have an answer from the server, by which point sign-out may already have taken it away.
    val erpWorkInFlight by appVm.erpWorkInFlight.collectAsState()
    var showSwitchBlocked by remember { mutableStateOf(false) }

    fun requestSignOut() {
        if (erpWorkInFlight) showSwitchBlocked = true else appVm.signOut()
    }

    if (showSwitchBlocked) {
        SwitchBlockedDialog(onDismiss = { showSwitchBlocked = false })
    }

    // A configuration that names another ERP installation (server or company) ends the session:
    // the open database, the saved sign-in and the locations all belong to the one signed in to.
    // That is asked about first, because it signs the operator out and because scans not yet sent
    // stay behind with the installation they were made for. The rule itself is in
    // AppViewModel.saveExtSystemConfig; this is only the asking.
    var pendingServerChange by remember { mutableStateOf<ExtSystemConfig?>(null) }

    /** Saves [config] if that can be done here and now; false when it had to ask, or refuse, first. */
    fun requestConfigSave(config: ExtSystemConfig): Boolean {
        if (config.serverIdentity == extSystemConfig.serverIdentity) {
            appVm.saveExtSystemConfig(config)
            return true
        }
        if (erpWorkInFlight) showSwitchBlocked = true else pendingServerChange = config
        return false
    }

    pendingServerChange?.let { config ->
        ServerChangeDialog(
            // Anything scanned and not yet accepted by the ERP, on any document of this operator.
            hasUnsentScans = documents.any { doc ->
                doc.orphanedScans.isNotEmpty() || doc.lines.any { it.scanned > it.sentQuantity }
            },
            onConfirm = {
                pendingServerChange = null
                when (appVm.saveExtSystemConfig(config)) {
                    AppViewModel.ConfigSave.RefusedTransferRunning -> showSwitchBlocked = true
                    AppViewModel.ConfigSave.SavedAndSignedOut ->
                        Toast.makeText(context, context.getString(R.string.server_changed_toast), Toast.LENGTH_LONG).show()
                    AppViewModel.ConfigSave.Saved -> Unit
                }
            },
            onDismiss = { pendingServerChange = null },
        )
    }

    // The nav controller is remembered above the sign-in gate, so its back stack outlives a change
    // of operator. Without this the next person lands wherever the last one left off — typically a
    // document route they have no copy of, which renders as a blank screen.
    LaunchedEffect(profile?.key) {
        nav.popBackStack(Routes.MAIN, inclusive = false)
    }

    NavHost(navController = nav, startDestination = Routes.MAIN) {
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
        composable(Routes.RECORDINGS_LIST) {
            RecordingsListScreen(
                docs = recordedDocs,
                onBack = { nav.popBackStack() },
                onDocumentTap = { doc -> nav.navigate(Routes.recordings(doc.documentNo, doc.type.key)) },
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
                onTestConnection = ::testSignIn,
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
                    requireCredentials {
                        if (backgroundSync) {
                            launchWithDebug(listOf(appVm.getRecordingSyncUrl())) {
                                appVm.uploadInBackground(docs)
                                nav.popBackStack(Routes.MAIN, false)
                            }
                        } else {
                            val cb: (Int) -> Unit = { failures ->
                                processingMessage = null
                                nav.popBackStack(Routes.MAIN, false)
                                if (failures > 0) showSyncErrorDialog = true
                            }
                            launchWithDebug(listOf(appVm.getRecordingSyncUrl())) {
                                processingMessage = R.string.processing_uploading
                                appVm.uploadToExtSystem(docs, cb)
                            }
                        }
                    }
                },
                onErrorTap = { doc -> nav.navigate(Routes.uploadError(doc.documentNo)) },
                onClearErrors = { appVm.clearErrorDocs() },
                filter = docFilter,
                onOpenFilter = { nav.navigate(Routes.FILTER) },
            )
        }
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
                onUpload = { docs ->
                    requireCredentials {
                        if (backgroundSync) {
                            launchWithDebug(listOf(appVm.getRecordingSyncUrl())) {
                                appVm.uploadInBackground(docs)
                            }
                        } else {
                            val cb: (Int) -> Unit = { failures ->
                                processingMessage = null
                                if (failures > 0) showSyncErrorDialog = true
                            }
                            launchWithDebug(listOf(appVm.getRecordingSyncUrl())) {
                                processingMessage = R.string.processing_uploading
                                appVm.uploadToExtSystem(docs, cb)
                            }
                        }
                    }
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
                onTestConnection = ::testSignIn,
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
                        requireCredentials {
                            if (backgroundSync) {
                                launchWithDebug(listOf(appVm.getRecordingSyncUrl())) {
                                    appVm.uploadInBackground(listOf(currentDoc))
                                    nav.popBackStack(Routes.MAIN, false)
                                }
                            } else {
                                val cb: (Int) -> Unit = { failures ->
                                    processingMessage = null
                                    nav.popBackStack(Routes.MAIN, false)
                                    if (failures > 0) showSyncErrorDialog = true
                                }
                                launchWithDebug(listOf(appVm.getRecordingSyncUrl())) {
                                    processingMessage = R.string.processing_uploading
                                    appVm.uploadToExtSystem(listOf(currentDoc), cb)
                                }
                            }
                        }
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
                    onRetryUpload = {
                        requireCredentials {
                            if (backgroundSync) {
                                launchWithDebug(listOf(appVm.getRecordingSyncUrl())) {
                                    appVm.uploadInBackground(listOf(currentDoc))
                                    nav.popBackStack()
                                }
                            } else {
                                val cb: (Int) -> Unit = { failures ->
                                    processingMessage = null
                                    if (failures > 0) { showSyncErrorDialog = true } else { nav.popBackStack() }
                                }
                                launchWithDebug(listOf(appVm.getRecordingSyncUrl())) {
                                    processingMessage = R.string.processing_uploading
                                    appVm.uploadToExtSystem(listOf(currentDoc), cb)
                                }
                            }
                        }
                    },
                )
            }
        }
    }

    processingMessage?.let { message ->
        ProcessingDialog(message)
    }

    if (showDownloadErrorDialog) {
        DownloadErrorDialog(downloadErrorMessage, onDismiss = { showDownloadErrorDialog = false })
    }

    if (showDebugDialog) {
        DebugUrlsDialog(
            urls = debugUrls,
            onProceed = { showDebugDialog = false; pendingAction?.invoke() },
            onCancel = { showDebugDialog = false; pendingCancel?.invoke() },
        )
    }

    if (showUploadLoginSheet) {
        LoginSheet(
            credentialTtlHours = extSystemConfig.credentialTtlHours,
            ctaLabel           = stringResource(R.string.btn_sign_in),
            initialUsername    = appVm.savedCredentials()?.username ?: "",
            initialPassword    = appVm.savedCredentials()?.password ?: "",
            loginQrKey         = extSystemConfig.loginQrKey,
            hapticEnabled      = hapticEnabled,
            soundEnabled       = soundEnabled,
            onDismiss          = { showUploadLoginSheet = false; pendingUploadAction = null },
            onTestConnection   = ::testSignIn,
            onSubmit           = { _, _ ->
                showUploadLoginSheet = false
                pendingUploadAction?.invoke()
                pendingUploadAction = null
            },
        )
    }

    if (showMainLoginSheet) {
        LoginSheet(
            credentialTtlHours = extSystemConfig.credentialTtlHours,
            ctaLabel           = stringResource(R.string.btn_sign_in),
            loginQrKey         = extSystemConfig.loginQrKey,
            hapticEnabled      = hapticEnabled,
            soundEnabled       = soundEnabled,
            onDismiss          = { showMainLoginSheet = false },
            onTestConnection   = ::testSignIn,
            onSubmit           = { _, _ -> showMainLoginSheet = false },
        )
    }

    if (showSyncErrorDialog) {
        SyncErrorDialog(
            onSeeErrors = { showSyncErrorDialog = false; nav.navigate(Routes.DASHBOARD_ERRORS) },
            onDismiss = { showSyncErrorDialog = false },
        )
    }
}
