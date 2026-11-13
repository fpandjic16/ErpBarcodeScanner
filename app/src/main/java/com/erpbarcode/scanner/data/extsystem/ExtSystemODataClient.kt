package com.erpbarcode.scanner.data.extsystem

import android.content.Context
import com.google.gson.Gson
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.data.auth.ExtSystemConfig
import com.erpbarcode.scanner.data.auth.ExtSystemCredentials
import com.erpbarcode.scanner.i18n.inAppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import okhttp3.OkHttpClient
import timber.log.Timber
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class ExtSystemResult<out T> {
    data class Success<T>(val data: T) : ExtSystemResult<T>()
    data class Failure(val message: String, val code: Int = -1) : ExtSystemResult<Nothing>()
}

@Singleton
class ExtSystemODataClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val gson = Gson()

    /**
     * An operator-facing message, in the app's language — see [inAppLanguage].
     *
     * These messages reach the screen as they are: the sign-in error dialog, the download error
     * dialog and the upload error screen all show [ExtSystemResult.Failure.message] verbatim. They
     * used to be English literals, so a wrong password read "NTLM handshake completed (phase 3) but
     * server rejected the credentials" on an otherwise Croatian screen.
     */
    private fun text(id: Int): String = context.inAppLanguage().getString(id)

    /**
     * What to tell the operator when a request threw instead of getting an answer.
     *
     * The exception's own message is the network stack's English ("failed to connect to
     * /192.168.100.113 (port 7048) after 30000ms") and used to be shown as it stood. The cases an
     * operator can act on are named in their language; anything else keeps the raw message, which
     * is still the most useful thing to hand to IT. The full exception is logged either way.
     *
     * Timeouts are checked before refused connections because a connect timeout can arrive as a
     * subclass of ConnectException, depending on how Ktor wraps it.
     */
    private fun describe(error: Throwable): String {
        val chain = generateSequence(error) { it.cause }.take(8).toList()
        return when {
            chain.any { it is UnknownHostException } -> text(R.string.ext_error_unknown_host)
            chain.any { it is SocketTimeoutException || it.javaClass.simpleName.contains("Timeout") } ->
                text(R.string.ext_error_timeout)
            chain.any { it is ConnectException || it is NoRouteToHostException } ->
                text(R.string.ext_error_unreachable)
            else -> error.message ?: text(R.string.ext_error_unknown)
        }
    }

    /**
     * Runs one transfer on a connection of its own.
     *
     * A client is built for [creds] when the transfer starts, handed to [block] as its
     * [ErpConnection] and closed when the block ends, however it ends. Every request the transfer
     * makes goes through that one client, so a transfer runs from its first request to its last
     * under the credentials and the domain it started with.
     *
     * Until 3.6.82 there was one client for all of them, replaced by whichever transfer started
     * with other credentials or another configured domain, and read anew at every request. A
     * transfer that was already running (a background upload is the one that leaves the screen
     * free for it) carried on, from its next request, on the client the later one had built: in
     * the middle of a document, under whatever that one had been given. With the saved sign-in
     * typed again in another form that changed nothing anyone could see; with a domain changed in
     * the configuration and not yet right, every row still to go was refused. Closing a client
     * does not cut off a request already on its way, but one that began as the old client closed
     * failed with "Client already closed".
     *
     * The price is one sign-in handshake per transfer, where a client kept from the last transfer
     * could sometimes reuse a connection: three round trips on the local network.
     */
    suspend fun <T> transfer(
        config: ExtSystemConfig,
        creds: ExtSystemCredentials,
        block: suspend (ErpConnection) -> T,
    ): T {
        val (client, _) = buildClient(config.domain, creds.username, creds.password)
        return try {
            block(ErpConnection(client))
        } finally {
            client.close()
        }
    }

    /** One transfer's connection to the ERP. Only [transfer] makes one, and closes it. */
    inner class ErpConnection internal constructor(private val client: HttpClient) {

        /** One recording, as one OData POST. */
        suspend fun uploadRecording(url: String, row: ErpRecordingRow): ExtSystemResult<Unit> =
            postRecording(client, url, row)

        /** Every recording of one document in a single SOAP call: all of them, or none. */
        suspend fun uploadDocument(url: String, rows: List<ErpRecordingRow>): ExtSystemResult<Unit> =
            postDocument(client, url, rows)

        /** A GET that follows the ERP's paging to the end and returns every row as one list. */
        suspend fun downloadRaw(url: String): ExtSystemResult<String> = getAllPages(client, url)
    }

    /** A client and the authenticator that answers for it; the two are only ever built together. */
    private fun buildClient(
        configuredDomain: String,
        rawUsername: String,
        password: String,
    ): Pair<HttpClient, NtlmAuthenticator> {
        // What the user typed always wins over the configured domain, so a login can be
        // corrected on the spot without touching Settings:
        //   DOMAIN\user / user@domain -> that domain
        //   .\user  (and bare \user)  -> no domain at all, deliberately overriding Settings
        //                                (the Windows "this machine, not the domain" form)
        //   user                      -> fall back to Settings → Credential session
        // A username written with a separator states its own domain and is taken at its
        // word — including when what it states is "none", which is why the check is on the
        // separator rather than on the parsed domain being non-blank. The username handed
        // to NTLM is always the bare one, so "DOMAIN\user" plus a configured domain can't
        // send the domain through twice.
        val typed = rawUsername.trim()
        val (typedDomain, username) = parseDomainUser(typed)
        val statesOwnDomain = '\\' in typed || '@' in typed
        val domain = if (statesOwnDomain) typedDomain else configuredDomain.trim()
        val auth = NtlmAuthenticator(domain, username, password)
        val okHttp = OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)  // bypass system proxy; the ERP is always on local LAN
            .authenticator(auth)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val client = HttpClient(OkHttp) {
            engine { preconfigured = okHttp }
            expectSuccess = false
        }
        return client to auth
    }

    /**
     * Asks the ERP whether [creds] are accepted at [baseUrl]: the connection test and the sign-in.
     *
     * On a client built for this one question and closed after it, as every transfer has had
     * since ([transfer]). It used to go through the one client all of them shared, which was
     * rebuilt whenever the credentials differed and read anew at every request. A test run while
     * an upload was sending in the background, or while the locations were refreshing, therefore
     * switched the rest of that transfer to whatever had just been typed: somebody else's account,
     * or a mistyped password that failed every row still to go.
     */
    suspend fun checkCredentials(
        config: ExtSystemConfig,
        creds: ExtSystemCredentials,
        baseUrl: String,
    ): ExtSystemResult<Unit> {
        val (client, auth) = buildClient(config.domain, creds.username, creds.password)
        return try {
            probe(client, auth, baseUrl)
        } finally {
            client.close()
        }
    }

    private suspend fun probe(client: HttpClient, auth: NtlmAuthenticator, baseUrl: String): ExtSystemResult<Unit> {
        auth.resetPhase()
        return runCatching {
            val response = client.get(baseUrl) { accept(ContentType.Application.Json) }
            val phase = auth.phaseReached
            if (response.status.isSuccess()) {
                // A 2xx on its own proves nothing about the password. NtlmAuthenticator is an
                // okhttp3.Authenticator, so OkHttp only invokes it in answer to a 401 — an endpoint
                // that serves the request without challenging never authenticates anybody, and
                // every password "works". Treating that as a success would enrol an operator on
                // this device under a password the ERP has never seen.
                if (phase == 0) {
                    Timber.w("checkCredentials: HTTP ${response.status.value} with no NTLM challenge")
                    return@runCatching ExtSystemResult.Failure(text(R.string.ext_error_no_challenge_ok))
                }
                ExtSystemResult.Success(Unit)
            } else {
                Timber.w("checkCredentials: HTTP ${response.status.value}, NTLM phase=$phase")
                val msg = when {
                    response.status.value == 401 && phase == 0 -> text(R.string.ext_error_no_challenge)
                    // The NTLM phase reached is in the log line above; the operator only needs to
                    // know the name or the password was refused.
                    response.status.value == 401 -> text(R.string.ext_error_credentials_rejected)
                    else -> {
                        val body = runCatching { response.bodyAsText() }.getOrDefault("")
                        "HTTP ${response.status.value}: $body".take(300)
                    }
                }
                ExtSystemResult.Failure(msg, response.status.value)
            }
        }.getOrElse {
            Timber.e(it, "OData connection test failed")
            ExtSystemResult.Failure(describe(it))
        }
    }

    private suspend fun postRecording(client: HttpClient, url: String, row: ErpRecordingRow): ExtSystemResult<Unit> {
        return runCatching {
            val body = gson.toJson(row)
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(body)
            }
            if (response.status.isSuccess()) {
                ExtSystemResult.Success(Unit)
            } else {
                val errorBody = runCatching { response.bodyAsText() }.getOrDefault("")
                Timber.w("OData upload failed [${response.status.value}]: $errorBody")
                // A 401 carries no body worth showing; the server's own text for anything else
                // (the ERP's validation messages) is the most precise thing available, so it stays.
                val message = if (response.status.value == 401) text(R.string.ext_error_http_401)
                    else "HTTP ${response.status.value}: $errorBody".take(300)
                ExtSystemResult.Failure(message, response.status.value)
            }
        }.getOrElse {
            Timber.e(it, "OData upload error")
            ExtSystemResult.Failure(describe(it))
        }
    }

    /**
     * Sends every recording of one document in a single SOAP call (see [DocumentUploadSoap]).
     * Success means the ERP holds all of [rows]; a failure means it holds none of them from this call.
     *
     * A fault's `faultstring` is the ERP's own error text (a closed document, a refused field), which
     * is the most precise thing there is to show, so it is shown. The read timeout is the client's
     * usual minute; a document of a few hundred scans is one insert pass on the server.
     */
    private suspend fun postDocument(
        client: HttpClient,
        url: String,
        rows: List<ErpRecordingRow>,
    ): ExtSystemResult<Unit> {
        return runCatching {
            val response = client.post(url) {
                header("SOAPAction", DocumentUploadSoap.SOAP_ACTION)
                setBody(TextContent(DocumentUploadSoap.envelope(rows), ContentType.Text.Xml.withCharset(Charsets.UTF_8)))
            }
            if (response.status.isSuccess()) {
                ExtSystemResult.Success(Unit)
            } else {
                val errorBody = runCatching { response.bodyAsText() }.getOrDefault("")
                Timber.w("SOAP document upload failed [${response.status.value}]: ${errorBody.take(2000)}")
                val message = when {
                    response.status.value == 401 -> text(R.string.ext_error_http_401)
                    else -> DocumentUploadSoap.faultMessage(errorBody)
                        ?: "HTTP ${response.status.value}: $errorBody".take(300)
                }
                ExtSystemResult.Failure(message, response.status.value)
            }
        }.getOrElse {
            Timber.e(it, "SOAP document upload error")
            ExtSystemResult.Failure(describe(it))
        }
    }

    private suspend fun getAllPages(client: HttpClient, url: String): ExtSystemResult<String> {
        return runCatching {
            val allValues = com.google.gson.JsonArray()
            var nextUrl: String? = url
            while (nextUrl != null) {
                val response = client.get(nextUrl) {
                    accept(ContentType.Application.Json)
                    header("Accept", "application/json;odata=nometadata")
                }
                if (!response.status.isSuccess()) {
                    val body = runCatching { response.bodyAsText() }.getOrDefault("")
                    Timber.w("ERP download failed [${response.status.value}]: $body")
                    val message = if (response.status.value == 401) text(R.string.ext_error_http_401)
                        else "HTTP ${response.status.value}: ${response.status.description}"
                    return ExtSystemResult.Failure(message, response.status.value)
                }
                val page = com.google.gson.JsonParser.parseString(response.bodyAsText()).asJsonObject
                page.getAsJsonArray("value")?.forEach { allValues.add(it) }
                nextUrl = page.get("@odata.nextLink")?.takeIf { !it.isJsonNull }?.asString
                Timber.d("OData page: ${allValues.size()} rows total, more=${nextUrl != null}")
            }
            val merged = com.google.gson.JsonObject().apply { add("value", allValues) }
            ExtSystemResult.Success(gson.toJson(merged))
        }.getOrElse {
            Timber.e(it, "ERP download error: $url")
            ExtSystemResult.Failure(describe(it))
        }
    }

    companion object {
        /** Windows' "this machine, not a domain" prefix — `.\user`. */
        private const val LOCAL_MACHINE = "."

        /**
         * Splits an ERP login into (domain, bareUsername). Accepts `DOMAIN\user` and
         * `user@domain`. The domain comes back empty when none was given, and also for
         * `.\user` — Windows' "this machine, not a domain" form is a statement of *no*
         * domain, not a domain literally named ".".
         */
        fun parseDomainUser(raw: String): Pair<String, String> {
            val trimmed = raw.trim()
            return when {
                '\\' in trimmed -> {
                    val domain = trimmed.substringBefore('\\')
                    (if (domain == LOCAL_MACHINE) "" else domain) to trimmed.substringAfter('\\')
                }
                '@'  in trimmed -> trimmed.substringAfter('@') to trimmed.substringBefore('@')
                else            -> "" to trimmed
            }
        }
    }
}
