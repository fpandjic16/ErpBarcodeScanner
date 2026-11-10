package com.prima.barcode.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.prima.barcode.R
import androidx.core.content.ContextCompat
import com.prima.barcode.data.auth.UserProfile
import com.prima.barcode.data.auth.UserProfileStore
import com.prima.barcode.data.auth.parseLoginQr
import com.prima.barcode.data.barcode.DataWedgeManager
import com.prima.barcode.data.haptic.HapticEngine
import com.prima.barcode.data.sound.rememberSoundEngine
import com.prima.barcode.ui.component.CameraPreview
import com.prima.barcode.ui.component.PrimaTopBar
import com.prima.barcode.ui.theme.PrimaPalette
import com.prima.barcode.ui.theme.monoLabel
import com.prima.barcode.ui.theme.PrimaSignal

/**
 * The way into the app. Nothing else is reachable until somebody is signed in.
 *
 * This is not only a gate. Every recording stamps the operator who made it at the moment it is
 * written, so the app has to know who that is *before* the first scan — filling it in afterwards
 * is not possible, because by then the scan is already on disk and there is nothing to say who
 * made it. That is what the empty `userId` used to be.
 *
 * Known operators are listed so the common case is a tap and a password rather than typing a
 * username on a device with no comfortable keyboard.
 *
 * Signing in by QR belongs here and not only in [LoginSheet]. Before this screen existed the way
 * into the app *was* that sheet, so moving the gate in front of it quietly took the scanner away
 * from the one moment an operator most wants it — picking up a terminal with no keyboard worth
 * the name. The hardware trigger and the camera both feed the same [parseLoginQr].
 *
 * The one thing reachable from here without signing in is the external-system configuration — see
 * [onOpenConfig].
 */
@Composable
fun SignInScreen(
    profiles: List<UserProfile>,
    onSignIn: (username: String, password: String) -> Unit,
    busy: Boolean = false,
    error: String? = null,
    onErrorDismiss: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    // Opens the external-system configuration without signing in first; null hides the button.
    // The chicken and egg it breaks: signing in asks the server whether the password is good,
    // and a device out of the box has no server URL to ask, so without a way through from here
    // a fresh install could never be configured and so could never be signed into either.
    onOpenConfig: (() -> Unit)? = null,
    // Opens the app info (version, Android, ERP system and company) without signing in; null
    // hides the button. What support asks first, and often of someone who cannot sign in.
    onOpenAppInfo: (() -> Unit)? = null,
    // Blank on an install that has not loaded a configuration carrying one, which switches QR
    // sign-in off: every code then fails to decode and is reported as unreadable.
    loginQrKey: String = "",
    hapticEnabled: Boolean = true,
    soundEnabled: Boolean = true,
) {
    var username by remember { mutableStateOf(profiles.firstOrNull()?.displayName.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var cameraOpen by remember { mutableStateOf(false) }
    // Its own line rather than the [error] dialog: a mis-pull of the trigger is a routine event,
    // and a dialog for it would be in the way of the next attempt.
    var qrError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    // Resolved here because the scan callbacks below are not composable scopes.
    val invalidQrMessage = stringResource(R.string.login_qr_invalid)
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) cameraOpen = true }
    // The MC3300 ships in configurations with no camera at all, where the button used to open a
    // black overlay with nothing behind it. The hardware trigger still covers those devices.
    val hasCamera = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    // Scanner first: where there is one, the button drives it and no camera is offered. The button
    // used to exist only when there was a camera, so a camera-less MC3300 showed no way to scan a
    // login code at all — the trigger worked, but nothing on the screen said so.
    val hasScanner = remember { DataWedgeManager.isAvailable(context) }
    val hapticEngine = remember { HapticEngine(context) }
    val soundEngine = rememberSoundEngine()

    /**
     * One path for both scanners: the camera and the hardware trigger read the same code.
     *
     * Declared below the engines it uses, and it has to stay that way — a local function can
     * only capture locals that already exist above it.
     */
    fun applyScannedQr(raw: String) {
        // Any symbology can land here — an ordinary product barcode pulled on the trigger, or a
        // code made under a different key. Say so rather than leaving the fields unchanged.
        val scanned = parseLoginQr(raw, loginQrKey)
        if (scanned != null) {
            if (hapticEnabled) hapticEngine.confirm()
            if (soundEnabled) soundEngine.confirm()
            username = scanned.username
            password = scanned.password
            qrError = null
        } else {
            // Easy to miss the error line when the operator is looking at the scanner rather than
            // the screen, which on a barcode terminal is most of the time.
            if (hapticEnabled) hapticEngine.error()
            if (soundEnabled) soundEngine.error()
            qrError = invalidQrMessage
        }
    }

    // Registered for as long as the gate is up. Nothing else is composed while it is — the gate
    // early-returns — so no second receiver can double-deliver the same scan.
    val latestScan = rememberUpdatedState(::applyScannedQr)
    DisposableEffect(Unit) {
        val receiver = DataWedgeManager.createReceiver { raw -> latestScan.value(raw) }
        DataWedgeManager.register(context, receiver)
        onDispose { context.unregisterReceiver(receiver) }
    }

    val tooLong = username.trim().length > UserProfileStore.MAX_NAME_LENGTH
    val canSubmit = !busy && username.isNotBlank() && password.isNotEmpty() && !tooLong

    // A Box so the camera overlay can cover the screen; the sheet this replaced had the same.
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(PrimaPalette.Cream)) {
            PrimaTopBar(title = stringResource(R.string.signin_title), onBack = onBack)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (profiles.isNotEmpty()) {
                    Text(
                        stringResource(R.string.signin_known_operators),
                        style = monoLabel.copy(color = PrimaPalette.Ink3),
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(profiles, key = { it.id }) { profile ->
                            ProfileRow(
                                profile = profile,
                                selected = UserProfileStore.normalise(username) == profile.id,
                                onClick = { username = profile.displayName; password = "" },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.login_username)) },
                    singleLine = true,
                    isError = tooLong,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (tooLong) {
                    Text(
                        stringResource(R.string.signin_name_too_long, UserProfileStore.MAX_NAME_LENGTH),
                        style = monoLabel.copy(color = PrimaSignal.Danger),
                    )
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.login_password)) },
                    singleLine = true,
                    visualTransformation =
                        if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (hasScanner || hasCamera) {
                    OutlinedButton(
                        onClick = {
                            qrError = null
                            if (hasScanner) {
                                // Same read as a trigger pull; the result arrives through this
                                // screen's scan receiver, so nothing else is needed here.
                                DataWedgeManager.startScan(context)
                            } else {
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                    PackageManager.PERMISSION_GRANTED
                                if (granted) cameraOpen = true
                                else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        enabled = !busy,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(
                            Icons.Outlined.QrCodeScanner,
                            contentDescription = null,
                            tint = PrimaPalette.Ink3,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.login_scan_qr),
                            style = monoLabel.copy(color = PrimaPalette.Ink),
                        )
                    }
                }

                qrError?.let { Text(it, style = monoLabel, color = PrimaSignal.Danger) }

                Text(
                    stringResource(R.string.signin_first_time_note),
                    style = monoLabel.copy(color = PrimaPalette.Ink3),
                )

                onOpenConfig?.let { open ->
                    OutlinedButton(
                        onClick = open,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = null,
                            tint = PrimaPalette.Ink3,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.signin_open_config),
                            style = monoLabel.copy(color = PrimaPalette.Ink),
                        )
                    }
                }

                onOpenAppInfo?.let { open ->
                    OutlinedButton(
                        onClick = open,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = PrimaPalette.Ink3,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.app_info_title),
                            style = monoLabel.copy(color = PrimaPalette.Ink),
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (canSubmit) PrimaPalette.Coral else PrimaPalette.Coral.copy(alpha = 0.35f))
                    .clickable(enabled = canSubmit) { onSignIn(username.trim(), password) },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        stringResource(R.string.signin_action),
                        style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium),
                    )
                }
            }
        }

        if (cameraOpen) {
            CameraPreview(
                onBarcode = { raw ->
                    applyScannedQr(raw)
                    cameraOpen = false
                },
                onClose = { cameraOpen = false },
            )
        }
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = onErrorDismiss,
            title = { Text(stringResource(R.string.signin_failed_title), fontWeight = FontWeight.Bold) },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = onErrorDismiss) { Text(stringResource(R.string.btn_ok)) }
            },
        )
    }
}

@Composable
private fun ProfileRow(profile: UserProfile, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) PrimaPalette.CreamAlt else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(PrimaPalette.Coral),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                profile.displayName.take(2).uppercase(),
                style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Bold),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(profile.displayName, style = monoLabel.copy(color = PrimaPalette.Ink))
    }
}
