package com.prima.barcode.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prima.barcode.R
import com.prima.barcode.data.auth.AppSettings
import com.prima.barcode.data.auth.AppSettingsStore
import com.prima.barcode.data.auth.DOC_TYPES_BEFORE_COMPLAINT_SPLIT
import com.prima.barcode.data.auth.ExtSystemConfig
import com.prima.barcode.data.auth.ExtSystemConfigStore
import com.prima.barcode.data.auth.ExtSystemCredentialStore
import com.prima.barcode.data.auth.ExtSystemCredentials
import com.prima.barcode.data.auth.DeviceConfiguration
import com.prima.barcode.data.auth.ExtSystemDefaultsCompany
import com.prima.barcode.data.auth.ExtSystemEnvironment
import com.prima.barcode.data.auth.RecordingUploadMode
import com.prima.barcode.data.auth.UserProfile
import com.prima.barcode.data.auth.UserProfileStore
import com.prima.barcode.data.db.DatabaseProvider
import com.prima.barcode.data.db.LocationDao
import com.prima.barcode.data.db.LocationEntity
import com.prima.barcode.data.db.ResponsibilityCenterEntity
import com.prima.barcode.data.db.toDomain
import com.prima.barcode.data.export.DatabaseExporter
import com.prima.barcode.data.extsystem.ExtSystemODataClient
import com.prima.barcode.data.extsystem.ExtSystemResult
import com.prima.barcode.data.extsystem.toNavRecording
import com.prima.barcode.data.model.DocState
import com.prima.barcode.data.model.DocTypeFilterMode
import com.prima.barcode.data.model.Document
import com.prima.barcode.data.model.DocumentType
import com.prima.barcode.data.model.DownloadFilter
import com.prima.barcode.data.model.InventoryBatch
import com.prima.barcode.data.model.Item
import com.prima.barcode.data.model.Line
import com.prima.barcode.data.model.Location
import com.prima.barcode.data.model.ResponsibilityCenter
import com.prima.barcode.data.repository.DocumentRepository
import com.prima.barcode.i18n.inAppLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.prima.barcode.data.extsystem.NavBarcodeAppEntry
import com.prima.barcode.data.extsystem.NavInventoryBatch
import com.prima.barcode.data.extsystem.odataLiteral
import com.prima.barcode.data.extsystem.NavLocation
import com.prima.barcode.data.extsystem.NavODataList
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import timber.log.Timber

@HiltViewModel
class AppViewModel @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val appContext: Context,
    private val repository: DocumentRepository,
    private val locationDao: LocationDao,
    private val exporter: DatabaseExporter,
    val extSystemConfigStore: ExtSystemConfigStore,
    private val extSystemCredentialStore: ExtSystemCredentialStore,
    private val extSystemClient: ExtSystemODataClient,
    private val appSettingsStore: AppSettingsStore,
    val profileStore: UserProfileStore,
    private val databaseProvider: DatabaseProvider,
    private val configController: ConfigController,
) : ViewModel() {

    private val gson = Gson()

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

    private val _credentials = MutableStateFlow(extSystemCredentialStore.get(profileStore.currentId()))
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

    val locations: StateFlow<List<Location>> = locationDao.observeLocations()
        .map { it.map { e -> e.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val responsibilityCenters: StateFlow<List<ResponsibilityCenter>> = locationDao.observeRcs()
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

    /** Returns null on success, or an error message on failure. */
    private suspend fun realDownloadLocations(): String? {
        val config = extSystemConfig.value
        val creds  = savedCredentials() ?: return inAppText(R.string.ext_error_not_signed_in)
        if (!config.isConfigured) return inAppText(R.string.ext_error_not_configured)
        if (config.locationsUrl.isBlank()) return inAppText(R.string.ext_error_locations_url_missing)
        extSystemClient.configure(config, creds)
        return when (val result = extSystemClient.downloadRaw(config.locationsUrl)) {
            is ExtSystemResult.Success -> {
                val typeToken = object : TypeToken<NavODataList<NavLocation>>() {}.type
                val odata = gson.fromJson<NavODataList<NavLocation>>(result.data, typeToken)
                val rcCodes = odata.value.map { it.rcCode }.filter { it.isNotBlank() }.distinct()
                locationDao.clearRcs()
                locationDao.upsertRcs(rcCodes.map { ResponsibilityCenterEntity(it, it, null) })
                locationDao.clearLocations()
                locationDao.upsertLocations(odata.value.map { LocationEntity(it.code, it.name, it.rcCode) })
                null
            }
            is ExtSystemResult.Failure -> result.message
        }
    }

    private fun buildODataFilterString(filter: DownloadFilter, typeCode: String, type: DocumentType): String? {
        val clauses = mutableListOf<String>()
        if (typeCode.isNotBlank()) clauses.add("Document_Type eq ${odataLiteral(typeCode)}")
        // Retail and warehouse share a Document_Type code, so this clause is what actually
        // separates them — mandatory for all four, never optional. Transport sheets have no
        // retailLocation and so get no clause at all.
        type.retailLocation?.let { clauses.add("Retail_Location eq $it") }
        filter.dateFrom?.let { clauses.add("Document_Date ge $it") }
        filter.dateTo?.let { clauses.add("Document_Date le $it") }
        if (filter.destinationCode.isNotBlank()) clauses.add("Destination_No eq ${odataLiteral(filter.destinationCode)}")
        if (filter.sourceCode.isNotBlank()) clauses.add("Source_No eq ${odataLiteral(filter.sourceCode)}")
        if (filter.rcCode.isNotBlank()) clauses.add("Responsibility_Center eq ${odataLiteral(filter.rcCode)}")
        if (filter.documentNo.isNotBlank()) clauses.add("Document_No eq ${odataLiteral(filter.documentNo)}")
        return if (clauses.isEmpty()) null else clauses.joinToString(" and ")
    }

    /**
     * The inventory journal batches the ERP has sent for scanning in [filter]'s location or
     * responsibility centre, for the operator to pick one before downloading.
     *
     * Read from the ERP's own list of them (`BarcodeInventoryBatches`, page 50208): one row per
     * batch that has a count on the devices and is not finished yet, with its location,
     * responsibility centre, date and number of lines worked out on the ERP side. There is no
     * other way: reading every Barcode App Entry of the location and grouping them here was
     * replaced, because on a large location that meant thousands of rows to list a handful.
     */
    fun loadInventoryBatches(filter: DownloadFilter, onResult: (ExtSystemResult<List<InventoryBatch>>) -> Unit) {
        viewModelScope.launch {
            val config = extSystemConfig.value
            val creds  = savedCredentials()
            if (!config.isConfigured || creds == null) {
                onResult(ExtSystemResult.Failure(
                    if (creds == null) inAppText(R.string.ext_error_not_signed_in)
                    else inAppText(R.string.ext_error_not_configured)
                ))
                return@launch
            }
            if (config.inventoryBatchesUrl.isBlank()) {
                onResult(ExtSystemResult.Failure(inAppText(R.string.ext_error_inventory_batches_url_missing)))
                return@launch
            }
            extSystemClient.configure(config, creds)
            val clauses = buildList {
                if (filter.sourceCode.isNotBlank()) add("Barcode_Location_Code eq ${odataLiteral(filter.sourceCode)}")
                if (filter.rcCode.isNotBlank()) add("Barcode_Resp_Center eq ${odataLiteral(filter.rcCode)}")
            }
            val url = if (clauses.isEmpty()) config.inventoryBatchesUrl
                else appendODataFilter(config.inventoryBatchesUrl, clauses.joinToString(" and "))
            val result = duringErpWork { extSystemClient.downloadRaw(url) }
            onResult(when (result) {
                is ExtSystemResult.Success -> {
                    val typeToken = object : TypeToken<NavODataList<NavInventoryBatch>>() {}.type
                    val odata = gson.fromJson<NavODataList<NavInventoryBatch>>(result.data, typeToken)
                    ExtSystemResult.Success(
                        odata.value
                            .filter { it.documentNo.isNotBlank() }
                            .map { row ->
                                InventoryBatch(
                                    documentNo          = row.documentNo,
                                    journalTemplateName = row.journalTemplateName,
                                    journalBatchName    = row.journalBatchName,
                                    description         = row.description,
                                    // NAV writes an empty date as 0001-01-01.
                                    documentDate        = row.postingDate
                                        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                                        ?.takeIf { it.year > 1 },
                                    lineCount           = row.lineCount,
                                )
                            }
                            .sortedWith(compareBy({ it.journalTemplateName }, { it.journalBatchName }, { it.documentNo }))
                    )
                }
                is ExtSystemResult.Failure -> result
            })
        }
    }

    private fun appendODataFilter(url: String, filterStr: String): String {
        val encoded = java.net.URLEncoder.encode(filterStr, "UTF-8").replace("+", "%20")
        val sep = if ('?' in url) '&' else '?'
        return "$url${sep}\$filter=$encoded"
    }

    fun buildDownloadUrls(filter: DownloadFilter, docType: DocumentType? = null): List<Pair<String, String>> {
        val config = extSystemConfig.value
        if (config.documentLinesUrl.isBlank()) return emptyList()
        val types = if (docType != null) listOf(docType) else DocumentType.entries
        return types.map { type ->
            val typeCode = config.docTypeCodeFor(type)
            val filterStr = buildODataFilterString(filter, typeCode, type)
            val finalUrl = if (filterStr != null) appendODataFilter(config.documentLinesUrl, filterStr) else config.documentLinesUrl
            type.display to finalUrl
        }
    }

    fun getLocationsUrl(): String = extSystemConfig.value.locationsUrl
    /** The address an upload goes to under the configured upload mode; the debugger shows it. */
    fun getRecordingSyncUrl(): String = extSystemConfig.value.let {
        if (it.uploadMode == RecordingUploadMode.PER_DOCUMENT) it.documentUploadUrl else it.recordingSyncUrl
    }

    fun realDownloadDocuments(
        filter: DownloadFilter = DownloadFilter(),
        docType: DocumentType? = null,
        onComplete: (failureCount: Int, errors: List<String>) -> Unit = { _, _ -> },
    ) {
        viewModelScope.launch {
            val config = extSystemConfig.value
            val creds  = savedCredentials()
            if (!config.isConfigured || creds == null) {
                val msg = if (creds == null) inAppText(R.string.ext_error_not_signed_in)
                    else inAppText(R.string.ext_error_not_configured)
                onComplete(1, listOf(msg))
                return@launch
            }
            if (config.documentLinesUrl.isBlank()) {
                onComplete(1, listOf(inAppText(R.string.ext_error_document_lines_url_missing)))
                return@launch
            }
            extSystemClient.configure(config, creds)
            val typesToDownload = if (docType != null) listOf(docType) else DocumentType.entries
            var failures = 0
            val errorMessages = mutableListOf<String>()
            duringErpWork {
                for (type in typesToDownload) {
                    val typeCode = config.docTypeCodeFor(type)
                    val filterStr = buildODataFilterString(filter, typeCode, type)
                    val finalUrl = if (filterStr != null) appendODataFilter(config.documentLinesUrl, filterStr) else config.documentLinesUrl
                    val result = extSystemClient.downloadRaw(finalUrl)
                    when (result) {
                        is ExtSystemResult.Success -> {
                            val now = Instant.now()
                            val typeToken = object : TypeToken<NavODataList<NavBarcodeAppEntry>>() {}.type
                            val odata = gson.fromJson<NavODataList<NavBarcodeAppEntry>>(result.data, typeToken)
                            val documents = odata.value.groupBy { it.documentNo }.map { (docNo, rows) ->
                                val first = rows.first()
                                val lines = rows.filter { it.lineNo > 0 }.map { row ->
                                    Line(
                                        documentNo        = docNo,
                                        lineNo            = row.lineNo,
                                        item              = Item(row.itemNo, row.description),
                                        barcodeNo         = row.barcodeNo,
                                        expected          = row.qtyOutstanding,
                                        scanned           = 0.0,
                                        destinationCode   = row.destinationCode,
                                        sourceCode        = row.sourceCode,
                                        unitOfMeasureCode = row.unitOfMeasureCode,
                                        scanningQty       = row.scanningQty,
                                    )
                                }
                                Document(
                                    documentNo       = docNo,
                                    type             = type,
                                    destinationCode  = first.destinationCode,
                                    sourceCode       = first.sourceCode,
                                    rcCode           = first.rcCode,
                                    isSourceRetail   = first.isRetailLocation,
                                    creationDateTime = now,
                                    documentDate     = runCatching {
                                        first.documentDate?.let { Instant.parse("${it}T00:00:00Z") } ?: now
                                    }.getOrDefault(now),
                                    lines            = lines,
                                    state            = DocState.Downloaded,
                                )
                            }
                            // A download of one picked document says nothing about the others.
                            repository.replaceDownloadedDocuments(type, documents, pruneMissing = filter.documentNo.isBlank())
                        }
                        is ExtSystemResult.Failure -> {
                            Timber.w("Failed to download ${type.display}: ${result.message}")
                            failures++
                            errorMessages.add(result.message)
                        }
                    }
                }
            }
            onComplete(failures, errorMessages)
        }
    }

    fun loadSettings(): AppSettings = appSettingsStore.get()

    fun saveSettings(settings: AppSettings) = appSettingsStore.save(settings)

    val documents: StateFlow<List<Document>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _extSystemConfig = MutableStateFlow(extSystemConfigStore.get())
    val extSystemConfig: StateFlow<ExtSystemConfig> = _extSystemConfig

    fun saveExtSystemConfig(config: ExtSystemConfig) {
        extSystemConfigStore.save(config)
        _extSystemConfig.value = config
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
        val id = profileStore.currentId() ?: return CredentialSave.NoProfile
        if (UserProfileStore.normalise(username) != id) return CredentialSave.WrongOperator
        extSystemCredentialStore.save(id, username, password, extSystemConfig.value.credentialTtlHours)
        _credentials.value = extSystemCredentialStore.get(id)
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
    fun savedCredentials(): ExtSystemCredentials? = extSystemCredentialStore.get(profileStore.currentId())

    fun hasCredentials(): Boolean = extSystemCredentialStore.isValid(profileStore.currentId())

    /**
     * Leaves the app, not the data.
     *
     * Drops this operator's server access and closes their session, returning to the sign-in
     * screen. Their documents and recordings stay exactly where they are — signing out has never
     * deleted work and must not start now, since those rows are the only copy until the ERP has
     * them. Their profile stays enrolled, so signing back in works offline.
     */
    fun signOut() {
        profileStore.currentId()?.let { extSystemCredentialStore.clear(it) }
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
        profileId: String,
        onResult: (documents: Int, scans: Int, unsentScans: Int) -> Unit,
    ) {
        viewModelScope.launch {
            val db = databaseProvider.forProfile(profileId)
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
    fun deleteProfile(profileId: String, onDone: () -> Unit = {}) {
        if (profileId == profileStore.currentId()) return
        viewModelScope.launch {
            databaseProvider.deleteProfileData(profileId)
            extSystemCredentialStore.clear(profileId)
            appSettingsStore.deletePersonal(profileId)
            profileStore.delete(profileId)
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
            val profile = UserProfile(id = id, displayName = id)
            val serverResult = if (config.serverBaseUrl.isBlank()) null else {
                extSystemClient.configure(config, ExtSystemCredentials(typedUsername.trim(), password))
                extSystemClient.testConnection(config.serverBaseUrl.trim())
            }

            if (serverResult is ExtSystemResult.Success) {
                profileStore.enroll(profile)
                databaseProvider.switchTo(id)
                _currentProfile.value = profile
                extSystemCredentialStore.save(id, typedUsername, password, config.credentialTtlHours)
                _credentials.value = extSystemCredentialStore.get(id)
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
            if (result is ExtSystemResult.Success &&
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

    /** Uploads each doc; on success deletes it, on failure marks UploadFailed. Returns failure count. */
    private suspend fun runUpload(docs: List<Document>): Int {
        val config = extSystemConfig.value
        val creds  = savedCredentials()
        if (!config.isConfigured || creds == null) {
            docs.forEach {
                repository.updateDocState(it.documentNo, it.type.key,
                    DocState.UploadFailed(inAppText(R.string.upload_error_not_configured)))
            }
            return docs.size
        }
        val perDocument = config.uploadMode == RecordingUploadMode.PER_DOCUMENT
        val url = if (perDocument) config.documentUploadUrl else config.recordingSyncUrl
        if (url.isBlank()) {
            val reason = inAppText(
                if (perDocument) R.string.upload_error_document_url_missing else R.string.upload_error_sync_url_missing
            )
            docs.forEach {
                repository.updateDocState(it.documentNo, it.type.key, DocState.UploadFailed(reason))
            }
            return docs.size
        }
        extSystemClient.configure(config, creds)
        var failures = 0
        for (doc in docs) {
            // Checked before a single row goes out. A document holding scans whose line NAV has
            // since removed would otherwise send a Document_Line_No that no longer exists there,
            // and because the send loop breaks on the first failure, that one row would block
            // every legitimate recording behind it on every retry.
            //
            // All-or-nothing per document, not "send the good rows and flag the rest": a
            // partially posted document while the operator is still deciding about the remainder
            // leaves nobody able to say afterwards what had already gone through.
            val orphans = repository.getOrphanedRecordings(doc.documentNo, doc.type.key)
            if (orphans.isNotEmpty()) {
                repository.updateDocState(
                    doc.documentNo,
                    doc.type.key,
                    DocState.UploadFailed(
                        inAppText(R.string.upload_error_needs_review, orphans.size)
                    ),
                )
                failures++
                continue
            }

            val docTypeCode = config.docTypeCodeFor(doc.type)
            // Retail and warehouse share a Document_Type code, so the recording has to carry the
            // same discriminator for NAV to attribute it. The value is the Retail_Location NAV
            // itself reported for this document on download. Transport sheets take part in
            // neither bucket (retailLocation == null) and keep sending no field at all rather
            // than a false that would claim they're warehouse documents.
            val retailLocation = if (doc.type.retailLocation != null) doc.isSourceRetail else null
            // Line-attached and not yet accepted. The check above should have caught anything
            // else, but this is the query that decides what actually leaves the device.
            val rows = repository.getQueuedRecordings(doc.documentNo, doc.type.key)

            if (rows.isEmpty()) {
                // Nothing queued means one of two very different things. A document that never
                // held a recording is freshly downloaded and must be left alone — deleting those
                // is the bug 220f284 fixed. One whose rows have all been accepted is finished,
                // and this is where it is finally removed, taking its sent rows with it.
                if (repository.hasAnyRecordings(doc.documentNo, doc.type.key)) {
                    repository.deleteDocument(doc.documentNo, doc.type.key)
                }
                continue
            }

            if (perDocument) {
                // One call, one transaction on the ERP side: all of these rows are in NAV, or none
                // of them are. Every row carries the same outcome, so the error screen shows the
                // one reason against each of them.
                val result = extSystemClient.uploadDocument(
                    url, rows.map { it.toNavRecording(docTypeCode, retailLocation) },
                )
                when (result) {
                    is ExtSystemResult.Success -> {
                        rows.forEach {
                            repository.markRecordingSent(doc.documentNo, doc.type.key, it.documentLine, it.recordingLineNo)
                        }
                        repository.deleteDocument(doc.documentNo, doc.type.key)
                    }
                    is ExtSystemResult.Failure -> {
                        rows.forEach {
                            repository.recordRecordingFailure(
                                doc.documentNo, doc.type.key, it.documentLine, it.recordingLineNo, result.message,
                            )
                        }
                        repository.updateDocState(doc.documentNo, doc.type.key, DocState.UploadFailed(result.message))
                        failures++
                    }
                }
                continue
            }

            var lastFailure: String? = null
            var failedRows = 0
            var connectionLost = false

            for (row in rows) {
                val result = extSystemClient.uploadRecording(
                    url, row.toNavRecording(docTypeCode, retailLocation),
                )
                when (result) {
                    // Marked, not deleted. The row stays until the whole document goes, so the
                    // document keeps showing everything the operator scanned while it is only
                    // partly sent.
                    is ExtSystemResult.Success -> repository.markRecordingSent(
                        doc.documentNo, doc.type.key, row.documentLine, row.recordingLineNo,
                    )
                    is ExtSystemResult.Failure -> {
                        repository.recordRecordingFailure(
                            doc.documentNo, doc.type.key, row.documentLine, row.recordingLineNo,
                            result.message,
                        )
                        lastFailure = result.message
                        failedRows++
                        // A real status code means the server answered and objected to this row,
                        // so the rest are worth trying. `code` left at its -1 default means no
                        // response came back at all — the server or the network is gone, and
                        // continuing would spend a connect timeout per remaining row to learn
                        // the same thing again.
                        if (result.code <= 0) {
                            connectionLost = true
                            break
                        }
                    }
                }
            }

            if (lastFailure == null) {
                repository.deleteDocument(doc.documentNo, doc.type.key)
            } else {
                // A dropped connection is best described by the error itself; individually
                // refused rows are better described by how many, with the detail per row on the
                // error screen.
                val reason = if (connectionLost) lastFailure
                else inAppText(R.string.upload_error_rows_failed, failedRows, rows.size)
                repository.updateDocState(doc.documentNo, doc.type.key, DocState.UploadFailed(reason))
                failures++
            }
        }
        return failures
    }

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
            profileStore.currentId()?.let { extSystemCredentialStore.clear(it) }
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

