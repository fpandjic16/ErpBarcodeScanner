package com.erpbarcode.scanner.data.db

data class DocumentHeaderWithLines(
    val document: DocumentHeaderEntity,
    val lines: List<DocumentLineEntity>,
    val recordings: List<RecordingEntity>,
)
