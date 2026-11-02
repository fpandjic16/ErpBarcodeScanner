package com.prima.barcode.data.model

import java.time.LocalDate

data class DocumentFilter(
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val states: Set<LineStatus> = emptySet(),
    val types: Set<DocumentType> = emptySet(),
    val destinationCode: String = "",
    val sourceCode: String = "",
    val rcCode: String = "",
) {
    val isActive: Boolean get() =
        dateFrom != null || dateTo != null || states.isNotEmpty() || types.isNotEmpty() ||
        destinationCode.isNotBlank() || sourceCode.isNotBlank() || rcCode.isNotBlank()
}

data class DownloadFilter(
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val destinationCode: String = "",
    val sourceCode: String = "",
    val rcCode: String = "",
    /** One document only — how a stock count downloads the batch the operator picked. */
    val documentNo: String = "",
)
