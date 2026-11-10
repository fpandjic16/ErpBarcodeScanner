package com.prima.barcode.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.prima.barcode.R
import com.prima.barcode.data.model.LineStatus

/**
 * The status's name in the operator's language, as the document lists and filters show it.
 *
 * One function for every screen, like [localizedDisplay] for document types: the document list,
 * the overview and the filter each had a private copy of these lines. The recording screen's chip
 * deliberately uses its own shorter words (`status_chip_*`) and is not this.
 */
@Composable
internal fun LineStatus.localizedLabel(): String = when (this) {
    LineStatus.EMPTY   -> stringResource(R.string.doc_status_empty)
    LineStatus.PARTIAL -> stringResource(R.string.doc_status_partial)
    LineStatus.EXACT   -> stringResource(R.string.doc_status_ready)
    LineStatus.OVER    -> stringResource(R.string.doc_status_over_qty)
}
