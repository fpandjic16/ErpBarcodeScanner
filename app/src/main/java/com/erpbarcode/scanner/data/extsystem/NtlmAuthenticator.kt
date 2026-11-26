package com.erpbarcode.scanner.data.extsystem

import android.util.Base64
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * OkHttp Authenticator implementing NTLMv2 for OData.
 * No external NTLM library — uses Android standard crypto + inline MD4.
 *
 * Security: NTLMv2 only; client challenge is cryptographically random;
 * credentials are never stored in this class.
 *
 * It is given the **NT hash** of the password ([ntHashOf]), sixteen bytes, not the password: the
 * hash is all the protocol signs with, and the password is turned into it where it is typed or
 * scanned and kept nowhere (3.6.92).
 */
class NtlmAuthenticator(
    private val domain: String,
    private val username: String,
    private val ntHash: ByteArray,
) : Authenticator {

    @Volatile var phaseReached: Int = 0
        internal set

    fun resetPhase() { phaseReached = 0 }

    /**
     * Whether the Type 3 message, the one that carries the proof of the password, has gone out
     * since [resetPhase]. An answer that follows it is the ERP's word on the account; an answer
     * without it says nothing about the account either way.
     */
    val sentProof: Boolean get() = phaseReached == 2

    override fun authenticate(route: Route?, response: Response): Request? {
        val wwwAuth = response.header("WWW-Authenticate") ?: return null
        Timber.d("NTLM: WWW-Authenticate = $wwwAuth")
        if (!wwwAuth.contains("NTLM", ignoreCase = true)) return null

        val trimmed = wwwAuth.trim()
        return if (trimmed.equals("NTLM", ignoreCase = true) ||
                   !trimmed.startsWith("NTLM ", ignoreCase = true)) {
            // Phase 1 — send Type 1 Negotiate.
            // If the request already carries an NTLM token the full handshake already
            // ran and the server is restarting negotiation → credentials wrong, stop.
            if (response.request.header("Authorization")?.startsWith("NTLM ") == true) {
                Timber.w("NTLM: full handshake already ran, stopping (bad credentials?)")
                return null
            }
            phaseReached = 1
            Timber.d("NTLM: → Phase 1 (Type1 Negotiate)")
            response.request.newBuilder()
                .header("Authorization", "NTLM ${type1()}")
                .build()
        } else {
            // Phase 2 — respond with Type 3 Authenticate
            phaseReached = 2
            Timber.d("NTLM: → Phase 2 (Type3 Authenticate)")
            val b64 = trimmed.substring(5).trim()
            runCatching { type3(Base64.decode(b64, Base64.DEFAULT)) }
                .onFailure { Timber.e(it, "NTLM: Type3 construction failed") }
                .getOrNull()
                ?.let { response.request.newBuilder().header("Authorization", "NTLM $it").build() }
        }
    }

    // ── Type 1: Negotiate ─────────────────────────────────────────────────────

    private fun type1(): String {
        val buf = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(SIG)
        buf.putInt(1)        // MessageType
        buf.putInt(FLAGS)
        // DomainNameFields  (empty, offset=32)
        buf.putShort(0); buf.putShort(0); buf.putInt(32)
        // WorkstationFields (empty, offset=32)
        buf.putShort(0); buf.putShort(0); buf.putInt(32)
        return Base64.encodeToString(buf.array(), Base64.NO_WRAP)
    }

    // ── Type 3: Authenticate ─────────────────────────────────────────────────

    private fun type3(challenge: ByteArray): String {
        val serverChallenge = challenge.copyOfRange(24, 32)
        val clientChallenge = ByteArray(8).also { SecureRandom().nextBytes(it) }

        // NT hash = MD4(UTF-16LE(password)), worked out by whoever made this authenticator.
        // NTLMv2 key = HMAC-MD5(ntHash, UTF-16LE(uppercase(user + domain)))
        val ntlmv2Key = hmacMd5(ntHash,
            (username.uppercase() + domain.uppercase()).toByteArray(Charsets.UTF_16LE))

        // Target info from Type2 offset 40
        val tiLen = le16(challenge, 40)
        val tiOff = le32(challenge, 44)
        val targetInfo = if (tiLen > 0 && tiOff + tiLen <= challenge.size)
            challenge.copyOfRange(tiOff, tiOff + tiLen) else ByteArray(0)

        // Windows FILETIME: 100-ns intervals since 1601-01-01
        val ts = System.currentTimeMillis() * 10_000L + 116_444_736_000_000_000L

        val blob = ByteBuffer.allocate(32 + targetInfo.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0x00000101)   // BlobSignature
            putInt(0)            // Reserved
            putLong(ts)          // Timestamp
            put(clientChallenge) // ChallengeFromClient
            putInt(0)            // Reserved2
            put(targetInfo)
        }.array()

        val ntResponse = hmacMd5(ntlmv2Key, serverChallenge + blob) + blob
        // LMv2 response: HMAC-MD5(ntlmv2Key, serverChallenge||clientChallenge) + clientChallenge
        val lmResponse = hmacMd5(ntlmv2Key, serverChallenge + clientChallenge) + clientChallenge

        val domBytes = domain.toByteArray(Charsets.UTF_16LE)
        val usrBytes = username.toByteArray(Charsets.UTF_16LE)

        // Fixed header = 64 bytes (Type3 base without Version field)
        val domOff = 64
        val usrOff = domOff + domBytes.size
        val wsOff  = usrOff + usrBytes.size
        val lmOff  = wsOff
        val ntOff  = lmOff + lmResponse.size
        val skOff  = ntOff + ntResponse.size

        val buf = ByteBuffer.allocate(skOff).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(SIG)
        buf.putInt(3)                                // MessageType
        secBuf(buf, lmResponse.size, lmOff)          // LmChallengeResponseFields
        secBuf(buf, ntResponse.size, ntOff)          // NtChallengeResponseFields
        secBuf(buf, domBytes.size,   domOff)         // DomainNameFields
        secBuf(buf, usrBytes.size,   usrOff)         // UserNameFields
        secBuf(buf, 0,               wsOff)          // WorkstationFields (empty)
        secBuf(buf, 0,               skOff)          // EncryptedRandomSessionKeyFields (empty)
        buf.putInt(FLAGS)
        buf.put(domBytes)
        buf.put(usrBytes)
        buf.put(lmResponse)
        buf.put(ntResponse)

        return Base64.encodeToString(buf.array(), Base64.NO_WRAP)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun secBuf(buf: ByteBuffer, len: Int, off: Int) {
        buf.putShort(len.toShort()); buf.putShort(len.toShort()); buf.putInt(off)
    }

    private fun le16(b: ByteArray, i: Int) =
        ((b[i + 1].toInt() and 0xFF) shl 8) or (b[i].toInt() and 0xFF)

    private fun le32(b: ByteArray, i: Int) =
        ((b[i + 3].toInt() and 0xFF) shl 24) or ((b[i + 2].toInt() and 0xFF) shl 16) or
        ((b[i + 1].toInt() and 0xFF) shl 8)  or  (b[i].toInt() and 0xFF)

    private fun hmacMd5(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacMD5").also { it.init(SecretKeySpec(key, "HmacMD5")) }.doFinal(data)

    companion object {
        // "NTLMSSP\0" in ASCII
        private val SIG = byteArrayOf(0x4E,0x54,0x4C,0x4D,0x53,0x53,0x50,0x00)
        // Flags: 56-bit | TARGET_INFO | NTLM | REQUEST_TARGET | UNICODE
        private val FLAGS = 0x80800205L.toInt()
    }
}
