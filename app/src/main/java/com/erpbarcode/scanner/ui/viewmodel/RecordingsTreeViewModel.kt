package com.erpbarcode.scanner.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erpbarcode.scanner.data.model.Document
import com.erpbarcode.scanner.data.model.LineScans
import com.erpbarcode.scanner.data.model.ScanRecord
import com.erpbarcode.scanner.data.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** The document and its scans, resolved together so the screen never draws a half-built tree. */
data class RecordingsTreeState(
    val document: Document? = null,
    val tree: List<LineScans> = emptyList(),
)

/**
 * Backs the recordings tree: every line of the document, each with the individual scans recorded
 * against it.
 *
 * The scans come straight from the recordings table rather than from [Document], because
 * `Mappers.toDomain` sums them into the line's quantity and only the exceptional ones — orphaned,
 * failed — survive as objects. Observing them separately keeps that summing where it belongs and
 * costs nothing on the paths that don't need the detail.
 */
@HiltViewModel
class RecordingsTreeViewModel @Inject constructor(
    private val repository: DocumentRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val documentNo: String = checkNotNull(savedStateHandle["documentNo"])
    private val type: String = checkNotNull(savedStateHandle["type"])

    val state: StateFlow<RecordingsTreeState> = combine(
        repository.observeDocument(documentNo, type),
        repository.observeRecordings(documentNo, type),
    ) { document, recordings ->
        val byLine = recordings.groupBy { it.documentLine }
        RecordingsTreeState(
            document = document,
            // Every line, scanned or not — "Item C 0/3" is as much a part of the picture as the
            // lines that were scanned, and it is the only place the operator sees what's missing.
            tree = document?.lines.orEmpty().map { line ->
                LineScans(
                    line = line,
                    scans = byLine[line.lineNo].orEmpty().map { rec ->
                        ScanRecord(
                            lineNo = rec.documentLine,
                            recordingLineNo = rec.recordingLineNo,
                            barcodeNo = rec.barcodeNo,
                            quantity = rec.quantity,
                            at = runCatching { Instant.parse(rec.creationDateTime) }.getOrNull(),
                            userId = rec.userId,
                            sent = rec.sentAt != null,
                        )
                    },
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordingsTreeState())

    /** No-op for a scan already accepted by the ERP — the repository refuses it either way. */
    fun deleteScan(scan: ScanRecord) {
        if (scan.sent) return
        viewModelScope.launch {
            repository.deleteQueuedRecording(documentNo, type, scan.lineNo, scan.recordingLineNo)
        }
    }

    fun deleteAllQueued() {
        viewModelScope.launch { repository.deleteDocumentRecordings(documentNo, type) }
    }
}
