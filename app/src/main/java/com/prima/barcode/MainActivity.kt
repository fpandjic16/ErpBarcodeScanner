package com.prima.barcode

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.prima.barcode.data.auth.AppSettings
import com.prima.barcode.data.barcode.DataWedgeManager
import com.prima.barcode.data.extsystem.ExtSystemResult
import com.prima.barcode.data.model.DocState
import com.prima.barcode.data.model.DocTypeFilterMode
import com.prima.barcode.data.model.DocumentFilter
import com.prima.barcode.data.model.DownloadFilter
import com.prima.barcode.data.model.DocumentType
import com.prima.barcode.data.model.LineStatus
import com.prima.barcode.data.model.scanStatus
import com.prima.barcode.data.model.User
import com.prima.barcode.ui.screen.AppInfoScreen
import com.prima.barcode.ui.screen.DocTypeSummary
import com.prima.barcode.ui.screen.ExtSystemConfigScreen
import com.prima.barcode.ui.screen.DocumentOverviewScreen
import com.prima.barcode.ui.screen.DocumentListScreen
import com.prima.barcode.ui.screen.UploadErrorScreen
import com.prima.barcode.ui.screen.DownloadFilterScreen
import com.prima.barcode.ui.screen.DocumentFilterScreen
import com.prima.barcode.ui.screen.LocationRcPickScreen
import com.prima.barcode.ui.screen.LoginSheet
import com.prima.barcode.ui.screen.MainMenuScreen
import com.prima.barcode.ui.screen.RecordingScreen
import com.prima.barcode.ui.screen.ProfilesScreen
import com.prima.barcode.ui.screen.RecordingsListScreen
import com.prima.barcode.ui.screen.RecordingsTreeScreen
import com.prima.barcode.ui.screen.SettingsScreen
import com.prima.barcode.ui.screen.SignInScreen
import com.prima.barcode.ui.screen.UserInfoScreen
import com.prima.barcode.ui.screen.localizedDisplay
import com.prima.barcode.ui.theme.Language
import com.prima.barcode.ui.theme.PrimaBarcodeTheme
import com.prima.barcode.ui.theme.TextSize
import com.prima.barcode.ui.viewmodel.AppViewModel
import com.prima.barcode.ui.viewmodel.RecordingViewModel
import com.prima.barcode.ui.viewmodel.RecordingsTreeViewModel
import com.prima.barcode.navigation.DebugUrlsDialog
import com.prima.barcode.navigation.Routes
import com.prima.barcode.navigation.DownloadErrorDialog
import com.prima.barcode.navigation.ProcessingDialog
import com.prima.barcode.navigation.SwitchBlockedDialog
import com.prima.barcode.navigation.SyncErrorDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideNavBar()
        DataWedgeManager.configure(this)
        setContent {
            // Showing the IME re-shows the nav bar on many devices/OS versions even though we
            // asked for it hidden — re-hide it whenever the keyboard becomes visible so it
            // doesn't linger onscreen for the whole time the user is typing. Reading this via
            // Compose's own WindowInsets (rather than a View-level OnApplyWindowInsetsListener
            // on the decor view) avoids interfering with how insets propagate to Compose's own
            // status-bar padding.
            val imeVisible = WindowInsets.isImeVisible
            LaunchedEffect(imeVisible) {
                if (imeVisible) hideNavBar()
            }

            val appVm: AppViewModel = hiltViewModel()
            val initialSettings = remember { appVm.loadSettings() }
            var textSize         by remember { mutableStateOf(initialSettings.textSize) }
            var uppercaseText    by remember { mutableStateOf(initialSettings.uppercaseText) }
            var language         by remember { mutableStateOf(initialSettings.language) }
            var debounceTime     by remember { mutableStateOf(initialSettings.debounceTime) }
            var hapticEnabled     by remember { mutableStateOf(initialSettings.hapticEnabled) }
            var soundEnabled      by remember { mutableStateOf(initialSettings.soundEnabled) }
            var warnOnOver       by remember { mutableStateOf(initialSettings.warnOnOver) }
            var backgroundSync   by remember { mutableStateOf(initialSettings.backgroundSync) }
            var disabledDocTypes by remember { mutableStateOf(initialSettings.disabledDocTypes) }
            var docTypeFilters    by remember { mutableStateOf(initialSettings.docTypeFilters) }
            var debuggerActive   by remember { mutableStateOf(initialSettings.debuggerActive) }
            var locationCode     by remember { mutableStateOf(initialSettings.lastLocationCode) }
            var rcCode           by remember { mutableStateOf(initialSettings.lastRcCode) }

            fun buildSettings() = AppSettings(
                textSize         = textSize,
                uppercaseText    = uppercaseText,
                language         = language,
                debounceTime     = debounceTime,
                hapticEnabled    = hapticEnabled,
                soundEnabled     = soundEnabled,
                warnOnOver          = warnOnOver,
                backgroundSync      = backgroundSync,
                lastLocationCode = locationCode,
                lastRcCode       = rcCode,
                disabledDocTypes = disabledDocTypes,
                docTypeFilters   = docTypeFilters,
                debuggerActive   = debuggerActive,
            )

            // Bulk-applies settings saved (and confirmed) from the Settings screen —
            // called once on exit-confirm, not per-field, since Settings now buffers
            // edits locally instead of auto-saving each change.
            fun applySettings(s: AppSettings) {
                if (language != s.language) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(s.language.tag))
                }
                textSize = s.textSize
                uppercaseText = s.uppercaseText
                language = s.language
                debounceTime = s.debounceTime
                hapticEnabled = s.hapticEnabled
                soundEnabled = s.soundEnabled
                warnOnOver = s.warnOnOver
                backgroundSync = s.backgroundSync
                debuggerActive = s.debuggerActive
                // A configuration loaded from Settings brings these two. Left out, the main menu
                // kept the old set of document types until the app was restarted.
                disabledDocTypes = s.disabledDocTypes
                docTypeFilters = s.docTypeFilters
                appVm.saveSettings(s)
            }

            // Most settings are personal, so they have to follow the operator.
            //
            // The initial read above happens before anyone has signed in and therefore returns
            // device defaults. This replaces them with the profile's own as soon as there is a
            // profile, and again on every change of operator — otherwise the second shift would
            // inherit the first one's language, text size and working location.
            //
            // Deliberately does not save: this is reading what is already stored, and writing it
            // back would let a half-applied state overwrite the real one.
            val signedInProfile by appVm.currentProfile.collectAsState()
            LaunchedEffect(signedInProfile?.id) {
                if (signedInProfile == null) return@LaunchedEffect
                val s = appVm.loadSettings()
                // Against the locale actually on screen, not against `language`. That starts from
                // the signed-out defaults, so an operator whose language happens to equal the
                // default — anyone who never chose one — would otherwise inherit whatever locale
                // the previous operator left in effect, while their Settings named another.
                if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != s.language.tag) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(s.language.tag))
                }
                textSize = s.textSize
                uppercaseText = s.uppercaseText
                language = s.language
                debounceTime = s.debounceTime
                hapticEnabled = s.hapticEnabled
                soundEnabled = s.soundEnabled
                warnOnOver = s.warnOnOver
                backgroundSync = s.backgroundSync
                debuggerActive = s.debuggerActive
                disabledDocTypes = s.disabledDocTypes
                docTypeFilters = s.docTypeFilters
                locationCode = s.lastLocationCode
                rcCode = s.lastRcCode
            }

            PrimaBarcodeTheme(textSizeOffset = textSize.spOffset, uppercaseEnabled = uppercaseText) {
                PrimaBarcodeApp(
                    locationCode              = locationCode,
                    rcCode                    = rcCode,
                    onLocationCodeChange      = { code -> locationCode = code; appVm.saveSettings(buildSettings().copy(lastLocationCode = code)) },
                    onRcCodeChange            = { code -> rcCode = code; appVm.saveSettings(buildSettings().copy(lastRcCode = code)) },
                    textSize                  = textSize,
                    uppercaseText             = uppercaseText,
                    language                  = language,
                    debounceTime              = debounceTime,
                    hapticEnabled             = hapticEnabled,
                    soundEnabled              = soundEnabled,
                    warnOnOver                = warnOnOver,
                    backgroundSync            = backgroundSync,
                    disabledDocTypes          = disabledDocTypes,
                    onDisabledDocTypesChange  = { disabledDocTypes = it; appVm.saveSettings(buildSettings().copy(disabledDocTypes = it)) },
                    docTypeFilters            = docTypeFilters,
                    onDocTypeFiltersChange    = { docTypeFilters = it; appVm.saveSettings(buildSettings().copy(docTypeFilters = it)) },
                    debuggerActive            = debuggerActive,
                    onDebuggerActiveChange    = { debuggerActive = it; appVm.saveSettings(buildSettings().copy(debuggerActive = it)) },
                    onSettingsSaved           = { s -> applySettings(s) },
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavBar()
    }

    private fun hideNavBar() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

private val exportTimestampFmt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    .withZone(ZoneId.systemDefault())

@Composable
private fun PrimaBarcodeApp(
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

    // No exemption for work already started. A document scanned into at one location used to
    // bypass this filter for good, so moving from CS165 to CS175 still showed CS165's documents —
    // the filter stopped meaning anything as soon as a shift got going.
    //
    // That escape existed because in-progress work was otherwise unreachable from another
    // location. The RECORDINGS section removed the reason: it lists every document carrying a
    // scan, deliberately unfiltered by location, and is where unsent work is found now.
    //
    // `DocumentOverviewScreen`'s own `atLocation` has always been strict, so this brings the rest
    // of the app in line with the dashboard rather than inventing a rule.
    //
    // A type scoped by responsibility centre needs one selected, the same as a location-scoped
    // type needs a location. No centre used to mean every centre, so an operator who had chosen
    // nothing saw — and counted — every complaint in the company.
    val filteredDocs = documents.filter { doc ->
        when (docTypeFilters[doc.type.key] ?: doc.type.defaultFilterMode) {
            DocTypeFilterMode.LOCATION -> location != null && doc.sourceCode == location.code
            DocTypeFilterMode.RESPONSIBILITY_CENTER -> rc != null && doc.rcCode == rc.code
        }
    }

    val docTypes = DocumentType.entries.map { type ->
        val filterMode = docTypeFilters[type.key] ?: type.defaultFilterMode
        // Blocked until the operator has selected what this type is scoped by — not merely until
        // the device has a list to select from. The old test was the list: once any operator had
        // refreshed locations, the next one could open every list with nothing selected. The
        // location-scoped ones then showed nothing, and their download went out with no
        // location at all and fetched every location's documents. `location` and `rc` are null
        // both when nothing is selected and when the selected code is no longer on the list, and
        // a list never fetched leaves nothing to select, so this one test covers all three.
        val blocked = when (filterMode) {
            DocTypeFilterMode.LOCATION -> location == null
            DocTypeFilterMode.RESPONSIBILITY_CENTER -> rc == null
        }
        DocTypeSummary(
            type = type,
            count = filteredDocs.count { it.type == type },
            statusMini = filteredDocs.filter { it.type == type }.mapNotNull { doc ->
                when {
                    doc.state is DocState.UploadFailed -> LineStatus.EMPTY
                    doc.state == DocState.Downloaded   -> null
                    else -> doc.scanStatus().takeIf { it != LineStatus.EMPTY }
                }
            },
            blocked = blocked,
        )
    }.filter { it.type.key !in disabledDocTypes }

    // The ZAPISI section: work in progress across every type, newest document first. Deliberately
    // not filtered by disabledDocTypes — a type switched off mid-job would otherwise take the
    // operator's only view of those scans with it.
    //
    // **It must stay built from `documents`.** Now that the location filter above admits no
    // exceptions, this is the only place in the app that shows an operator work they started
    // somewhere else. Narrowing it by location, by type, or by anything else would leave unsent
    // scans with nowhere to be seen — and unsent scans exist on this device and nowhere else.
    // Built from documents rather than filteredDocs, and on totalScans rather than hasProgress:
    // both of those narrowings key off a line's scanned quantity, and a scan whose line the ERP
    // has since removed adds to no line at all. A document whose scans were all orphaned would
    // drop out of the one list that is supposed to hold everything the operator has scanned.
    val recordedDocs = documents
        .filter { it.totalScans > 0 }
        .sortedByDescending { it.documentDate ?: it.creationDateTime }

    val shiftScans  = filteredDocs.sumOf { d -> d.lines.count { it.scanned > 0 } }
    val errorDocs   = filteredDocs.filter { it.state is DocState.UploadFailed }
    val readyDocs   = filteredDocs.filter { it.state !is DocState.UploadFailed && it.scanStatus() == LineStatus.EXACT }
    val partialDocs = filteredDocs.filter { it.state !is DocState.UploadFailed && it.scanStatus() == LineStatus.PARTIAL }
    val overDocs    = filteredDocs.filter { it.state !is DocState.UploadFailed && it.scanStatus() == LineStatus.OVER }
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
    // action and already handles testing itself) — verifies the NAV server actually accepts
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
        if (configuringAtSignIn) {
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
            onOpenConfig = { configuringAtSignIn = true },
            onOpenAppInfo = { appInfoAtSignIn = true },
            loginQrKey = extSystemConfig.loginQrKey,
            hapticEnabled = hapticEnabled,
            soundEnabled = soundEnabled,
            profiles = remember(signingIn) { appVm.profileStore.profiles() },
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

    // The nav controller is remembered above the sign-in gate, so its back stack outlives a change
    // of operator. Without this the next person lands wherever the last one left off — typically a
    // document route they have no copy of, which renders as a blank screen.
    LaunchedEffect(profile?.id) {
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
                signedInId = activeProfile.id,
                onBack = { nav.popBackStack() },
                loadFootprint = { id, onResult -> appVm.profileFootprint(id, onResult) },
                onDelete = { id -> appVm.deleteProfile(id) { profilesRefresh++ } },
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
                onSave  = { config ->
                    appVm.saveExtSystemConfig(config)
                    nav.popBackStack()
                },
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
                onSaveExtSystemConfig = { config -> appVm.saveExtSystemConfig(config) },
                loadExtSystemConfigDefaults = { fileName -> appVm.loadExtSystemDefaults(fileName) },
                listExtSystemDefaultsCompanies = { appVm.listExtSystemDefaultsCompanies() },
                parseExtSystemConfigJson = { json -> appVm.parseExtSystemConfigJson(json) },
                exportConfiguration = { appVm.exportConfigurationJson() },
                onExport = {
                    val ts = exportTimestampFmt.format(Instant.now())
                    exportLauncher.launch("prima_export_${ts}.json")
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
                doc.type == selectedDocType &&
                    when (docTypeFilters[selectedDocType.key] ?: selectedDocType.defaultFilterMode) {
                        DocTypeFilterMode.LOCATION -> location != null && doc.sourceCode == location.code
                        DocTypeFilterMode.RESPONSIBILITY_CENTER -> rc != null && doc.rcCode == rc.code
                    }
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
