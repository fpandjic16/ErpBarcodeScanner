package com.erpbarcode.scanner.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The document types as the database and the ERP see them.
 *
 * A type's key is written into every row the type owns, so the keys below are a record and not a
 * choice: a changed key leaves the rows stored under the old one without a type, and a reused key
 * hands one type's rows to another.
 */
class DocumentTypeTest {

    @Test
    fun `the stored keys are what they have always been`() {
        assertEquals(
            listOf(
                "WHSE_SHIP", "WHSE_RCPT", "RT_SHIP", "RT_RCPT", "TRANSPORT",
                "CPL_SHIP", "CPL_RCPT", "VCPL_SHIP", "VCPL_RCPT", "INVENTORY",
            ),
            DocumentType.entries.map { it.key },
        )
    }

    @Test
    fun `no two types share a key, and the retired complaint key is not back`() {
        val keys = DocumentType.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertFalse("COMPLAINT" in keys)
    }

    @Test
    fun `a vendor complaint behaves as the company's own complaint does`() {
        val pairs = listOf(
            DocumentType.COMPLAINT_SHIPMENT to DocumentType.VENDOR_COMPLAINT_SHIPMENT,
            DocumentType.COMPLAINT_RECEIPT to DocumentType.VENDOR_COMPLAINT_RECEIPT,
        )
        for ((own, vendor) in pairs) {
            assertEquals(own.defaultFilterMode, vendor.defaultFilterMode)
            assertEquals(DocTypeFilterMode.RESPONSIBILITY_CENTER, vendor.defaultFilterMode)
            // A code of its own, so there is no retail flag to send or to filter by.
            assertNull(vendor.retailLocation)
            assertFalse(vendor.isStockCount)
        }
    }

    @Test
    fun `only the four types that share a code with another carry the retail flag`() {
        assertEquals(
            setOf(
                DocumentType.WAREHOUSE_SHIPMENT, DocumentType.WAREHOUSE_RECEIPT,
                DocumentType.RETAIL_SHIPMENT, DocumentType.RETAIL_RECEIPT,
            ),
            DocumentType.entries.filter { it.retailLocation != null }.toSet(),
        )
    }
}
