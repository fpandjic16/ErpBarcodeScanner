package com.erpbarcode.scanner.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erpbarcode.scanner.BuildConfig
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
import com.erpbarcode.scanner.data.auth.KeyProtection
import com.erpbarcode.scanner.data.auth.RecordingUploadMode
import com.erpbarcode.scanner.data.auth.RememberedSignIn
import com.erpbarcode.scanner.data.auth.SecureStore
import com.erpbarcode.scanner.data.auth.SignInCodeCheck
import com.erpbarcode.scanner.data.auth.TrustedClock
import com.erpbarcode.scanner.data.auth.UserProfile
import com.erpbarcode.scanner.data.auth.UserProfileStore
import com.erpbarcode.scanner.data.auth.checkSignInCode
import com.erpbarcode.scanner.data.auth.rememberedSignIn
import com.erpbarcode.scanner.data.extsystem.ntHashOf
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
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
import kotlinx.coroutines.yield
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
    private val trustedClock: TrustedClock,
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
            // A transfer can end with the saved sign-in gone: refused for the fifth time in a
            // row, it is removed where the transfer ends (SignInLedger). What the screens hold
            // of it follows.
            _credentials.value = extSystemCredentialStore.get(profileStore.currentKey())
        }
    }

    // Declared above the two flows below because they follow it, and properties initialise in
    // the order they are written.
    private val _extSystemConfig = MutableStateFlow(extSystemConfigStore.get())
    val extSystemConfig: StateFlow<ExtSystemConfig> = _extSystemConfig

    /**
     * Where this device holds the key the saved sign-ins are encrypted with. Asked once: it
     * means a call into the Keystore, and it cannot change while the app runs.
     *
     * Declared above the `init` block that reads it: properties initialise in the order they are
     * written, and that includes the holder a lazy value sits in.
     */
    val keyProtection: KeyProtection by lazy { SecureStore.keyProtection() }

    /**
     * Whether a sign-in may be remembered on this device at all: only where the storage key
     * cannot be read out of it.
     *
     * A debug build is let through wherever it runs. It is debuggable, so its storage can be
     * read whatever the key is held in, and an emulator, where this has to be tried, holds keys
     * in software.
     */
    private val keyIsProtected: Boolean
        get() = keyProtection == KeyProtection.SECURE_HARDWARE || BuildConfig.DEBUG

    /**
     * For how many days this device remembers a sign-in under [config]: what the configuration
     * says where the storage key is protected, and 0 where it is not.
     *
     * Everything that asks "is anything remembered here?" asks this, not the configuration: what
     * the screens promise, whether signing out keeps the saved sign-in, what is cleared when the
     * app starts. On a device that cannot protect its key, a saved sign-in therefore lasts until
     * sign-out and no longer, exactly as with the setting at 0.
     */
    private fun rememberDaysOn(config: ExtSystemConfig): Int =
        if (keyIsProtected) config.rememberSignInDays.coerceAtLeast(0) else 0

    /** [rememberDaysOn] the configuration in effect; what the sign-in sheets say under their button. */
    fun rememberDaysInEffect(): Int = rememberDaysOn(extSystemConfig.value)

    init {
        // Deletes the storage of earlier versions, which kept passwords, before anything else
        // could want it.
        extSystemCredentialStore.open()
        // Nobody is signed in when the process starts. Where nothing is remembered, a sign-in is
        // kept only until sign-out, and a process that was killed never signed out.
        if (profileStore.current() == null && rememberDaysOn(_extSystemConfig.value) <= 0) {
            forgetSavedSignIns(except = null)
        }
    }

    /** Removes every operator's saved sign-in but [except]'s. Profiles and their data stay. */
    private fun forgetSavedSignIns(except: String?) {
        profileStore.profiles().map { it.key }.filter { it != except }
            .forEach { extSystemCredentialStore.clear(it) }
    }

    /** What [rememberedSignIn] says of one profile now. A sign-in found expired is removed here. */
    private fun rememberedState(profileKey: String, days: Int): RememberedSignIn {
        val saved = extSystemCredentialStore.saved(profileKey)
        val state = rememberedSignIn(
            days = days,
            keyInSecureHardware = keyIsProtected,
            verifiedAt = saved?.verifiedAt,
            trustedNow = trustedClock.now(),
            wallNow = trustedClock.wallNow(),
        )
        if (state == RememberedSignIn.EXPIRED) extSystemCredentialStore.clear(profileKey)
        return state
    }

    /** What the sign-in screen shows about remembered sign-ins; see [rememberedSignIns]. */
    data class RememberedSignIns(
        /** The operators whose sign-in code lets them in without the ERP right now, by profile key. */
        val keys: Set<String> = emptySet(),
        /** For how many days a sign-in is remembered on this device; 0 when none is. */
        val days: Int = 0,
        /** The device's clock stands behind a time already seen, so nothing remembered works. */
        val clockBehind: Boolean = false,
    )

    /**
     * Who the sign-in screen may mark as remembered, and what it says about remembering.
     *
     * Off the main thread: it reads one encrypted value for each operator enrolled here, and a
     * read through the Keystore is slow enough on the older terminals to be seen on a screen
     * that is waiting to be drawn.
     */
    suspend fun rememberedSignIns(): RememberedSignIns = withContext(Dispatchers.IO) {
        val config = extSystemConfig.value
        val days = rememberDaysOn(config)
        if (days <= 0) return@withContext RememberedSignIns()
        RememberedSignIns(
            keys = profileStore.profilesOn(config.serverIdentity)
                .map { it.key }
                .filter { rememberedState(it, config.rememberSignInDays) == RememberedSignIn.ALLOWED }
                .toSet(),
            days = days,
            clockBehind = trustedClock.deviceClockIsBehind(),
        )
    }

    private val _profilesChanged = MutableStateFlow(0)

    /**
     * Counts up whenever the list of operators on this device changes underneath a screen that
     * shows it. The sign-in screen reads the list once and would otherwise go on offering an
     * operator who has just deleted their own profile, since that deletion finishes after the
     * screen is already up.
     */
    val profilesChanged: StateFlow<Int> = _profilesChanged

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
        // Remembering switched off takes effect at once for everybody who is not working right
        // now. The signed-in operator's own sign-in goes when they sign out.
        if (rememberDaysOn(config) <= 0) forgetSavedSignIns(except = profileStore.currentKey())
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
        // The password ends here: what is kept is its NT hash.
        extSystemCredentialStore.save(current.key, username, ntHashOf(password))
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

    /**
     * What the signed-in operator's transfers sign with, or null when they have to sign in first:
     * nothing saved yet, or removed after the ERP refused it five times in a row.
     */
    fun savedCredentials(): ExtSystemCredentials? = extSystemCredentialStore.get(profileStore.currentKey())

    fun hasCredentials(): Boolean = extSystemCredentialStore.isValid(profileStore.currentKey())

    /**
     * Leaves the app, not the data.
     *
     * Closes this operator's session and returns to the sign-in screen. Their documents and
     * recordings stay exactly where they are — signing out has never deleted work and must not
     * start now, since those rows are the only copy until the ERP has them. Their profile stays
     * enrolled.
     *
     * **The saved sign-in stays too, while this device remembers sign-ins**: it is what lets
     * the same operator in with their sign-in code tomorrow, with or without the ERP, and it would
     * be no use if handing the device over erased it. Where nothing is remembered
     * (`rememberSignInDays` 0, or a device that cannot protect its storage key; see
     * [rememberDaysOn]) it goes here, as it always did. Removing it on purpose is deleting
     * the profile ([signOutAndDeleteProfile], or another operator in Settings → Profiles).
     */
    fun signOut() {
        if (rememberDaysInEffect() <= 0) {
            profileStore.currentKey()?.let { extSystemCredentialStore.clear(it) }
        }
        _credentials.value = null
        _currentProfile.value = null
        databaseProvider.release()
    }

    /**
     * Signs the operator out and removes their own profile from the device: documents, scans, the
     * saved sign-in, their settings. [onDone] is told whether it was done.
     *
     * The one way to delete the profile one is signed in as. Everything underneath refuses the
     * signed-in profile ([deleteProfile], `DatabaseProvider.deleteProfileData`,
     * `UserProfileStore.delete`), and rightly: it is the file being written to. So the session is
     * closed first, and what is deleted is then nobody's open database.
     *
     * **Unsent scans go with it.** That is the operator's decision to make, and the screen that
     * calls this asks them with the number in front of them; answering no leaves everything as it
     * was, so they can upload first. This is the profile exception to the rule that a document
     * with an unsent scan is never deleted, the same one Settings → Profiles has always been.
     *
     * Refused while a transfer runs, as signing out is: the transfer is writing to that database.
     */
    fun signOutAndDeleteProfile(onDone: (deleted: Boolean) -> Unit = {}) {
        val key = profileStore.currentKey()
        if (key == null || _erpWorkCount.value > 0) {
            onDone(false)
            return
        }
        signOut()
        viewModelScope.launch {
            // One turn of the main thread first. Everything that was reading that database is
            // told it is gone by the sign-out above, and should have let go of it before the
            // file is closed and deleted underneath.
            yield()
            deleteProfile(key) { onDone(true) }
        }
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
            _profilesChanged.update { it + 1 }
            onDone()
        }
    }

    /** Result of an attempt to sign in at the launch screen. */
    sealed interface SignInResult {
        /** The ERP was asked and accepted. */
        data object Online : SignInResult

        /** Let in on a sign-in the ERP accepted earlier, without asking it now. */
        data object Remembered : SignInResult

        data class Failed(val message: String) : SignInResult
    }

    /** Holds a scanned code against what is saved for [profileKey]; the rule is [checkSignInCode]. */
    private fun checkAgainstSaved(profileKey: String, offeredHash: String, days: Int): SignInCodeCheck {
        // The state first: finding the sign-in expired removes it, and then there is nothing saved.
        val state = rememberedState(profileKey, days)
        return checkSignInCode(state, extSystemCredentialStore.saved(profileKey)?.ntHash, offeredHash)
    }

    /** Opens [profile]'s data as the session, on whatever sign-in is saved for it. */
    private fun openSession(profile: UserProfile) {
        databaseProvider.switchTo(profile)
        _currentProfile.value = profile
        _credentials.value = extSystemCredentialStore.get(profile.key)
    }

    /**
     * Signs an operator in and opens their data.
     *
     * **The ERP decides every password it is asked about, and it is asked about every one that is
     * typed.** If it cannot be reached, or refuses, a typed password lets nobody in. A profile is
     * created by the ERP's acceptance, so an operator it has never accepted does not exist on this
     * device, and a wrong password at a first sign-in leaves nothing behind.
     *
     * **A scanned sign-in code ([scanned]) of an operator the ERP accepted here before is first
     * held against what was saved then** (3.6.92). For `rememberSignInDays` days after the ERP
     * last accepted that sign-in, a code carrying the same password lets the operator in without
     * asking it: the device compares NT hashes, and never sees or keeps a password to compare.
     * That is what lets a shift start where the network does not reach. Everything else still
     * goes to the ERP: a code that does not match (the password may have changed, and the new
     * code is right), an operator nothing is saved for, a sign-in remembered too long ago, a
     * device whose clock or storage key cannot be trusted ([rememberedSignIn]).
     *
     * A code that did not match and that the ERP did not then accept counts against the saved
     * sign-in like a refused transfer, and the fifth in a row removes it: a device left lying
     * about is not a place to try codes on.
     *
     * An earlier version unlocked offline against a stored digest of a typed password. That was
     * removed, and this is not it coming back: nothing typed is ever checked on the device.
     */
    fun signIn(
        typedUsername: String,
        password: String,
        scanned: Boolean = false,
        onResult: (SignInResult) -> Unit,
    ) {
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
            // The password ends here. What goes to the ERP, and what is kept, is its NT hash.
            val offered = ExtSystemCredentials.fromPassword(typedUsername.trim(), password)

            val code = if (scanned) checkAgainstSaved(profile.key, offered.ntHash, config.rememberSignInDays)
                else SignInCodeCheck.NOT_REMEMBERED
            if (code == SignInCodeCheck.MATCHES) {
                openSession(profile)
                onResult(SignInResult.Remembered)
                return@launch
            }

            val serverResult = if (config.serverBaseUrl.isBlank()) null else {
                extSystemClient.checkCredentials(config, offered, config.serverBaseUrl.trim())
            }

            if (serverResult is ExtSystemResult.Success) {
                profileStore.enroll(profile)
                // A device clock standing behind a time already seen lets nobody in on a
                // remembered sign-in, and would go on doing so for good if the clock had once
                // been ahead by mistake. The ERP has just vouched for this operator, so the clock
                // is taken at its word again from here; and because that is a step back in time,
                // nobody's saved sign-in is carried across it. Everybody else signs in through
                // the ERP once more.
                if (trustedClock.deviceClockIsBehind()) {
                    forgetSavedSignIns(except = null)
                    trustedClock.acceptDeviceClock()
                }
                // Replaces whatever was saved: the time starts again and the failures are forgotten.
                extSystemCredentialStore.save(profile.key, offered.username, offered.ntHash)
                openSession(profile)
                onResult(SignInResult.Online)
            } else {
                if (code == SignInCodeCheck.DIFFERS) extSystemCredentialStore.recordRefused(profile.key)
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
            val creds  = ExtSystemCredentials.fromPassword(username.trim(), password)
            // On a client of its own: a transfer may be running, and it must go on under the
            // credentials it started with whatever is typed here.
            val result = extSystemClient.checkCredentials(config, creds, url)
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
            val uploadable = docs.filter { it.isUploadable }
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
     *
     * A document holding a scan that has not been sent is not cache either, and stays: it goes
     * once the scan is sent, or once the operator deletes the scan. [onDone] is told how many
     * documents stayed for that reason.
     */
    fun clearCache(onDone: (keptDocuments: Int) -> Unit = {}) {
        viewModelScope.launch {
            val kept = repository.deleteDocumentsWithNothingUnsent()
            profileStore.currentKey()?.let { extSystemCredentialStore.clear(it) }
            _credentials.value = null
            onDone(kept)
        }
    }

    /** Removes every document without an unsent scan; [onDone] is told how many had to stay. */
    fun deleteAllDocuments(onDone: (keptDocuments: Int) -> Unit = {}) {
        viewModelScope.launch {
            onDone(repository.deleteDocumentsWithNothingUnsent())
        }
    }

    /** Operator has reviewed the surplus scans and confirmed the goods are off the document. */
    fun discardOrphanedScans(documentNo: String, type: DocumentType, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.discardOrphanedScans(documentNo, type.key)
            onDone()
        }
    }

    /**
     * Operator has given up on rows the ERP keeps refusing, so the document can be closed.
     * [onDone] is told whether they went: they do not while a background upload is sending it.
     */
    fun discardFailedScans(documentNo: String, type: DocumentType, onDone: (discarded: Boolean) -> Unit = {}) {
        viewModelScope.launch {
            onDone(repository.discardFailedScans(documentNo, type.key))
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

