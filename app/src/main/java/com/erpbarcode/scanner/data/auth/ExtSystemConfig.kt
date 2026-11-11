package com.erpbarcode.scanner.data.auth

import com.erpbarcode.scanner.data.model.DocTypeFilterMode
import com.erpbarcode.scanner.data.model.DocumentType
import java.net.URLDecoder
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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
     * password is good, and where every endpoint sends and fetches data. Blank while no server is
     * configured.
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
     *
     * **Every endpoint counts, not only the server address.** The identity is the server base URL
     * plus, for each endpoint that is filled in, where it leads: scheme, host, port, server
     * instance and company ([endpointKey]). In a consistent configuration they all lead to the
     * same place, and the identity is that. A configuration whose upload address, say, names
     * another company, host or port is a different installation from the consistent one, with
     * data of its own, so scans made under the proper configuration can never be sent through
     * it; and changing where any endpoint leads ends the session like any other change of
     * installation.
     *
     * The SOAP upload address is the one exception to "same port": an instance's SOAP services
     * listen on another port than its OData ones. It adds nothing to the identity while its host,
     * instance and company match an OData endpoint's — so filling it in later does not strand the
     * work already on the device — and counts in full when they do not.
     */
    val serverIdentity: String
        get() {
            val server = serverBaseUrl.trim().trimEnd('/').lowercase()
            if (server.isEmpty()) return ""
            val keys = listOf(documentLinesUrl, recordingSyncUrl, locationsUrl, inventoryBatchesUrl, documentUploadUrl)
                .filter { it.isNotBlank() }
                .map { endpointKey(it) }
            val odata = keys.filter { !it.soap }
            val straySoap = keys.filter { it.soap && odata.none { o -> o.loose == it.loose } }
            val entries = (odata + straySoap).map { it.strict }.distinct().sorted()
            return server + "|" + entries.joinToString(";")
        }

    /** What to call that installation on screen: the company, or the server's address when the URL names none. */
    val serverLabel: String
        get() = companyName ?: serverBaseUrl.trim().substringAfter("://").trimEnd('/')

    private companion object {
        val COMPANY_IN_URL = Regex("""Company\('((?:[^']|'')*)'\)""")

        /** `Company('…')` as a whole path segment, already percent-decoded. */
        val COMPANY_SEGMENT = Regex("""Company\('((?:[^']|'')*)'\)""", RegexOption.IGNORE_CASE)

        /**
         * Where one endpoint address leads.
         *
         * [strict] is `scheme://host:port/instance|company`; [loose] leaves scheme and port out,
         * for comparing a SOAP address ([soap]) with the OData ones of the same instance.
         */
        class EndpointKey(val strict: String, val loose: String, val soap: Boolean)

        /**
         * Reads an endpoint address the way the HTTP client will, and says where it leads.
         *
         * Parsed with OkHttp's own `HttpUrl`, the parser the requests go through, so what is
         * compared here is what is actually called: dot segments resolved, host canonical,
         * segments decoded. The company is `Company('…')` in the path, a `company` query parameter,
         * or the segment after `/WS/` in a SOAP address.
         *
         * **Anything ambiguous is returned whole**, which makes it match nothing but itself — a
         * different installation rather than a trusted one:
         * - an address that does not parse;
         * - encoded dots, slashes or backslashes in it, which two parsers may read two ways;
         * - no company at all (the server would pick its default), or more than one.
         */
        fun endpointKey(url: String): EndpointKey {
            val raw = url.trim()
            val lower = raw.lowercase()
            val whole = EndpointKey(strict = lower, loose = lower, soap = false)
            if ("%2e" in lower || "%2f" in lower || "%5c" in lower || '\\' in raw) return whole
            val http = raw.toHttpUrlOrNull() ?: return whole
            val segments = http.pathSegments.filter { it.isNotEmpty() }
            val instance = segments.firstOrNull()?.lowercase() ?: return whole

            val inPath = segments.mapNotNull { COMPANY_SEGMENT.matchEntire(it)?.groupValues?.get(1)?.replace("''", "'") }
            val inQuery = http.queryParameterNames
                .filter { it.equals("company", ignoreCase = true) }
                .flatMap { http.queryParameterValues(it) }
                .filterNotNull()
                .map { it.trim().removeSurrounding("'") }
            val ws = segments.drop(1).indexOfFirst { it.equals("WS", ignoreCase = true) }
            val inSoap = if (ws >= 0) listOfNotNull(segments.getOrNull(ws + 2)) else emptyList()

            val companies = inPath + inQuery + inSoap
            if (companies.size != 1 || companies[0].isBlank()) return whole
            val company = companies[0].trim().lowercase()
            return EndpointKey(
                strict = "${http.scheme}://${http.host}:${http.port}/$instance|$company",
                loose = "${http.host}/$instance|$company",
                soap = ws >= 0,
            )
        }
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
