package com.prima.barcode.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prima.barcode.data.model.Document
import com.prima.barcode.data.repository.DocumentRepository
import com.prima.barcode.data.repository.SetQuantityResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordingViewModel @Inject constructor(
    private val repository: DocumentRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val documentNo: String = checkNotNull(savedStateHandle["documentNo"])
    private val type: String = checkNotNull(savedStateHandle["type"])

    val document: StateFlow<Document?> = repository.observeDocument(documentNo, type)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun recordScan(lineNo: Int, barcodeNo: String, userId: String, quantity: Double) {
        viewModelScope.launch {
            repository.recordScan(documentNo, type, lineNo, barcodeNo, userId, quantity)
        }
    }

    fun recordScanNotOnDocument(barcodeNo: String, unitOfMeasureCode: String, userId: String, quantity: Double) {
        viewModelScope.launch {
            repository.recordScanNotOnDocument(documentNo, type, barcodeNo, unitOfMeasureCode, userId, quantity)
        }
    }

    /**
     * [onRefused] is handed the quantity the ERP already holds for this line, when the edit asked
     * to go below it. The repository refuses rather than clamps, so somebody has to say why —
     * silently snapping the number back would look like the app had simply ignored the operator.
     */
    fun setLineScanned(lineNo: Int, scanned: Double, userId: String, onRefused: (Double) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.setLineScanned(documentNo, type, lineNo, scanned, userId)
            if (result is SetQuantityResult.BelowSent) onRefused(result.sent)
        }
    }
}
