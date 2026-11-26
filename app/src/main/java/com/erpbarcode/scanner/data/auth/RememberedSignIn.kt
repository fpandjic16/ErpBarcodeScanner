package com.erpbarcode.scanner.data.auth

import com.erpbarcode.scanner.data.extsystem.sameNtHash

/**
 * Whether an operator may be let in on a sign-in the ERP accepted earlier, without asking it now.
 *
 * Only [ALLOWED] lets anybody in; every other value says why not, and sends the sign-in to the ERP.
 */
enum class RememberedSignIn {
    ALLOWED,

    /** The configuration keeps no sign-in: `rememberSignInDays` is 0. */
    OFF,

    /** The key the saved sign-in is encrypted with is not held in the device's secure hardware. */
    UNPROTECTED_KEY,

    /** Nothing is saved for this operator on this installation. */
    NOT_SAVED,

    /** The ERP last accepted it longer ago than the configuration allows. */
    EXPIRED,

    /** The device's clock stands well before a time this app has already seen. */
    CLOCK_BEHIND,
}

/** The longest a sign-in may be remembered, whatever a configuration says. */
const val MAX_REMEMBER_SIGN_IN_DAYS = 30

/** What a configuration that does not say gets. */
const val DEFAULT_REMEMBER_SIGN_IN_DAYS = 5

/**
 * How many times in a row a saved sign-in may fail before it is removed from the device: refused
 * by the ERP on a transfer, or not matched by a sign-in code that the ERP did not then accept.
 */
const val MAX_SAVED_SIGN_IN_FAILURES = 5

private const val DAY_MS = 86_400_000L

/**
 * How far the device's clock may stand behind the trusted time before it is called wrong. A clock
 * corrected from the network steps back by seconds or minutes, and that must not cost anybody
 * their sign-in. [TrustedClock.deviceClockIsBehind] applies the same measure.
 */
internal const val CLOCK_TOLERANCE_MS = 3_600_000L

/**
 * The one rule for letting an operator in without the ERP.
 *
 * The time allowed runs from [verifiedAt], the last moment the ERP accepted this very sign-in, and
 * is measured on the trusted clock ([TrustedClock.now]), not on the device's, so turning the date
 * back does not stretch it. A device whose own clock stands well behind the trusted time is not
 * believed about anything: it lets nobody in until the ERP has been asked.
 *
 * Free of Android and of the stores, so every branch is tested as it stands.
 *
 * @param days `ExtSystemConfig.rememberSignInDays`; 0 switches remembering off.
 * @param keyInSecureHardware whether the storage key cannot be read out of the device.
 * @param verifiedAt when the ERP last accepted the saved sign-in, or null when nothing is saved.
 */
fun rememberedSignIn(
    days: Int,
    keyInSecureHardware: Boolean,
    verifiedAt: Long?,
    trustedNow: Long,
    wallNow: Long,
): RememberedSignIn = when {
    days <= 0 -> RememberedSignIn.OFF
    !keyInSecureHardware -> RememberedSignIn.UNPROTECTED_KEY
    verifiedAt == null -> RememberedSignIn.NOT_SAVED
    // Accepted "after now" can only be a store carried over to a device that has not seen that
    // time; it is not trusted either.
    verifiedAt > trustedNow -> RememberedSignIn.EXPIRED
    trustedNow - verifiedAt > days.coerceAtMost(MAX_REMEMBER_SIGN_IN_DAYS) * DAY_MS -> RememberedSignIn.EXPIRED
    wallNow < trustedNow - CLOCK_TOLERANCE_MS -> RememberedSignIn.CLOCK_BEHIND
    else -> RememberedSignIn.ALLOWED
}

/** What holding a scanned sign-in code against the saved sign-in comes to. */
enum class SignInCodeCheck {
    /** Remembered, and the code carries the password the ERP accepted: the operator is let in. */
    MATCHES,

    /**
     * Remembered, and the code carries another password. Not a refusal yet: the password may have
     * been changed, and then the code is right and what is saved is stale, so the ERP is asked.
     * If it does not accept either, this counts against the saved sign-in.
     */
    DIFFERS,

    /** Nothing to hold it against: the ERP is asked, as for any sign-in. */
    NOT_REMEMBERED,
}

/**
 * Holds the NT hash of a scanned sign-in code's password against the saved one.
 *
 * Only a sign-in the rule allows ([RememberedSignIn.ALLOWED]) is compared at all, so a right code
 * does not open a sign-in that has expired, that sits on a device whose key or clock is not
 * trusted, or that the configuration no longer remembers. A saved hash that is damaged matches
 * nothing (`sameNtHash`).
 */
fun checkSignInCode(remembered: RememberedSignIn, savedHash: String?, offeredHash: String): SignInCodeCheck =
    when {
        remembered != RememberedSignIn.ALLOWED || savedHash == null -> SignInCodeCheck.NOT_REMEMBERED
        sameNtHash(savedHash, offeredHash) -> SignInCodeCheck.MATCHES
        else -> SignInCodeCheck.DIFFERS
    }
