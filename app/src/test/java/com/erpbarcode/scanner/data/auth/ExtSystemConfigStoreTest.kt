package com.erpbarcode.scanner.data.auth

import com.erpbarcode.scanner.data.model.DocumentType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The code of a document type on a device that was configured before the type existed.
 *
 * The code field is read-only on the configuration screen, so such a device has no way to type the
 * code in. The store answers with the code the type has in every company until a configuration
 * says otherwise, and with nothing for a type it has no such answer for.
 */
@RunWith(RobolectricTestRunner::class)
class ExtSystemConfigStoreTest {

    private lateinit var store: ExtSystemConfigStore

    @Before
    fun setUp() {
        store = ExtSystemConfigStore(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `a device configured before a type existed still has its code`() {
        // Saved by a build that knew nothing of complaints, the company's own or a vendor's.
        store.save(
            ExtSystemConfig(
                documentTypeCodes = mapOf(
                    DocumentType.WAREHOUSE_SHIPMENT to "SHIPMENT",
                    DocumentType.TRANSPORT_SHEET to "TRANSPORT",
                    DocumentType.INVENTORY to "INVENTORY",
                ),
            ),
        )

        val config = store.get()

        assertEquals("SHIPMENT", config.docTypeCodeFor(DocumentType.WAREHOUSE_SHIPMENT))
        assertEquals("REKOTPR", config.docTypeCodeFor(DocumentType.COMPLAINT_SHIPMENT))
        assertEquals("REKZAPR", config.docTypeCodeFor(DocumentType.COMPLAINT_RECEIPT))
        assertEquals("REKOTPRDOB", config.docTypeCodeFor(DocumentType.VENDOR_COMPLAINT_SHIPMENT))
        assertEquals("REKZAPRDOB", config.docTypeCodeFor(DocumentType.VENDOR_COMPLAINT_RECEIPT))
        // A type that was there all along and was given no code has none: nothing is guessed.
        assertEquals("", config.docTypeCodeFor(DocumentType.WAREHOUSE_RECEIPT))
    }

    @Test
    fun `a code that a configuration has set is kept`() {
        store.save(
            ExtSystemConfig(documentTypeCodes = mapOf(DocumentType.VENDOR_COMPLAINT_SHIPMENT to "OTHER")),
        )

        assertEquals("OTHER", store.get().docTypeCodeFor(DocumentType.VENDOR_COMPLAINT_SHIPMENT))
    }
}
