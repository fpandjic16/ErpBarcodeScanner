package com.erpbarcode.scanner.data.extsystem

import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The SOAP call that sends one document's recordings in a single transaction: the ERP's web
 * service `BarcodeAppUpload`, function `UploadDocument(recordings)`.
 *
 * SOAP because the ERP's OData has no `$batch` for writes, so this is the one way to hand it a
 * whole document at once. The service inserts every row into the same recordings table the OData
 * endpoint writes, inside the call's own transaction: the first row the ERP refuses rolls back
 * every row before it, and the device is told why. A row whose GUID the ERP already holds is
 * skipped rather than refused, so sending a document again after a lost response is harmless.
 *
 * The namespaces below are the wire format the ERP side publishes and must match it exactly; what
 * the service is and how it is built there is in TECHNICAL_GUIDE §B.18.
 *
 * Pure text in and out, so it can be tested without a server.
 */
internal object DocumentUploadSoap {

    const val SERVICE_NAMESPACE = "urn:microsoft-dynamics-schemas/codeunit/BarcodeAppUpload"
    const val PAYLOAD_NAMESPACE = "urn:microsoft-dynamics-nav/xmlports/x50008"
    const val SOAP_ACTION = "\"$SERVICE_NAMESPACE:UploadDocument\""

    /** The request body for [rows]. */
    fun envelope(rows: List<ErpRecordingRow>): String = buildString {
        append("""<?xml version="1.0" encoding="utf-8"?>""")
        append("""<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body>""")
        append("""<UploadDocument xmlns="$SERVICE_NAMESPACE"><recordings>""")
        for (row in rows) {
            append("""<Recording xmlns="$PAYLOAD_NAMESPACE">""")
            element("RecordingGuid", "{${row.recordingGuid}}")
            element("DocumentType", row.documentType)
            element("DocumentNo", row.documentNo)
            element("DocumentLineNo", row.documentLineNo.toString())
            element("RecordingLineNo", row.recordingLineNo.toString())
            element("Barcode", row.barcode)
            element("ScannedQuantity", BigDecimal(row.scannedQuantity.toString()).toPlainString())
            element("UnitOfMeasureCode", row.unitOfMeasureCode)
            element("SourceCreationDateTime", xmlDateTime(row.sourceCreationDateTime))
            element("SourceUserID", row.sourceUserId)
            element("SourceCode", row.sourceCode)
            row.retailLocation?.let { element("RetailLocation", it.toString()) }
            element("DestinationCode", row.destinationCode)
            append("</Recording>")
        }
        append("</recordings></UploadDocument></soap:Body></soap:Envelope>")
    }

    /** The ERP's own message from a SOAP fault, or null when [body] carries none. */
    fun faultMessage(body: String): String? =
        FAULT_STRING.find(body)?.groupValues?.get(1)?.let(::unescape)?.trim()?.takeIf { it.isNotEmpty() }

    private fun StringBuilder.element(name: String, value: String) {
        append('<').append(name).append('>').append(escape(value)).append("</").append(name).append('>')
    }

    /**
     * xs:dateTime as the ERP reads it: .NET takes at most seven fractional digits, and a clock that
     * reports nanoseconds would give nine. Milliseconds are all a scan time needs. A value that is
     * not an instant at all is passed on as it is, for the ERP to refuse with its own message.
     */
    private fun xmlDateTime(value: String): String =
        runCatching { Instant.parse(value).truncatedTo(ChronoUnit.MILLIS).toString() }.getOrDefault(value)

    private fun escape(value: String): String = buildString(value.length) {
        for (c in value) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }

    private fun unescape(value: String): String = value
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&#39;", "'").replace("&amp;", "&")

    private val FAULT_STRING = Regex("<faultstring[^>]*>(.*?)</faultstring>", RegexOption.DOT_MATCHES_ALL)
}
