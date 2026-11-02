package com.prima.barcode.data.auth

import android.content.Context
import com.prima.barcode.data.model.DocumentType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtSystemConfigStore @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val prefs by lazy { context.getSharedPreferences("ext_system_config", Context.MODE_PRIVATE) }

    fun get(): ExtSystemConfig = ExtSystemConfig(
        serverBaseUrl      = prefs.getString("serverBaseUrl", "") ?: "",
        credentialTtlHours = prefs.getInt("credentialTtlHours", 24),
        documentLinesUrl   = prefs.getString("documentLinesUrl", "") ?: "",
        documentTypeCodes = DocumentType.entries.associateWith { type ->
            prefs.getString("doc_type_code_${type.key}", null) ?: CODES_OF_TYPES_ADDED_LATER[type].orEmpty()
        },
        recordingSyncUrl = prefs.getString("recordingSyncUrl", "") ?: "",
        locationsUrl     = prefs.getString("locationsUrl", "") ?: "",
        domain           = prefs.getString("domain", "") ?: "",
        loginQrKey       = prefs.getString("loginQrKey", "") ?: "",
        // Absent, or a name from a newer build this one does not know, both read as "nobody
        // has said" rather than as a guess.
        environment      = prefs.getString("environment", null)
            ?.let { name -> ExtSystemEnvironment.entries.firstOrNull { it.name == name } },
    )


    fun save(config: ExtSystemConfig) {
        val ed = prefs.edit()
            .putString("serverBaseUrl",      config.serverBaseUrl)
            .putInt   ("credentialTtlHours", config.credentialTtlHours)
            .putString("documentLinesUrl", config.documentLinesUrl)
            .putString("recordingSyncUrl", config.recordingSyncUrl)
            .putString("locationsUrl",     config.locationsUrl)
            .putString("domain",           config.domain)
            .putString("loginQrKey",       config.loginQrKey)
            .putString("environment",      config.environment?.name)
        config.documentTypeCodes.forEach { (type, code) ->
            ed.putString("doc_type_code_${type.key}", code)
        }
        ed.apply()
    }

    private companion object {
        /**
         * The code of a type this device was configured without, until a configuration says
         * otherwise.
         *
         * The code field is read-only on the configuration screen, so a device configured before
         * a type existed would otherwise have no way to get its code short of reloading a whole
         * configuration over its own. These codes are the same in every company.
         */
        val CODES_OF_TYPES_ADDED_LATER = mapOf(
            DocumentType.COMPLAINT_SHIPMENT to "REKOTPR",
            DocumentType.COMPLAINT_RECEIPT  to "REKZAPR",
        )
    }
}
