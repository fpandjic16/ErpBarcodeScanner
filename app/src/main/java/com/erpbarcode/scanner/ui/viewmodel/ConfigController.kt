package com.erpbarcode.scanner.ui.viewmodel

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.erpbarcode.scanner.data.auth.AppSettingsStore
import com.erpbarcode.scanner.data.auth.DOC_TYPES_BEFORE_COMPLAINT_SPLIT
import com.erpbarcode.scanner.data.auth.DeviceConfiguration
import com.erpbarcode.scanner.data.auth.ExtSystemConfig
import com.erpbarcode.scanner.data.auth.ExtSystemConfigStore
import com.erpbarcode.scanner.data.auth.ExtSystemDefaultsCompany
import com.erpbarcode.scanner.data.auth.ExtSystemEnvironment
import com.erpbarcode.scanner.data.auth.RecordingUploadMode
import com.erpbarcode.scanner.data.model.DocTypeFilterMode
import com.erpbarcode.scanner.data.model.DocumentType
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Configuration files: reading one (imported or bundled), writing this device's out, and listing the
 * bundled ones for the "Load built-in defaults" picker.
 *
 * Split out of AppViewModel, which keeps one-line delegations under the same names. Nothing here is
 * saved: a parsed configuration fills the External System Configuration form, and that screen's own
 * save writes it. "The device's configuration now" is read from [ExtSystemConfigStore], the same
 * store AppViewModel.extSystemConfig mirrors.
 */
class ConfigController @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val appSettingsStore: AppSettingsStore,
    private val extSystemConfigStore: ExtSystemConfigStore,
) {

    private val gson = Gson()

    /** Separate instance so the exported configuration file stays readable. */
    private val exportGson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Parses an `ext_system_defaults.json`-formatted string into an [ExtSystemConfig].
     * Returns null if the JSON is missing required fields or malformed.
     * Does not persist — the caller fills the editable form fields.
     */
    fun parseExtSystemConfigJson(json: String): DeviceConfiguration? = runCatching {
        val dto = gson.fromJson(json, ExtSystemDefaultsDto::class.java)
        val current = appSettingsStore.get()
        DeviceConfiguration(
            extSystem = ExtSystemConfig(
                serverBaseUrl            = dto.serverBaseUrl.orEmpty(),
                credentialTtlHours       = dto.credentialTtlHours ?: 24,
                documentLinesUrl         = dto.documentLinesUrl.orEmpty(),
                // A type the file does not mention keeps the device's code. A file written before
                // the complaint types existed would otherwise blank their codes.
                documentTypeCodes        = DocumentType.entries.associateWith { type ->
                    dto.documentTypeCodes?.get(type.name) ?: extSystemConfigStore.get().docTypeCodeFor(type)
                },
                locationsUrl     = dto.locationsUrl.orEmpty(),
                recordingSyncUrl = dto.recordingSyncUrl.orEmpty(),
                domain           = dto.domain.orEmpty(),
                // Absent or blank means "leave the key alone", never "clear it". Exported files
                // carry no key (see exportConfigurationJson), so importing one must not silently
                // kill QR sign-in on a device that already has a working key. Rotation still
                // works: a file that does carry a key overwrites whatever is there.
                loginQrKey       = dto.loginQrKey?.takeIf { it.isNotBlank() }
                    ?: extSystemConfigStore.get().loginQrKey,
                // Same rule again: a file that says nothing leaves the device's answer alone, and
                // a name this build does not recognise is treated as saying nothing rather than
                // as a guess.
                environment      = dto.environment
                    ?.let { name -> ExtSystemEnvironment.entries.firstOrNull { it.name == name } }
                    ?: extSystemConfigStore.get().environment,
                // Added later than the rest, so absent means "leave as it is": a file written
                // before these existed must not blank them on a device that already has them.
                inventoryBatchesUrl = dto.inventoryBatchesUrl ?: extSystemConfigStore.get().inventoryBatchesUrl,
                uploadMode       = dto.uploadMode
                    ?.let { name -> RecordingUploadMode.entries.firstOrNull { it.name == name } }
                    ?: extSystemConfigStore.get().uploadMode,
                documentUploadUrl = dto.documentUploadUrl ?: extSystemConfigStore.get().documentUploadUrl,
            ),
            // Same rule as the QR key, and for the same reason: an absent section means "leave
            // this alone". A configuration file written before these fields existed must not
            // silently switch every document type back on and reset how each one is scoped.
            //
            // Keyed by `DocumentType.name` in the file to match `documentTypeCodes` beside it,
            // and stored by `DocumentType.key`; a name nothing matches is dropped rather than
            // carried, so a typo cannot invent a document type.
            //
            // Files now list the types switched on. An older file lists those switched off, which
            // is read against the types that existed when it was written, so it cannot switch on a
            // type it never mentioned (see DOC_TYPES_BEFORE_COMPLAINT_SPLIT). The bundled files
            // carry neither, so loading built-in defaults leaves the device's choice alone.
            disabledDocTypes = when {
                dto.enabledDocTypes != null -> allDocTypeKeys -
                    dto.enabledDocTypes.mapNotNull { name -> docTypeByName(name)?.key }.toSet()
                dto.disabledDocTypes != null -> allDocTypeKeys -
                    (DOC_TYPES_BEFORE_COMPLAINT_SPLIT.map { it.key }.toSet() -
                        dto.disabledDocTypes.mapNotNull { name -> docTypeByName(name)?.key }.toSet())
                else -> current.disabledDocTypes
            },
            docTypeFilters = dto.docTypeFilters
                ?.mapNotNull { (name, mode) ->
                    val type = docTypeByName(name) ?: return@mapNotNull null
                    val parsed = DocTypeFilterMode.entries.firstOrNull { it.name == mode }
                        ?: return@mapNotNull null
                    type.key to parsed
                }?.toMap()
                ?: current.docTypeFilters,
            debuggerActive = dto.debuggerActive ?: current.debuggerActive,
        )
    }.onFailure { Timber.w(it, "parseExtSystemConfigJson failed") }.getOrNull()

    private fun docTypeByName(name: String): DocumentType? =
        DocumentType.entries.firstOrNull { it.name == name }

    private val allDocTypeKeys: Set<String> get() = DocumentType.entries.map { it.key }.toSet()

    /** Loads predefined parameters from a bundled `ext_system_defaults_*.json` asset. */
    fun loadExtSystemDefaults(fileName: String): DeviceConfiguration? = runCatching {
        appContext.assets.open(fileName)
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.onFailure { Timber.w(it, "Failed to load $fileName") }
     .getOrNull()
     ?.let { parseExtSystemConfigJson(it) }

    /**
     * This device's configuration as a file, with [ExtSystemConfig.loginQrKey] left out.
     *
     * Exports **what is on the device now**, not the bundled asset it may once have come from.
     * That is what makes the round trip real: set one device up by hand, export it, import it on
     * the rest. Exporting the shipped asset instead meant a hand-tuned device could never become
     * the template for its fleet.
     *
     * Every document type is written out explicitly, present or not, so the file shows the whole
     * shape of what it controls rather than only the parts that differ from a default.
     *
     * The key must never leave the app this way. An exported file lands in shared storage and
     * then travels by mail and chat, which is exactly how the one secret protecting printed login
     * QR codes ends up somewhere it cannot be recalled. Devices pick the key up from the bundled
     * assets ("Load built-in defaults") or from a file prepared deliberately for a rotation.
     * Importing a file exported here leaves the device's existing key alone rather than blanking
     * it (see [parseExtSystemConfigJson]).
     */
    fun exportConfigurationJson(): String? = runCatching {
        val config = extSystemConfigStore.get()
        val settings = appSettingsStore.get()
        val dto = ExtSystemDefaultsDto(
            serverBaseUrl      = config.serverBaseUrl,
            credentialTtlHours = config.credentialTtlHours,
            documentLinesUrl   = config.documentLinesUrl,
            documentTypeCodes  = DocumentType.entries.associate { it.name to config.docTypeCodeFor(it) },
            locationsUrl       = config.locationsUrl,
            recordingSyncUrl   = config.recordingSyncUrl,
            domain             = config.domain,
            environment        = config.environment?.name,
            inventoryBatchesUrl = config.inventoryBatchesUrl,
            uploadMode         = config.uploadMode.name,
            documentUploadUrl  = config.documentUploadUrl,
            // Never exported. Gson omits nulls, so the field is absent rather than empty, which
            // is what parseExtSystemConfigJson reads as "leave the key alone".
            loginQrKey         = null,
            enabledDocTypes    = DocumentType.entries.filter { it.key !in settings.disabledDocTypes }.map { it.name },
            docTypeFilters     = DocumentType.entries.associate {
                it.name to (settings.docTypeFilters[it.key] ?: it.defaultFilterMode).name
            },
            debuggerActive     = settings.debuggerActive,
        )
        val text = exportGson.toJson(dto)

        // Belt and braces. If the key is in the output anyway - a field renamed, a value that
        // happens to carry it - refuse to write anything rather than hand it out; the caller
        // already shows a save-failed toast. Leaking it silently is the worse failure.
        val key = config.loginQrKey
        if (key.isNotBlank() && text.contains(key)) {
            Timber.e("Refusing to export the configuration: the login QR key is in the output.")
            return@runCatching null
        }
        text
    }.onFailure { Timber.w(it, "Failed to build the configuration for export") }.getOrNull()

    /**
     * Scans bundled assets for `ext_system_defaults_*.json` files and reads each one's
     * `companyName` field, so the "Load built-in defaults" picker always matches whatever
     * files are actually bundled — adding a company is just adding a new asset file, no
     * code change needed.
     */
    fun listExtSystemDefaultsCompanies(): List<ExtSystemDefaultsCompany> = runCatching {
        appContext.assets.list("")
            ?.filter { it.startsWith("ext_system_defaults_") && it.endsWith(".json") }
            ?.sorted()
            ?.mapNotNull { fileName ->
                val text = runCatching {
                    appContext.assets.open(fileName).bufferedReader(Charsets.UTF_8).use { it.readText() }
                }.getOrNull() ?: return@mapNotNull null
                val dto = runCatching { gson.fromJson(text, ExtSystemDefaultsDto::class.java) }.getOrNull()
                val name = dto?.companyName
                // A file with no environment cannot be offered under either heading, so it is
                // skipped rather than guessed at. Every bundled file declares one.
                val environment = dto?.environment
                    ?.let { n -> ExtSystemEnvironment.entries.firstOrNull { it.name == n } }
                if (name.isNullOrBlank() || environment == null) null
                else ExtSystemDefaultsCompany(
                    label = name,
                    assetFileName = fileName,
                    environment = environment,
                    // Everything the app cannot work without. locationsUrl is left out on
                    // purpose: blank is a legitimate setting (locations not managed), not a
                    // sign that the file was never filled in.
                    isConfigured = listOf(dto.serverBaseUrl, dto.documentLinesUrl, dto.recordingSyncUrl)
                        .all { !it.isNullOrBlank() },
                )
            }
            ?: emptyList()
    }.onFailure { Timber.w(it, "Failed to list ext_system_defaults companies") }.getOrDefault(emptyList())

    /**
     * The wire shape of a configuration file. Every field nullable on purpose: absent means
     * "leave this as it is", which is what lets an older file be imported without wiping settings
     * that did not exist when it was written.
     */
    private data class ExtSystemDefaultsDto(
        // Read only when listing the bundled files, to label and group the picker.
        val companyName: String? = null,
        val environment: String? = null,
        val serverBaseUrl: String? = null,
        val credentialTtlHours: Int? = null,
        val documentLinesUrl: String? = null,
        val documentTypeCodes: Map<String, String>? = null,
        val locationsUrl: String? = null,
        val recordingSyncUrl: String? = null,
        val domain: String? = null,
        val inventoryBatchesUrl: String? = null,
        val uploadMode: String? = null,
        val documentUploadUrl: String? = null,
        val loginQrKey: String? = null,
        // The device half of AppSettings. Keyed by DocumentType.name, like documentTypeCodes.
        val enabledDocTypes: List<String>? = null,
        // Written by builds before the complaint split; read, never written.
        val disabledDocTypes: List<String>? = null,
        val docTypeFilters: Map<String, String>? = null,
        val debuggerActive: Boolean? = null,
    )
}
