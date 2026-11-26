package com.erpbarcode.scanner.data.auth

import com.erpbarcode.scanner.data.extsystem.sameNtHash
import javax.inject.Inject
import javax.inject.Singleton

/** What the ERP said, during one transfer, about the sign-in the transfer ran under. */
enum class ErpSignInVerdict {
    /** Nothing either way: it never answered, or never asked who was calling. */
    NOT_SAID,

    /** It took the proof of the password and answered the request. */
    ACCEPTED,

    /** It took the proof of the password and turned the account away. */
    REFUSED,
}

/**
 * Notes, against the signed-in operator's saved sign-in, what the ERP said about it.
 *
 * Every transfer ends here (`ExtSystemODataClient.transfer`), so every one counts and none has to
 * remember to: an accepted transfer makes the saved sign-in good as of now, which is what keeps a
 * device that reaches the ERP every day from ever asking for a password it has no reason to doubt;
 * a refused one is held against it, and the fifth in a row removes it
 * ([ExtSystemCredentialStore.recordRefused]).
 *
 * Only a verdict on the sign-in that is saved **now** is noted. A transfer can outlast a change of
 * password: one that started under the old sign-in and is refused says nothing about the new one
 * typed since, and must not be counted against it.
 */
@Singleton
class SignInLedger @Inject constructor(
    private val credentialStore: ExtSystemCredentialStore,
    private val profileStore: UserProfileStore,
) {
    fun transferEnded(creds: ExtSystemCredentials, verdict: ErpSignInVerdict) {
        if (verdict == ErpSignInVerdict.NOT_SAID) return
        val key = profileStore.currentKey() ?: return
        val saved = credentialStore.saved(key) ?: return
        if (saved.username != creds.username || !sameNtHash(saved.ntHash, creds.ntHash)) return
        when (verdict) {
            ErpSignInVerdict.ACCEPTED -> credentialStore.recordAccepted(key)
            ErpSignInVerdict.REFUSED -> credentialStore.recordRefused(key)
            ErpSignInVerdict.NOT_SAID -> Unit
        }
    }
}
