package com.erpbarcode.scanner.navigation

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.erpbarcode.scanner.data.auth.AppSettings
import com.erpbarcode.scanner.ui.screen.ExtSystemConfigScreen
import com.erpbarcode.scanner.ui.screen.LocationRcPickScreen
import com.erpbarcode.scanner.ui.screen.ProfilesScreen
import com.erpbarcode.scanner.ui.screen.SettingsScreen
import com.erpbarcode.scanner.ui.screen.UserInfoScreen
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val exportTimestampFmt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    .withZone(ZoneId.systemDefault())

/**
 * Settings and everything reached from them: the connection to the ERP, the operators on the
 * device, the signed-in user, and the working location.
 *
 * Moved out of AppNavHost.kt as written. Inside `with(shell)` every name the routes use — who is
 * signed in, the settings, what is picked, the shell's actions — is a member of [Shell].
 */
internal fun NavGraphBuilder.settingsRoutes(shell: Shell) {
    with(shell) {
        composable(Routes.PROFILES) {
            // Re-read after a deletion rather than observed: profiles change only here, and a
            // Flow over the encrypted profile store would be machinery for one screen.
            var profilesRefresh by remember { mutableStateOf(0) }
            ProfilesScreen(
                profiles = remember(profilesRefresh) { appVm.profileStore.profiles() },
                signedInKey = activeProfile.key,
                onBack = { nav.popBackStack() },
                loadFootprint = { key, onResult -> appVm.profileFootprint(key, onResult) },
                onDelete = { key -> appVm.deleteProfile(key) { profilesRefresh++ } },
            )
        }
        composable(Routes.USER_INFO) {
            UserInfoScreen(
                user = user,
                location = location,
                rc = rc,
                onBack = { nav.popBackStack() },
                onSignOut = { requestSignOut() },
            )
        }
        composable(Routes.LOCATION_RC_PICK) {
            val isRefreshing by appVm.isRefreshingLocations.collectAsState()
            val lastSyncedAt by appVm.lastLocationSyncAt.collectAsState()
            LocationRcPickScreen(
                currentRcCode = rcCode,
                currentLocationCode = locationCode,
                availableRcs = rcs,
                availableLocations = locations,
                isRefreshing = isRefreshing,
                lastSyncedAt = lastSyncedAt,
                hasCredentials = appVm.hasCredentials(),
                credentialTtlHours = extSystemConfig.credentialTtlHours,
                loginQrKey = extSystemConfig.loginQrKey,
                hapticEnabled = hapticEnabled,
                soundEnabled = soundEnabled,
                onSelect = { rc, loc ->
                    onRcCodeChange(rc)
                    onLocationCodeChange(loc)
                    nav.popBackStack()
                },
                onRefresh = {
                    launchWithDebug(
                        listOf(appVm.getLocationsUrl()).filter { it.isNotBlank() }
                    ) {
                        appVm.downloadLocations { error ->
                            if (error != null) {
                                downloadErrorMessage = error
                                showDownloadErrorDialog = true
                            }
                        }
                    }
                },
                onSaveCredentials = { u, p -> appVm.saveCredentials(u, p) },
                onTestConnection = shell::testSignIn,
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.EXT_SYSTEM_CONFIG) {
            ExtSystemConfigScreen(
                hapticEnabled = hapticEnabled,
                soundEnabled = soundEnabled,
                initial = extSystemConfig,
                // Stays on the screen, with the form as it is, while a change of installation is
                // being asked about; confirming it signs out, which leaves this screen anyway.
                onSave  = { config -> if (requestConfigSave(config)) nav.popBackStack() },
                onDiscard = { nav.popBackStack() },
                loadDefaults = { fileName -> appVm.loadExtSystemDefaults(fileName) },
                listCompanies = { appVm.listExtSystemDefaultsCompanies() },
                exportConfiguration = { appVm.exportConfigurationJson() },
                disabledDocTypes = disabledDocTypes,
                onDisabledDocTypesChange = onDisabledDocTypesChange,
                docTypeFilters = docTypeFilters,
                onDocTypeFiltersChange = onDocTypeFiltersChange,
                onDebuggerActiveChange = onDebuggerActiveChange,
                savedCredentials = appVm.savedCredentials(),
                onTestConnection = { serverUrl, username, password, cb ->
                    launchWithDebug(
                        listOf(serverUrl.trim()),
                        onCancel = { cb(false, null) },
                    ) {
                        appVm.testExtSystemConnection(serverUrl, username, password) { reportTestResult(it, cb) }
                    }
                },
                onImportJson = { json -> appVm.parseExtSystemConfigJson(json) },
            )
        }
        composable(Routes.SETTINGS) {
            val initialSettingsSnapshot = AppSettings(
                textSize = textSize,
                uppercaseText = uppercaseText,
                language = language,
                debounceTime = debounceTime,
                hapticEnabled = hapticEnabled,
                soundEnabled = soundEnabled,
                warnOnOver = warnOnOver,
                backgroundSync = backgroundSync,
                lastLocationCode = locationCode,
                lastRcCode = rcCode,
                disabledDocTypes = disabledDocTypes,
                docTypeFilters = docTypeFilters,
                debuggerActive = debuggerActive,
            )
            SettingsScreen(
                user = user,
                location = location,
                rc = rc,
                initial = initialSettingsSnapshot,
                onSave = { s -> onSettingsSaved(s); nav.popBackStack() },
                onDiscard = { nav.popBackStack() },
                onSaveExtSystemConfig = { config -> requestConfigSave(config) },
                loadExtSystemConfigDefaults = { fileName -> appVm.loadExtSystemDefaults(fileName) },
                listExtSystemDefaultsCompanies = { appVm.listExtSystemDefaultsCompanies() },
                parseExtSystemConfigJson = { json -> appVm.parseExtSystemConfigJson(json) },
                exportConfiguration = { appVm.exportConfigurationJson() },
                onExport = {
                    val ts = exportTimestampFmt.format(Instant.now())
                    exportLauncher.launch("barcode_export_${ts}.json")
                },
                onClearCache = { appVm.clearCache() },
                onDeleteAllDocuments = { appVm.deleteAllDocuments() },
                onChangeLocation = { nav.navigate(Routes.LOCATION_RC_PICK) },
                onOpenExtSystemConfig = { nav.navigate(Routes.EXT_SYSTEM_CONFIG) },
                onOpenProfiles = { nav.navigate(Routes.PROFILES) },
                onSignOut = { requestSignOut() },
                onSignInTap = { requireCredentials {} },
                environment = extSystemConfig.environment,
                companyName = extSystemConfig.companyName,
            )
        }
    }
}
