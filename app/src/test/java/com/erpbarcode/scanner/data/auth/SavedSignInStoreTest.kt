package com.erpbarcode.scanner.data.auth

import com.erpbarcode.scanner.data.extsystem.ntHashOf
import com.erpbarcode.scanner.testing.DAY
import com.erpbarcode.scanner.testing.InMemorySecrets
import com.erpbarcode.scanner.testing.TestClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the device keeps of a sign-in, and how it loses it: `ExtSystemCredentialStore` over a map
 * instead of the Keystore-backed storage, under a clock set by hand.
 */
class SavedSignInStoreTest {

    private val clock = TestClock()
    private val secrets = InMemorySecrets()
    private val store = ExtSystemCredentialStore({ secrets }, clock.trusted)

    // Profile keys, as `UserProfileStore.profileKey` builds them: operator and installation.
    private val alice = "5:ALICEserver|company"
    private val bob = "3:BOBserver|company"
    private val hash = ntHashOf("a password")

    @Test
    fun `nothing is kept until a sign-in is saved`() {
        assertNull(store.saved(alice))
        assertNull(store.get(alice))
        assertFalse(store.isValid(alice))
        assertNull(store.saved(null))
        assertNull(store.get(null))
    }

    @Test
    fun `a saved sign-in is read back with when it was accepted and no failures`() {
        store.save(alice, "DOMAIN\\alice", hash)

        val saved = store.saved(alice)!!
        assertEquals("DOMAIN\\alice", saved.username)
        assertEquals(hash, saved.ntHash)
        assertEquals(clock.wall, saved.verifiedAt)
        assertEquals(0, saved.failures)
        assertEquals(ExtSystemCredentials("DOMAIN\\alice", hash), store.get(alice))
        assertTrue(store.isValid(alice))
    }

    @Test
    fun `four failures in a row keep the sign-in and the fifth removes it`() {
        store.save(alice, "alice", hash)

        repeat(4) { assertFalse("failure ${it + 1} must not remove it", store.recordRefused(alice)) }
        assertEquals(4, store.saved(alice)!!.failures)
        assertNotNull(store.get(alice))

        assertTrue(store.recordRefused(alice))
        assertNull(store.saved(alice))
        assertNull(store.get(alice))
        // Gone, not kept with a mark on it.
        assertEquals(0, secrets.size)
    }

    @Test
    fun `a failure does not move the time the ERP last accepted it`() {
        store.save(alice, "alice", hash)
        val acceptedAt = store.saved(alice)!!.verifiedAt

        clock.pass(2 * DAY)
        store.recordRefused(alice)

        assertEquals(acceptedAt, store.saved(alice)!!.verifiedAt)
    }

    @Test
    fun `an acceptance forgets the failures and is the new time`() {
        store.save(alice, "alice", hash)
        repeat(4) { store.recordRefused(alice) }

        clock.pass(2 * DAY)
        store.recordAccepted(alice)

        val saved = store.saved(alice)!!
        assertEquals(0, saved.failures)
        assertEquals(clock.wall, saved.verifiedAt)
        // The count really started over: four more do not remove it.
        repeat(4) { assertFalse(store.recordRefused(alice)) }
        assertNotNull(store.saved(alice))
    }

    @Test
    fun `saving again replaces the name, the hash, the time and the count`() {
        store.save(alice, "alice", hash)
        repeat(3) { store.recordRefused(alice) }
        clock.pass(DAY)
        val changed = ntHashOf("a changed password")

        store.save(alice, "alice@domain", changed)

        val saved = store.saved(alice)!!
        assertEquals("alice@domain", saved.username)
        assertEquals(changed, saved.ntHash)
        assertEquals(clock.wall, saved.verifiedAt)
        assertEquals(0, saved.failures)
    }

    @Test
    fun `one operator's failures and removal are not another's`() {
        store.save(alice, "alice", hash)
        store.save(bob, "bob", ntHashOf("another password"))

        repeat(5) { store.recordRefused(alice) }

        assertNull(store.saved(alice))
        assertEquals(0, store.saved(bob)!!.failures)

        store.clear(bob)
        assertNull(store.saved(bob))
    }

    @Test
    fun `nothing is noted for an operator nothing is saved for`() {
        assertFalse(store.recordRefused(alice))
        store.recordAccepted(alice)
        assertNull(store.saved(alice))
        assertEquals(0, secrets.size)
    }

    @Test
    fun `a stored value that cannot be read is as good as absent`() {
        // Must fail to pass: damage may never read as a sign-in.
        secrets.edit { putString("$alice.signin", "not json at all") }
        assertNull(store.saved(alice))

        secrets.edit { putString("$alice.signin", "{}") }
        assertNull(store.saved(alice))

        secrets.edit { putString("$alice.signin", "{\"u\":\"alice\"}") }
        assertNull("a name with no hash is not a sign-in", store.saved(alice))
    }

    @Test
    fun `opening the storage leaves what is saved`() {
        store.save(alice, "alice", hash)
        store.open()
        assertNotNull(store.saved(alice))
    }

    @Test
    fun `the hash is never in the text form of what is saved`() {
        store.save(alice, "alice", hash)
        assertFalse(store.saved(alice).toString().contains(hash))
        assertFalse(store.get(alice).toString().contains(hash))
    }
}
