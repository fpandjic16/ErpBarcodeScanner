package com.erpbarcode.scanner.data.auth

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import timber.log.Timber
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Credentials handed to the sign-in screen by a scanned QR code, so a handheld can be signed in
 * without typing a Windows password on a keypad.
 *
 * The code is **encrypted**, so a QR reader — or a photo of the code — shows nothing but base64.
 * Wire format, base64 over the whole thing:
 *
 *     [ 12-byte IV ][ AES-256-GCM ciphertext + 16-byte tag ]
 *
 * and the plaintext inside is the JSON below:
 *
 *     {"username":"DOMAIN\\user","password":"…"}
 *
 * JSON rather than a delimited string because a Windows password may contain the very characters
 * a delimiter would have to reserve (`|`, `:`, `,`); splitting on one would truncate the password
 * into something that merely looks wrong at sign-in. The domain rides inside `username` exactly
 * as if typed — see `ExtSystemODataClient.buildClient`.
 *
 * GCM authenticates as well as encrypts, so a code made with a different key fails to decrypt
 * rather than yielding garbage credentials that would then fail confusingly against the ERP.
 *
 * **What this does and doesn't protect.** The key travels in `ExtSystemConfig.loginQrKey`, which
 * means it sits both in the bundled defaults inside the APK and in the device's settings. So this
 * stops someone photographing or glancing at a printed code — the realistic exposure — but not
 * someone who unpacks the app or reads a provisioned device. Treat a printed code as sensitive
 * regardless. Rotating the key is an imported configuration, no new build, and it invalidates
 * every code issued under the old one.
 */
private data class LoginQrDto(
    @SerializedName("username") val username: String? = null,
    @SerializedName("password") val password: String? = null,
)

private val qrGson = Gson()

private const val IV_BYTES = 12
private const val GCM_TAG_BITS = 128
private const val AES_KEY_BYTES = 32

/**
 * Decrypts a scanned QR payload into credentials, or null when the code isn't one this build can
 * read — a plaintext or foreign code, a code encrypted under a different key, or the common case
 * of the camera catching an ordinary product barcode. Callers surface that as a message rather
 * than ignoring it; they can't distinguish the causes, and deliberately aren't told which, since
 * that distinction is only useful to someone probing the codes.
 *
 * The username is trimmed the way typed input is; the password deliberately isn't, since leading
 * or trailing whitespace can be a genuine part of it.
 */
fun parseLoginQr(raw: String, keyB64: String): ExtSystemCredentials? {
    val key = decodeKey(keyB64) ?: return null
    return runCatching {
        val blob = Base64.getDecoder().decode(raw.trim())
        if (blob.size <= IV_BYTES) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, blob, 0, IV_BYTES),
            )
        }
        val json = String(cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES), Charsets.UTF_8)
        val dto = qrGson.fromJson(json, LoginQrDto::class.java) ?: return null
        val user = dto.username?.trim().orEmpty()
        val pass = dto.password.orEmpty()
        if (user.isBlank() || pass.isBlank()) null else ExtSystemCredentials(user, pass)
    }.getOrElse {
        // Nothing about the payload is logged — a failure here is either a non-login barcode or
        // a code this build can't read, and neither is worth putting a ciphertext in logcat for.
        Timber.d("Login QR could not be read: ${it.javaClass.simpleName}")
        null
    }
}

/** The configured key, or null (with a reason logged) when this install hasn't got a usable one. */
private fun decodeKey(keyB64: String): ByteArray? {
    if (keyB64.isBlank()) {
        Timber.w("No loginQrKey in the external system configuration — QR sign-in is inactive.")
        return null
    }
    val bytes = runCatching { Base64.getDecoder().decode(keyB64) }.getOrNull()
    if (bytes == null || bytes.size != AES_KEY_BYTES) {
        Timber.w("loginQrKey is not base64 for %d bytes — QR sign-in is inactive.", AES_KEY_BYTES)
        return null
    }
    return bytes
}
