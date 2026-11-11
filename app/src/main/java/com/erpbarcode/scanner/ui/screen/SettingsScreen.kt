package com.erpbarcode.scanner.ui.screen

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.erpbarcode.scanner.data.auth.AppSettings
import com.erpbarcode.scanner.data.auth.DeviceConfiguration
import com.erpbarcode.scanner.data.auth.ExtSystemConfig
import com.erpbarcode.scanner.data.auth.ExtSystemDefaultsCompany
import com.erpbarcode.scanner.data.auth.ExtSystemEnvironment
import com.erpbarcode.scanner.data.model.Location
import com.erpbarcode.scanner.data.model.ResponsibilityCenter
import com.erpbarcode.scanner.data.model.User
import com.erpbarcode.scanner.ui.component.PrimaTopBar
import com.erpbarcode.scanner.ui.component.verticalScrollbar
import com.erpbarcode.scanner.ui.theme.PrimaPalette
import com.erpbarcode.scanner.ui.theme.Language
import com.erpbarcode.scanner.ui.theme.TextSize
import androidx.compose.ui.platform.LocalContext
import com.erpbarcode.scanner.ui.theme.monoLabel
import com.erpbarcode.scanner.ui.theme.uppercased
import androidx.compose.ui.res.stringResource
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.ui.theme.PrimaSignal
import com.erpbarcode.scanner.ui.theme.PrimaLine

// The enum can't hold a resource lookup, so the label is resolved here instead — same
// pattern as DocumentType.localizedDisplay() on the other screens.
@Composable
private fun TextSize.localizedLabel(): String = when (this) {
    TextSize.NORMAL -> stringResource(R.string.text_size_normal)
    TextSize.LARGER -> stringResource(R.string.text_size_larger)
}

@Composable
fun SettingsScreen(
    user: User?,
    location: Location?,
    rc: ResponsibilityCenter?,
    initial: AppSettings,
    onSave: (AppSettings) -> Unit,
    onDiscard: () -> Unit = {},
    onSaveExtSystemConfig: (ExtSystemConfig) -> Unit = {},
    loadExtSystemConfigDefaults: (fileName: String) -> DeviceConfiguration? = { null },
    parseExtSystemConfigJson: (String) -> DeviceConfiguration? = { null },
    // Exports what is on the device now, not the asset it came from.
    exportConfiguration: () -> String? = { null },
    listExtSystemDefaultsCompanies: () -> List<ExtSystemDefaultsCompany> = { emptyList() },
    onExport: () -> Unit = {},
    onClearCache: () -> Unit = {},
    onDeleteAllDocuments: () -> Unit = {},
    onChangeLocation: () -> Unit,
    onOpenExtSystemConfig: () -> Unit = {},
    onOpenProfiles: () -> Unit = {},
    onSignOut: () -> Unit,
    onSignInTap: () -> Unit = {},
    // For the System info block: which ERP this device talks to.
    environment: ExtSystemEnvironment? = null,
    companyName: String? = null,
) {
    var textSize by remember { mutableStateOf(initial.textSize) }
    var uppercaseText by remember { mutableStateOf(initial.uppercaseText) }
    var language by remember { mutableStateOf(initial.language) }
    var debounceTime by remember { mutableStateOf(initial.debounceTime) }
    var hapticEnabled by remember { mutableStateOf(initial.hapticEnabled) }
    var soundEnabled by remember { mutableStateOf(initial.soundEnabled) }
    var warnOnOver by remember { mutableStateOf(initial.warnOnOver) }
    var backgroundSync by remember { mutableStateOf(initial.backgroundSync) }
    var debuggerActive by remember { mutableStateOf(initial.debuggerActive) }
    // No controls on this screen — these two are edited on ExtSystemConfigScreen. They are held
    // here only so a loaded configuration can bring them and the exit-save carries them out with
    // everything else. Declared above buildSettings, which reads them: a local function can only
    // capture locals that already exist above it.
    var disabledDocTypes by remember { mutableStateOf(initial.disabledDocTypes) }
    var docTypeFilters by remember { mutableStateOf(initial.docTypeFilters) }

    fun buildSettings() = initial.copy(
        textSize = textSize,
        uppercaseText = uppercaseText,
        language = language,
        debounceTime = debounceTime,
        hapticEnabled = hapticEnabled,
        soundEnabled = soundEnabled,
        warnOnOver = warnOnOver,
        backgroundSync = backgroundSync,
        debuggerActive = debuggerActive,
        disabledDocTypes = disabledDocTypes,
        docTypeFilters = docTypeFilters,
    )

    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showDeleteAllDocumentsDialog by remember { mutableStateOf(false) }
    var showInsertSystemDefaultsDialog by remember { mutableStateOf(false) }
    var pendingExtSystemConfig by remember { mutableStateOf<ExtSystemConfig?>(null) }

    /**
     * Takes a loaded configuration apart into the two things this screen can hold.
     *
     * The ERP half is staged and only written by the exit-save, which is what the "(pending)"
     * label on the row is about. The device half goes into the local state that `buildSettings`
     * reads, so it travels out on that same save rather than by a separate path.
     */
    fun applyConfiguration(cfg: DeviceConfiguration) {
        pendingExtSystemConfig = cfg.extSystem
        disabledDocTypes = cfg.disabledDocTypes
        docTypeFilters = cfg.docTypeFilters
        debuggerActive = cfg.debuggerActive
    }
    var showExitDialog by remember { mutableStateOf(false) }

    fun attemptExit() {
        if (buildSettings() != initial || pendingExtSystemConfig != null) showExitDialog = true else onDiscard()
    }

    BackHandler { attemptExit() }

    val context = LocalContext.current

    val importExtSystemDefaultsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val json = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            val parsed = json?.let { parseExtSystemConfigJson(it) }
            if (parsed != null) {
                applyConfiguration(parsed)
                Toast.makeText(context, context.getString(R.string.ext_config_import_success), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.ext_config_import_parse_error), Toast.LENGTH_LONG).show()
            }
        }
    }

    val downloadExtSystemDefaultsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val text = exportConfiguration()
            val written = text != null && runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            }.isSuccess
            if (written) {
                Toast.makeText(context, context.getString(R.string.ext_config_defaults_saved), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.ext_config_defaults_save_error), Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(PrimaPalette.Cream)) {
        PrimaTopBar(
            title = stringResource(R.string.settings_title),
            subtitle = stringResource(R.string.settings_subtitle),
            onBack = { attemptExit() },
        )

        val listState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScrollbar(listState),
            contentPadding = PaddingValues(bottom = 32.dp),
            state = listState,
        ) {
            // ── Appearance ────────────────────────────────────────────────────
            item { SettingsSectionHeader(stringResource(R.string.settings_sec_appearance)) }

            item {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                    SettingsRowHeader(
                        icon = Icons.Outlined.TextFields,
                        label = stringResource(R.string.settings_text_size),
                        description = stringResource(R.string.settings_text_size_desc),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 74.dp, end = 20.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextSize.entries.forEach { size ->
                            SettingsChoiceChip(
                                label = size.localizedLabel(),
                                selected = size == textSize,
                                onClick = { textSize = size },
                            )
                        }
                    }
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Outlined.TextFormat,
                        label = stringResource(R.string.settings_uppercase),
                        description = stringResource(R.string.settings_uppercase_desc),
                        checked = uppercaseText,
                        onCheckedChange = { uppercaseText = it },
                    )
                    SettingsDivider()
                    var langMenuOpen by remember { mutableStateOf(false) }
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { langMenuOpen = true }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SettingsIcon(Icons.Outlined.Language)
                            Spacer(Modifier.width(14.dp))
                            SettingsRowText(
                                label = stringResource(R.string.settings_language),
                                description = stringResource(R.string.settings_language_desc),
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                language.label,
                                style = monoLabel.copy(color = PrimaPalette.Ink3, fontWeight = FontWeight.Medium),
                            )
                        }
                        DropdownMenu(
                            expanded = langMenuOpen,
                            onDismissRequest = { langMenuOpen = false },
                        ) {
                            Language.entries.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang.label) },
                                    onClick = { language = lang; langMenuOpen = false },
                                    leadingIcon = if (lang == language) {
                                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                )
                            }
                        }
                    }
                }
            }

            // ── Scanning ──────────────────────────────────────────────────────
            item { SettingsSectionHeader(stringResource(R.string.settings_sec_scanning)) }

            item {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                    SettingsRowHeader(
                        icon = Icons.Outlined.Timer,
                        label = stringResource(R.string.settings_debounce),
                        description = stringResource(R.string.settings_debounce_desc),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 74.dp, end = 20.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(200, 300, 500, 1000, 2000).forEach { ms ->
                            SettingsChoiceChip(
                                label = if (ms >= 1000) "${ms / 1000}s" else "${ms}ms",
                                selected = ms == debounceTime,
                                onClick = { debounceTime = ms },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp),
                            )
                        }
                    }
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Outlined.Vibration,
                        label = stringResource(R.string.settings_haptic),
                        description = stringResource(R.string.settings_haptic_desc),
                        checked = hapticEnabled,
                        onCheckedChange = { hapticEnabled = it },
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.AutoMirrored.Outlined.VolumeUp,
                        label = stringResource(R.string.settings_sound),
                        description = stringResource(R.string.settings_sound_desc),
                        checked = soundEnabled,
                        onCheckedChange = { soundEnabled = it },
                    )
                    SettingsDivider()
                    SettingsToggleRow(
                        icon = Icons.Outlined.Warning,
                        label = stringResource(R.string.settings_warn_over),
                        description = stringResource(R.string.settings_warn_over_desc),
                        checked = warnOnOver,
                        onCheckedChange = { warnOnOver = it },
                    )
                }
            }

            // ── Sync ──────────────────────────────────────────────────────────
            item { SettingsSectionHeader(stringResource(R.string.settings_sec_sync)) }

            item {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                    SettingsToggleRow(
                        icon = Icons.Outlined.Sync,
                        label = stringResource(R.string.settings_background_sync),
                        description = stringResource(R.string.settings_background_sync_desc),
                        checked = backgroundSync,
                        onCheckedChange = { backgroundSync = it },
                    )
                }
            }

            // ── External System Configuration ─────────────────────────────────
            item { SettingsSectionHeader(stringResource(R.string.settings_sec_ext_config)) }

            item {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                    SettingsNavRow(
                        icon = Icons.Outlined.Dns,
                        label = stringResource(R.string.settings_server_endpoints),
                        description = stringResource(R.string.settings_server_endpoints_desc),
                        onClick = onOpenExtSystemConfig,
                    )
                }
            }

            // ── Debug ─────────────────────────────────────────────────────────
            item { SettingsSectionHeader(stringResource(R.string.settings_sec_debug)) }

            item {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                    SettingsToggleRow(
                        icon = Icons.Outlined.BugReport,
                        label = stringResource(R.string.settings_debugger_active),
                        description = stringResource(R.string.settings_debugger_active_desc),
                        checked = debuggerActive,
                        onCheckedChange = { debuggerActive = it },
                    )
                    SettingsDivider()
                    SettingsNavRow(
                        icon = Icons.Outlined.FileDownload,
                        label = stringResource(R.string.settings_export),
                        description = stringResource(R.string.settings_export_desc),
                        onClick = onExport,
                    )
                    SettingsDivider()
                    SettingsNavRow(
                        icon = Icons.Outlined.SettingsSuggest,
                        label = stringResource(R.string.settings_insert_defaults_dialog_title) +
                            if (pendingExtSystemConfig != null) " " + stringResource(R.string.settings_insert_defaults_pending) else "",
                        description = stringResource(R.string.settings_insert_defaults_desc),
                        onClick = { showInsertSystemDefaultsDialog = true },
                    )
                    SettingsDivider()
                    SettingsDangerRow(
                        label = stringResource(R.string.settings_clear_cache),
                        description = stringResource(R.string.settings_clear_cache_desc),
                        onClick = { showClearCacheDialog = true },
                    )
                    SettingsDivider()
                    SettingsDangerRow(
                        label = stringResource(R.string.settings_delete_all),
                        description = stringResource(R.string.settings_delete_all_desc),
                        onClick = { showDeleteAllDocumentsDialog = true },
                    )
                }
            }

            // ── System info ───────────────────────────────────────────────────
            item { SettingsSectionHeader(stringResource(R.string.settings_sec_system_info)) }

            // The same block the sign-in screen shows on its own screen — see AppInfo.kt.
            item { AppInfoCard(environment, companyName) }

            // ── Account ───────────────────────────────────────────────────────
            item { SettingsSectionHeader(stringResource(R.string.settings_sec_account)) }

            item {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = user == null, onClick = onSignInTap)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (user != null) PrimaPalette.SlateAlt else PrimaLine.Vacant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                user?.initials ?: "?",
                                style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                user?.displayName ?: stringResource(R.string.settings_not_signed_in),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = if (user != null) PrimaPalette.Ink else PrimaPalette.Ink3,
                                ),
                            )
                            if (user != null) {
                                Text(
                                    user.username,
                                    style = monoLabel.copy(color = PrimaPalette.Ink3),
                                )
                            }
                        }
                    }
                    SettingsDivider()
                    // Sits in Account because that section is already about who uses this device.
                    SettingsNavRow(
                        icon = Icons.Outlined.Group,
                        label = stringResource(R.string.settings_profiles),
                        description = stringResource(R.string.settings_profiles_desc),
                        onClick = onOpenProfiles,
                    )
                    SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onSignOut)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Logout,
                            contentDescription = null,
                            tint = PrimaSignal.Danger,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            stringResource(R.string.settings_sign_out),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = PrimaSignal.Danger,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            }
        }
    }

    if (showInsertSystemDefaultsDialog) {
        ConfigLoadFlow(
            title = stringResource(R.string.settings_insert_defaults_dialog_title),
            listCompanies = listExtSystemDefaultsCompanies,
            loadDefaults = loadExtSystemConfigDefaults,
            onLoaded = { applyConfiguration(it) },
            onExport = { downloadExtSystemDefaultsLauncher.launch(EXPORT_FILE_NAME) },
            onImport = { importExtSystemDefaultsLauncher.launch("application/json") },
            onClose = { showInsertSystemDefaultsDialog = false },
        )
    }

    if (showClearCacheDialog) {
        SettingsConfirmDialog(
            title = stringResource(R.string.dialog_clear_cache_title),
            text = stringResource(R.string.dialog_clear_cache_text),
            onConfirm = { showClearCacheDialog = false; onClearCache() },
            onDismiss = { showClearCacheDialog = false },
        )
    }

    if (showDeleteAllDocumentsDialog) {
        SettingsConfirmDialog(
            title = stringResource(R.string.settings_delete_all_title),
            text = stringResource(R.string.settings_delete_all_text),
            onConfirm = { showDeleteAllDocumentsDialog = false; onDeleteAllDocuments() },
            onDismiss = { showDeleteAllDocumentsDialog = false },
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.dialog_save_changes_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.settings_exit_dialog_text)) },
            confirmButton = {
                Button(onClick = {
                    showExitDialog = false
                    onSave(buildSettings())
                    pendingExtSystemConfig?.let { onSaveExtSystemConfig(it) }
                }) {
                    Text(stringResource(R.string.btn_yes), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showExitDialog = false; onDiscard() }) { Text(stringResource(R.string.btn_no)) }
            },
        )
    }
}
