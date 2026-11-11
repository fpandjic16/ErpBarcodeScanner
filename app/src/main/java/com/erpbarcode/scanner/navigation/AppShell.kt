package com.erpbarcode.scanner.navigation

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.data.auth.AppSettings
import com.erpbarcode.scanner.data.auth.ExtSystemConfig
import com.erpbarcode.scanner.data.auth.UserProfile
import com.erpbarcode.scanner.data.extsystem.ExtSystemResult
import com.erpbarcode.scanner.data.model.DocTypeFilterMode
import com.erpbarcode.scanner.data.model.Document
import com.erpbarcode.scanner.data.model.DocumentFilter
import com.erpbarcode.scanner.data.model.DocumentType
import com.erpbarcode.scanner.data.model.Location
import com.erpbarcode.scanner.data.model.ResponsibilityCenter
import com.erpbarcode.scanner.data.model.User
import com.erpbarcode.scanner.ui.screen.DocTypeSummary
import com.erpbarcode.scanner.ui.theme.Language
import com.erpbarcode.scanner.ui.theme.TextSize
import com.erpbarcode.scanner.ui.viewmodel.AppViewModel

/**
 * The app shell's own state: what the operator has picked, and which of the shell's dialogs is up.
 *
 * Remembered once in `BarcodeApp`, above the sign-in gate, so it lives as long as the app does.
 * Until 3.6.57 these were some twenty separate `remember`ed variables inside that function, which is
 * what kept every route in one file: a route could only reach them from inside it.
 */
internal class AppShellState {
    // ── What is picked ────────────────────────────────────────────────────────
    var selectedDocType by mutableStateOf(DocumentType.WAREHOUSE_SHIPMENT)
    var docFilter by mutableStateOf(DocumentFilter())
    var overviewFilter by mutableStateOf(DocumentFilter())
    var overviewLockedSource by mutableStateOf<String?>(null)
    var overviewLockedRc by mutableStateOf<String?>(null)

    // ── Dialogs ───────────────────────────────────────────────────────────────
    var showSyncErrorDialog by mutableStateOf(false)
    var showDownloadErrorDialog by mutableStateOf(false)
    var downloadErrorMessage by mutableStateOf("")

    // A string resource, resolved where it is shown: the places that set it are callbacks, not
    // composable scopes, and a literal there is how "Downloading..." stayed English in every
    // language.
    var processingMessage by mutableStateOf<Int?>(null)

    // The debugger: the addresses a request is about to call, and what to do once it is let go.
    var debugUrls by mutableStateOf<List<String>>(emptyList())
    var pendingAction by mutableStateOf<(() -> Unit)?>(null)
    var pendingCancel by mutableStateOf<(() -> Unit)?>(null)
    var showDebugDialog by mutableStateOf(false)

    // Sign-in asked for in the middle of something: the something waits in pendingUploadAction.
    var showUploadLoginSheet by mutableStateOf(false)
    var pendingUploadAction by mutableStateOf<(() -> Unit)?>(null)
    var showMainLoginSheet by mutableStateOf(false)

    var showSwitchBlocked by mutableStateOf(false)
    var pendingServerChange by mutableStateOf<ExtSystemConfig?>(null)
}

/**
 * Everything a route needs from the shell, as it stands in one composition of `BarcodeApp`.
 *
 * Built anew each time `BarcodeApp` recomposes, exactly as the local values it replaces were: a
 * route sees the values of the composition that built it, and the NavHost's graph is rebuilt with
 * the new ones. Only [state] outlives a composition.
 *
 * The property names are the ones the routes used when they were all inside `BarcodeApp`, so a
 * route moved into its own file under `with(shell) { … }` reads as it did there.
 */
internal class Shell(
    val state: AppShellState,
    val nav: NavHostController,
    val appVm: AppViewModel,
    val context: Context,
    val exportLauncher: ActivityResultLauncher<String>,

    // ── Who is signed in, and where they are working ──────────────────────────
    val activeProfile: UserProfile,
    val user: User?,
    val location: Location?,
    val rc: ResponsibilityCenter?,
    val locations: List<Location>,
    val rcs: List<ResponsibilityCenter>,
    val documents: List<Document>,
    val extSystemConfig: ExtSystemConfig,
    val data: ShellData,
    val erpWorkInFlight: Boolean,

    // ── The settings in effect, and the ways to change them ───────────────────
    val locationCode: String,
    val rcCode: String,
    val onLocationCodeChange: (String) -> Unit,
    val onRcCodeChange: (String) -> Unit,
    val textSize: TextSize,
    val uppercaseText: Boolean,
    val language: Language,
    val debounceTime: Int,
    val hapticEnabled: Boolean,
    val soundEnabled: Boolean,
    val warnOnOver: Boolean,
    val backgroundSync: Boolean,
    val disabledDocTypes: Set<String>,
    val onDisabledDocTypesChange: (Set<String>) -> Unit,
    val docTypeFilters: Map<String, DocTypeFilterMode>,
    val onDocTypeFiltersChange: (Map<String, DocTypeFilterMode>) -> Unit,
    val debuggerActive: Boolean,
    val onDebuggerActiveChange: (Boolean) -> Unit,
    val onSettingsSaved: (AppSettings) -> Unit,
) {
    // ── What the menu and the lists show (see ShellData.kt) ───────────────────
    val docTypes: List<DocTypeSummary> get() = data.docTypes
    val recordedDocs: List<Document> get() = data.recordedDocs
    val shiftScans: Int get() = data.shiftScans
    val errorDocs: List<Document> get() = data.errorDocs
    val readyDocs: List<Document> get() = data.readyDocs
    val partialDocs: List<Document> get() = data.partialDocs
    val overDocs: List<Document> get() = data.overDocs

    // ── The parts of the state that routes read and set by name ───────────────
    var selectedDocType: DocumentType
        get() = state.selectedDocType
        set(value) { state.selectedDocType = value }
    var docFilter: DocumentFilter
        get() = state.docFilter
        set(value) { state.docFilter = value }
    var overviewFilter: DocumentFilter
        get() = state.overviewFilter
        set(value) { state.overviewFilter = value }
    var overviewLockedSource: String?
        get() = state.overviewLockedSource
        set(value) { state.overviewLockedSource = value }
    var overviewLockedRc: String?
        get() = state.overviewLockedRc
        set(value) { state.overviewLockedRc = value }
    var showSyncErrorDialog: Boolean
        get() = state.showSyncErrorDialog
        set(value) { state.showSyncErrorDialog = value }
    var showDownloadErrorDialog: Boolean
        get() = state.showDownloadErrorDialog
        set(value) { state.showDownloadErrorDialog = value }
    var downloadErrorMessage: String
        get() = state.downloadErrorMessage
        set(value) { state.downloadErrorMessage = value }
    var processingMessage: Int?
        get() = state.processingMessage
        set(value) { state.processingMessage = value }
    var showMainLoginSheet: Boolean
        get() = state.showMainLoginSheet
        set(value) { state.showMainLoginSheet = value }

    // ── What routes ask the shell to do ───────────────────────────────────────

    /** Runs [action] straight away, or, with the debugger on, after showing [urls] and being let go. */
    fun launchWithDebug(urls: List<String>, onCancel: () -> Unit = {}, action: () -> Unit) {
        if (debuggerActive && urls.isNotEmpty()) {
            state.debugUrls = urls
            state.pendingAction = action
            state.pendingCancel = onCancel
            state.showDebugDialog = true
        } else {
            action()
        }
    }

    /** Runs [action] now if the ERP session is still valid, otherwise once the operator has signed in again. */
    fun requireCredentials(action: () -> Unit) {
        if (appVm.hasCredentials()) {
            action()
        } else {
            state.pendingUploadAction = action
            state.showUploadLoginSheet = true
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

    fun reportTestResult(result: ExtSystemResult<Unit>, cb: (Boolean, String?) -> Unit) =
        reportConnectionTest(context, result, cb)

    // Changing operator swaps the database underneath everything, and a transfer that is still
    // running would then be writing into the wrong person's file — an upload recording "this row
    // reached the ERP", a download replacing documents someone else is holding. So the switch
    // waits rather than the transfer being cancelled. Covers the background upload, which outlives
    // the screen that started it, and every download: those resolve the database only once they
    // have an answer from the server, by which point sign-out may already have taken it away.
    fun requestSignOut() {
        if (erpWorkInFlight) state.showSwitchBlocked = true else appVm.signOut()
    }

    /**
     * Saves [config] if that can be done here and now; false when it had to ask, or refuse, first.
     *
     * A configuration that names another ERP installation (server or company) ends the session:
     * the open database, the saved sign-in and the locations all belong to the one signed in to.
     * That is asked about first, because it signs the operator out and because scans not yet sent
     * stay behind with the installation they were made for. The rule itself is in
     * AppViewModel.saveExtSystemConfig; this is only the asking.
     */
    fun requestConfigSave(config: ExtSystemConfig): Boolean {
        if (config.serverIdentity == extSystemConfig.serverIdentity) {
            appVm.saveExtSystemConfig(config)
            return true
        }
        if (erpWorkInFlight) state.showSwitchBlocked = true else state.pendingServerChange = config
        return false
    }

    /**
     * Sends [docs] to the ERP: the one upload path, whichever screen asked for it.
     *
     * Asks for a sign-in first if the session has lapsed, shows the address with the debugger on,
     * then uploads in the background or behind the "Uploading…" dialog, as the operator's settings
     * say. What differs between screens is only where to go afterwards, so that is what they pass:
     * [onBackgroundStarted] once a background upload has been handed over, [onBlockingDone] with
     * the number of documents that failed once a blocking one has finished.
     */
    fun upload(
        docs: List<Document>,
        onBackgroundStarted: () -> Unit = {},
        onBlockingDone: (failures: Int) -> Unit = {},
    ) {
        requireCredentials {
            launchWithDebug(listOf(appVm.getRecordingSyncUrl())) {
                if (backgroundSync) {
                    appVm.uploadInBackground(docs)
                    onBackgroundStarted()
                } else {
                    state.processingMessage = R.string.processing_uploading
                    appVm.uploadToExtSystem(docs) { failures ->
                        state.processingMessage = null
                        onBlockingDone(failures)
                    }
                }
            }
        }
    }
}

/**
 * Turns the outcome of a connection test into what the configuration screen shows. Shared by that
 * screen on both sides of the sign-in gate, so the two do not drift apart.
 */
internal fun reportConnectionTest(context: Context, result: ExtSystemResult<Unit>, cb: (Boolean, String?) -> Unit) {
    when (result) {
        is ExtSystemResult.Success -> cb(true, context.getString(R.string.ext_config_test_ok))
        is ExtSystemResult.Failure -> cb(
            false,
            if (result.code > 0) "HTTP ${result.code}: ${result.message}" else result.message,
        )
    }
}
