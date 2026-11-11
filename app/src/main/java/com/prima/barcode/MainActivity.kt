package com.prima.barcode

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
import com.prima.barcode.data.auth.AppSettings
import com.prima.barcode.data.barcode.DataWedgeManager
import com.prima.barcode.data.extsystem.ExtSystemResult
import com.prima.barcode.data.model.DocState
import com.prima.barcode.data.model.DocTypeFilterMode
import com.prima.barcode.data.model.DocumentFilter
import com.prima.barcode.data.model.DownloadFilter
import com.prima.barcode.data.model.DocumentType
import com.prima.barcode.data.model.LineStatus
import com.prima.barcode.data.model.scanStatus
import com.prima.barcode.data.model.User
import com.prima.barcode.ui.screen.AppInfoScreen
import com.prima.barcode.ui.screen.DocTypeSummary
import com.prima.barcode.ui.screen.ExtSystemConfigScreen
import com.prima.barcode.ui.screen.DocumentOverviewScreen
import com.prima.barcode.ui.screen.DocumentListScreen
import com.prima.barcode.ui.screen.UploadErrorScreen
import com.prima.barcode.ui.screen.DownloadFilterScreen
import com.prima.barcode.ui.screen.DocumentFilterScreen
import com.prima.barcode.ui.screen.LocationRcPickScreen
import com.prima.barcode.ui.screen.LoginSheet
import com.prima.barcode.ui.screen.MainMenuScreen
import com.prima.barcode.ui.screen.RecordingScreen
import com.prima.barcode.ui.screen.ProfilesScreen
import com.prima.barcode.ui.screen.RecordingsListScreen
import com.prima.barcode.ui.screen.RecordingsTreeScreen
import com.prima.barcode.ui.screen.SettingsScreen
import com.prima.barcode.ui.screen.SignInScreen
import com.prima.barcode.ui.screen.UserInfoScreen
import com.prima.barcode.ui.screen.localizedDisplay
import com.prima.barcode.ui.theme.Language
import com.prima.barcode.ui.theme.PrimaBarcodeTheme
import com.prima.barcode.ui.theme.TextSize
import com.prima.barcode.ui.viewmodel.AppViewModel
import com.prima.barcode.ui.viewmodel.RecordingViewModel
import com.prima.barcode.ui.viewmodel.RecordingsTreeViewModel
import com.prima.barcode.navigation.DebugUrlsDialog
import com.prima.barcode.navigation.Routes
import com.prima.barcode.navigation.PrimaBarcodeApp
import com.prima.barcode.navigation.DownloadErrorDialog
import com.prima.barcode.navigation.ProcessingDialog
import com.prima.barcode.navigation.SwitchBlockedDialog
import com.prima.barcode.navigation.SyncErrorDialog
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
            LaunchedEffect(signedInProfile?.id) {
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

            PrimaBarcodeTheme(textSizeOffset = textSize.spOffset, uppercaseEnabled = uppercaseText) {
                PrimaBarcodeApp(
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
