package com.erpbarcode.scanner.data.model

import com.erpbarcode.scanner.testing.testDocument
import com.erpbarcode.scanner.testing.testLine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [Document.isUploadable]: what an Upload button may hand to the upload, on every list. */
class DocumentUploadableTest {

    private fun document(scanned: Double = 0.0, state: DocState = DocState.InProgress, orphans: Int = 0) =
        testDocument("D1", listOf(testLine("D1", 10000, "111", 2.0).copy(scanned = scanned))).copy(
            state = state,
            orphanedScans = List(orphans) { OrphanedScan("999", 1.0, "KOM", 20000, null, "tester") },
        )

    @Test
    fun `a document with something scanned can be uploaded`() {
        assertTrue(document(scanned = 1.0).isUploadable)
        assertTrue(document(scanned = 2.0, state = DocState.Completed).isUploadable)
    }

    @Test
    fun `a document with nothing scanned cannot`() {
        assertFalse(document(state = DocState.Downloaded).isUploadable)
    }

    @Test
    fun `scans needing review make a document uploadable, so the error is raised`() {
        assertTrue(document(orphans = 1).isUploadable)
    }

    @Test
    fun `a document whose upload failed can be sent again`() {
        assertTrue(document(scanned = 1.0, state = DocState.UploadFailed("HTTP 400")).isUploadable)
    }

    @Test
    fun `a document being sent in the background is left to that upload`() {
        assertFalse(document(scanned = 1.0, state = DocState.PendingUpload).isUploadable)
        assertFalse(document(orphans = 1, state = DocState.PendingUpload).isUploadable)
    }
}
