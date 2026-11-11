package com.erpbarcode.scanner.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.data.auth.UserProfile
import com.erpbarcode.scanner.ui.component.AppTopBar
import com.erpbarcode.scanner.ui.component.verticalScrollbar
import com.erpbarcode.scanner.ui.theme.AppPalette
import com.erpbarcode.scanner.ui.theme.monoLabel
import com.erpbarcode.scanner.ui.theme.AppSignal
import com.erpbarcode.scanner.ui.theme.AppLine

private val SignOutRed = AppSignal.Danger

/** What deleting a profile would destroy. Null while it is still being counted. */
private data class Footprint(val documents: Int, val scans: Int, val unsentScans: Int)

/**
 * The operators this device knows, and the only way to remove one.
 *
 * A profile could previously only ever be created: `UserProfileStore.enroll` runs on the first
 * successful online sign-in and had no opposite. Someone who left the company stayed on the
 * sign-in screen for good, and their database stayed on disk, reachable only by wiping the whole
 * application's data, which takes everybody else with it.
 *
 * Removing an operator takes their documents and recordings with it, which is why this screen is
 * mostly a confirmation dialog with a list attached.
 */
@Composable
fun ProfilesScreen(
    profiles: List<UserProfile>,
    signedInId: String?,
    onBack: () -> Unit,
    // Counting opens the other operator's database, so it is asked for only when a deletion is
    // actually proposed — never merely to draw the list.
    loadFootprint: (profileId: String, onResult: (Int, Int, Int) -> Unit) -> Unit,
    onDelete: (profileId: String) -> Unit,
) {
    var pending by remember { mutableStateOf<UserProfile?>(null) }
    var footprint by remember { mutableStateOf<Footprint?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(AppPalette.Cream)) {
        AppTopBar(title = stringResource(R.string.profiles_title), onBack = onBack)

        val listState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScrollbar(listState),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.profiles_explainer),
                    style = monoLabel.copy(color = AppPalette.Ink3),
                )
            }
            items(profiles, key = { it.id }) { profile ->
                ProfileManageRow(
                    profile = profile,
                    isSignedIn = profile.id == signedInId,
                    onDelete = {
                        pending = profile
                        footprint = null
                        loadFootprint(profile.id) { docs, scans, unsent ->
                            footprint = Footprint(docs, scans, unsent)
                        }
                    },
                )
            }
        }
    }

    pending?.let { profile ->
        val counts = footprint
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.profiles_delete_title), fontWeight = FontWeight.Bold) },
            text = {
                // Counting is one database open and two reads. It is quick, but asking "delete?"
                // before knowing the answer would be asking the operator to agree to an unknown.
                if (counts == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.profiles_delete_counting))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.profiles_delete_text, profile.displayName))
                        if (counts.scans > 0) {
                            // Its own block, not a clause in the sentence above. Documents come
                            // back on the next download; scans do not come back at all.
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SignOutRed.copy(alpha = 0.10f))
                                    .border(1.dp, SignOutRed.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    stringResource(
                                        R.string.profiles_delete_scans,
                                        counts.scans,
                                        counts.documents,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = SignOutRed,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                                if (counts.unsentScans > 0) {
                                    // The half that exists here and nowhere else.
                                    Text(
                                        stringResource(R.string.profiles_delete_unsent, counts.unsentScans),
                                        style = monoLabel.copy(color = SignOutRed),
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(profile.id); pending = null },
                    enabled = counts != null,
                    colors = ButtonDefaults.textButtonColors(contentColor = SignOutRed),
                ) { Text(stringResource(R.string.profiles_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }
}

@Composable
private fun ProfileManageRow(profile: UserProfile, isSignedIn: Boolean, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, AppLine.CardBorder, RoundedCornerShape(14.dp))
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(AppPalette.Coral),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                profile.displayName.take(2).uppercase(),
                style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Bold),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                profile.displayName,
                style = MaterialTheme.typography.bodyMedium.copy(color = AppPalette.Ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isSignedIn) {
                Text(
                    stringResource(R.string.profiles_signed_in),
                    style = monoLabel.copy(color = AppPalette.Ink3),
                )
            }
        }
        // No delete on your own row. Every layer below refuses it as well, but an affordance that
        // cannot work should not be drawn at all.
        if (!isSignedIn) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.DeleteForever,
                    contentDescription = stringResource(R.string.profiles_delete_confirm),
                    tint = SignOutRed,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
