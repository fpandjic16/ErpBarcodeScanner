package com.erpbarcode.scanner.ui.screen

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.erpbarcode.scanner.data.auth.DeviceConfiguration
import com.erpbarcode.scanner.data.auth.ExtSystemConfig
import com.erpbarcode.scanner.data.auth.ExtSystemCredentials
import com.erpbarcode.scanner.data.auth.ExtSystemDefaultsCompany
import com.erpbarcode.scanner.data.auth.ExtSystemEnvironment
import com.erpbarcode.scanner.data.auth.RecordingUploadMode
import com.erpbarcode.scanner.data.model.DocTypeFilterMode
import com.erpbarcode.scanner.data.model.DocumentType
import com.erpbarcode.scanner.ui.component.AppAlertDialog
import com.erpbarcode.scanner.ui.component.AppTopBar
import com.erpbarcode.scanner.ui.theme.AppPalette
import com.erpbarcode.scanner.ui.theme.monoLabel
import androidx.compose.ui.res.stringResource
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.ui.theme.AppLine

private val TTL_OPTIONS = listOf(8, 24, 48, 168)

// Short unit symbols, from resources so Macedonian can write ч and д.
@Composable
private fun Int.ttlLabel(): String =
    if (this == 168) stringResource(R.string.ext_config_ttl_days_short, 7)
    else stringResource(R.string.ext_config_ttl_hours_short, this)

/**
 * Suggested name for an exported configuration; the file picker lets the user change it.
 *
 * Internal rather than file-private: SettingsScreen offers the same export from its own
 * "Insert system defaults" dialog, and two spellings of one filename is how they drift apart.
 */
internal const val EXPORT_FILE_NAME = "barcode_configuration.json"

@Composable
fun ExtSystemConfigScreen(
    initial: ExtSystemConfig,
    onSave: (ExtSystemConfig) -> Unit,
    onDiscard: () -> Unit = {},
    loadDefaults: (fileName: String) -> DeviceConfiguration? = { null },
    // Exports what is on the device now, not the asset it came from — so one device set up by
    // hand can become the template for the rest.
    exportConfiguration: () -> String? = { null },
    listCompanies: () -> List<ExtSystemDefaultsCompany> = { emptyList() },
    disabledDocTypes: Set<String> = emptySet(),
    onDisabledDocTypesChange: (Set<String>) -> Unit = {},
    docTypeFilters: Map<String, DocTypeFilterMode> = emptyMap(),
    onDocTypeFiltersChange: (Map<String, DocTypeFilterMode>) -> Unit = {},
    savedCredentials: ExtSystemCredentials? = null,
    onTestConnection: (
        serverBaseUrl: String,
        username: String,
        password: String,
        // onResult with message == null means "cancelled" — reset the testing state without showing a result dialog.
        onResult: (success: Boolean, message: String?) -> Unit,
    ) -> Unit = { _, _, _, cb -> cb(false, "Test connection is not wired up") },
    onImportJson: ((json: String) -> DeviceConfiguration?)? = null,
    // No matching read parameter: this screen has no debugger control of its own, it only has
    // to be able to carry the value a loaded configuration brings.
    onDebuggerActiveChange: (Boolean) -> Unit = {},
    hapticEnabled: Boolean = true,
    soundEnabled: Boolean = true,
) {
    var serverBaseUrl             by remember { mutableStateOf(initial.serverBaseUrl) }
    var domain                   by remember { mutableStateOf(initial.domain) }
    var loginQrKey               by remember { mutableStateOf(initial.loginQrKey) }
    var ttlHours                 by remember { mutableIntStateOf(initial.credentialTtlHours) }
    var documentLinesUrl         by remember { mutableStateOf(initial.documentLinesUrl) }
    val documentTypeCodes = remember {
        mutableStateMapOf<DocumentType, String>().also { map ->
            DocumentType.entries.forEach { map[it] = initial.docTypeCodeFor(it) }
        }
    }
    var recordingSyncUrl by remember { mutableStateOf(initial.recordingSyncUrl) }
    var locationsUrl     by remember { mutableStateOf(initial.locationsUrl) }
    var inventoryBatchesUrl by remember { mutableStateOf(initial.inventoryBatchesUrl) }
    var uploadMode          by remember { mutableStateOf(initial.uploadMode) }
    var documentUploadUrl   by remember { mutableStateOf(initial.documentUploadUrl) }

    var showLoginSheet by remember { mutableStateOf(false) }
    var testing        by remember { mutableStateOf(false) }
    var testResult     by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showLoadDialog by remember { mutableStateOf(false) }
    var environment by remember { mutableStateOf(initial.environment) }
    // Saving with a server address but no environment asks first; see [requestSave].
    var showEnvironmentMissingDialog by remember { mutableStateOf(false) }
    var showEnvironmentSetDialog     by remember { mutableStateOf(false) }

    val context = LocalContext.current

    fun buildConfig() = ExtSystemConfig(
        serverBaseUrl            = serverBaseUrl.trim(),
        credentialTtlHours       = ttlHours,
        documentLinesUrl         = documentLinesUrl.trim(),
        documentTypeCodes        = documentTypeCodes.toMap(),
        recordingSyncUrl = recordingSyncUrl.trim(),
        locationsUrl     = locationsUrl.trim(),
        domain           = domain.trim(),
        // Carried through untouched: neither has an editable field on this screen, and
        // rebuilding the config without them would wipe them every time it is saved.
        loginQrKey       = loginQrKey,
        environment      = environment,
        inventoryBatchesUrl = inventoryBatchesUrl.trim(),
        uploadMode       = uploadMode,
        documentUploadUrl = documentUploadUrl.trim(),
    )

    /**
     * Saves, unless the device would be left pointing at a server without knowing which system it
     * is. That is asked about rather than refused: the operator may genuinely not know yet, but
     * should not end up there without noticing. The main menu shows the environment, and a blank
     * there is easy to overlook; the moment of saving is not.
     */
    fun requestSave() {
        val config = buildConfig()
        if (config.serverBaseUrl.isNotBlank() && config.environment == null) showEnvironmentMissingDialog = true
        else onSave(config)
    }

    /**
     * Fills the form from a loaded configuration, and applies the device settings that come with
     * it.
     *
     * The ERP half lands in this screen's own fields and is saved with the rest of the form. The
     * device half has no form fields — the enable switches and Filter-by controls write straight
     * through their callbacks — so it is applied here and now. Before this, loading a
     * configuration filled the URLs and codes and silently left the two controls beside them
     * untouched: same screen, half of it loading.
     */
    fun applyConfiguration(cfg: DeviceConfiguration) {
        val c = cfg.extSystem
        serverBaseUrl = c.serverBaseUrl
        ttlHours = c.credentialTtlHours
        documentLinesUrl = c.documentLinesUrl
        DocumentType.entries.forEach { documentTypeCodes[it] = c.docTypeCodeFor(it) }
        recordingSyncUrl = c.recordingSyncUrl
        locationsUrl = c.locationsUrl
        domain = c.domain
        loginQrKey = c.loginQrKey
        environment = c.environment
        inventoryBatchesUrl = c.inventoryBatchesUrl
        uploadMode = c.uploadMode
        documentUploadUrl = c.documentUploadUrl

        onDisabledDocTypesChange(cfg.disabledDocTypes)
        onDocTypeFiltersChange(cfg.docTypeFilters)
        onDebuggerActiveChange(cfg.debuggerActive)
    }

    fun attemptExit() {
        if (buildConfig() != initial) showExitDialog = true else onDiscard()
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && onImportJson != null) {
            val json = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            when {
                json == null -> Toast.makeText(context, context.getString(R.string.ext_config_import_read_error), Toast.LENGTH_LONG).show()
                else -> {
                    val config = onImportJson(json)
                    if (config != null) {
                        applyConfiguration(config)
                        Toast.makeText(context, context.getString(R.string.ext_config_import_success), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.ext_config_import_parse_error), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val downloadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
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

    BackHandler { attemptExit() }

    Column(modifier = Modifier.fillMaxSize().background(AppPalette.Cream)) {
        AppTopBar(
            title    = stringResource(R.string.ext_config_title),
            subtitle = listOfNotNull(
                stringResource(R.string.ext_config_subtitle),
                environment?.let { stringResource(it.labelRes) },
            ).joinToString(" · "),
            onBack   = { attemptExit() },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            // ── Load configuration ────────────────────────────────────────────
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Button(
                        onClick = { showLoadDialog = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text(stringResource(R.string.ext_config_load_title), fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        stringResource(R.string.ext_config_load_desc),
                        style = monoLabel.copy(color = AppPalette.Ink3),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            // ── Connection ────────────────────────────────────────────────────
            item { ConfigSectionHeader(stringResource(R.string.ext_config_sec_connection)) }
            item {
                ConfigCard {
                    ConfigField(
                        label = stringResource(R.string.ext_config_server_url),
                        hint  = "http://server:7048/ExternalSystem",
                        value = serverBaseUrl,
                        onValueChange = { serverBaseUrl = it },
                        keyboardType = KeyboardType.Uri,
                    )
                    ConfigDivider()
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        OutlinedButton(
                            onClick = { showLoginSheet = true },
                            enabled = serverBaseUrl.isNotBlank() && !testing,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text(stringResource(R.string.btn_test_connection), fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            stringResource(R.string.ext_config_test_connection_desc),
                            style = monoLabel.copy(color = AppPalette.Ink3),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }

            // ── Session TTL ───────────────────────────────────────────────────
            item { ConfigSectionHeader(stringResource(R.string.ext_config_sec_session)) }
            item {
                ConfigCard {
                    ConfigField(
                        label = stringResource(R.string.ext_config_windows_domain),
                        hint  = stringResource(R.string.ext_config_domain_hint),
                        value = domain,
                        onValueChange = { domain = it },
                    )
                    ConfigDivider()
                    ConfigSegmentedChoice(
                        title = stringResource(R.string.ext_config_session_duration),
                        description = stringResource(R.string.ext_config_session_desc),
                        options = TTL_OPTIONS,
                        selected = ttlHours,
                        optionLabel = { it.ttlLabel() },
                        onSelect = { ttlHours = it },
                        verticalPadding = 14.dp,
                        gap = 12.dp,
                    )
                }
            }

            // ── Document endpoints ────────────────────────────────────────────
            item { ConfigSectionHeader(stringResource(R.string.ext_config_sec_endpoints)) }
            item {
                ConfigCard {
                    ConfigField(
                        label = stringResource(R.string.ext_config_document_lines_url),
                        hint  = stringResource(R.string.ext_config_hint_document_lines),
                        value = documentLinesUrl,
                        onValueChange = { documentLinesUrl = it },
                    )
                }
            }
            for (type in DocumentType.entries) {
                item(key = type.key) {
                    DocTypeConfigCard(
                        type = type,
                        enabled = type.key !in disabledDocTypes,
                        code = documentTypeCodes[type] ?: "",
                        filterMode = docTypeFilters[type.key] ?: type.defaultFilterMode,
                        onEnabledChange = { on ->
                            onDisabledDocTypesChange(
                                if (on) disabledDocTypes - type.key else disabledDocTypes + type.key
                            )
                        },
                        onFilterModeChange = { mode -> onDocTypeFiltersChange(docTypeFilters + (type.key to mode)) },
                    )
                    if (type != DocumentType.entries.last()) ConfigDivider()
                }
            }

            // ── Reference data ────────────────────────────────────────────────
            item { ConfigSectionHeader(stringResource(R.string.ext_config_sec_reference)) }
            item {
                ConfigCard {
                    ConfigField(
                        label = stringResource(R.string.ext_config_locations_url),
                        hint  = stringResource(R.string.ext_config_hint_locations),
                        value = locationsUrl,
                        onValueChange = { locationsUrl = it },
                    )
                    ConfigDivider()
                    ConfigField(
                        label = stringResource(R.string.ext_config_inventory_batches_url),
                        hint  = stringResource(R.string.ext_config_hint_inventory_batches),
                        value = inventoryBatchesUrl,
                        onValueChange = { inventoryBatchesUrl = it },
                    )
                }
            }

            // ── Recording sync ────────────────────────────────────────────────
            item { ConfigSectionHeader(stringResource(R.string.ext_config_sec_recording)) }
            item {
                ConfigCard {
                    ConfigSegmentedChoice(
                        title = stringResource(R.string.ext_config_upload_mode),
                        description = stringResource(R.string.ext_config_upload_mode_desc),
                        options = RecordingUploadMode.entries,
                        selected = uploadMode,
                        optionLabel = { mode ->
                            when (mode) {
                                RecordingUploadMode.PER_SCAN     -> stringResource(R.string.ext_config_upload_mode_per_scan)
                                RecordingUploadMode.PER_DOCUMENT -> stringResource(R.string.ext_config_upload_mode_per_document)
                            }
                        },
                        onSelect = { uploadMode = it },
                    )
                    ConfigDivider()
                    ConfigField(
                        label = stringResource(R.string.ext_config_recording_url),
                        hint  = stringResource(R.string.ext_config_hint_recording_sync),
                        value = recordingSyncUrl,
                        onValueChange = { recordingSyncUrl = it },
                    )
                    ConfigDivider()
                    ConfigField(
                        label = stringResource(R.string.ext_config_document_upload_url),
                        hint  = stringResource(R.string.ext_config_hint_document_upload),
                        value = documentUploadUrl,
                        onValueChange = { documentUploadUrl = it },
                        imeAction = ImeAction.Done,
                    )
                }
            }
        }
    }

    if (showLoginSheet) {
        LoginSheet(
            credentialTtlHours = ttlHours,
            ctaLabel           = stringResource(R.string.btn_test_connection),
            loginQrKey         = loginQrKey,
            hapticEnabled      = hapticEnabled,
            soundEnabled       = soundEnabled,
            initialUsername    = savedCredentials?.username ?: "",
            initialPassword    = savedCredentials?.password ?: "",
            onDismiss          = { showLoginSheet = false },
            onSubmit           = { u, p ->
                showLoginSheet = false
                testing = true
                onTestConnection(serverBaseUrl.trim(), u, p) { ok, msg ->
                    testing = false
                    if (msg != null) testResult = ok to msg
                }
            },
        )
    }

    if (showLoadDialog) {
        ConfigLoadFlow(
            title = stringResource(R.string.ext_config_load_title),
            listCompanies = listCompanies,
            loadDefaults = loadDefaults,
            onLoaded = { applyConfiguration(it) },
            onExport = { downloadLauncher.launch(EXPORT_FILE_NAME) },
            onImport = { importLauncher.launch("application/json") },
            onClose = { showLoadDialog = false },
        )
    }

    if (showExitDialog) {
        AppAlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = stringResource(R.string.dialog_save_changes_title),
            text = stringResource(R.string.ext_config_exit_dialog_text),
            confirmLabel = stringResource(R.string.btn_yes),
            onConfirm = { showExitDialog = false; requestSave() },
            dismissLabel = stringResource(R.string.btn_no),
            onDismiss = { showExitDialog = false; onDiscard() },
        )
    }

    if (showEnvironmentMissingDialog) {
        EnvironmentMissingDialog(
            onPick = { showEnvironmentMissingDialog = false; showEnvironmentSetDialog = true },
            onSaveAnyway = { showEnvironmentMissingDialog = false; onSave(buildConfig()) },
            onDismiss = { showEnvironmentMissingDialog = false },
        )
    }

    // Sets the environment of the form as it stands and saves it; ConfigLoadFlow asks the same
    // question, but to choose which bundled file to load.
    if (showEnvironmentSetDialog) {
        EnvironmentPickDialog(
            onPick = { env ->
                showEnvironmentSetDialog = false
                environment = env
                onSave(buildConfig().copy(environment = env))
            },
            onDismiss = { showEnvironmentSetDialog = false },
        )
    }

    if (testing) {
        ConnectionTestingDialog()
    }

    testResult?.let { (ok, message) ->
        ConnectionResultDialog(ok, message, onDismiss = { testResult = null })
    }
}
