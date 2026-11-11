package com.erpbarcode.scanner.data.auth

import com.erpbarcode.scanner.data.model.DocTypeFilterMode
import com.erpbarcode.scanner.data.model.DocumentType
import java.net.URLDecoder

/**
 * Which ERP a device is talking to.
 *
 * The distinction is worth a field of its own because the two mistakes it prevents are both
 * expensive and both silent: scanning a real shipment into the test company, and scanning test
 * work into production. Nothing in a URL can be trusted to reveal which is which — a hostname
 * says whatever somebody typed — so the configuration states it outright.
 */
enum class ExtSystemEnvironment { TEST, PRODUCTION }

/**
 * How a document's recordings travel to the ERP.
 *
 * [PER_SCAN] is one OData POST per recording to [ExtSystemConfig.recordingSyncUrl]: each row is
 * accepted or refused on its own, so a document can end up partly sent.
 *
 * [PER_DOCUMENT] is one SOAP call per document to [ExtSystemConfig.documentUploadUrl] (the ERP's
 * BarcodeAppUpload service): every recording of the document in one transaction, all of
 * them accepted or none. A document is never half in the ERP, and a few hundred scans go in one
 * round trip instead of a few hundred.
 */
enum class RecordingUploadMode { PER_SCAN, PER_DOCUMENT }

data class ExtSystemConfig(
    val serverBaseUrl: String = "",
    val credentialTtlHours: Int = 24,
    val documentLinesUrl: String = "",
    val documentTypeCodes: Map<DocumentType, String> = emptyMap(),
    val recordingSyncUrl: String = "",
    val locationsUrl: String = "",
    // Windows domain for the ERP's NTLM auth. When set, it's combined with whatever the user
    // types on the login screen so they only ever need to enter a bare username. Left blank,
    // a domain embedded in the username itself (DOMAIN\user or user@domain) still works —
    // see ExtSystemODataClient.buildClient.
    val domain: String = "",
    // AES-256 key (base64, 32 bytes) that login QR codes are encrypted with — see
    // LoginQrPayload.kt. Carried in the config rather than the build so it can be rotated by
    // importing a new configuration instead of shipping a new APK. Blank disables QR sign-in.
    val loginQrKey: String = "",
    // Null means "nobody has said". Deliberately not defaulted to either value: a device that
    // claims TEST while pointing at production, or the reverse, is worse than one that admits it
    // does not know. It becomes known as soon as a configuration declaring an environment is
    // loaded, which every bundled file now does.
    val environment: ExtSystemEnvironment? = null,
    // The inventory journal batches sent for scanning (the ERP's BarcodeInventoryBatches list).
    // Required for a stock count: there is no other way to list them.
    val inventoryBatchesUrl: String = "",
    val uploadMode: RecordingUploadMode = RecordingUploadMode.PER_SCAN,
    // SOAP address of the ERP's BarcodeAppUpload service (…/WS/<Company>/Codeunit/BarcodeAppUpload); used only when
    // uploadMode is PER_DOCUMENT.
    val documentUploadUrl: String = "",
) {
    fun docTypeCodeFor(type: DocumentType): String = documentTypeCodes[type] ?: ""
    val isConfigured: Boolean get() = serverBaseUrl.isNotBlank()

    /**
     * The ERP company the endpoints address, read from `Company('…')` in the document lines URL;
     * null when there is none.
     *
     * Read from the address rather than stored beside it: this is the company the device actually
     * talks to, and a separate field could say one thing while the URL said another. OData doubles
     * an apostrophe inside the name, and the name is percent-encoded (`Acme%20Ltd`).
     */
    val companyName: String?
        get() {
            val raw = COMPANY_IN_URL.find(documentLinesUrl)?.groupValues?.get(1) ?: return null
            val decoded = runCatching { URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8") }.getOrDefault(raw)
            return decoded.replace("''", "'").takeIf { it.isNotBlank() }
        }

    /**
     * Which ERP installation this configuration talks to: the server that decides whether a
     * password is good, and the company on it. Blank while no server is configured.
     *
     * Everything an operator owns on the device is filed under their id **and** this (see
     * `UserProfile.key`): documents, scans, the saved sign-in, and the device's list of locations.
     * So a device pointed at another server or another company opens other, separate data, and
     * nothing scanned for one can be sent to, or mixed with, the other. It is also what stops a
     * device being pointed at a server that accepts any password in order to open an enrolled
     * operator's work: under that server, their data is simply not there.
     *
     * Trailing slashes and letter case do not make a different installation; a different host,
     * port, instance or company does, and so does a test and a production system. Renaming a
     * service in one of the endpoint addresses does not.
     */
    val serverIdentity: String
        get() {
            val server = serverBaseUrl.trim().trimEnd('/').lowercase()
            if (server.isEmpty()) return ""
            return server + "|" + companyName.orEmpty().trim().lowercase()
        }

    /** What to call that installation on screen: the company, or the server's address when the URL names none. */
    val serverLabel: String
        get() = companyName ?: serverBaseUrl.trim().substringAfter("://").trimEnd('/')

    private companion object {
        val COMPANY_IN_URL = Regex("""Company\('((?:[^']|'')*)'\)""")
    }
}

data class ExtSystemCredentials(
    val username: String,
    val password: String,
)

/**
 * One bundled per-company ERP connection defaults file, selectable from "Load built-in
 * defaults". Discovered at runtime from `ext_system_defaults_*.json` assets — see
 * [com.erpbarcode.scanner.ui.viewmodel.AppViewModel.listExtSystemDefaultsCompanies] — rather than
 * a fixed list, so adding a new company is just adding a new asset file.
 */
data class ExtSystemDefaultsCompany(
    val label: String,
    val assetFileName: String,
    val environment: ExtSystemEnvironment,
    /**
     * False for a file that is missing any address the app cannot work without — the server, the
     * document lines or the recording sync. Checking the server alone was not enough: a file half
     * filled in would pass, load, and replace a working configuration with blank endpoints. The
     * picker shows these and refuses to load them.
     */
    val isConfigured: Boolean,
)

/**
 * Everything a configuration file sets up on a device.
 *
 * Two halves, one owner. [extSystem] is where the ERP lives; the rest is what this
 * installation offers and how it is scoped — the device half of `AppSettings`, set once by
 * an administrator and inherited by every operator.
 *
 * The personal half is deliberately absent. Text size, language, haptics and the working
 * location belong to a person, and a deployment file that reset them on every load would
 * undo the reason those settings are per-operator at all.
 */
data class DeviceConfiguration(
    val extSystem: ExtSystemConfig,
    val disabledDocTypes: Set<String>,
    val docTypeFilters: Map<String, DocTypeFilterMode>,
    val debuggerActive: Boolean,
)
