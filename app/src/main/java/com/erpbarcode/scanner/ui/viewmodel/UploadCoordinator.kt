package com.erpbarcode.scanner.ui.viewmodel

import android.content.Context
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.data.auth.ExtSystemConfig
import com.erpbarcode.scanner.data.auth.ExtSystemCredentials
import com.erpbarcode.scanner.data.auth.RecordingUploadMode
import com.erpbarcode.scanner.data.extsystem.ExtSystemODataClient
import com.erpbarcode.scanner.data.extsystem.ExtSystemResult
import com.erpbarcode.scanner.data.extsystem.toErpRecording
import com.erpbarcode.scanner.data.model.DocState
import com.erpbarcode.scanner.data.model.Document
import com.erpbarcode.scanner.data.repository.DocumentRepository
import com.erpbarcode.scanner.i18n.inAppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends documents' recordings to the ERP, per scan or per document (ExtSystemConfig.uploadMode), and
 * moves each document on: removed once the ERP holds every scan on it, UploadFailed with a reason
 * otherwise.
 *
 * "Every scan on it" is asked of the database at the moment of removal, not assumed from the rows
 * this run sent: a scan made while those were on their way is not among them. Such a document
 * stays, with its sent rows marked, and the next upload sends what is new
 * (DocumentRepository.deleteDocumentIfNothingUnsent).
 *
 * Split out of AppViewModel, which keeps the two entry points (blocking and background), holds the
 * change of operator off while this runs, and passes in the configuration and the signed-in
 * operator's credentials.
 *
 * One instance for the process, because [oneAtATime] only means anything if every upload goes
 * through the same one.
 */
@Singleton
class UploadCoordinator @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val repository: DocumentRepository,
    private val extSystemClient: ExtSystemODataClient,
) {

    /**
     * Held for the length of an upload, so that a second one waits for the first to finish.
     *
     * A background upload returns the screen to the operator at once, and nothing stops them
     * pressing Upload again while it runs. Two runs over the same document each read its queued
     * rows before the other had marked any as sent, and sent every row twice; the ERP refused the
     * second copy as already there, and the document ended up marked as failed although it had
     * gone through. A run that waited reads the queue afterwards and finds what is really left:
     * nothing, where the first run removed the document.
     */
    private val oneAtATime = Mutex()

    /** In the app's language, not the device's; see AppViewModel.inAppText. */
    private fun inAppText(id: Int, vararg args: Any): String =
        appContext.inAppLanguage().getString(id, *args)

    /**
     * Uploads each doc; on success removes it unless a new scan has arrived meanwhile, on failure
     * marks it UploadFailed. Returns the failure count. Waits for an upload already running.
     */
    suspend fun runUpload(docs: List<Document>, config: ExtSystemConfig, creds: ExtSystemCredentials?): Int =
        oneAtATime.withLock { upload(docs, config, creds) }

    private suspend fun upload(docs: List<Document>, config: ExtSystemConfig, creds: ExtSystemCredentials?): Int {
        if (!config.isConfigured || creds == null) {
            docs.forEach {
                repository.updateDocState(it.documentNo, it.type.key,
                    DocState.UploadFailed(inAppText(R.string.upload_error_not_configured)))
            }
            return docs.size
        }
        val perDocument = config.uploadMode == RecordingUploadMode.PER_DOCUMENT
        val url = if (perDocument) config.documentUploadUrl else config.recordingSyncUrl
        if (url.isBlank()) {
            val reason = inAppText(
                if (perDocument) R.string.upload_error_document_url_missing else R.string.upload_error_sync_url_missing
            )
            docs.forEach {
                repository.updateDocState(it.documentNo, it.type.key, DocState.UploadFailed(reason))
            }
            return docs.size
        }
        extSystemClient.configure(config, creds)
        var failures = 0
        for (doc in docs) {
            // Checked before a single row goes out. A document holding scans whose line the ERP has
            // since removed would otherwise send a Document_Line_No that no longer exists there,
            // for a quantity nobody has decided what to do with.
            //
            // All-or-nothing per document, not "send the good rows and flag the rest": a
            // partially posted document while the operator is still deciding about the remainder
            // leaves nobody able to say afterwards what had already gone through.
            val orphans = repository.getOrphanedRecordings(doc.documentNo, doc.type.key)
            if (orphans.isNotEmpty()) {
                repository.updateDocState(
                    doc.documentNo,
                    doc.type.key,
                    DocState.UploadFailed(
                        inAppText(R.string.upload_error_needs_review, orphans.size)
                    ),
                )
                failures++
                continue
            }

            val docTypeCode = config.docTypeCodeFor(doc.type)
            // Retail and warehouse share a Document_Type code, so the recording has to carry the
            // same discriminator for the ERP to attribute it. The value is the Retail_Location the ERP
            // itself reported for this document on download. Transport sheets take part in
            // neither bucket (retailLocation == null) and keep sending no field at all rather
            // than a false that would claim they're warehouse documents.
            val retailLocation = if (doc.type.retailLocation != null) doc.isSourceRetail else null
            // Line-attached and not yet accepted. The check above should have caught anything
            // else, but this is the query that decides what actually leaves the device.
            val rows = repository.getQueuedRecordings(doc.documentNo, doc.type.key)

            if (rows.isEmpty()) {
                // Nothing queued means one of two very different things. A document that never
                // held a recording is freshly downloaded and must be left alone — deleting those
                // is the bug 220f284 fixed. One whose rows have all been accepted is finished,
                // and this is where it is finally removed, taking its sent rows with it.
                if (repository.hasAnyRecordings(doc.documentNo, doc.type.key)) {
                    repository.deleteDocumentIfNothingUnsent(doc.documentNo, doc.type.key)
                }
                continue
            }

            if (perDocument) {
                // One call, one transaction on the ERP side: all of these rows are in the ERP, or none
                // of them are. Every row carries the same outcome, so the error screen shows the
                // one reason against each of them.
                val result = extSystemClient.uploadDocument(
                    url, rows.map { it.toErpRecording(docTypeCode, retailLocation) },
                )
                when (result) {
                    is ExtSystemResult.Success -> {
                        rows.forEach {
                            repository.markRecordingSent(doc.documentNo, doc.type.key, it.documentLine, it.recordingLineNo)
                        }
                        repository.deleteDocumentIfNothingUnsent(doc.documentNo, doc.type.key)
                    }
                    is ExtSystemResult.Failure -> {
                        rows.forEach {
                            repository.recordRecordingFailure(
                                doc.documentNo, doc.type.key, it.documentLine, it.recordingLineNo, result.message,
                            )
                        }
                        repository.updateDocState(doc.documentNo, doc.type.key, DocState.UploadFailed(result.message))
                        failures++
                    }
                }
                continue
            }

            var lastFailure: String? = null
            var failedRows = 0
            var connectionLost = false

            for (row in rows) {
                val result = extSystemClient.uploadRecording(
                    url, row.toErpRecording(docTypeCode, retailLocation),
                )
                when (result) {
                    // Marked, not deleted. The row stays until the whole document goes, so the
                    // document keeps showing everything the operator scanned while it is only
                    // partly sent.
                    is ExtSystemResult.Success -> repository.markRecordingSent(
                        doc.documentNo, doc.type.key, row.documentLine, row.recordingLineNo,
                    )
                    is ExtSystemResult.Failure -> {
                        repository.recordRecordingFailure(
                            doc.documentNo, doc.type.key, row.documentLine, row.recordingLineNo,
                            result.message,
                        )
                        lastFailure = result.message
                        failedRows++
                        // A real status code means the server answered and objected to this row,
                        // so the rest are worth trying. `code` left at its -1 default means no
                        // response came back at all — the server or the network is gone, and
                        // continuing would spend a connect timeout per remaining row to learn
                        // the same thing again.
                        if (result.code <= 0) {
                            connectionLost = true
                            break
                        }
                    }
                }
            }

            if (lastFailure == null) {
                repository.deleteDocumentIfNothingUnsent(doc.documentNo, doc.type.key)
            } else {
                // A dropped connection is best described by the error itself; individually
                // refused rows are better described by how many, with the detail per row on the
                // error screen.
                val reason = if (connectionLost) lastFailure
                else inAppText(R.string.upload_error_rows_failed, failedRows, rows.size)
                repository.updateDocState(doc.documentNo, doc.type.key, DocState.UploadFailed(reason))
                failures++
            }
        }
        return failures
    }
}
