package com.erpbarcode.scanner.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.erpbarcode.scanner.data.auth.ExtSystemConfig
import com.erpbarcode.scanner.data.model.DocTypeFilterMode
import com.erpbarcode.scanner.ui.screen.AppInfoScreen
import com.erpbarcode.scanner.ui.screen.ExtSystemConfigScreen
import com.erpbarcode.scanner.ui.screen.SignInScreen
import com.erpbarcode.scanner.ui.viewmodel.AppViewModel

/**
 * What the app shows while nobody is signed in: the sign-in screen, and the two doors out of it.
 *
 * `BarcodeApp` composes this and returns, so no route is reachable signed-out. Its own state
 * (signing in, the error, which door is open) lives only as long as the gate is up, and starts
 * afresh for the next operator. Moved out of AppNavHost.kt as it was.
 */
@Composable
internal fun SignInGate(
    appVm: AppViewModel,
    extSystemConfig: ExtSystemConfig,
    hapticEnabled: Boolean,
    soundEnabled: Boolean,
    disabledDocTypes: Set<String>,
    onDisabledDocTypesChange: (Set<String>) -> Unit,
    docTypeFilters: Map<String, DocTypeFilterMode>,
    onDocTypeFiltersChange: (Map<String, DocTypeFilterMode>) -> Unit,
    onDebuggerActiveChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var signingIn   by remember { mutableStateOf(false) }
    var signInError by remember { mutableStateOf<String?>(null) }
    var configuringAtSignIn by remember { mutableStateOf(false) }
    var appInfoAtSignIn by remember { mutableStateOf(false) }


    // The one door out of the gate, and it has to exist: signing in asks the server whether
    // the password is good, and a device out of the box has no server URL to ask. Without a
    // way to set one from here, a fresh install could never be configured and so could never
    // be signed into either.
    //
    // Not wrapped in launchWithDebug the way the signed-in route is — that dialog composes
    // below this return and so would never appear.
    //
    // **Only until a server address is saved** (3.6.52). The server is what decides whether a
    // password is good, so whoever can change it before signing in can point the device at a
    // server that accepts anything and sign in as any operator enrolled here, opening that
    // person's documents and unsent scans. Once configured, the configuration is changed after
    // signing in (Settings). A device whose saved address is wrong, or whose server has moved,
    // has to be reset (clear the app's data, or reinstall) — the price of closing that door.
    val firstSetup = extSystemConfig.serverBaseUrl.isBlank()
    if (configuringAtSignIn && firstSetup) {
        ExtSystemConfigScreen(
            hapticEnabled = hapticEnabled,
            soundEnabled = soundEnabled,
            initial = extSystemConfig,
            onSave = { config ->
                appVm.saveExtSystemConfig(config)
                configuringAtSignIn = false
            },
            onDiscard = { configuringAtSignIn = false },
            loadDefaults = { fileName -> appVm.loadExtSystemDefaults(fileName) },
            listCompanies = { appVm.listExtSystemDefaultsCompanies() },
            exportConfiguration = { appVm.exportConfigurationJson() },
            disabledDocTypes = disabledDocTypes,
            onDisabledDocTypesChange = onDisabledDocTypesChange,
            docTypeFilters = docTypeFilters,
            onDocTypeFiltersChange = onDocTypeFiltersChange,
            onDebuggerActiveChange = onDebuggerActiveChange,
            // savedCredentials left null on purpose: nobody is signed in, so there is nothing
            // to prefill and nowhere a test's credentials would be stored.
            onTestConnection = { serverUrl, username, password, cb ->
                appVm.testExtSystemConnection(serverUrl, username, password) { reportConnectionTest(context, it, cb) }
            },
            onImportJson = { json -> appVm.parseExtSystemConfigJson(json) },
        )
        return
    }
    // The second door out of the gate: read-only, and nothing in it is personal or secret.
    if (appInfoAtSignIn) {
        AppInfoScreen(
            environment = extSystemConfig.environment,
            companyName = extSystemConfig.companyName,
            onBack = { appInfoAtSignIn = false },
        )
        return
    }
    SignInScreen(
        onOpenConfig = if (firstSetup) ({ configuringAtSignIn = true }) else null,
        onOpenAppInfo = { appInfoAtSignIn = true },
        loginQrKey = extSystemConfig.loginQrKey,
        hapticEnabled = hapticEnabled,
        soundEnabled = soundEnabled,
        // Those enrolled on the installation the device is pointed at: a name known only on
        // another server or company would be offered here and then find none of its work.
        profiles = remember(signingIn, extSystemConfig.serverIdentity) {
            appVm.profileStore.profilesOn(extSystemConfig.serverIdentity)
        },
        busy = signingIn,
        error = signInError,
        onErrorDismiss = { signInError = null },
        onSignIn = { username, password ->
            signingIn = true
            appVm.signIn(username, password) { result ->
                signingIn = false
                when (result) {
                    is AppViewModel.SignInResult.Online -> Unit
                    is AppViewModel.SignInResult.Failed -> signInError = result.message
                }
            }
        },
    )
}
