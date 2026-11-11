package com.erpbarcode.scanner.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "documentHeader",
    primaryKeys = ["documentNo", "type"],
)
data class DocumentHeaderEntity(
    val documentNo: String,
    val type: String,
    val destinationCode: String,
    val sourceCode: String,
    val rcCode: String,
    val isSourceRetail: Boolean,
    val creationDateTime: Long,
    val documentDate: Long?,
    val docState: String,
)

@Entity(
    tableName = "documentLine",
    primaryKeys = ["documentNo", "type", "lineNo"],
    foreignKeys = [ForeignKey(entity = DocumentHeaderEntity::class, parentColumns = ["documentNo", "type"], childColumns = ["documentNo", "type"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("documentNo", "type"), Index("barcodeNo")],
)
data class DocumentLineEntity(
    val documentNo: String,
    val type: String,
    val lineNo: Int,
    val itemNo: String,
    val itemName: String,
    val barcodeNo: String,
    val expected: Double,
    val destinationCode: String,
    val sourceCode: String,
    val unitOfMeasureCode: String,
    val scanningQty: Double = 1.0,
    /**
     * Created on the device for an item a stock count did not list; see `Line.isAddedOnDevice`.
     * Such a line still gets a negative [lineNo] so it can never collide with the ERP's own
     * numbers, but what it *is* is said here rather than read off the sign.
     *
     * Declared last on purpose: the 20→21 migration adds it with ALTER TABLE ADD COLUMN, which
     * appends, and the entity's field order has to match the table's column order.
     */
    val addedOnDevice: Boolean = false,
)

@Entity(
    tableName = "recordings",
    primaryKeys = ["documentNo", "type", "documentLine", "recordingLineNo"],
    foreignKeys = [ForeignKey(entity = DocumentHeaderEntity::class, parentColumns = ["documentNo", "type"], childColumns = ["documentNo", "type"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("documentNo", "type"), Index("documentLine")],
)
data class RecordingEntity(
    val documentNo: String,
    val type: String,
    val documentLine: Int,
    val recordingLineNo: Int,
    val barcodeNo: String,
    val quantity: Double,
    val creationDateTime: String,
    val userId: String,
    val destinationCode: String,
    val sourceCode: String,
    val unitOfMeasureCode: String,
    val rcCode: String,
    /**
     * Identity of this recording for the ERP, generated once when the scan is recorded and never
     * regenerated. Every upload attempt for this row sends the same value, so a retry after a
     * lost success response arrives as the row NAV already holds rather than as a new one.
     *
     * Declared last on purpose: the 16→17 migration adds it with ALTER TABLE ADD COLUMN, which
     * appends, and the entity's field order has to match the table's column order.
     */
    val recordingGuid: String,
    /**
     * When the ERP accepted this row (ISO-8601), or null while it is still queued.
     *
     * Rows are marked rather than deleted on success, and the whole document is removed once
     * nothing is left queued. Deleting them one by one made the document's quantities fall as the
     * upload progressed, so a partly-sent document looked like it had lost work — and re-scanning
     * to "finish" it posts duplicates, which this ERP records as surplus rather than rejecting.
     *
     * Carrying the timestamp rather than a bare flag costs nothing and answers "when did this
     * leave the device", which is the question support actually asks.
     */
    val sentAt: String? = null,
    /**
     * Message from the last failed attempt; null if never attempted, or never failed.
     *
     * Distinguishes "still queued, untried" from "tried and rejected", which is what lets the
     * operator be shown the rows that will never go through on their own.
     */
    val lastError: String? = null,
)

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey val code: String,
    val name: String,
    val rcCode: String,
)

@Entity(tableName = "responsibility_centers")
data class ResponsibilityCenterEntity(
    @PrimaryKey val code: String,
    val name: String,
    val short: String?,
)
