package com.erpbarcode.scanner.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.ui.theme.AppSignal
import com.erpbarcode.scanner.ui.theme.monoLabel

/*
 * What a dialog shows before a profile is deleted, in the one form both such dialogs use: the
 * Profiles screen, where an operator removes somebody else, and the user info screen, where they
 * remove themselves. Written once so the two cannot say it differently.
 */

/** What deleting a profile would destroy. */
internal data class ProfileFootprint(val documents: Int, val scans: Int, val unsentScans: Int)

/**
 * Shown while a profile's documents and scans are being counted.
 *
 * Counting is one database open and two reads. It is quick, but asking "delete?" before knowing
 * the answer would be asking the operator to agree to an unknown, so the confirming button waits
 * for it.
 */
@Composable
internal fun ProfileFootprintCounting() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.profiles_delete_counting))
    }
}

/**
 * The scans a deletion would take, and how many of them the ERP has never had.
 *
 * Its own block, not a clause in the sentence above it. Documents come back on the next download;
 * scans do not come back at all. Draws nothing for a profile that holds no scan. [unsentHint] is
 * a last line for the dialog that offers a way out: it is shown only when there are unsent scans.
 */
@Composable
internal fun ProfileDeletionWarning(footprint: ProfileFootprint, unsentHint: String? = null) {
    if (footprint.scans <= 0) return
    val red = AppSignal.Danger
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(red.copy(alpha = 0.10f))
            .border(1.dp, red.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.profiles_delete_scans, footprint.scans, footprint.documents),
            style = MaterialTheme.typography.bodyMedium.copy(color = red, fontWeight = FontWeight.Bold),
        )
        if (footprint.unsentScans > 0) {
            // The half that exists here and nowhere else.
            Text(
                stringResource(R.string.profiles_delete_unsent, footprint.unsentScans),
                style = monoLabel.copy(color = red),
            )
            if (unsentHint != null) {
                Text(unsentHint, style = monoLabel.copy(color = red, fontWeight = FontWeight.Medium))
            }
        }
    }
}
