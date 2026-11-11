package com.erpbarcode.scanner

import android.app.Application
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.erpbarcode.scanner.data.auth.AppSettingsStore
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class BarcodeApplication : Application() {

    @Inject lateinit var appSettingsStore: AppSettingsStore

    override fun onCreate() {
        super.onCreate()   // Hilt injects appSettingsStore here, before it's read below.
        Timber.plant(Timber.DebugTree())
        applySavedLanguage()
        // The cleanup of the single pre-operator database that used to run here went with the
        // change of application id in 3.6.48: a new id starts with an empty data directory, so
        // that file can no longer exist.
    }

    /**
     * Re-applies the language stored in [AppSettingsStore] to AppCompat.
     *
     * The chosen language is persisted in the app's own settings, but the locale that actually
     * renders the UI lives in AppCompatDelegate, which keeps its own storage — and below API 33
     * it persists nothing at all unless the app declares `AppLocalesMetadataHolderService`.
     * Without this the two drift apart on every process restart, and most visibly after an APK
     * upgrade: the Settings screen still reads "Croatian" while the whole UI renders English.
     * Doing it here, before any Activity exists, means the first frame is already correct rather
     * than flashing English and recreating.
     *
     * This only fills a gap — it never overrides a locale already in effect, so a per-app
     * language set from Android's own system settings (API 33+) keeps winning. Where nothing is
     * stored it applies the default, Croatian, rather than leaving the device locale to decide:
     * these handhelds usually ship in English, and the app is meant to start in Croatian.
     *
     * From API 33 this goes to the framework's [LocaleManager] directly, not through AppCompat.
     * There AppCompat forwards both calls to the LocaleManager it finds through a live activity,
     * and here there is none yet, so both silently did nothing. A fresh install on a TC21 or
     * TC22 then showed sign-in, setup and About in English, and switched to Croatian only once
     * the signed-in activity set the language itself.
     */
    private fun applySavedLanguage() {
        val language = appSettingsStore.get().language
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = getSystemService(LocaleManager::class.java)
            if (!localeManager.applicationLocales.isEmpty) return
            localeManager.applicationLocales = LocaleList.forLanguageTags(language.tag)
        } else {
            if (!AppCompatDelegate.getApplicationLocales().isEmpty) return
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
        }
    }
}
