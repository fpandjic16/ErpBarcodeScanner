package com.erpbarcode.scanner.ui.component

import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.erpbarcode.scanner.R
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.erpbarcode.scanner.data.barcode.BarcodeAnalyzer
import timber.log.Timber
import java.util.concurrent.Executors
import com.erpbarcode.scanner.ui.theme.AppCamera

// Shared by the drawn reticle and the aim filter that decides which barcodes count — keep
// them driven off the same values so what the user sees can't drift from what's scanned.
private val RETICLE_W = 280.dp
private val RETICLE_H = 170.dp

@Composable
fun CameraPreview(
    onBarcode: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    val latestOnBarcode = rememberUpdatedState(onBarcode)
    val latestOnClose = rememberUpdatedState(onClose)

    val density = LocalDensity.current
    val reticleWpx = with(density) { RETICLE_W.toPx() }
    val reticleHpx = with(density) { RETICLE_H.toPx() }

    DisposableEffect(lifecycleOwner) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        // No tone here any more. The beep used to live in this component, which meant the camera
        // was audible and the hardware trigger silent, and neither obeyed any setting. Callers now
        // sound the outcome themselves, so both inputs behave the same and the operator can turn
        // it off.
        var cameraProvider: ProcessCameraProvider? = null
        // One accepted read per opening of the preview. Not a time window: the rule here is
        // "one read", and a window has to be guessed at.
        var consumed = false

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also { imageAnalysis ->
                        val analyzer = BarcodeAnalyzer(
                            isAimedAt = { box, imgW, imgH ->
                                isInsideReticle(
                                    box = box,
                                    imageW = imgW, imageH = imgH,
                                    viewW = previewView.width, viewH = previewView.height,
                                    reticleWpx = reticleWpx, reticleHpx = reticleHpx,
                                )
                            },
                        ) { barcode ->
                            // The analyser runs on its own executor and keeps working through
                            // frames already in flight while the close below travels to the main
                            // thread. Without this latch a neighbouring label drifting into the
                            // reticle in that gap would be reported as a second scan the operator
                            // never asked for — and because BarcodeAnalyzer's own guard is keyed
                            // on the value, a *different* code passes it unimpeded.
                            if (consumed) return@BarcodeAnalyzer
                            consumed = true
                            mainExecutor.execute {
                                latestOnBarcode.value(barcode)
                                latestOnClose.value()
                            }
                        }
                        imageAnalysis.setAnalyzer(analysisExecutor, analyzer)
                    }

                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )

                previewView.post {
                    if (previewView.width > 0 && previewView.height > 0) {
                        runCatching {
                            val point = previewView.meteringPointFactory.createPoint(0.5f, 0.5f)
                            val action = FocusMeteringAction.Builder(point)
                                .disableAutoCancel()
                                .build()
                            camera.cameraControl.startFocusAndMetering(action)
                        }
                    }
                }
            }.onFailure { e -> Timber.e(e, "CameraX bind failed") }
        }, mainExecutor)

        onDispose {
            cameraProvider?.unbindAll()
            analysisExecutor.shutdown()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        ScanningOverlay(modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 16.dp, top = 8.dp)
                .size(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(AppCamera.Scrim)
                .clickable(onClick = { latestOnClose.value() }),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.cd_close_camera),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun ScanningOverlay(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(
        modifier = modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val reticleW = RETICLE_W.toPx()
        val reticleH = RETICLE_H.toPx()
        val left   = (size.width  - reticleW) / 2f
        val top    = (size.height - reticleH) / 2f
        val right  = left + reticleW
        val bottom = top  + reticleH

        drawRect(color = AppCamera.Mask)
        drawRect(
            color     = Color.Transparent,
            topLeft   = Offset(left, top),
            size      = Size(reticleW, reticleH),
            blendMode = BlendMode.Clear,
        )

        // Static red aiming line across the middle of the reticle, mirroring the red laser
        // line on the Zebra hardware scanner: the user lines it up with the barcode rather
        // than waiting for a sweeping animation to pass over it. Drawn twice — a soft wide
        // pass under a crisp thin one — so it reads as a laser beam instead of a hairline.
        val aimY = top + reticleH / 2f
        drawLine(
            color       = AppCamera.LaserGlow,
            start       = Offset(left + 4f, aimY),
            end         = Offset(right - 4f, aimY),
            strokeWidth = 7.dp.toPx(),
        )
        drawLine(
            color       = AppCamera.Laser,
            start       = Offset(left + 4f, aimY),
            end         = Offset(right - 4f, aimY),
            strokeWidth = 2.dp.toPx(),
        )

        drawCornerBrackets(left, top, right, bottom)
    }
}

/**
 * True when [box] — a detected barcode's bounding box in upright image coordinates — has its
 * centre inside the on-screen aiming reticle.
 *
 * The mapping matters: `PreviewView` renders the feed FILL_CENTER, scaling the analysis image
 * up until it covers the view and then centre-cropping it. A rectangle drawn on the view
 * therefore does *not* cover the same fraction of the analysis image — e.g. a 3:4 image in a
 * 9:16 view has ~25% of its width cropped away off-screen. This inverts that transform
 * (view px -> image px) so the region actually tested is the rectangle the user sees.
 *
 * Returns true when the geometry isn't known yet (view not laid out), so a barcode is never
 * silently dropped just because the filter couldn't be evaluated.
 */
private fun isInsideReticle(
    box: android.graphics.Rect,
    imageW: Int, imageH: Int,
    viewW: Int, viewH: Int,
    reticleWpx: Float, reticleHpx: Float,
): Boolean {
    if (imageW <= 0 || imageH <= 0 || viewW <= 0 || viewH <= 0) return true

    val scale = maxOf(viewW.toFloat() / imageW, viewH.toFloat() / imageH)
    val cropX = (imageW * scale - viewW) / 2f
    val cropY = (imageH * scale - viewH) / 2f

    val leftView = (viewW - reticleWpx) / 2f
    val topView  = (viewH - reticleHpx) / 2f
    val left   = (leftView + cropX) / scale
    val top    = (topView + cropY) / scale
    val right  = (leftView + reticleWpx + cropX) / scale
    val bottom = (topView + reticleHpx + cropY) / scale

    return box.exactCenterX() in left..right && box.exactCenterY() in top..bottom
}

private fun DrawScope.drawCornerBrackets(
    left: Float, top: Float, right: Float, bottom: Float,
) {
    val cl = 22.dp.toPx()
    val cs = 3.dp.toPx()
    val c  = Color.White
    drawLine(c, Offset(left,  top),    Offset(left + cl, top),     cs)
    drawLine(c, Offset(left,  top),    Offset(left,  top + cl),    cs)
    drawLine(c, Offset(right, top),    Offset(right - cl, top),    cs)
    drawLine(c, Offset(right, top),    Offset(right, top + cl),    cs)
    drawLine(c, Offset(left,  bottom), Offset(left + cl, bottom),  cs)
    drawLine(c, Offset(left,  bottom), Offset(left, bottom - cl),  cs)
    drawLine(c, Offset(right, bottom), Offset(right - cl, bottom), cs)
    drawLine(c, Offset(right, bottom), Offset(right, bottom - cl), cs)
}
