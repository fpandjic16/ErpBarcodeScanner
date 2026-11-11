package com.erpbarcode.scanner.navigation

import com.erpbarcode.scanner.data.model.DocState
import com.erpbarcode.scanner.data.model.DocTypeFilterMode
import com.erpbarcode.scanner.data.model.Document
import com.erpbarcode.scanner.data.model.DocumentType
import com.erpbarcode.scanner.data.model.LineStatus
import com.erpbarcode.scanner.data.model.Location
import com.erpbarcode.scanner.data.model.ResponsibilityCenter
import com.erpbarcode.scanner.data.model.scanStatus
import com.erpbarcode.scanner.ui.screen.DocTypeSummary

/**
 * What the main menu and the lists show, worked out from the operator's documents and what they
 * have selected. No Compose and no state, so the rules below can be tested on their own; they are
 * the ones that decide what an operator sees and, more to the point, what they do not.
 */
internal class ShellData(
    /** The documents in the operator's selected location or responsibility centre. */
    val filteredDocs: List<Document>,
    /** One summary per document type that is switched on. */
    val docTypes: List<DocTypeSummary>,
    /** Every document carrying a scan, newest first: the RECORDINGS section. */
    val recordedDocs: List<Document>,
    val shiftScans: Int,
    val errorDocs: List<Document>,
    val readyDocs: List<Document>,
    val partialDocs: List<Document>,
    val overDocs: List<Document>,
)

/**
 * Whether this document belongs to what the operator has selected, by its type's scope.
 *
 * No exemption for work already started. A document scanned into at one location used to
 * bypass this filter for good, so moving from CS165 to CS175 still showed CS165's documents —
 * the filter stopped meaning anything as soon as a shift got going.
 *
 * That escape existed because in-progress work was otherwise unreachable from another
 * location. The RECORDINGS section removed the reason: it lists every document carrying a
 * scan, deliberately unfiltered by location, and is where unsent work is found now.
 *
 * `DocumentOverviewScreen`'s own `atLocation` has always been strict, so this brings the rest
 * of the app in line with the dashboard rather than inventing a rule.
 *
 * A type scoped by responsibility centre needs one selected, the same as a location-scoped
 * type needs a location. No centre used to mean every centre, so an operator who had chosen
 * nothing saw — and counted — every complaint in the company.
 */
internal fun Document.isInScope(
    docTypeFilters: Map<String, DocTypeFilterMode>,
    location: Location?,
    rc: ResponsibilityCenter?,
): Boolean = when (docTypeFilters[type.key] ?: type.defaultFilterMode) {
    DocTypeFilterMode.LOCATION -> location != null && sourceCode == location.code
    DocTypeFilterMode.RESPONSIBILITY_CENTER -> rc != null && rcCode == rc.code
}

internal fun shellData(
    documents: List<Document>,
    location: Location?,
    rc: ResponsibilityCenter?,
    docTypeFilters: Map<String, DocTypeFilterMode>,
    disabledDocTypes: Set<String>,
): ShellData {
    val filteredDocs = documents.filter { it.isInScope(docTypeFilters, location, rc) }

    val docTypes = DocumentType.entries.map { type ->
        val filterMode = docTypeFilters[type.key] ?: type.defaultFilterMode
        // Blocked until the operator has selected what this type is scoped by — not merely until
        // the device has a list to select from. The old test was the list: once any operator had
        // refreshed locations, the next one could open every list with nothing selected. The
        // location-scoped ones then showed nothing, and their download went out with no
        // location at all and fetched every location's documents. `location` and `rc` are null
        // both when nothing is selected and when the selected code is no longer on the list, and
        // a list never fetched leaves nothing to select, so this one test covers all three.
        val blocked = when (filterMode) {
            DocTypeFilterMode.LOCATION -> location == null
            DocTypeFilterMode.RESPONSIBILITY_CENTER -> rc == null
        }
        DocTypeSummary(
            type = type,
            count = filteredDocs.count { it.type == type },
            statusMini = filteredDocs.filter { it.type == type }.mapNotNull { doc ->
                when {
                    doc.state is DocState.UploadFailed -> LineStatus.EMPTY
                    doc.state == DocState.Downloaded   -> null
                    else -> doc.scanStatus().takeIf { it != LineStatus.EMPTY }
                }
            },
            blocked = blocked,
        )
    }.filter { it.type.key !in disabledDocTypes }

    // The RECORDINGS section: work in progress across every type, newest document first.
    // Deliberately not filtered by disabledDocTypes — a type switched off mid-job would otherwise
    // take the operator's only view of those scans with it.
    //
    // **It must stay built from `documents`.** Now that the location filter above admits no
    // exceptions, this is the only place in the app that shows an operator work they started
    // somewhere else. Narrowing it by location, by type, or by anything else would leave unsent
    // scans with nowhere to be seen — and unsent scans exist on this device and nowhere else.
    // Built from documents rather than filteredDocs, and on totalScans rather than hasProgress:
    // both of those narrowings key off a line's scanned quantity, and a scan whose line the ERP
    // has since removed adds to no line at all. A document whose scans were all orphaned would
    // drop out of the one list that is supposed to hold everything the operator has scanned.
    val recordedDocs = documents
        .filter { it.totalScans > 0 }
        .sortedByDescending { it.documentDate ?: it.creationDateTime }

    return ShellData(
        filteredDocs = filteredDocs,
        docTypes     = docTypes,
        recordedDocs = recordedDocs,
        shiftScans   = filteredDocs.sumOf { d -> d.lines.count { it.scanned > 0 } },
        errorDocs    = filteredDocs.filter { it.state is DocState.UploadFailed },
        readyDocs    = filteredDocs.filter { it.state !is DocState.UploadFailed && it.scanStatus() == LineStatus.EXACT },
        partialDocs  = filteredDocs.filter { it.state !is DocState.UploadFailed && it.scanStatus() == LineStatus.PARTIAL },
        overDocs     = filteredDocs.filter { it.state !is DocState.UploadFailed && it.scanStatus() == LineStatus.OVER },
    )
}
