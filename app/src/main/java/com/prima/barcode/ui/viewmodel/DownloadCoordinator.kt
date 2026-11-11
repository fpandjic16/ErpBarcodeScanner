package com.prima.barcode.ui.viewmodel

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.prima.barcode.R
import com.prima.barcode.data.auth.ExtSystemConfig
import com.prima.barcode.data.auth.ExtSystemCredentials
import com.prima.barcode.data.db.LocationDao
import com.prima.barcode.data.db.LocationEntity
import com.prima.barcode.data.db.ResponsibilityCenterEntity
import com.prima.barcode.data.extsystem.ExtSystemODataClient
import com.prima.barcode.data.extsystem.ExtSystemResult
import com.prima.barcode.data.extsystem.NavBarcodeAppEntry
import com.prima.barcode.data.extsystem.NavInventoryBatch
import com.prima.barcode.data.extsystem.NavLocation
import com.prima.barcode.data.extsystem.NavODataList
import com.prima.barcode.data.extsystem.odataLiteral
import com.prima.barcode.data.model.DocState
import com.prima.barcode.data.model.Document
import com.prima.barcode.data.model.DocumentType
import com.prima.barcode.data.model.DownloadFilter
import com.prima.barcode.data.model.InventoryBatch
import com.prima.barcode.data.model.Item
import com.prima.barcode.data.model.Line
import com.prima.barcode.data.repository.DocumentRepository
import com.prima.barcode.i18n.inAppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Everything read from the ERP: locations, the inventory journal batches sent for scanning, and the
 * documents themselves, with the OData `$filter` each request carries.
 *
 * Split out of AppViewModel, which keeps the public entry points, the coroutine scope, the
 * "refreshing" state and the guard against changing operator mid transfer, and passes in the
 * configuration and the signed-in operator's credentials. Nothing here launches anything.
 */
class DownloadCoordinator @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val repository: DocumentRepository,
    private val locationDao: LocationDao,
    private val extSystemClient: ExtSystemODataClient,
) {

    private val gson = Gson()

    /** In the app's language, not the device's; see AppViewModel.inAppText. */
    private fun inAppText(id: Int, vararg args: Any): String =
        appContext.inAppLanguage().getString(id, *args)

    /** Locations and their responsibility centres into the shared database. Returns null on success, or an error message on failure. */
    suspend fun downloadLocations(config: ExtSystemConfig, savedCreds: ExtSystemCredentials?): String? {
        val creds  = savedCreds ?: return inAppText(R.string.ext_error_not_signed_in)
        if (!config.isConfigured) return inAppText(R.string.ext_error_not_configured)
        if (config.locationsUrl.isBlank()) return inAppText(R.string.ext_error_locations_url_missing)
        extSystemClient.configure(config, creds)
        return when (val result = extSystemClient.downloadRaw(config.locationsUrl)) {
            is ExtSystemResult.Success -> {
                val typeToken = object : TypeToken<NavODataList<NavLocation>>() {}.type
                val odata = gson.fromJson<NavODataList<NavLocation>>(result.data, typeToken)
                val rcCodes = odata.value.map { it.rcCode }.filter { it.isNotBlank() }.distinct()
                locationDao.clearRcs()
                locationDao.upsertRcs(rcCodes.map { ResponsibilityCenterEntity(it, it, null) })
                locationDao.clearLocations()
                locationDao.upsertLocations(odata.value.map { LocationEntity(it.code, it.name, it.rcCode) })
                null
            }
            is ExtSystemResult.Failure -> result.message
        }
    }

    private fun buildODataFilterString(filter: DownloadFilter, typeCode: String, type: DocumentType): String? {
        val clauses = mutableListOf<String>()
        if (typeCode.isNotBlank()) clauses.add("Document_Type eq ${odataLiteral(typeCode)}")
        // Retail and warehouse share a Document_Type code, so this clause is what actually
        // separates them — mandatory for all four, never optional. Transport sheets have no
        // retailLocation and so get no clause at all.
        type.retailLocation?.let { clauses.add("Retail_Location eq $it") }
        filter.dateFrom?.let { clauses.add("Document_Date ge $it") }
        filter.dateTo?.let { clauses.add("Document_Date le $it") }
        if (filter.destinationCode.isNotBlank()) clauses.add("Destination_No eq ${odataLiteral(filter.destinationCode)}")
        if (filter.sourceCode.isNotBlank()) clauses.add("Source_No eq ${odataLiteral(filter.sourceCode)}")
        if (filter.rcCode.isNotBlank()) clauses.add("Responsibility_Center eq ${odataLiteral(filter.rcCode)}")
        if (filter.documentNo.isNotBlank()) clauses.add("Document_No eq ${odataLiteral(filter.documentNo)}")
        return if (clauses.isEmpty()) null else clauses.joinToString(" and ")
    }

    private fun appendODataFilter(url: String, filterStr: String): String {
        val encoded = java.net.URLEncoder.encode(filterStr, "UTF-8").replace("+", "%20")
        val sep = if ('?' in url) '&' else '?'
        return "$url${sep}\$filter=$encoded"
    }

    /**
     * The inventory journal batches the ERP has sent for scanning in [filter]'s location or
     * responsibility centre, for the operator to pick one before downloading.
     *
     * Read from the ERP's own list of them (`BarcodeInventoryBatches`, page 50208): one row per
     * batch that has a count on the devices and is not finished yet, with its location,
     * responsibility centre, date and number of lines worked out on the ERP side. There is no
     * other way: reading every Barcode App Entry of the location and grouping them here was
     * replaced, because on a large location that meant thousands of rows to list a handful.
     */
    suspend fun loadInventoryBatches(
        filter: DownloadFilter,
        config: ExtSystemConfig,
        creds: ExtSystemCredentials?,
    ): ExtSystemResult<List<InventoryBatch>> {
        if (!config.isConfigured || creds == null) {
            return ExtSystemResult.Failure(
                if (creds == null) inAppText(R.string.ext_error_not_signed_in)
                else inAppText(R.string.ext_error_not_configured)
            )
        }
        if (config.inventoryBatchesUrl.isBlank()) {
            return ExtSystemResult.Failure(inAppText(R.string.ext_error_inventory_batches_url_missing))
        }
        extSystemClient.configure(config, creds)
        val clauses = buildList {
            if (filter.sourceCode.isNotBlank()) add("Barcode_Location_Code eq ${odataLiteral(filter.sourceCode)}")
            if (filter.rcCode.isNotBlank()) add("Barcode_Resp_Center eq ${odataLiteral(filter.rcCode)}")
        }
        val url = if (clauses.isEmpty()) config.inventoryBatchesUrl
            else appendODataFilter(config.inventoryBatchesUrl, clauses.joinToString(" and "))
        return when (val result = extSystemClient.downloadRaw(url)) {
            is ExtSystemResult.Success -> {
                val typeToken = object : TypeToken<NavODataList<NavInventoryBatch>>() {}.type
                val odata = gson.fromJson<NavODataList<NavInventoryBatch>>(result.data, typeToken)
                ExtSystemResult.Success(
                    odata.value
                        .filter { it.documentNo.isNotBlank() }
                        .map { row ->
                            InventoryBatch(
                                documentNo          = row.documentNo,
                                journalTemplateName = row.journalTemplateName,
                                journalBatchName    = row.journalBatchName,
                                description         = row.description,
                                // NAV writes an empty date as 0001-01-01.
                                documentDate        = row.postingDate
                                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                                    ?.takeIf { it.year > 1 },
                                lineCount           = row.lineCount,
                            )
                        }
                        .sortedWith(compareBy({ it.journalTemplateName }, { it.journalBatchName }, { it.documentNo }))
                )
            }
            is ExtSystemResult.Failure -> result
        }
    }

    /** Display name and full address of each document download, as the debugger shows them. */
    fun buildDownloadUrls(filter: DownloadFilter, docType: DocumentType?, config: ExtSystemConfig): List<Pair<String, String>> {
        if (config.documentLinesUrl.isBlank()) return emptyList()
        val types = if (docType != null) listOf(docType) else DocumentType.entries
        return types.map { type ->
            val typeCode = config.docTypeCodeFor(type)
            val filterStr = buildODataFilterString(filter, typeCode, type)
            val finalUrl = if (filterStr != null) appendODataFilter(config.documentLinesUrl, filterStr) else config.documentLinesUrl
            type.display to finalUrl
        }
    }

    /**
     * Downloads [docType]'s documents (or every type's) matching [filter] and merges them into the
     * signed-in operator's database. Returns how many types failed, and their messages.
     */
    suspend fun downloadDocuments(
        filter: DownloadFilter,
        docType: DocumentType?,
        config: ExtSystemConfig,
        creds: ExtSystemCredentials?,
    ): Pair<Int, List<String>> {
        if (!config.isConfigured || creds == null) {
            val msg = if (creds == null) inAppText(R.string.ext_error_not_signed_in)
                else inAppText(R.string.ext_error_not_configured)
            return 1 to listOf(msg)
        }
        if (config.documentLinesUrl.isBlank()) {
            return 1 to listOf(inAppText(R.string.ext_error_document_lines_url_missing))
        }
        extSystemClient.configure(config, creds)
        val typesToDownload = if (docType != null) listOf(docType) else DocumentType.entries
        var failures = 0
        val errorMessages = mutableListOf<String>()
        for (type in typesToDownload) {
            val typeCode = config.docTypeCodeFor(type)
            val filterStr = buildODataFilterString(filter, typeCode, type)
            val finalUrl = if (filterStr != null) appendODataFilter(config.documentLinesUrl, filterStr) else config.documentLinesUrl
            val result = extSystemClient.downloadRaw(finalUrl)
            when (result) {
                is ExtSystemResult.Success -> {
                    val now = Instant.now()
                    val typeToken = object : TypeToken<NavODataList<NavBarcodeAppEntry>>() {}.type
                    val odata = gson.fromJson<NavODataList<NavBarcodeAppEntry>>(result.data, typeToken)
                    val documents = odata.value.groupBy { it.documentNo }.map { (docNo, rows) ->
                        val first = rows.first()
                        val lines = rows.filter { it.lineNo > 0 }.map { row ->
                            Line(
                                documentNo        = docNo,
                                lineNo            = row.lineNo,
                                item              = Item(row.itemNo, row.description),
                                barcodeNo         = row.barcodeNo,
                                expected          = row.qtyOutstanding,
                                scanned           = 0.0,
                                destinationCode   = row.destinationCode,
                                sourceCode        = row.sourceCode,
                                unitOfMeasureCode = row.unitOfMeasureCode,
                                scanningQty       = row.scanningQty,
                            )
                        }
                        Document(
                            documentNo       = docNo,
                            type             = type,
                            destinationCode  = first.destinationCode,
                            sourceCode       = first.sourceCode,
                            rcCode           = first.rcCode,
                            isSourceRetail   = first.isRetailLocation,
                            creationDateTime = now,
                            documentDate     = runCatching {
                                first.documentDate?.let { Instant.parse("${it}T00:00:00Z") } ?: now
                            }.getOrDefault(now),
                            lines            = lines,
                            state            = DocState.Downloaded,
                        )
                    }
                    // A download of one picked document says nothing about the others.
                    repository.replaceDownloadedDocuments(type, documents, pruneMissing = filter.documentNo.isBlank())
                }
                is ExtSystemResult.Failure -> {
                    Timber.w("Failed to download ${type.display}: ${result.message}")
                    failures++
                    errorMessages.add(result.message)
                }
            }
        }
        return failures to errorMessages
    }
}
