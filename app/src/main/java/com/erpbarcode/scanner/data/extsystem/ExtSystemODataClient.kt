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
    private var httpClient: HttpClient? = null
    private var clientKey: Triple<String, String, String>? = null  // domain, rawUsername, pass
    private var ntlmAuth: NtlmAuthenticator? = null

    fun configure(config: ExtSystemConfig, creds: ExtSystemCredentials): ExtSystemODataClient {
        val key = Triple(config.domain, creds.username, creds.password)
        if (clientKey != key) {
            httpClient?.close()
            httpClient = buildClient(config.domain, creds.username, creds.password)
            clientKey = key
        }
        return this
    }

    private fun buildClient(configuredDomain: String, rawUsername: String, password: String): HttpClient {
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
        ntlmAuth = auth
        val okHttp = OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)  // bypass system proxy; the ERP is always on local LAN
            .authenticator(auth)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        return HttpClient(OkHttp) {
            engine { preconfigured = okHttp }
            expectSuccess = false
        }
    }

    suspend fun testConnection(baseUrl: String): ExtSystemResult<Unit> {
        val client = httpClient ?: return ExtSystemResult.Failure(text(R.string.ext_error_client_not_ready))
        ntlmAuth?.resetPhase()
        return runCatching {
            val response = client.get(baseUrl) { accept(ContentType.Application.Json) }
            val phase = ntlmAuth?.phaseReached ?: 0
            if (response.status.isSuccess()) {
                // A 2xx on its own proves nothing about the password. NtlmAuthenticator is an
                // okhttp3.Authenticator, so OkHttp only invokes it in answer to a 401 — an endpoint
                // that serves the request without challenging never authenticates anybody, and
                // every password "works". Treating that as a success would enrol an operator on
                // this device under a password the ERP has never seen.
                if (phase == 0) {
                    Timber.w("testConnection: HTTP ${response.status.value} with no NTLM challenge")
                    return@runCatching ExtSystemResult.Failure(text(R.string.ext_error_no_challenge_ok))
                }
                ExtSystemResult.Success(Unit)
            } else {
                Timber.w("testConnection: HTTP ${response.status.value}, NTLM phase=$phase")
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

    suspend fun uploadRecording(url: String, row: ErpRecordingRow): ExtSystemResult<Unit> {
        val client = httpClient ?: return ExtSystemResult.Failure(text(R.string.ext_error_client_not_ready))
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
    suspend fun uploadDocument(url: String, rows: List<ErpRecordingRow>): ExtSystemResult<Unit> {
        val client = httpClient ?: return ExtSystemResult.Failure(text(R.string.ext_error_client_not_ready))
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

    suspend fun downloadRaw(url: String): ExtSystemResult<String> {
        val client = httpClient ?: return ExtSystemResult.Failure(text(R.string.ext_error_client_not_ready))
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

    fun close() {
        httpClient?.close()
        httpClient = null
        clientKey = null
        ntlmAuth = null
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
