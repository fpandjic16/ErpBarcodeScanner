package com.erpbarcode.scanner.data.barcode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import timber.log.Timber

private const val DW_ACTION   = "com.symbol.datawedge.api.ACTION"
private const val SCAN_ACTION = "com.erpbarcode.scanner.SCAN"
private const val SCAN_EXTRA  = "com.symbol.datawedge.data_string"

/** Present on every Zebra device that has a scan engine; its absence means there is no scanner. */
private const val DATAWEDGE_PACKAGE = "com.symbol.datawedge"

// DataWedge aim_type: single shot, one pull one scan. Pinned rather than inherited so the trigger
// behaves the same on every device — a unit left in continuous read by another profile would
// otherwise deliver a burst of scans the app has no reason to expect.
private const val AIM_TYPE_TRIGGER = "0"

object DataWedgeManager {

    fun configure(context: Context) {
        context.sendBroadcast(Intent(DW_ACTION).apply {
            putExtra("com.symbol.datawedge.api.CREATE_PROFILE", "PrimaBarcode")
        })

        val appBundle = Bundle().apply {
            putString("PACKAGE_NAME", context.packageName)
            putStringArray("ACTIVITY_LIST", arrayOf("*"))
        }
        val intentPlugin = Bundle().apply {
            putString("PLUGIN_NAME",  "INTENT")
            putString("RESET_CONFIG", "true")
            putBundle("PARAM_LIST", Bundle().apply {
                putString("intent_output_enabled", "true")
                putString("intent_action",         SCAN_ACTION)
                putString("intent_delivery",       "2")
            })
        }
        // QR is turned on explicitly because sign-in codes depend on it. Everything else is left
        // to the device's own decoder settings (RESET_CONFIG "false" merges rather than replaces),
        // so this doesn't quietly disable a symbology a site relies on for its labels.
        // Devices whose scan engine is a 1D laser — the SE965 option on the MC3300 — cannot
        // decode QR at all, and no profile setting changes that; those fall back to the camera,
        // or to typing.
        val barcodePlugin = Bundle().apply {
            putString("PLUGIN_NAME",  "BARCODE")
            putString("RESET_CONFIG", "false")
            putBundle("PARAM_LIST", Bundle().apply {
                putString("decoder_qrcode", "true")
                putString("aim_type", AIM_TYPE_TRIGGER)
            })
        }
        // Keystroke output off, so every scan reaches the app exactly once, as an intent.
        //
        // A new DataWedge profile starts with keystroke output on, and this one used to leave it
        // that way. Every scan was then delivered twice: as the intent the screens listen for,
        // and typed into whichever text field had focus. On the sign-in screen that typed a login
        // code's ciphertext into the user-name field. On the recording screen it sent each scan
        // through handleScan twice, once from the intent and once from the scan bar, and only the
        // debounce stopped it being counted twice — an accident, not a design.
        val keystrokePlugin = Bundle().apply {
            putString("PLUGIN_NAME",  "KEYSTROKE")
            putString("RESET_CONFIG", "true")
            putBundle("PARAM_LIST", Bundle().apply {
                putString("keystroke_output_enabled", "false")
            })
        }
        val pluginList = ArrayList<Bundle>().apply {
            add(intentPlugin); add(keystrokePlugin); add(barcodePlugin)
        }

        context.sendBroadcast(Intent(DW_ACTION).apply {
            putExtra("com.symbol.datawedge.api.SET_CONFIG", Bundle().apply {
                putString("PROFILE_NAME",    "PrimaBarcode")
                putString("PROFILE_ENABLED", "true")
                putString("CONFIG_MODE",     "UPDATE")
                putParcelableArray("APP_LIST", arrayOf(appBundle))
                putParcelableArrayList("PLUGIN_CONFIG", pluginList)
            })
        })
        Timber.d("DataWedge profile configured")
    }


    /**
     * Whether this device has a hardware scanner the app can drive.
     *
     * Decides every "scan or photograph?" choice in the app: where a scanner exists it is the only
     * way in and no camera is offered; the camera is the fallback for devices without one. Needs
     * the `<queries>` entry for the package in the manifest — from Android 11, without it the
     * package is invisible to this check and every device would look scanner-less.
     */
    fun isAvailable(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(DATAWEDGE_PACKAGE, 0)
        true
    }.getOrDefault(false)

    /**
     * Starts a read, exactly as pulling the hardware trigger does. The result arrives through the
     * same intent as a trigger pull, so no screen needs a second path for it.
     */
    fun startScan(context: Context) {
        context.sendBroadcast(Intent(DW_ACTION).apply {
            putExtra("com.symbol.datawedge.api.SOFT_SCAN_TRIGGER", "START_SCANNING")
        })
    }

    /**
     * Starts delivering scans to [receiver]. Every screen that listens for the trigger registers
     * through here, so how it is registered is decided once.
     *
     * **Exported, on purpose.** DataWedge is another app, and from Android 13 a receiver registered
     * as not exported hears only its own app: the system drops DataWedge's broadcast and says
     * nothing. Each scanning screen used to register its own copy that way. It went unnoticed while
     * keystroke output was on, because every scan also arrived typed into the focused field; once
     * that was switched off, the trigger on an Android 14 TC21 beeped and nothing happened, on every
     * screen. Below Android 13 the flag does not exist and the call is unchanged — the MC3300 is on
     * Android 8.1.
     *
     * The cost: another app on the device could broadcast [SCAN_ACTION] and be taken for a scan. It
     * can do no more than the trigger can, on a managed handheld where installing apps is not open
     * to anyone.
     */
    fun register(context: Context, receiver: BroadcastReceiver) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, intentFilter(), Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, intentFilter())
        }
    }

    fun createReceiver(onScan: (String) -> Unit) = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != SCAN_ACTION) return
            val value = intent.getStringExtra(SCAN_EXTRA)?.trim() ?: return
            if (value.isNotEmpty()) onScan(value)
        }
    }

    fun intentFilter() = IntentFilter(SCAN_ACTION)
}
