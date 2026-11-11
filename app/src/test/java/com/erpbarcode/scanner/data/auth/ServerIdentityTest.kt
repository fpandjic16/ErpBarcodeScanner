package com.erpbarcode.scanner.data.auth

import com.erpbarcode.scanner.data.db.SharedDatabaseProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ServerIdentityTest {

    private fun odata(server: String, company: String, service: String) =
        "${server.trimEnd('/')}/Company('${company.replace(" ", "%20")}')/$service"

    /** A consistent configuration: every endpoint on [server], addressing [company]. */
    private fun config(
        server: String,
        company: String = "Acme Ltd",
        linesService: String = "BarcodeAppEntries",
        upload: String? = null,
        soap: String = "",
    ) = ExtSystemConfig(
        serverBaseUrl = server,
        documentLinesUrl = odata(server, company, linesService),
        recordingSyncUrl = upload ?: odata(server, company, "BarcodeAppRecordings"),
        locationsUrl = odata(server, company, "LocationList"),
        inventoryBatchesUrl = odata(server, company, "BarcodeInventoryBatches"),
        documentUploadUrl = soap,
    )

    private val live = "http://erp:7048/Live/ODataV4/"

    @Test
    fun `no server, no identity`() {
        assertEquals("", ExtSystemConfig().serverIdentity)
        assertEquals("", ExtSystemConfig(serverBaseUrl = "   ").serverIdentity)
    }

    @Test
    fun `slashes and letter case do not make another installation`() {
        assertEquals(config(live).serverIdentity, config("HTTP://ERP:7048/live/odatav4").serverIdentity)
    }

    @Test
    fun `another company is another installation`() {
        assertNotEquals(config(live, "Acme Ltd").serverIdentity, config(live, "Acme Retail").serverIdentity)
    }

    @Test
    fun `another server is another installation, test and production included`() {
        assertNotEquals(config(live).serverIdentity, config("http://erp:8048/Test/ODataV4/").serverIdentity)
    }

    @Test
    fun `renaming a service does not change the installation`() {
        assertEquals(config(live).serverIdentity, config(live, linesService = "DocumentLines").serverIdentity)
    }

    @Test
    fun `an upload address for another company is another installation`() {
        val stray = config(live, upload = odata(live, "Acme Retail", "BarcodeAppRecordings"))
        assertNotEquals(config(live).serverIdentity, stray.serverIdentity)
    }

    @Test
    fun `an upload address on another host is another installation`() {
        val stray = config(live, upload = odata("http://elsewhere:7048/Live/ODataV4/", "Acme Ltd", "BarcodeAppRecordings"))
        assertNotEquals(config(live).serverIdentity, stray.serverIdentity)
    }

    @Test
    fun `the SOAP service of the same instance and company is the same installation`() {
        val withSoap = config(live, soap = "http://erp:7047/Live/WS/Acme%20Ltd/Codeunit/BarcodeAppUpload")
        assertEquals(config(live).serverIdentity, withSoap.serverIdentity)
        val otherCompany = config(live, soap = "http://erp:7047/Live/WS/Acme%20Retail/Codeunit/BarcodeAppUpload")
        assertNotEquals(config(live).serverIdentity, otherCompany.serverIdentity)
    }

    @Test
    fun `dot segments cannot hide another instance`() {
        val stray = config(live, upload = "http://erp:7048/Live/ODataV4/../../Other/ODataV4/Company('Acme%20Ltd')/BarcodeAppRecordings")
        assertNotEquals(config(live).serverIdentity, stray.serverIdentity)
    }

    @Test
    fun `an address naming two companies matches no installation but its own`() {
        val stray = config(live, upload = "http://erp:7048/Live/ODataV4/Company('Acme%20Ltd')/Company('Acme%20Retail')/X")
        assertNotEquals(config(live).serverIdentity, stray.serverIdentity)
    }

    @Test
    fun `the label is the company, or the address when the URL names none`() {
        assertEquals("Acme Ltd", config(live).serverLabel)
        assertEquals("erp:7048/Live/ODataV4", ExtSystemConfig(serverBaseUrl = live).serverLabel)
    }

    @Test
    fun `one operator on two installations has two keys and two database files`() {
        val onLive = UserProfile("ANA", "ANA", serverKey = config(live).serverIdentity)
        val onTest = UserProfile("ANA", "ANA", serverKey = config("http://erp:8048/Test/ODataV4/").serverIdentity)
        assertNotEquals(onLive.key, onTest.key)
        assertNotEquals(UserProfileStore.databaseName(onLive.key), UserProfileStore.databaseName(onTest.key))
        assertEquals(
            UserProfileStore.databaseName(onLive.key),
            UserProfileStore.databaseName(UserProfile("ANA", "Ana", serverKey = config(live).serverIdentity).key),
        )
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
        val a = config(live).serverIdentity
        val b = config("http://erp:8048/Test/ODataV4/").serverIdentity
        assertNotEquals(SharedDatabaseProvider.fileName(a), SharedDatabaseProvider.fileName(b))
        assertEquals(SharedDatabaseProvider.fileName(a), SharedDatabaseProvider.fileName(a))
    }
}
