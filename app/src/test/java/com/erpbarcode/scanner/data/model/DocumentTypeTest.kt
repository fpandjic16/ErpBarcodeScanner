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
    fun `a vendor complaint is a complaint with a code of its own and no scope`() {
        val pairs = listOf(
            DocumentType.COMPLAINT_SHIPMENT to DocumentType.VENDOR_COMPLAINT_SHIPMENT,
            DocumentType.COMPLAINT_RECEIPT to DocumentType.VENDOR_COMPLAINT_RECEIPT,
        )
        for ((own, vendor) in pairs) {
            // The company's own complaints go by responsibility centre; a vendor's go by nothing.
            assertEquals(DocTypeFilterMode.RESPONSIBILITY_CENTER, own.defaultFilterMode)
            assertEquals(DocTypeFilterMode.NONE, vendor.defaultFilterMode)
            // A code of its own, so there is no retail flag to send or to filter by.
            assertNull(vendor.retailLocation)
            assertFalse(vendor.isStockCount)
        }
    }

    @Test
    fun `only the two vendor complaint types have no scope`() {
        assertEquals(
            setOf(DocumentType.VENDOR_COMPLAINT_SHIPMENT, DocumentType.VENDOR_COMPLAINT_RECEIPT),
            DocumentType.entries.filter { it.defaultFilterMode == DocTypeFilterMode.NONE }.toSet(),
        )
    }

    @Test
    fun `a type with no scope keeps none, whatever the settings hold for it`() {
        // Devices configured while these types were scoped by centre still hold that.
        val vendor = DocumentType.VENDOR_COMPLAINT_SHIPMENT
        for (stored in DocTypeFilterMode.entries) {
            assertEquals(DocTypeFilterMode.NONE, vendor.filterModeIn(mapOf(vendor.key to stored)))
        }
        assertEquals(DocTypeFilterMode.NONE, vendor.filterModeIn(emptyMap()))
    }

    @Test
    fun `a type with a scope cannot be set to none`() {
        // Must fail to pass: one word in a configuration file may not show an operator every
        // location's documents.
        for (type in DocumentType.entries.filter { it.defaultFilterMode != DocTypeFilterMode.NONE }) {
            assertEquals(
                "${type.name} set to NONE",
                type.defaultFilterMode,
                type.filterModeIn(mapOf(type.key to DocTypeFilterMode.NONE)),
            )
        }
    }

    @Test
    fun `a type with a scope takes the one the settings give it, and its own without`() {
        val shipment = DocumentType.WAREHOUSE_SHIPMENT
        assertEquals(DocTypeFilterMode.LOCATION, shipment.filterModeIn(emptyMap()))
        assertEquals(
            DocTypeFilterMode.RESPONSIBILITY_CENTER,
            shipment.filterModeIn(mapOf(shipment.key to DocTypeFilterMode.RESPONSIBILITY_CENTER)),
        )
        // A setting made for another type says nothing about this one.
        assertEquals(
            DocTypeFilterMode.LOCATION,
            shipment.filterModeIn(mapOf(DocumentType.COMPLAINT_SHIPMENT.key to DocTypeFilterMode.RESPONSIBILITY_CENTER)),
        )
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
