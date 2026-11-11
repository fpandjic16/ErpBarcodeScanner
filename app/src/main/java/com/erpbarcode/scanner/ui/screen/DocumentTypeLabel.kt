package com.erpbarcode.scanner.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.data.model.DocumentType

/**
 * The document type's name in the operator's language.
 *
 * `DocumentType.display` is the English constant the ERP and the logs use; this is what a person
 * reads. Every screen that shows a type needs it, and each of the seven that did had grown its own
 * private copy of exactly these lines — which is not merely duplication: the eighth screen could
 * not call any of them, and adding a type meant finding all seven.
 */
@Composable
internal fun DocumentType.localizedDisplay(): String = when (this) {
    DocumentType.WAREHOUSE_SHIPMENT -> stringResource(R.string.doctype_warehouse_shipment)
    DocumentType.WAREHOUSE_RECEIPT  -> stringResource(R.string.doctype_warehouse_receipt)
    DocumentType.RETAIL_SHIPMENT    -> stringResource(R.string.doctype_retail_shipment)
    DocumentType.RETAIL_RECEIPT     -> stringResource(R.string.doctype_retail_receipt)
    DocumentType.TRANSPORT_SHEET    -> stringResource(R.string.doctype_transport_sheet)
    DocumentType.COMPLAINT_SHIPMENT -> stringResource(R.string.doctype_complaint_shipment)
    DocumentType.COMPLAINT_RECEIPT  -> stringResource(R.string.doctype_complaint_receipt)
    DocumentType.INVENTORY          -> stringResource(R.string.doctype_inventory)
}
