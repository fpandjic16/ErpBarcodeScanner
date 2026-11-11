package com.erpbarcode.scanner.data.db

import com.erpbarcode.scanner.data.model.DocState
import com.erpbarcode.scanner.data.model.Document
import com.erpbarcode.scanner.data.model.DocumentType
import com.erpbarcode.scanner.data.model.FailedScan
import com.erpbarcode.scanner.data.model.Item
import com.erpbarcode.scanner.data.model.Line
import com.erpbarcode.scanner.data.model.Location
import com.erpbarcode.scanner.data.model.OrphanedScan
import com.erpbarcode.scanner.data.model.ResponsibilityCenter
import java.time.Instant

// ── DocState serialization ────────────────────────────────────────────────────

fun DocState.toDbString(): String = when (this) {
    DocState.Downloaded      -> "Downloaded"
    DocState.InProgress      -> "InProgress"
    DocState.Completed       -> "Completed"
    DocState.PendingUpload   -> "PendingUpload"
    is DocState.UploadFailed -> "UploadFailed:${reason}"
}

fun String.toDocState(): DocState = when {
    this == "InProgress"        -> DocState.InProgress
    this == "Completed"         -> DocState.Completed
    this == "PendingUpload"     -> DocState.PendingUpload
    startsWith("UploadFailed:") -> DocState.UploadFailed(removePrefix("UploadFailed:"))
    else                        -> DocState.Downloaded
}

// ── DocumentType serialization ────────────────────────────────────────────────

fun String.toDocumentType(): DocumentType =
    DocumentType.entries.firstOrNull { it.key == this } ?: DocumentType.WAREHOUSE_SHIPMENT

// ── Entity -> Domain ──────────────────────────────────────────────────────────

fun DocumentLineEntity.toDomain(scanned: Double, sent: Double = 0.0): Line = Line(
    documentNo = documentNo,
    lineNo = lineNo,
    item = Item(itemNo, itemName),
    barcodeNo = barcodeNo,
    expected = expected,
    scanned = scanned,
    destinationCode = destinationCode,
    sourceCode = sourceCode,
    unitOfMeasureCode = unitOfMeasureCode,
    scanningQty = scanningQty,
    sentQuantity = sent,
    isAddedOnDevice = addedOnDevice,
)

fun DocumentHeaderWithLines.toDomain(): Document {
    val lineNos = lines.mapTo(HashSet()) { it.lineNo }
    return Document(
        documentNo = document.documentNo,
        type = document.type.toDocumentType(),
        destinationCode = document.destinationCode,
        sourceCode = document.sourceCode,
        rcCode = document.rcCode,
        isSourceRetail = document.isSourceRetail,
        creationDateTime = Instant.ofEpochMilli(document.creationDateTime),
        documentDate = document.documentDate?.let { Instant.ofEpochMilli(it) },
        // Sums sent and queued alike. A row already accepted by the ERP is still something the
        // operator scanned, and dropping it from the total would make a partly-sent document look
        // like it had lost work — which invites re-scanning, and this ERP records the duplicate as
        // surplus rather than refusing it.
        lines = lines.sortedBy { it.lineNo }.map { lineEntity ->
            val onLine = recordings.filter { it.documentLine == lineEntity.lineNo }
            // Split out here, from a list already in hand, because the ERP-accepted part is the
            // floor a manual quantity edit may not go below — see Line.sentQuantity.
            lineEntity.toDomain(
                scanned = onLine.sumOf { it.quantity },
                sent = onLine.filter { it.sentAt != null }.sumOf { it.quantity },
            )
        },
        state = document.docState.toDocState(),
        // Recordings pointing at a line this document no longer has. Building them here rather
        // than with another query is free: both lists are already in hand, and the line loop
        // above would otherwise be the only thing that ever looks at recordings — which is
        // exactly why these went unnoticed.
        // Only queued ones are actionable. An orphan already accepted by the ERP is history: it
        // will go when the document goes, and asking the operator to decide about it would be
        // asking them to undo something that already happened.
        orphanedScans = recordings
            .filter { it.sentAt == null && it.documentLine !in lineNos }
            .sortedWith(compareBy({ it.documentLine }, { it.recordingLineNo }))
            .map { rec ->
                OrphanedScan(
                    barcodeNo = rec.barcodeNo,
                    quantity = rec.quantity,
                    unitOfMeasureCode = rec.unitOfMeasureCode,
                    lineNo = rec.documentLine,
                    at = runCatching { Instant.parse(rec.creationDateTime) }.getOrNull(),
                    userId = rec.userId,
                )
            },
        failedScans = recordings
            .filter { it.sentAt == null && it.lastError != null && it.documentLine in lineNos }
            .sortedWith(compareBy({ it.documentLine }, { it.recordingLineNo }))
            .map { rec ->
                FailedScan(
                    barcodeNo = rec.barcodeNo,
                    quantity = rec.quantity,
                    unitOfMeasureCode = rec.unitOfMeasureCode,
                    lineNo = rec.documentLine,
                    error = rec.lastError.orEmpty(),
                )
            },
        // Sent while their line still existed, orphaned by an ERP-side edit afterwards. Only
        // reachable on a document left partly sent, since a fully sent one is removed outright.
        sentOrphanedScans = recordings
            .filter { it.sentAt != null && it.documentLine !in lineNos }
            .sortedWith(compareBy({ it.documentLine }, { it.recordingLineNo }))
            .map { rec ->
                OrphanedScan(
                    barcodeNo = rec.barcodeNo,
                    quantity = rec.quantity,
                    unitOfMeasureCode = rec.unitOfMeasureCode,
                    lineNo = rec.documentLine,
                    at = runCatching { Instant.parse(rec.creationDateTime) }.getOrNull(),
                    userId = rec.userId,
                )
            },
        sentScans = recordings.count { it.sentAt != null },
        pendingScans = recordings.count { it.sentAt == null },
    )
}

// ── Domain -> Entity ──────────────────────────────────────────────────────────

fun Document.toEntity(): DocumentHeaderEntity = DocumentHeaderEntity(
    documentNo = documentNo,
    type = type.key,
    destinationCode = destinationCode,
    sourceCode = sourceCode,
    rcCode = rcCode,
    isSourceRetail = isSourceRetail,
    creationDateTime = creationDateTime.toEpochMilli(),
    documentDate = documentDate?.toEpochMilli(),
    docState = state.toDbString(),
)

fun Line.toEntity(type: String): DocumentLineEntity = DocumentLineEntity(
    documentNo = documentNo,
    type = type,
    lineNo = lineNo,
    itemNo = item.no,
    itemName = item.name,
    barcodeNo = barcodeNo,
    expected = expected,
    destinationCode = destinationCode,
    sourceCode = sourceCode,
    unitOfMeasureCode = unitOfMeasureCode,
    scanningQty = scanningQty,
    addedOnDevice = isAddedOnDevice,
)

// ── Location / RC mappers ─────────────────────────────────────────────────────

fun LocationEntity.toDomain() = Location(code = code, name = name, rc = rcCode)
fun ResponsibilityCenterEntity.toDomain() = ResponsibilityCenter(code = code, name = name, short = short)
