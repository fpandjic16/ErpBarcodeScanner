package com.erpbarcode.scanner.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.data.auth.DeviceConfiguration
import com.erpbarcode.scanner.data.auth.ExtSystemDefaultsCompany
import com.erpbarcode.scanner.data.auth.ExtSystemEnvironment
import com.erpbarcode.scanner.ui.theme.AppPalette
import com.erpbarcode.scanner.ui.theme.monoLabel

/*
 * Loading a configuration, the same three dialogs wherever it is offered (External System
 * Configuration and Settings): what to do, then which system, then which company. Both screens
 * carried their own copy of these until 3.6.44.
 */

private enum class LoadStep { MENU, ENVIRONMENT, COMPANY }

/**
 * The whole "load configuration" flow, shown while the caller keeps it in composition and closed
 * through [onClose].
 *
 * Exporting and importing a file need an activity-result launcher, which belongs to the screen, so
 * those two come in as [onExport] and [onImport]. A bundled file that loads goes to [onLoaded] and a
 * toast says so; nothing is saved here, the screen's own save does that.
 */
@Composable
internal fun ConfigLoadFlow(
    title: String,
    listCompanies: () -> List<ExtSystemDefaultsCompany>,
    loadDefaults: (fileName: String) -> DeviceConfiguration?,
    onLoaded: (DeviceConfiguration) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(LoadStep.MENU) }
    // Which set of bundled files the company picker is about to show. Asked before the company,
    // because "which system" is the question that actually carries risk.
    var pickedEnvironment by remember { mutableStateOf<ExtSystemEnvironment?>(null) }

    when (step) {
        LoadStep.MENU -> Dialog(onDismissRequest = onClose) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(title, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { step = LoadStep.ENVIRONMENT },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.ext_config_load_builtin), fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { onClose(); onExport() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.ext_config_export_current), fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { onClose(); onImport() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.ext_config_load_from_file), fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }
        }

        LoadStep.ENVIRONMENT -> EnvironmentPickDialog(
            onPick = { env -> pickedEnvironment = env; step = LoadStep.COMPANY },
            onDismiss = onClose,
        )

        LoadStep.COMPANY -> Dialog(onDismissRequest = onClose) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.ext_config_pick_company_title), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    val companies = remember(pickedEnvironment) {
                        listCompanies().filter { it.environment == pickedEnvironment }
                    }
                    if (companies.isEmpty()) {
                        Text(
                            stringResource(R.string.ext_config_no_companies_found),
                            style = monoLabel.copy(color = AppPalette.Ink3),
                        )
                    }
                    companies.forEach { company ->
                        OutlinedButton(
                            // A file added but not filled in is shown rather than hidden, so it is
                            // clear the company exists and what is missing, but it cannot be
                            // loaded: its blank addresses would wipe a working configuration.
                            enabled = company.isConfigured,
                            onClick = {
                                onClose()
                                val defaults = loadDefaults(company.assetFileName)
                                if (defaults != null) {
                                    onLoaded(defaults)
                                    Toast.makeText(context, context.getString(R.string.ext_config_import_success), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.ext_config_company_load_error, company.assetFileName), Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (company.isConfigured) company.label
                                else stringResource(R.string.ext_config_company_not_configured, company.label),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    TextButton(
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            }
        }
    }
}

/** "Which system?": Test or Production. Used to pick bundled files, and to set the environment on saving. */
@Composable
internal fun EnvironmentPickDialog(onPick: (ExtSystemEnvironment) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.ext_config_pick_environment_title), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                ExtSystemEnvironment.entries.forEach { env ->
                    OutlinedButton(
                        onClick = { onPick(env) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(env.labelRes), fontWeight = FontWeight.SemiBold)
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        }
    }
}
