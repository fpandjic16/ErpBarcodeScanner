package com.erpbarcode.scanner

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.erpbarcode.scanner.data.auth.AppSettings
import com.erpbarcode.scanner.data.barcode.DataWedgeManager
import com.erpbarcode.scanner.data.extsystem.ExtSystemResult
import com.erpbarcode.scanner.data.model.DocState
import com.erpbarcode.scanner.data.model.DocTypeFilterMode
import com.erpbarcode.scanner.data.model.DocumentFilter
import com.erpbarcode.scanner.data.model.DownloadFilter
import com.erpbarcode.scanner.data.model.DocumentType
import com.erpbarcode.scanner.data.model.LineStatus
import com.erpbarcode.scanner.data.model.scanStatus
import com.erpbarcode.scanner.data.model.User
import com.erpbarcode.scanner.ui.screen.AppInfoScreen
import com.erpbarcode.scanner.ui.screen.DocTypeSummary
import com.erpbarcode.scanner.ui.screen.ExtSystemConfigScreen
import com.erpbarcode.scanner.ui.screen.DocumentOverviewScreen
import com.erpbarcode.scanner.ui.screen.DocumentListScreen
import com.erpbarcode.scanner.ui.screen.UploadErrorScreen
import com.erpbarcode.scanner.ui.screen.DownloadFilterScreen
import com.erpbarcode.scanner.ui.screen.DocumentFilterScreen
import com.erpbarcode.scanner.ui.screen.LocationRcPickScreen
import com.erpbarcode.scanner.ui.screen.LoginSheet
import com.erpbarcode.scanner.ui.screen.MainMenuScreen
import com.erpbarcode.scanner.ui.screen.RecordingScreen
import com.erpbarcode.scanner.ui.screen.ProfilesScreen
import com.erpbarcode.scanner.ui.screen.RecordingsListScreen
import com.erpbarcode.scanner.ui.screen.RecordingsTreeScreen
import com.erpbarcode.scanner.ui.screen.SettingsScreen
import com.erpbarcode.scanner.ui.screen.SignInScreen
import com.erpbarcode.scanner.ui.screen.UserInfoScreen
import com.erpbarcode.scanner.ui.screen.localizedDisplay
import com.erpbarcode.scanner.ui.theme.Language
import com.erpbarcode.scanner.ui.theme.AppTheme
import com.erpbarcode.scanner.ui.theme.TextSize
import com.erpbarcode.scanner.ui.viewmodel.AppViewModel
import com.erpbarcode.scanner.ui.viewmodel.RecordingViewModel
import com.erpbarcode.scanner.ui.viewmodel.RecordingsTreeViewModel
import com.erpbarcode.scanner.navigation.DebugUrlsDialog
import com.erpbarcode.scanner.navigation.Routes
import com.erpbarcode.scanner.navigation.BarcodeApp
import com.erpbarcode.scanner.navigation.DownloadErrorDialog
import com.erpbarcode.scanner.navigation.ProcessingDialog
import com.erpbarcode.scanner.navigation.SwitchBlockedDialog
import com.erpbarcode.scanner.navigation.SyncErrorDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideNavBar()
        DataWedgeManager.configure(this)
        setContent {
            // Showing the IME re-shows the nav bar on many devices/OS versions even though we
            // asked for it hidden — re-hide it whenever the keyboard becomes visible so it
            // doesn't linger onscreen for the whole time the user is typing. Reading this via
            // Compose's own WindowInsets (rather than a View-level OnApplyWindowInsetsListener
            // on the decor view) avoids interfering with how insets propagate to Compose's own
            // status-bar padding.
            val imeVisible = WindowInsets.isImeVisible
            LaunchedEffect(imeVisible) {
                if (imeVisible) hideNavBar()
            }

            val appVm: AppViewModel = hiltViewModel()
            val initialSettings = remember { appVm.loadSettings() }
            var textSize         by remember { mutableStateOf(initialSettings.textSize) }
            var uppercaseText    by remember { mutableStateOf(initialSettings.uppercaseText) }
            var language         by remember { mutableStateOf(initialSettings.language) }
            var debounceTime     by remember { mutableStateOf(initialSettings.debounceTime) }
            var hapticEnabled     by remember { mutableStateOf(initialSettings.hapticEnabled) }
            var soundEnabled      by remember { mutableStateOf(initialSettings.soundEnabled) }
            var warnOnOver       by remember { mutableStateOf(initialSettings.warnOnOver) }
            var backgroundSync   by remember { mutableStateOf(initialSettings.backgroundSync) }
            var disabledDocTypes by remember { mutableStateOf(initialSettings.disabledDocTypes) }
            var docTypeFilters    by remember { mutableStateOf(initialSettings.docTypeFilters) }
            var debuggerActive   by remember { mutableStateOf(initialSettings.debuggerActive) }
            var locationCode     by remember { mutableStateOf(initialSettings.lastLocationCode) }
            var rcCode           by remember { mutableStateOf(initialSettings.lastRcCode) }

            fun buildSettings() = AppSettings(
                textSize         = textSize,
                uppercaseText    = uppercaseText,
                language         = language,
                debounceTime     = debounceTime,
                hapticEnabled    = hapticEnabled,
                soundEnabled     = soundEnabled,
                warnOnOver          = warnOnOver,
                backgroundSync      = backgroundSync,
                lastLocationCode = locationCode,
                lastRcCode       = rcCode,
                disabledDocTypes = disabledDocTypes,
                docTypeFilters   = docTypeFilters,
                debuggerActive   = debuggerActive,
            )

            // Bulk-applies settings saved (and confirmed) from the Settings screen —
            // called once on exit-confirm, not per-field, since Settings now buffers
            // edits locally instead of auto-saving each change.
            fun applySettings(s: AppSettings) {
                if (language != s.language) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(s.language.tag))
                }
                textSize = s.textSize
                uppercaseText = s.uppercaseText
                language = s.language
                debounceTime = s.debounceTime
                hapticEnabled = s.hapticEnabled
                soundEnabled = s.soundEnabled
                warnOnOver = s.warnOnOver
                backgroundSync = s.backgroundSync
                debuggerActive = s.debuggerActive
                // A configuration loaded from Settings brings these two. Left out, the main menu
                // kept the old set of document types until the app was restarted.
                disabledDocTypes = s.disabledDocTypes
                docTypeFilters = s.docTypeFilters
                appVm.saveSettings(s)
            }

            // Most settings are personal, so they have to follow the operator.
            //
            // The initial read above happens before anyone has signed in and therefore returns
            // device defaults. This replaces them with the profile's own as soon as there is a
            // profile, and again on every change of operator — otherwise the second shift would
            // inherit the first one's language, text size and working location.
            //
            // Deliberately does not save: this is reading what is already stored, and writing it
            // back would let a half-applied state overwrite the real one.
            val signedInProfile by appVm.currentProfile.collectAsState()
            LaunchedEffect(signedInProfile?.key) {
                if (signedInProfile == null) return@LaunchedEffect
                val s = appVm.loadSettings()
                // Against the locale actually on screen, not against `language`. That starts from
                // the signed-out defaults, so an operator whose language happens to equal the
                // default — anyone who never chose one — would otherwise inherit whatever locale
                // the previous operator left in effect, while their Settings named another.
                if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != s.language.tag) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(s.language.tag))
                }
                textSize = s.textSize
                uppercaseText = s.uppercaseText
                language = s.language
                debounceTime = s.debounceTime
                hapticEnabled = s.hapticEnabled
                soundEnabled = s.soundEnabled
                warnOnOver = s.warnOnOver
                backgroundSync = s.backgroundSync
                debuggerActive = s.debuggerActive
                disabledDocTypes = s.disabledDocTypes
                docTypeFilters = s.docTypeFilters
                locationCode = s.lastLocationCode
                rcCode = s.lastRcCode
            }

            AppTheme(textSizeOffset = textSize.spOffset, uppercaseEnabled = uppercaseText) {
                BarcodeApp(
                    locationCode              = locationCode,
                    rcCode                    = rcCode,
                    onLocationCodeChange      = { code -> locationCode = code; appVm.saveSettings(buildSettings().copy(lastLocationCode = code)) },
                    onRcCodeChange            = { code -> rcCode = code; appVm.saveSettings(buildSettings().copy(lastRcCode = code)) },
                    textSize                  = textSize,
                    uppercaseText             = uppercaseText,
                    language                  = language,
                    debounceTime              = debounceTime,
                    hapticEnabled             = hapticEnabled,
                    soundEnabled              = soundEnabled,
                    warnOnOver                = warnOnOver,
                    backgroundSync            = backgroundSync,
                    disabledDocTypes          = disabledDocTypes,
                    onDisabledDocTypesChange  = { disabledDocTypes = it; appVm.saveSettings(buildSettings().copy(disabledDocTypes = it)) },
                    docTypeFilters            = docTypeFilters,
                    onDocTypeFiltersChange    = { docTypeFilters = it; appVm.saveSettings(buildSettings().copy(docTypeFilters = it)) },
                    debuggerActive            = debuggerActive,
                    onDebuggerActiveChange    = { debuggerActive = it; appVm.saveSettings(buildSettings().copy(debuggerActive = it)) },
                    onSettingsSaved           = { s -> applySettings(s) },
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavBar()
    }

    private fun hideNavBar() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
