package com.erpbarcode.scanner.i18n

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate

/**
 * This context, speaking the language the app is shown in.
 *
 * Below Android 13, `AppCompatDelegate.setApplicationLocales` reaches activities only: the
 * application context keeps the device's own language. Text resolved through it — an error message
 * built in a ViewModel or in the ERP client, neither of which has an activity to hand — comes out in
 * the device's language while every screen around it is in the app's. On an MC3300 (Android 8.1)
 * left in English, a Croatian operator would read Croatian screens and English errors.
 *
 * From Android 13 the per-app language covers the whole process, and this changes nothing.
 * Building the context is cheap enough for messages; it is not meant for a loop.
 */
fun Context.inAppLanguage(): Context {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty) return this
    val config = Configuration(resources.configuration)
    config.setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
    return createConfigurationContext(config)
}
