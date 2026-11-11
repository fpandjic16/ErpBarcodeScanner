package com.erpbarcode.scanner.data.auth

import com.erpbarcode.scanner.data.db.SharedDatabaseProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ServerIdentityTest {

    private fun config(server: String, company: String? = "Acme Ltd", service: String = "BarcodeAppEntries") =
        ExtSystemConfig(
            serverBaseUrl = server,
            documentLinesUrl = if (company == null) "" else
                "http://erp:7048/Live/ODataV4/Company('${company.replace(" ", "%20")}')/$service",
        )

    @Test
    fun `no server, no identity`() {
        assertEquals("", config("").serverIdentity)
        assertEquals("", config("   ").serverIdentity)
    }

    @Test
    fun `slashes and letter case do not make another installation`() {
        val a = config("http://erp:7048/Live/ODataV4/")
        val b = config("HTTP://ERP:7048/live/odatav4")
        assertEquals(a.serverIdentity, b.serverIdentity)
    }

    @Test
    fun `another company is another installation`() {
        val server = "http://erp:7048/Live/ODataV4/"
        assertNotEquals(config(server, "Acme Ltd").serverIdentity, config(server, "Acme Retail").serverIdentity)
    }

    @Test
    fun `another server is another installation, test and production included`() {
        assertNotEquals(
            config("http://erp:7048/Live/ODataV4/").serverIdentity,
            config("http://erp:8048/Test/ODataV4/").serverIdentity,
        )
    }

    @Test
    fun `renaming a service does not change the installation`() {
        val server = "http://erp:7048/Live/ODataV4/"
        assertEquals(
            config(server, service = "BarcodeAppEntries").serverIdentity,
            config(server, service = "DocumentLines").serverIdentity,
        )
    }

    @Test
    fun `the label is the company, or the address when the URL names none`() {
        assertEquals("Acme Ltd", config("http://erp:7048/Live/ODataV4/").serverLabel)
        assertEquals("erp:7048/Live/ODataV4", config("http://erp:7048/Live/ODataV4/", company = null).serverLabel)
    }

    @Test
    fun `one operator on two installations has two keys and two database files`() {
        val live = config("http://erp:7048/Live/ODataV4/").serverIdentity
        val test = config("http://erp:8048/Test/ODataV4/").serverIdentity
        val onLive = UserProfile("ANA", "ANA", serverKey = live)
        val onTest = UserProfile("ANA", "ANA", serverKey = test)
        assertNotEquals(onLive.key, onTest.key)
        assertNotEquals(UserProfileStore.databaseName(onLive.key), UserProfileStore.databaseName(onTest.key))
        assertEquals(UserProfileStore.databaseName(onLive.key), UserProfileStore.databaseName(UserProfile("ANA", "Ana", serverKey = live).key))
    }

    @Test
    fun `a name cannot be made to look like another operator on another installation`() {
        // Same characters end to end, split differently: the id's length keeps them apart.
        assertNotEquals(
            UserProfileStore.profileKey("ANA", "Xhttp://erp|acme"),
            UserProfileStore.profileKey("ANAX", "http://erp|acme"),
        )
    }

    @Test
    fun `reference data is one file per installation`() {
        val live = config("http://erp:7048/Live/ODataV4/").serverIdentity
        val test = config("http://erp:8048/Test/ODataV4/").serverIdentity
        assertNotEquals(SharedDatabaseProvider.fileName(live), SharedDatabaseProvider.fileName(test))
        assertEquals(SharedDatabaseProvider.fileName(live), SharedDatabaseProvider.fileName(live))
    }
}
