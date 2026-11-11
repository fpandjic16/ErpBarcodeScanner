package com.erpbarcode.scanner.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.data.auth.AppSettings
import com.erpbarcode.scanner.data.auth.AppSettingsStore
import com.erpbarcode.scanner.data.auth.DOC_TYPES_BEFORE_COMPLAINT_SPLIT
import com.erpbarcode.scanner.data.auth.ExtSystemConfig
import com.erpbarcode.scanner.data.auth.ExtSystemConfigStore
import com.erpbarcode.scanner.data.auth.ExtSystemCredentialStore
import com.erpbarcode.scanner.data.auth.ExtSystemCredentials
import com.erpbarcode.scanner.data.auth.DeviceConfiguration
import com.erpbarcode.scanner.data.auth.ExtSystemDefaultsCompany
import com.erpbarcode.scanner.data.auth.ExtSystemEnvironment
import com.erpbarcode.scanner.data.auth.RecordingUploadMode
import com.erpbarcode.scanner.data.auth.UserProfile
import com.erpbarcode.scanner.data.auth.UserProfileStore
import com.erpbarcode.scanner.data.db.DatabaseProvider
import com.erpbarcode.scanner.data.db.SharedDatabaseProvider
import com.erpbarcode.scanner.data.db.LocationEntity
import com.erpbarcode.scanner.data.db.ResponsibilityCenterEntity
import com.erpbarcode.scanner.data.db.toDomain
import com.erpbarcode.scanner.data.export.DatabaseExporter
import com.erpbarcode.scanner.data.extsystem.ExtSystemODataClient
import com.erpbarcode.scanner.data.extsystem.ExtSystemResult
import com.erpbarcode.scanner.data.model.DocState
import com.erpbarcode.scanner.data.model.DocTypeFilterMode
import com.erpbarcode.scanner.data.model.Document
import com.erpbarcode.scanner.data.model.DocumentType
import com.erpbarcode.scanner.data.model.DownloadFilter
import com.erpbarcode.scanner.data.model.InventoryBatch
import com.erpbarcode.scanner.data.model.Item
import com.erpbarcode.scanner.data.model.Line
import com.erpbarcode.scanner.data.model.Location
import com.erpbarcode.scanner.data.model.ResponsibilityCenter
import com.erpbarcode.scanner.data.repository.DocumentRepository
import com.erpbarcode.scanner.i18n.inAppLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.erpbarcode.scanner.data.extsystem.ErpDocumentLineRow
import com.erpbarcode.scanner.data.extsystem.ErpInventoryBatchRow
import com.erpbarcode.scanner.data.extsystem.odataLiteral
import com.erpbarcode.scanner.data.extsystem.ErpLocationRow
import com.erpbarcode.scanner.data.extsystem.ODataList
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import timber.log.Timber

@HiltViewModel
class AppViewModel @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val appContext: Context,
    private val repository: DocumentRepository,
    private val sharedDatabaseProvider: SharedDatabaseProvider,
    private val exporter: DatabaseExporter,
    val extSystemConfigStore: ExtSystemConfigStore,
    private val extSystemCredentialStore: ExtSystemCredentialStore,
    private val extSystemClient: ExtSystemODataClient,
    private val appSettingsStore: AppSettingsStore,
    val profileStore: UserProfileStore,
    private val databaseProvider: DatabaseProvider,
    private val configController: ConfigController,
    private val uploadCoordinator: UploadCoordinator,
    private val downloadCoordinator: DownloadCoordinator,
) : ViewModel() {

    /**
     * A message for the operator, in the app's language rather than the device's — see
     * [inAppLanguage]. Everything this ViewModel puts in front of a person goes through here: it
     * has only the application context, which below Android 13 does not follow the app's language.
     */
    private fun inAppText(id: Int, vararg args: Any): String =
        appContext.inAppLanguage().getString(id, *args)

    init {
        // Any PendingUpload still on disk was left by an upload whose scope no longer exists —
        // this ViewModel is only recreated once the previous one is gone, so nothing it started
        // can still be running. Clearing it here is what gives a stranded document a way back.
        //
        // Runs on every profile whose database opens, not once at construction: each operator has
        // their own file, so each has their own strandings to recover, and the one signing in an
        // hour from now would otherwise never be looked at.
        viewModelScope.launch {
            databaseProvider.database.filterNotNull().collect { repository.recoverStalePendingUploads() }
        }
    }

    private val _currentProfile = MutableStateFlow(profileStore.current())
    /** Who is signed in. Null only before sign-in — every screen but the sign-in one is behind it. */
    val currentProfile: StateFlow<UserProfile?> = _currentProfile

    private val _credentials = MutableStateFlow(extSystemCredentialStore.get(profileStore.currentKey()))
    val credentials: StateFlow<ExtSystemCredentials?> = _credentials

    /**
     * How many ERP operations are running right now, in either direction.
     *
     * Guards the change of operator. `DocState.PendingUpload` cannot do that job on its own: it
     * survives process death, which is the whole reason `recoverStalePendingUploads` exists, so a
     * stale flag would block switching forever. This one is in memory and therefore honest about
     * *this* process.
     *
     * A count, not a flag: a background upload and a download can overlap, and whichever finished
     * first would lower a shared boolean while the other was still writing.
     */
    private val _erpWorkCount = MutableStateFlow(0)

    /** True while anything at all is talking to the ERP. See [duringErpWork]. */
    val erpWorkInFlight: StateFlow<Boolean> =
        _erpWorkCount.map { it > 0 }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Runs [block] with the change of operator held off.
     *
     * Downloads need this every bit as much as uploads do, and are the easier half to forget:
     * `replaceDownloadedDocuments` resolves `provider.current()`, which throws once sign-out has
     * released the database — and it throws inside a coroutine, so it takes the process with it.
     */
    private suspend fun <T> duringErpWork(block: suspend () -> T): T {
        _erpWorkCount.update { it + 1 }
        return try {
            block()
        } finally {
            // finally, not after: a cancelled scope would otherwise leave the count raised and
            // lock the operator out of changing profile for the life of the process.
            _erpWorkCount.update { it - 1 }
        }
    }

    // Declared above the two flows below because they follow it, and properties initialise in
    // the order they are written.
    private val _extSystemConfig = MutableStateFlow(extSystemConfigStore.get())
    val extSystemConfig: StateFlow<ExtSystemConfig> = _extSystemConfig

    /**
     * The reference data of the ERP installation the device is pointed at, re-read whenever the
     * configuration names another one. Locations belong to a server and a company, so each has
     * its own list (see [SharedDatabaseProvider]): another company's locations are never offered,
     * and coming back to a company finds its list where it was.
     */
    private val installationData = _extSystemConfig
        .map { it.serverIdentity }
        .distinctUntilChanged()
        .map { sharedDatabaseProvider.forServer(it) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val locations: StateFlow<List<Location>> = installationData
        .flatMapLatest { it.locationDao().observeLocations() }
        .map { it.map { e -> e.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val responsibilityCenters: StateFlow<List<ResponsibilityCenter>> = installationData
        .flatMapLatest { it.locationDao().observeRcs() }
        .map { it.map { e -> e.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())


    private val _isRefreshingLocations = MutableStateFlow(false)
    val isRefreshingLocations: StateFlow<Boolean> get() = _isRefreshingLocations

    private val _lastLocationSyncAt = MutableStateFlow<Instant?>(null)
    val lastLocationSyncAt: StateFlow<Instant?> get() = _lastLocationSyncAt

    fun downloadLocations(onComplete: (error: String?) -> Unit = {}) {
        viewModelScope.launch {
            _isRefreshingLocations.value = true
            val error = duringErpWork { realDownloadLocations() }
            if (error == null) _lastLocationSyncAt.value = Instant.now()
            _isRefreshingLocations.value = false
            onComplete(error)
        }
    }

    /** See [DownloadCoordinator.downloadLocations]. */
    private suspend fun realDownloadLocations(): String? =
        downloadCoordinator.downloadLocations(extSystemConfig.value, savedCredentials())

    /** See [DownloadCoordinator.loadInventoryBatches]. */
    fun loadInventoryBatches(filter: DownloadFilter, onResult: (ExtSystemResult<List<InventoryBatch>>) -> Unit) {
        viewModelScope.launch {
            onResult(duringErpWork {
                downloadCoordinator.loadInventoryBatches(filter, extSystemConfig.value, savedCredentials())
            })
        }
    }

    fun buildDownloadUrls(filter: DownloadFilter, docType: DocumentType? = null): List<Pair<String, String>> =
        downloadCoordinator.buildDownloadUrls(filter, docType, extSystemConfig.value)

    fun getLocationsUrl(): String = extSystemConfig.value.locationsUrl
    /** The address an upload goes to under the configured upload mode; the debugger shows it. */
    fun getRecordingSyncUrl(): String = extSystemConfig.value.let {
        if (it.uploadMode == RecordingUploadMode.PER_DOCUMENT) it.documentUploadUrl else it.recordingSyncUrl
    }

    /** See [DownloadCoordinator.downloadDocuments]. */
    fun realDownloadDocuments(
        filter: DownloadFilter = DownloadFilter(),
        docType: DocumentType? = null,
        onComplete: (failureCount: Int, errors: List<String>) -> Unit = { _, _ -> },
    ) {
        viewModelScope.launch {
            val (failures, errorMessages) = duringErpWork {
                downloadCoordinator.downloadDocuments(filter, docType, extSystemConfig.value, savedCredentials())
            }
            onComplete(failures, errorMessages)
        }
    }

    fun loadSettings(): AppSettings = appSettingsStore.get()

    fun saveSettings(settings: AppSettings) = appSettingsStore.save(settings)

    val documents: StateFlow<List<Document>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** What [saveExtSystemConfig] did. */
    enum class ConfigSave { Saved, SavedAndSignedOut, RefusedTransferRunning }

    /**
     * Saves the configuration, and ends the session when it names another ERP installation.
     *
     * **A session never outlives its installation.** The open database, the saved sign-in and the
     * list of locations all belong to the server and company the operator signed in to
     * (`ExtSystemConfig.serverIdentity`). Carrying the session on under new addresses is how one
     * company's unsent scans would be uploaded to another, and how its documents would be merged
     * with the other's on the next download. So a change of installation signs the operator out;
     * they sign in again, against the new server, into the data kept for it.
     *
     * Refused while a transfer is running, for the reason signing out is: the transfer is still
     * writing through the database this would release. The screens ask before calling this with
     * a changed installation (see `BarcodeApp`); this is the rule itself, kept here so no path
     * can save around it.
     */
    fun saveExtSystemConfig(config: ExtSystemConfig): ConfigSave {
        val endsSession = profileStore.current() != null &&
            config.serverIdentity != extSystemConfig.value.serverIdentity
        if (endsSession && _erpWorkCount.value > 0) return ConfigSave.RefusedTransferRunning
        if (endsSession) signOut()
        extSystemConfigStore.save(config)
        _extSystemConfig.value = config
        return if (endsSession) ConfigSave.SavedAndSignedOut else ConfigSave.Saved
    }

    /**
     * Caches server access for the operator who is signed in — and refuses anybody else's.
     *
     * The login sheet is reachable from inside a session, so someone handed a device that is
     * already signed in can type their own ERP account into it. Stored as it was, that put one
     * person's password under another person's profile: every row uploaded afterwards travelled on
     * the wrong session while still carrying the signed-in operator's name. Whoever the account
     * belongs to signs in at the launch screen; there is no second way in.
     *
     * Says which of the three things happened, so a caller can tell a refusal from having had
     * nowhere to store the answer.
     */
    fun saveCredentials(username: String, password: String): CredentialSave {
        val current = profileStore.current() ?: return CredentialSave.NoProfile
        if (UserProfileStore.normalise(username) != current.id) return CredentialSave.WrongOperator
        extSystemCredentialStore.save(current.key, username, password, extSystemConfig.value.credentialTtlHours)
        _credentials.value = extSystemCredentialStore.get(current.key)
        return CredentialSave.Stored
    }

    /**
     * What [saveCredentials] did.
     *
     * [NoProfile] is not a refusal. The external-system configuration is reachable from the
     * sign-in screen on purpose — a device out of the box has no server URL, so without that
     * door nobody could ever sign in to set one — and "test connection" there is a reachability
     * check with nobody yet to file the answer under.
     */
    enum class CredentialSave { Stored, NoProfile, WrongOperator }

    /** Server access for the signed-in operator, or null once its TTL has lapsed. */
    fun savedCredentials(): ExtSystemCredentials? = extSystemCredentialStore.get(profileStore.currentKey())

    fun hasCredentials(): Boolean = extSystemCredentialStore.isValid(profileStore.currentKey())

    /**
     * Leaves the app, not the data.
     *
     * Drops this operator's server access and closes their session, returning to the sign-in
     * screen. Their documents and recordings stay exactly where they are — signing out has never
     * deleted work and must not start now, since those rows are the only copy until the ERP has
     * them. Their profile stays enrolled, so signing back in works offline.
     */
    fun signOut() {
        profileStore.currentKey()?.let { extSystemCredentialStore.clear(it) }
        _credentials.value = null
        _currentProfile.value = null
        databaseProvider.release()
    }

    /**
     * What a profile is carrying, for the confirmation that precedes deleting it.
     *
     * [unsentScans] is the number that matters. Everything else is a copy of something the ERP
     * already has; those rows exist on this device and nowhere else, and deleting the profile is
     * the end of them.
     */
    fun profileFootprint(
        profileKey: String,
        onResult: (documents: Int, scans: Int, unsentScans: Int) -> Unit,
    ) {
        viewModelScope.launch {
            val db = databaseProvider.forProfile(profileKey)
            val recordings = db.recordingDao().getAll()
            onResult(
                db.documentHeaderDao().getAll().size,
                recordings.size,
                recordings.count { it.sentAt == null },
            )
        }
    }

    /**
     * Removes an operator from this device: their documents, their recordings, their server
     * access, their settings, and the enrolment that let them sign in offline.
     *
     * Order is deliberate — data first, enrolment last. A failure part-way leaves the profile
     * still listed and the removal repeatable; the other way round would leave a database file
     * nothing can reach and nothing can name.
     *
     * Refuses the signed-in profile, and so do [DatabaseProvider.deleteProfileData] and
     * [UserProfileStore.delete] underneath. The screen does not offer it either. Three refusals
     * for one mistake is not excessive here: it would be deleting the file being written to.
     * That refusal is also what keeps this clear of [duringErpWork] — every ERP transfer writes
     * through `provider.current()`, so it can only ever touch the one profile this cannot delete.
     */
    fun deleteProfile(profileKey: String, onDone: () -> Unit = {}) {
        if (profileKey == profileStore.currentKey()) return
        val profile = profileStore.profiles().firstOrNull { it.key == profileKey } ?: return
        viewModelScope.launch {
            databaseProvider.deleteProfileData(profileKey)
            extSystemCredentialStore.clear(profileKey)
            // Personal settings belong to the person, not to one installation, so they go with the
            // person's last profile — and never while that person is the one signed in.
            val lastOfThisOperator = profileStore.profiles().none { it.id == profile.id && it.key != profileKey }
            if (lastOfThisOperator && profile.id != profileStore.currentId()) {
                appSettingsStore.deletePersonal(profile.id)
            }
            profileStore.delete(profileKey)
            onDone()
        }
    }

    /** Result of an attempt to sign in at the launch screen. */
    sealed interface SignInResult {
        data object Online : SignInResult
        data class Failed(val message: String) : SignInResult
    }

    /**
     * Signs an operator in and opens their data.
     *
     * **The external system is the only authority, every time.** There is no local fallback: if it
     * cannot be reached, or refuses the password, nobody gets in. A profile is created by the same
     * step, so an operator who has never been accepted by the ERP does not exist on this device.
     *
     * That is a deliberate trade against availability, taken knowingly. A device that cannot reach
     * the server is a device nobody can open, including an operator whose unsent scans are sitting
     * on it — those rows stay safe on disk but are out of reach until the server answers. An
     * earlier version unlocked offline against a stored PBKDF2 digest for exactly that reason; it
     * was removed because signing in without the ERP is not a sign-in at all.
     */
    fun signIn(typedUsername: String, password: String, onResult: (SignInResult) -> Unit) {
        val id = UserProfileStore.normalise(typedUsername)
        if (id == null) {
            onResult(SignInResult.Failed(inAppText(R.string.signin_bad_username)))
            return
        }
        viewModelScope.launch {
            val config = extSystemConfig.value
            // Filed under the installation that is about to vouch for them, not only their name.
            val profile = UserProfile(
                id = id,
                displayName = id,
                serverKey = config.serverIdentity,
                serverLabel = config.serverLabel,
                environment = config.environment?.name,
            )
            val serverResult = if (config.serverBaseUrl.isBlank()) null else {
                extSystemClient.configure(config, ExtSystemCredentials(typedUsername.trim(), password))
                extSystemClient.testConnection(config.serverBaseUrl.trim())
            }

            if (serverResult is ExtSystemResult.Success) {
                profileStore.enroll(profile)
                databaseProvider.switchTo(profile)
                _currentProfile.value = profile
                extSystemCredentialStore.save(profile.key, typedUsername, password, config.credentialTtlHours)
                _credentials.value = extSystemCredentialStore.get(profile.key)
                onResult(SignInResult.Online)
            } else {
                // `serverResult == null` means there is no server URL to ask at all, which is a
                // configuration problem rather than a rejection — the sign-in screen's own door to
                // the external-system setup is the way out of it.
                onResult(
                    SignInResult.Failed(
                        (serverResult as? ExtSystemResult.Failure)?.message
                            ?: inAppText(R.string.signin_needs_server)
                    )
                )
            }
        }
    }

    /**
     * Performs an authenticated NTLM GET against [serverBaseUrl] to verify that the
     * server is reachable and the supplied Windows credentials are accepted.
     * The username carries the domain (user@domain or DOMAIN\user). Credentials are
     * only persisted (the user only becomes "signed in") once the server has actually
     * accepted them — a failed test leaves the credential store untouched, so a wrong
     * password never gets remembered.
     * The password is never logged.
     */
    fun testExtSystemConnection(
        serverBaseUrl: String,
        username: String,
        password: String,
        onResult: (ExtSystemResult<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val url = serverBaseUrl.trim()
            if (url.isBlank()) {
                onResult(ExtSystemResult.Failure(inAppText(R.string.ext_config_server_url_empty))); return@launch
            }
            val config = extSystemConfig.value.copy(serverBaseUrl = url)
            val creds  = ExtSystemCredentials(username.trim(), password)
            extSystemClient.configure(config, creds)
            val result = extSystemClient.testConnection(url)
            // A server that accepts somebody else's account is still a failure here: the point of
            // the test is to obtain access for this session, and access that cannot be stored is
            // access this session does not have. Nobody signed in is a different matter — see
            // [CredentialSave.NoProfile].
            //
            // Kept only when the server tested is the one this session belongs to. The form can
            // name another server before it is saved, and a password that server accepted says
            // nothing about this one.
            val sameInstallation = config.serverIdentity == extSystemConfig.value.serverIdentity
            if (result is ExtSystemResult.Success && sameInstallation &&
                saveCredentials(username.trim(), password) == CredentialSave.WrongOperator
            ) {
                onResult(ExtSystemResult.Failure(inAppText(R.string.signin_wrong_operator)))
                return@launch
            }
            onResult(result)
        }
    }

    /** See [ConfigController.parseExtSystemConfigJson]. */
    fun parseExtSystemConfigJson(json: String): DeviceConfiguration? =
        configController.parseExtSystemConfigJson(json)

    /** See [ConfigController.loadExtSystemDefaults]. */
    fun loadExtSystemDefaults(fileName: String): DeviceConfiguration? =
        configController.loadExtSystemDefaults(fileName)

    /** See [ConfigController.exportConfigurationJson]; the login QR key is never in it. */
    fun exportConfigurationJson(): String? = configController.exportConfigurationJson()

    /** See [ConfigController.listExtSystemDefaultsCompanies]. */
    fun listExtSystemDefaultsCompanies(): List<ExtSystemDefaultsCompany> =
        configController.listExtSystemDefaultsCompanies()

    /** See [UploadCoordinator.runUpload]. */
    private suspend fun runUpload(docs: List<Document>): Int =
        uploadCoordinator.runUpload(docs, extSystemConfig.value, savedCredentials())

    /** Blocking upload path — the caller shows a progress dialog and reacts in [onComplete]. */
    fun uploadToExtSystem(
        docs: List<Document>,
        onComplete: (failureCount: Int) -> Unit = {},
    ) {
        viewModelScope.launch {
            onComplete(duringErpWork { runUpload(docs) })
        }
    }

    /**
     * Background upload path. Marks each doc [DocState.PendingUpload] immediately so the UI can
     * return control, then uploads in the background. On success the doc is deleted; on failure
     * it becomes UploadFailed. The documents Flow reflects these transitions reactively.
     */
    fun uploadInBackground(docs: List<Document>) {
        viewModelScope.launch {
            // Mark only what runUpload will actually attempt. It skips documents with no
            // recordings, so marking those PendingUpload first would strand them in a state no
            // screen renders and nothing resets.
            //
            // Documents needing review are deliberately included even though no line shows
            // progress: runUpload is what raises their error, so filtering them out here would
            // turn Retry into a no-op that closes the screen as if it had worked.
            val uploadable = docs.filter { doc -> doc.lines.any { it.scanned > 0.0 } || doc.needsReview }
            uploadable.forEach { repository.updateDocState(it.documentNo, it.type.key, DocState.PendingUpload) }
            duringErpWork { runUpload(uploadable) }
        }
    }

    fun exportDatabase(uri: Uri, onComplete: () -> Unit) {
        viewModelScope.launch {
            exporter.exportTo(uri)
            onComplete()
        }
    }

    /**
     * Empties the signed-in operator's cache, and only theirs.
     *
     * It used to reach a good deal further than its name: one person pressing it inside their own
     * session revoked every enrolled operator's server access and wiped the device's ERP endpoints
     * with it, leaving an installation that had to be set up again from the bundled defaults. What
     * goes now is what the person pressing it owns — their documents, their recordings, their
     * credentials. The device's setup is administration, not cache, and has its own screen.
     */
    fun clearCache() {
        viewModelScope.launch {
            repository.clearAll()
            profileStore.currentKey()?.let { extSystemCredentialStore.clear(it) }
            _credentials.value = null
        }
    }

    fun deleteAllDocuments() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    /** Operator has reviewed the surplus scans and confirmed the goods are off the document. */
    fun discardOrphanedScans(documentNo: String, type: DocumentType, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.discardOrphanedScans(documentNo, type.key)
            onDone()
        }
    }

    /** Operator has given up on rows the ERP keeps refusing, so the document can be closed. */
    fun discardFailedScans(documentNo: String, type: DocumentType, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.discardFailedScans(documentNo, type.key)
            onDone()
        }
    }

    fun clearErrorDocs() {
        viewModelScope.launch {
            documents.value
                .filter { it.state is DocState.UploadFailed }
                .forEach { doc ->
                    val resetState = when {
                        doc.lines.all { it.scanned >= it.expected && it.expected > 0 } -> DocState.Completed
                        doc.lines.any { it.scanned > 0 } -> DocState.InProgress
                        else -> DocState.Downloaded
                    }
                    repository.updateDocState(doc.documentNo, doc.type.key, resetState)
                }
        }
    }

}

