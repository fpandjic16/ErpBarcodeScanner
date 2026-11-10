package com.prima.barcode.ui.screen

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
import com.prima.barcode.data.auth.AppSettings
import com.prima.barcode.data.auth.DeviceConfiguration
import com.prima.barcode.data.auth.ExtSystemConfig
import com.prima.barcode.data.auth.ExtSystemDefaultsCompany
import com.prima.barcode.data.auth.ExtSystemEnvironment
import com.prima.barcode.data.model.Location
import com.prima.barcode.data.model.ResponsibilityCenter
import com.prima.barcode.data.model.User
import com.prima.barcode.ui.component.PrimaTopBar
import com.prima.barcode.ui.component.verticalScrollbar
import com.prima.barcode.ui.theme.PrimaPalette
import com.prima.barcode.ui.theme.Language
import com.prima.barcode.ui.theme.TextSize
import androidx.compose.ui.platform.LocalContext
import com.prima.barcode.ui.theme.monoLabel
import com.prima.barcode.ui.theme.uppercased
import androidx.compose.ui.res.stringResource
import com.prima.barcode.R
import com.prima.barcode.ui.theme.PrimaSignal
import com.prima.barcode.ui.theme.PrimaLine

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
    var showCompanyPickDialog by remember { mutableStateOf(false) }
    // Asked before the company: "which system" is the question that carries the risk.
    var showEnvironmentPickDialog by remember { mutableStateOf(false) }
    var pickedEnvironment by remember { mutableStateOf<ExtSystemEnvironment?>(null) }
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
            item { SectionHeader(stringResource(R.string.settings_sec_appearance)) }

            item {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsIcon(Icons.Outlined.TextFields)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_text_size),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = PrimaPalette.Ink,
                                    fontWeight = FontWeight.Normal,
                                ),
                            )
                            Text(
                                stringResource(R.string.settings_text_size_desc),
                                style = monoLabel.copy(color = PrimaPalette.Ink3),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 74.dp, end = 20.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextSize.entries.forEach { size ->
                            val selected = size == textSize
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) PrimaPalette.Slate else PrimaPalette.CreamAlt)
                                    .clickable { textSize = size }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    size.localizedLabel(),
                                    style = monoLabel.copy(
                                        color = if (selected) Color.White else PrimaPalette.Ink2,
                                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                    ),
                                )
                            }
                        }
                    }
                    SettingsDivider()
                    ToggleRow(
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.settings_language),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = PrimaPalette.Ink,
                                        fontWeight = FontWeight.Normal,
                                    ),
                                )
                                Text(
                                    stringResource(R.string.settings_language_desc),
                                    style = monoLabel.copy(color = PrimaPalette.Ink3),
                                )
                            }
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
            item { SectionHeader(stringResource(R.string.settings_sec_scanning)) }

            item {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsIcon(Icons.Outlined.Timer)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_debounce),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = PrimaPalette.Ink,
                                    fontWeight = FontWeight.Normal,
                                ),
                            )
                            Text(
                                stringResource(R.string.settings_debounce_desc),
                                style = monoLabel.copy(color = PrimaPalette.Ink3),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 74.dp, end = 20.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(200, 300, 500, 1000, 2000).forEach { ms ->
                            val selected = ms == debounceTime
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) PrimaPalette.Slate else PrimaPalette.CreamAlt)
                                    .clickable { debounceTime = ms }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (ms >= 1000) "${ms / 1000}s" else "${ms}ms",
                                    style = monoLabel.copy(
                                        color = if (selected) Color.White else PrimaPalette.Ink2,
                                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                    ),
                                )
                            }
                        }
                    }
                    SettingsDivider()
                    ToggleRow(
                        icon = Icons.Outlined.Vibration,
                        label = stringResource(R.string.settings_haptic),
                        description = stringResource(R.string.settings_haptic_desc),
                        checked = hapticEnabled,
                        onCheckedChange = { hapticEnabled = it },
                    )
                    SettingsDivider()
                    ToggleRow(
                        icon = Icons.AutoMirrored.Outlined.VolumeUp,
                        label = stringResource(R.string.settings_sound),
                        description = stringResource(R.string.settings_sound_desc),
                        checked = soundEnabled,
                        onCheckedChange = { soundEnabled = it },
                    )
                    SettingsDivider()
                    ToggleRow(
                        icon = Icons.Outlined.Warning,
                        label = stringResource(R.string.settings_warn_over),
                        description = stringResource(R.string.settings_warn_over_desc),
                        checked = warnOnOver,
                        onCheckedChange = { warnOnOver = it },
                    )
                }
            }

            // ── Sync ──────────────────────────────────────────────────────────
            item { SectionHeader(stringResource(R.string.settings_sec_sync)) }

            item {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                    ToggleRow(
                        icon = Icons.Outlined.Sync,
                        label = stringResource(R.string.settings_background_sync),
                        description = stringResource(R.string.settings_background_sync_desc),
                        checked = backgroundSync,
                        onCheckedChange = { backgroundSync = it },
                    )
                }
            }

            // ── External System Configuration ─────────────────────────────────────────────────────
            item { SectionHeader(stringResource(R.string.settings_sec_ext_config)) }

            item {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenExtSystemConfig)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsIcon(Icons.Outlined.Dns)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_server_endpoints),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = PrimaPalette.Ink,
                                    fontWeight = FontWeight.Normal,
                                ),
                            )
                            Text(
                                stringResource(R.string.settings_server_endpoints_desc),
                                style = monoLabel.copy(color = PrimaPalette.Ink3),
                            )
                        }
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = PrimaPalette.Ink4,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // ── Debug ─────────────────────────────────────────────────────────
            item { SectionHeader(stringResource(R.string.settings_sec_debug)) }

            item {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                    ToggleRow(
                        icon = Icons.Outlined.BugReport,
                        label = stringResource(R.string.settings_debugger_active),
                        description = stringResource(R.string.settings_debugger_active_desc),
                        checked = debuggerActive,
                        onCheckedChange = { debuggerActive = it },
                    )
                    SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onExport)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsIcon(Icons.Outlined.FileDownload)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_export),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = PrimaPalette.Ink,
                                    fontWeight = FontWeight.Normal,
                                ),
                            )
                            Text(
                                stringResource(R.string.settings_export_desc),
                                style = monoLabel.copy(color = PrimaPalette.Ink3),
                            )
                        }
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = PrimaPalette.Ink4,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showInsertSystemDefaultsDialog = true }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsIcon(Icons.Outlined.SettingsSuggest)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_insert_defaults_dialog_title) +
                                    if (pendingExtSystemConfig != null) " " + stringResource(R.string.settings_insert_defaults_pending) else "",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = PrimaPalette.Ink,
                                    fontWeight = FontWeight.Normal,
                                ),
                            )
                            Text(
                                stringResource(R.string.settings_insert_defaults_desc),
                                style = monoLabel.copy(color = PrimaPalette.Ink3),
                            )
                        }
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = PrimaPalette.Ink4,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showClearCacheDialog = true }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PrimaSignal.DangerBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = SignOutRed, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_clear_cache),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = SignOutRed,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                            Text(
                                stringResource(R.string.settings_clear_cache_desc),
                                style = monoLabel.copy(color = PrimaPalette.Ink3),
                            )
                        }
                    }
                    SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDeleteAllDocumentsDialog = true }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PrimaSignal.DangerBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = SignOutRed, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_delete_all),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = SignOutRed,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                            Text(
                                stringResource(R.string.settings_delete_all_desc),
                                style = monoLabel.copy(color = PrimaPalette.Ink3),
                            )
                        }
                    }
                }
            }

            // ── Account ──────────────────────────────────────────────────────
            // System info
            item { SectionHeader(stringResource(R.string.settings_sec_system_info)) }

            // The same block the sign-in screen shows on its own screen — see AppInfo.kt.
            item { AppInfoCard(environment, companyName) }

            item { SectionHeader(stringResource(R.string.settings_sec_account)) }

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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenProfiles)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsIcon(Icons.Outlined.Group)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_profiles),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = PrimaPalette.Ink,
                                    fontWeight = FontWeight.Normal,
                                ),
                            )
                            Text(
                                stringResource(R.string.settings_profiles_desc),
                                style = monoLabel.copy(color = PrimaPalette.Ink3),
                            )
                        }
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = PrimaPalette.Ink4,
                            modifier = Modifier.size(18.dp),
                        )
                    }
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
                            tint = SignOutRed,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            stringResource(R.string.settings_sign_out),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = SignOutRed,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            }
        }
    }

    if (showInsertSystemDefaultsDialog) {
        Dialog(onDismissRequest = { showInsertSystemDefaultsDialog = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.settings_insert_defaults_dialog_title), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            showInsertSystemDefaultsDialog = false
                            showEnvironmentPickDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.ext_config_load_builtin), fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = {
                            showInsertSystemDefaultsDialog = false
                            downloadExtSystemDefaultsLauncher.launch(EXPORT_FILE_NAME)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.ext_config_export_current), fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = {
                            showInsertSystemDefaultsDialog = false
                            importExtSystemDefaultsLauncher.launch("application/json")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.ext_config_load_from_file), fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(
                        onClick = { showInsertSystemDefaultsDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }
        }
    }

    if (showEnvironmentPickDialog) {
        Dialog(onDismissRequest = { showEnvironmentPickDialog = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.ext_config_pick_environment_title), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    ExtSystemEnvironment.entries.forEach { env ->
                        OutlinedButton(
                            onClick = {
                                showEnvironmentPickDialog = false
                                pickedEnvironment = env
                                showCompanyPickDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(env.labelRes), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    TextButton(
                        onClick = { showEnvironmentPickDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }
        }
    }

    if (showCompanyPickDialog) {
        Dialog(onDismissRequest = { showCompanyPickDialog = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.ext_config_pick_company_title), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    val companies = remember(pickedEnvironment) {
                        listExtSystemDefaultsCompanies().filter { it.environment == pickedEnvironment }
                    }
                    if (companies.isEmpty()) {
                        Text(
                            stringResource(R.string.ext_config_no_companies_found),
                            style = monoLabel.copy(color = PrimaPalette.Ink3),
                        )
                    }
                    companies.forEach { company ->
                        OutlinedButton(
                            // Shown but not loadable when the file has been added and not filled
                            // in: its blank addresses would wipe a working configuration.
                            enabled = company.isConfigured,
                            onClick = {
                                showCompanyPickDialog = false
                                val defaults = loadExtSystemConfigDefaults(company.assetFileName)
                                if (defaults != null) {
                                    applyConfiguration(defaults)
                                    Toast.makeText(context, context.getString(R.string.ext_config_import_success), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.ext_config_company_load_error, company.assetFileName), Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (company.isConfigured) company.label
                                else stringResource(R.string.ext_config_company_not_configured, company.label),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    TextButton(
                        onClick = { showCompanyPickDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }
        }
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text(stringResource(R.string.dialog_clear_cache_title)) },
            text = { Text(stringResource(R.string.dialog_clear_cache_text)) },
            confirmButton = {
                TextButton(
                    onClick = { showClearCacheDialog = false; onClearCache() },
                    colors = ButtonDefaults.textButtonColors(contentColor = SignOutRed),
                ) { Text(stringResource(R.string.btn_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }

    if (showDeleteAllDocumentsDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDocumentsDialog = false },
            title = { Text(stringResource(R.string.settings_delete_all_title)) },
            text = { Text(stringResource(R.string.settings_delete_all_text)) },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteAllDocumentsDialog = false; onDeleteAllDocuments() },
                    colors = ButtonDefaults.textButtonColors(contentColor = SignOutRed),
                ) { Text(stringResource(R.string.btn_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDocumentsDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            },
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

private val SignOutRed = PrimaSignal.Danger

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercased,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
        style = monoLabel.copy(color = PrimaPalette.Ink3),
    )
}

@Composable
internal fun SettingsIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(PrimaPalette.CreamAlt),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = PrimaPalette.Slate, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 74.dp),
        color = PrimaLine.Divider,
    )
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    comingSoon: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (comingSoon) 0.45f else 1f)
            .then(if (!comingSoon) Modifier.clickable { onCheckedChange(!checked) } else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = PrimaPalette.Ink,
                        fontWeight = FontWeight.Normal,
                    ),
                )
                if (comingSoon) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(PrimaPalette.CreamAlt)
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(
                            stringResource(R.string.settings_coming_soon),
                            style = monoLabel.copy(
                                color = PrimaPalette.Ink3,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            }
            Text(description, style = monoLabel.copy(color = PrimaPalette.Ink3))
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = !comingSoon,
            colors = SwitchDefaults.colors(
                checkedTrackColor = PrimaPalette.Slate,
                checkedThumbColor = Color.White,
            ),
        )
    }
}

