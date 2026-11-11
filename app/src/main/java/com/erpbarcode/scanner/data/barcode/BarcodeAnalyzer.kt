package com.erpbarcode.scanner.data.barcode

import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

class BarcodeAnalyzer(
    private val debounceMs: Long = 1500L,
    /**
     * Optional aim filter, so only what the user is actually pointing at gets scanned rather
     * than whatever happens to be biggest in the frame. Receives a candidate's bounding box
     * along with the upright image dimensions, and decides whether it falls inside the
     * on-screen reticle. Null scans the whole frame.
     */
    private val isAimedAt: ((box: Rect, uprightWidth: Int, uprightHeight: Int) -> Boolean)? = null,
    private val onResult: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()
    private var lastValue: String? = null
    private var lastTime: Long = 0L

    override fun analyze(image: ImageProxy) {
        val mediaImage = image.image
        if (mediaImage == null) { image.close(); return }
        val rotation = image.imageInfo.rotationDegrees
        // ML Kit reports bounding boxes against the upright (already rotated) image, so for the
        // quarter turns the reported width/height are swapped relative to the ImageProxy's own.
        val uprightW = if (rotation == 90 || rotation == 270) image.height else image.width
        val uprightH = if (rotation == 90 || rotation == 270) image.width else image.height
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val aimFilter = isAimedAt
                val best = barcodes
                    .filter { it.rawValue != null }
                    .filter { b ->
                        // With a filter set, a barcode ML Kit couldn't place is not a candidate —
                        // there's no way to tell whether the user was aiming at it.
                        aimFilter == null || b.boundingBox?.let { aimFilter(it, uprightW, uprightH) } == true
                    }
                    .maxByOrNull { b -> b.boundingBox?.let { r -> r.width() * r.height() } ?: 0 }
                val value = best?.rawValue ?: return@addOnSuccessListener
                val now = System.currentTimeMillis()
                if (value != lastValue || now - lastTime > debounceMs) {
                    lastValue = value
                    lastTime = now
                    onResult(value)
                }
            }
            .addOnCompleteListener { image.close() }
    }
}
