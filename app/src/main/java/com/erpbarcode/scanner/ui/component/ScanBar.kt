package com.erpbarcode.scanner.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.ui.theme.PrimaPalette

/**
 * Always-docked slate bar at the bottom of the Recording screen.
 * Houses the ScanField and the multiplier control.
 */
@Composable
fun ScanBar(
    onScan: (String) -> Unit,
    onCameraTap: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = PrimaPalette.Slate,
    showCamera: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        ScanField(
            placeholder = stringResource(R.string.scan_placeholder),
            onScan = onScan,
            onCameraTap = onCameraTap,
            showCamera = showCamera,
            dark = true,
        )
    }
}
