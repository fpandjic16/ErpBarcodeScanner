package com.prima.barcode.data.auth

import android.content.Context
import android.content.SharedPreferences
import com.prima.barcode.data.model.DocTypeFilterMode
import com.prima.barcode.data.model.DocumentType
import com.prima.barcode.ui.theme.Language
import com.prima.barcode.ui.theme.TextSize
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings, split by who they actually belong to.
 *
 * **Personal, one set per profile:** text size, uppercase, language, debounce, haptics, sound,
 * over-scan warning, background sync, and the last location / responsibility centre. These
 * describe a person — how they read the screen, where they are working today — and it would be
 * rude for each shift to reset the next one's.
 *
 * **The device's, shared by everyone:** which document types are switched off, how each type is
 * scoped, and the debugger flag. These describe the installation, the same way `ExtSystemConfig`
 * does; an administrator sets them up once and a new operator should not have to repeat that
 * before they can work.
 *
 * [AppSettings] stays one type across that seam, so nothing above this class knows the split
 * exists.
 */
@Singleton
class AppSettingsStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val profileStore: UserProfileStore,
    private val extSystemConfigStore: ExtSystemConfigStore,
) {

    private val devicePrefs by lazy { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }

    private val allDocTypeKeys: Set<String> get() = DocumentType.entries.map { it.key }.toSet()

    /**
     * The document types switched on, stored as exactly that.
     *
     * Older builds stored the opposite — the types switched *off* — under `disabledDocTypes`, so
     * an empty list meant "everything", and a type added later arrived switched on. Every type now
     * starts off, and a device keeps only what someone deliberately switched on.
     *
     * The first read after the upgrade settles which case this device is, and writes the answer
     * down so it is never guessed at again:
     * - an older list exists: everything that build knew and did not switch off stays on;
     * - no list, but the device was already pointed at a server: it is an upgraded device whose
     *   settings were never saved, which in the older build meant everything was on;
     * - neither: a fresh installation, so nothing is on.
     *
     * In all three, a type the older build did not know stays off. The first read happens at app
     * start, before anything can be configured, so a fresh device cannot be mistaken for an
     * upgraded one by being set up first.
     */
    private fun enabledDocTypes(): Set<String> {
        devicePrefs.getString(KEY_ENABLED_DOC_TYPES, null)?.let { return it.splitKeys() }
        val knownBefore = DOC_TYPES_BEFORE_COMPLAINT_SPLIT.map { it.key }.toSet()
        val enabled: Set<String> = when {
            devicePrefs.contains(LEGACY_KEY_DISABLED_DOC_TYPES) ->
                knownBefore - devicePrefs.getString(LEGACY_KEY_DISABLED_DOC_TYPES, "").orEmpty().splitKeys()
            extSystemConfigStore.get().serverBaseUrl.isNotBlank() -> knownBefore
            else -> emptySet()
        }
        devicePrefs.edit()
            .putString(KEY_ENABLED_DOC_TYPES, enabled.joinToString(","))
            .remove(LEGACY_KEY_DISABLED_DOC_TYPES)
            .apply()
        return enabled
    }

    private fun String.splitKeys(): Set<String> = split(",").filter { it.isNotEmpty() }.toSet()

    /**
     * Falls back to the device file when nobody is signed in, so a read before sign-in returns
     * defaults instead of throwing. Nothing writes there in that state — every settings screen
     * sits behind sign-in.
     */
    private fun personalPrefs(): SharedPreferences {
        val id = profileStore.currentId() ?: return devicePrefs
        return context.getSharedPreferences("app_settings_$id", Context.MODE_PRIVATE)
    }

    fun get(): AppSettings {
        val personal = personalPrefs()
        // Missing keys fall back to AppSettings' own defaults, so they are written down once.
        val defaults = AppSettings()
        return AppSettings(
            textSize         = TextSize.entries.firstOrNull { it.name == personal.getString("textSize", null) } ?: defaults.textSize,
            uppercaseText    = personal.getBoolean("uppercaseText", false),
            language         = Language.entries.firstOrNull { it.name == personal.getString("language", null) } ?: defaults.language,
            debounceTime     = personal.getInt("debounceTime", defaults.debounceTime),
            hapticEnabled    = personal.getBoolean("hapticEnabled", true),
            soundEnabled     = personal.getBoolean("soundEnabled", true),
            warnOnOver          = personal.getBoolean("warnOnOver", true),
            backgroundSync      = personal.getBoolean("backgroundSync", false),
            lastLocationCode = personal.getString("lastLocationCode", "") ?: "",
            lastRcCode       = personal.getString("lastRcCode", "") ?: "",
            // Still handed out as the switched-off set, so nothing above this class changed.
            disabledDocTypes = allDocTypeKeys - enabledDocTypes(),
            docTypeFilters = devicePrefs.getString("docTypeFilters", "")
                ?.split(",")?.filter { it.isNotEmpty() }
                ?.mapNotNull { entry ->
                    val parts = entry.split(":")
                    if (parts.size != 2) return@mapNotNull null
                    val mode = DocTypeFilterMode.entries.firstOrNull { it.name == parts[1] } ?: return@mapNotNull null
                    parts[0] to mode
                }?.toMap() ?: emptyMap(),
            debuggerActive = devicePrefs.getBoolean("debuggerActive", false),
        )
    }

    /**
     * Drops one operator's personal settings. The device's are untouched, as are everyone else's.
     *
     * Guards against the signed-in id only by not being called for it: `personalPrefs()` falls
     * back to the device file when nobody is signed in, so deleting "the current profile's" file
     * blind could take the device's settings with it.
     */
    fun deletePersonal(profileId: String) {
        context.deleteSharedPreferences("app_settings_$profileId")
    }

    fun save(settings: AppSettings) {
        personalPrefs().edit()
            .putString ("textSize",          settings.textSize.name)
            .putBoolean("uppercaseText",      settings.uppercaseText)
            .putString ("language",           settings.language.name)
            .putInt    ("debounceTime",        settings.debounceTime)
            .putBoolean("hapticEnabled",      settings.hapticEnabled)
            .putBoolean("soundEnabled",       settings.soundEnabled)
            .putBoolean("warnOnOver",          settings.warnOnOver)
            .putBoolean("backgroundSync",      settings.backgroundSync)
            .putString ("lastLocationCode",   settings.lastLocationCode)
            .putString ("lastRcCode",         settings.lastRcCode)
            .apply()
        devicePrefs.edit()
            .putString (KEY_ENABLED_DOC_TYPES, (allDocTypeKeys - settings.disabledDocTypes).joinToString(","))
            .remove    (LEGACY_KEY_DISABLED_DOC_TYPES)
            .putString ("docTypeFilters",     settings.docTypeFilters.entries.joinToString(",") { "${it.key}:${it.value.name}" })
            .putBoolean("debuggerActive",     settings.debuggerActive)
            .apply()
    }

    private companion object {
        const val KEY_ENABLED_DOC_TYPES = "enabledDocTypes"
        const val LEGACY_KEY_DISABLED_DOC_TYPES = "disabledDocTypes"
    }
}
