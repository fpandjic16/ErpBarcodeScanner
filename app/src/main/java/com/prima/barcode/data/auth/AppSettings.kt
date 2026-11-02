package com.prima.barcode.data.auth

import com.prima.barcode.data.model.DocTypeFilterMode
import com.prima.barcode.data.model.DocumentType
import com.prima.barcode.ui.theme.Language
import com.prima.barcode.ui.theme.TextSize

/**
 * The document types that existed before Complaint was split into Complaint Shipment and
 * Complaint Receipt.
 *
 * Settings and configuration files from before that change list the types that are switched
 * *off*, which silently means "every other type is on" — including types the writer never knew.
 * Read that way, an upgraded device would switch on both new complaint types by itself. Read
 * against this set instead, a type the older list could not have mentioned stays off.
 */
internal val DOC_TYPES_BEFORE_COMPLAINT_SPLIT: Set<DocumentType> = setOf(
    DocumentType.WAREHOUSE_SHIPMENT,
    DocumentType.WAREHOUSE_RECEIPT,
    DocumentType.RETAIL_SHIPMENT,
    DocumentType.RETAIL_RECEIPT,
    DocumentType.TRANSPORT_SHEET,
    DocumentType.INVENTORY,
)

/**
 * The defaults here are the only copy: [AppSettingsStore] falls back to them for anything not yet
 * stored, so a new operator starts from exactly these values. Croatian, larger text and a 200 ms
 * debounce were chosen for the people who actually use these handhelds; an operator who has saved
 * their settings keeps what they saved.
 *
 * Every document type starts switched off. A fresh installation is set up for the work its site
 * actually does, in the external system configuration, rather than showing every type there is.
 */
data class AppSettings(
    val textSize: TextSize = TextSize.LARGER,
    val uppercaseText: Boolean = false,
    val language: Language = Language.CROATIAN,
    val debounceTime: Int = 200,
    val hapticEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val warnOnOver: Boolean = true,
    val backgroundSync: Boolean = false,
    val lastLocationCode: String = "",
    val lastRcCode: String = "",
    val disabledDocTypes: Set<String> = DocumentType.entries.map { it.key }.toSet(),
    val docTypeFilters: Map<String, DocTypeFilterMode> = emptyMap(),
    val debuggerActive: Boolean = false,
)
