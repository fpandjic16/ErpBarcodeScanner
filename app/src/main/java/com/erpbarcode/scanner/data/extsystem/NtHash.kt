package com.erpbarcode.scanner.data.extsystem

import java.security.MessageDigest

/**
 * The NT hash of a password: MD4 over its UTF-16LE bytes, as 32 lower-case hex digits.
 *
 * It is what NTLM signs with. The password itself is never needed for a request to the ERP, only
 * this, so this is what the device keeps for an operator and the password is dropped as soon as it
 * has been turned into it (3.6.92). What that buys is narrow and worth having: whoever reads the
 * device's storage gets something that signs in to the ERP over NTLM as that operator, exactly as
 * the password would, but not the password, which the same person very likely types into their
 * mail and every other system of the directory.
 *
 * Free of Android, so it is tested with the reference values of the protocol's own specification.
 */
fun ntHashOf(password: String): String = md4(password.toByteArray(Charsets.UTF_16LE)).toHex()

/**
 * Whether two NT hashes are the same, in a time that does not depend on where they differ.
 * Anything that is not 32 hex digits is equal to nothing, itself included.
 */
fun sameNtHash(a: String, b: String): Boolean {
    val x = a.hexToNtHashOrNull() ?: return false
    val y = b.hexToNtHashOrNull() ?: return false
    return MessageDigest.isEqual(x, y)
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/** The sixteen bytes of an NT hash written as 32 hex digits, or null for anything else. */
internal fun String.hexToNtHashOrNull(): ByteArray? {
    if (length != 32 || any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    return ByteArray(16) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}

// ── Inline MD4 (RFC 1320) ─────────────────────────────────────────────────────
// Required for the NT hash. Android's MessageDigest does not expose MD4.

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
