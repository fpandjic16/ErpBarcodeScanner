package com.erpbarcode.scanner.data.extsystem

import com.erpbarcode.scanner.data.db.RecordingEntity
import com.google.gson.annotations.SerializedName

// ── Upload request ────────────────────────────────────────────────────────────
// Barcode App Recordings: flat OData entity, one POST creates one recording row.

data class NavBarcodeAppRecording(
    @SerializedName("Document_Type")           val documentType: String,
    // Retail and warehouse documents share a Document_Type code, so the recording has to carry
    // the same discriminator the download filtered on or NAV can't tell which one it belongs
    // to. Null (transport sheets, which are neither) is omitted from the JSON by Gson.
    @SerializedName("Retail_Location")         val retailLocation: Boolean?,
    @SerializedName("Document_No")             val documentNo: String,
    @SerializedName("Document_Line_No")        val documentLineNo: Int,
    @SerializedName("Recording_Line_No")       val recordingLineNo: Int,
    @SerializedName("Recording_Guid")          val recordingGuid: String,
    @SerializedName("Barcode")                 val barcode: String,
    @SerializedName("Scanned_Quantity")        val scannedQuantity: Double,
    @SerializedName("Unit_Of_Measure_Code")    val unitOfMeasureCode: String,
    @SerializedName("Source_Creation_DateTime") val sourceCreationDateTime: String,
    @SerializedName("Source_User_ID")          val sourceUserId: String,
    @SerializedName("Source_Code")             val sourceCode: String,
    @SerializedName("Destination_Code")        val destinationCode: String,
)

// ── Mapping ───────────────────────────────────────────────────────────────────
// recordingGuid comes from the stored recording and is the same on every attempt, rather than
// being generated per attempt as it once was.
//
// What that buys: `Recording Guid` is the primary key of Barcode App Recordings (50180), and NAV's
// processing skips a GUID it already holds in Scanned Document Lines (50111). So a resent row is
// never counted twice (TECHNICAL_GUIDE §A.5.4).
//
// The window it cannot close: if NAV commits a row and the response is lost, the device cannot
// distinguish that from a failure and will send it again. Resent before NAV has processed the
// document, that copy is refused as "already exists" and the row shows as failed although NAV has
// it. That case is deliberately left to be resolved by hand.
//
// A line added on the device (negative number, see Line.isAddedOnDevice) goes out as line 0:
// "not on the document, find the item by its Barcode". Its own number means something only on
// this one device, so two devices could send the same number for different items.

fun RecordingEntity.toNavRecording(
    documentTypeCode: String,
    retailLocation: Boolean?,
): NavBarcodeAppRecording = NavBarcodeAppRecording(
    documentType             = documentTypeCode,
    retailLocation           = retailLocation,
    documentNo               = documentNo,
    documentLineNo           = documentLine.coerceAtLeast(0),
    recordingLineNo          = recordingLineNo,
    recordingGuid            = recordingGuid,
    barcode                  = barcodeNo,
    scannedQuantity          = quantity,
    unitOfMeasureCode        = unitOfMeasureCode,
    sourceCreationDateTime   = creationDateTime,
    sourceUserId             = userId,
    sourceCode               = sourceCode,
    destinationCode          = destinationCode,
)

// ── Download response wrapper ──────────────────────────────────────────────────

data class NavODataList<T>(
    @SerializedName("value") val value: List<T> = emptyList()
)

// Barcode App Entry: one record per document line, header fields repeated on every row.
// A single NAV table/OData page serves every document type, discriminated by Document_Type.
data class NavBarcodeAppEntry(
    @SerializedName("Document_Type")         val documentType:      String  = "",
    @SerializedName("Document_No")           val documentNo:        String,
    @SerializedName("Line_No")               val lineNo:            Int     = 0,
    @SerializedName("Source_No")             val sourceCode:        String  = "",
    @SerializedName("Retail_Location")       val isRetailLocation:  Boolean = false,
    @SerializedName("Destination_No")        val destinationCode:   String  = "",
    @SerializedName("Document_Date")         val documentDate:      String? = null,
    @SerializedName("Responsibility_Center") val rcCode:            String  = "",
    @SerializedName("Item_No")               val itemNo:            String  = "",
    @SerializedName("Item_Description")      val description:       String  = "",
    @SerializedName("Item_Qty")              val qtyOutstanding:    Double  = 0.0,
    @SerializedName("Scanning_Qty")          val scanningQty:       Double  = 1.0,
    @SerializedName("Item_UoM")              val unitOfMeasureCode: String  = "",
    @SerializedName("Barcode")               val barcodeNo:         String  = "",
)

// The same Barcode App Entry rows, reduced by $select to what the inventory batch list needs. An
// inventory row carries its journal template and batch, which no other type fills.
// Every field has a default, so Gson builds this through the no-argument constructor and a field
// the ERP leaves out keeps its default instead of becoming null.
/**
 * One row of `BarcodeInventoryBatches` (NAV page 50208 over Item Journal Batch, table 233): a
 * batch whose count has been sent to the devices and is not finished. The location, the
 * responsibility centre and the document no. are written to the batch when it is sent; the date
 * and the number of lines are FlowFields over the batch's journal lines.
 */
data class NavInventoryBatch(
    @SerializedName("Journal_Template_Name") val journalTemplateName: String  = "",
    @SerializedName("Name")                  val journalBatchName:    String  = "",
    @SerializedName("Description")           val description:         String  = "",
    @SerializedName("Barcode_Document_No")   val documentNo:          String  = "",
    @SerializedName("Barcode_Location_Code") val locationCode:        String  = "",
    @SerializedName("Barcode_Resp_Center")   val rcCode:              String  = "",
    @SerializedName("Barcode_Posting_Date")  val postingDate:         String? = null,
    @SerializedName("Barcode_Line_Count")    val lineCount:           Int     = 0,
)

data class NavLocation(
    @SerializedName("Code")                    val code:   String,
    @SerializedName("Name")                    val name:   String,
    @SerializedName("Responsibility_Center")   val rcCode: String = "",
)

data class NavResponsibilityCenter(
    @SerializedName("Code")  val code:  String,
    @SerializedName("Name")  val name:  String,
    @SerializedName("Short") val short: String? = null,
)
