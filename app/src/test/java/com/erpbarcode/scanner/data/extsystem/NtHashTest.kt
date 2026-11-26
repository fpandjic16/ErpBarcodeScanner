package com.erpbarcode.scanner.data.extsystem

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The NT hash is what the device keeps instead of a password and what every request to the ERP is
 * signed with, so it has to be the protocol's hash exactly: one wrong bit and nobody signs in.
 *
 * The expected values are not this code's own output. The first is the reference value of the
 * NTLM specification (MS-NLMP 4.2.2.1.2, the NT hash of "Password"); the next two are the hashes
 * every published table lists for an empty password and for "password"; the last was worked out
 * with a separate MD4 written from RFC 1320.
 */
class NtHashTest {

    @Test
    fun `the hash of the specification password is the specification hash`() {
        assertEquals("a4f49c406510bdcab6824ee7c30fd852", ntHashOf("Password"))
    }

    @Test
    fun `an empty password and a common one give the hashes every table lists`() {
        assertEquals("31d6cfe0d16ae931b73c59d7e0c089c0", ntHashOf(""))
        assertEquals("8846f7eaee8fb117ad06bdd830b7586c", ntHashOf("password"))
    }

    @Test
    fun `letters outside ASCII are hashed as two bytes each`() {
        assertEquals("942f59dc7da4aa9477d19d4259143698", ntHashOf("Lozinka-šđčćž"))
    }

    @Test
    fun `case and surrounding spaces are part of a password`() {
        assertNotEquals(ntHashOf("Password"), ntHashOf("password"))
        assertNotEquals(ntHashOf("Password"), ntHashOf("Password "))
        assertNotEquals(ntHashOf("Password"), ntHashOf(" Password"))
    }

    @Test
    fun `a hash is the same as itself, in whichever case its digits are written`() {
        val hash = ntHashOf("Password")
        assertTrue(sameNtHash(hash, hash))
        assertTrue(sameNtHash(hash, hash.uppercase()))
    }

    @Test
    fun `two passwords do not have the same hash`() {
        assertFalse(sameNtHash(ntHashOf("Password"), ntHashOf("password")))
    }

    @Test
    fun `what is not a hash is the same as nothing, itself included`() {
        // Must fail to pass: a damaged or empty stored value may never count as a match.
        assertFalse(sameNtHash("", ""))
        assertFalse(sameNtHash("abc", "abc"))
        val tooShort = ntHashOf("Password").dropLast(2)
        assertFalse(sameNtHash(tooShort, tooShort))
        val notHex = "g" + ntHashOf("Password").drop(1)
        assertFalse(sameNtHash(notHex, notHex))
        assertFalse(sameNtHash(ntHashOf("Password"), ""))
    }

    @Test
    fun `the sixteen bytes come back from the thirty-two digits`() {
        val bytes = "a4f49c406510bdcab6824ee7c30fd852".hexToNtHashOrNull()!!
        assertEquals(16, bytes.size)
        assertEquals(0xa4.toByte(), bytes.first())
        assertEquals(0x52.toByte(), bytes.last())
        assertEquals("a4f49c406510bdcab6824ee7c30fd852", bytes.toHex())
        assertArrayEquals(bytes, "A4F49C406510BDCAB6824EE7C30FD852".hexToNtHashOrNull())
        assertNull("a4f4".hexToNtHashOrNull())
    }
}
