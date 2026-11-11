package com.erpbarcode.scanner.data.extsystem

import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The SOAP call that sends one document's recordings in a single transaction: NAV codeunit 50117
 * (Barcode App Upload), published as the web service `BarcodeAppUpload`, function
 * `UploadDocument(VAR recordings : XMLport 50008)`.
 *
 * NAV 2018 publishes codeunits only over SOAP, and its OData has no `$batch` for writes, so this
 * is the one way to hand it a whole document at once. The codeunit inserts every row into Barcode
 * App Recordings (50180) exactly as the OData endpoint would, inside the web service call's own
 * transaction: the first row NAV refuses rolls back every row before it, and the device is told
 * why. A row whose GUID NAV already holds is skipped rather than refused, so sending a document
 * again after a lost response is harmless.
 *
 * Pure text in and out, so it can be tested without a server.
 */
internal object DocumentUploadSoap {

    const val CODEUNIT_NAMESPACE = "urn:microsoft-dynamics-schemas/codeunit/BarcodeAppUpload"
    const val XMLPORT_NAMESPACE = "urn:microsoft-dynamics-nav/xmlports/x50008"
    const val SOAP_ACTION = "\"$CODEUNIT_NAMESPACE:UploadDocument\""

    /** The request body for [rows]. */
    fun envelope(rows: List<NavBarcodeAppRecording>): String = buildString {
        append("""<?xml version="1.0" encoding="utf-8"?>""")
        append("""<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body>""")
        append("""<UploadDocument xmlns="$CODEUNIT_NAMESPACE"><recordings>""")
        for (row in rows) {
            append("""<Recording xmlns="$XMLPORT_NAMESPACE">""")
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

    /** NAV's own message from a SOAP fault, or null when [body] carries none. */
    fun faultMessage(body: String): String? =
        FAULT_STRING.find(body)?.groupValues?.get(1)?.let(::unescape)?.trim()?.takeIf { it.isNotEmpty() }

    private fun StringBuilder.element(name: String, value: String) {
        append('<').append(name).append('>').append(escape(value)).append("</").append(name).append('>')
    }

    /**
     * xs:dateTime as NAV reads it: .NET takes at most seven fractional digits, and a clock that
     * reports nanoseconds would give nine. Milliseconds are all a scan time needs. A value that is
     * not an instant at all is passed on as it is, for NAV to refuse with its own message.
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
