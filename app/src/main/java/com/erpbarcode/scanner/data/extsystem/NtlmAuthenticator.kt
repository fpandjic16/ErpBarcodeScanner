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
 */
class NtlmAuthenticator(
    private val domain: String,
    private val username: String,
    private val password: String,
) : Authenticator {

    @Volatile var phaseReached: Int = 0
        internal set

    fun resetPhase() { phaseReached = 0 }

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

        // NT Hash = MD4(UTF-16LE(password))
        val ntHash    = md4(password.toByteArray(Charsets.UTF_16LE))
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

// ── Inline MD4 (RFC 1320) ─────────────────────────────────────────────────────
// Required for NT Hash computation. Android's MessageDigest does not expose MD4.

internal fun md4(input: ByteArray): ByteArray {
    fun Int.rol(n: Int) = (this shl n) or (this ushr (32 - n))

    val len    = input.size
    val padLen = if ((len and 63) < 56) 56 - (len and 63) else 120 - (len and 63)
    val msg    = ByteArray(len + padLen + 8)
    System.arraycopy(input, 0, msg, 0, len)
    msg[len] = 0x80.toByte()
    val bitLen = len.toLong() * 8
    for (i in 0..7) msg[msg.size - 8 + i] = (bitLen ushr (i * 8)).toByte()

    // Initial hash values — hex literals > Int.MAX_VALUE need L suffix
    var a0 = 0x67452301
    var b0 = 0xEFCDAB89L.toInt()   // -271733879
    var c0 = 0x98BADCFEL.toInt()   // -1732584194
    var d0 = 0x10325476

    var pos = 0
    while (pos < msg.size) {
        val x = IntArray(16) { i ->
            (msg[pos + i*4].toInt() and 0xFF)          or
            ((msg[pos + i*4+1].toInt() and 0xFF) shl 8)  or
            ((msg[pos + i*4+2].toInt() and 0xFF) shl 16) or
            ((msg[pos + i*4+3].toInt() and 0xFF) shl 24)
        }
        var a = a0; var b = b0; var c = c0; var d = d0

        // Round 1 — F(b,c,d) = (b & c) | (~b & d)
        fun r1(a:Int,b:Int,c:Int,d:Int,k:Int,s:Int) = (a + ((b and c) or (b.inv() and d)) + x[k]).rol(s)
        a=r1(a,b,c,d,0,3);  d=r1(d,a,b,c,1,7);  c=r1(c,d,a,b,2,11);  b=r1(b,c,d,a,3,19)
        a=r1(a,b,c,d,4,3);  d=r1(d,a,b,c,5,7);  c=r1(c,d,a,b,6,11);  b=r1(b,c,d,a,7,19)
        a=r1(a,b,c,d,8,3);  d=r1(d,a,b,c,9,7);  c=r1(c,d,a,b,10,11); b=r1(b,c,d,a,11,19)
        a=r1(a,b,c,d,12,3); d=r1(d,a,b,c,13,7); c=r1(c,d,a,b,14,11); b=r1(b,c,d,a,15,19)

        // Round 2 — G(b,c,d) = (b & c) | (b & d) | (c & d);  const 0x5A827999
        fun r2(a:Int,b:Int,c:Int,d:Int,k:Int,s:Int) = (a + ((b and c) or (b and d) or (c and d)) + x[k] + 0x5A827999).rol(s)
        a=r2(a,b,c,d,0,3);  d=r2(d,a,b,c,4,5);  c=r2(c,d,a,b,8,9);  b=r2(b,c,d,a,12,13)
        a=r2(a,b,c,d,1,3);  d=r2(d,a,b,c,5,5);  c=r2(c,d,a,b,9,9);  b=r2(b,c,d,a,13,13)
        a=r2(a,b,c,d,2,3);  d=r2(d,a,b,c,6,5);  c=r2(c,d,a,b,10,9); b=r2(b,c,d,a,14,13)
        a=r2(a,b,c,d,3,3);  d=r2(d,a,b,c,7,5);  c=r2(c,d,a,b,11,9); b=r2(b,c,d,a,15,13)

        // Round 3 — H(b,c,d) = b ^ c ^ d;  const 0x6ED9EBA1
        fun r3(a:Int,b:Int,c:Int,d:Int,k:Int,s:Int) = (a + (b xor c xor d) + x[k] + 0x6ED9EBA1).rol(s)
        a=r3(a,b,c,d,0,3);  d=r3(d,a,b,c,8,9);  c=r3(c,d,a,b,4,11); b=r3(b,c,d,a,12,15)
        a=r3(a,b,c,d,2,3);  d=r3(d,a,b,c,10,9); c=r3(c,d,a,b,6,11); b=r3(b,c,d,a,14,15)
        a=r3(a,b,c,d,1,3);  d=r3(d,a,b,c,9,9);  c=r3(c,d,a,b,5,11); b=r3(b,c,d,a,13,15)
        a=r3(a,b,c,d,3,3);  d=r3(d,a,b,c,11,9); c=r3(c,d,a,b,7,11); b=r3(b,c,d,a,15,15)

        a0 += a; b0 += b; c0 += c; d0 += d
        pos += 64
    }

    return ByteArray(16).also { out ->
        arrayOf(a0, b0, c0, d0).forEachIndexed { i, v ->
            out[i*4]   = (v and 0xFF).toByte()
            out[i*4+1] = ((v ushr 8)  and 0xFF).toByte()
            out[i*4+2] = ((v ushr 16) and 0xFF).toByte()
            out[i*4+3] = ((v ushr 24) and 0xFF).toByte()
        }
    }
}
