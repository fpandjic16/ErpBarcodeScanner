package com.erpbarcode.scanner.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.erpbarcode.scanner.R
import com.erpbarcode.scanner.data.model.DocTypeFilterMode
import com.erpbarcode.scanner.data.model.DocumentType
import com.erpbarcode.scanner.ui.theme.PrimaLine
import com.erpbarcode.scanner.ui.theme.PrimaPalette
import com.erpbarcode.scanner.ui.theme.monoLabel

/*
 * The pieces External System Configuration is built from: section headers, white cards, labelled
 * fields, a titled segmented choice, and one document type's card. Split out of
 * ExtSystemConfigScreen.kt; the screen keeps the form state and decides what each change does.
 */

/**
 * A title, an optional line of explanation, and a segmented row of [options] with [selected] picked.
 * [verticalPadding] and [gap] let the session duration keep the slightly roomier spacing it had.
 */
@Composable
internal fun <T> ConfigSegmentedChoice(
    title: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    description: String? = null,
    verticalPadding: Dp = 12.dp,
    gap: Dp = 8.dp,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = verticalPadding)) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium, color = PrimaPalette.Ink,
            ),
        )
        description?.let {
            Text(it, style = monoLabel.copy(color = PrimaPalette.Ink3))
        }
        Spacer(Modifier.height(gap))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { idx, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick  = { onSelect(option) },
                    shape    = SegmentedButtonDefaults.itemShape(idx, options.size),
                ) { Text(optionLabel(option), style = monoLabel) }
            }
        }
    }
}

/**
 * One document type: its switch, and while it is on, its read-only type code and what it is scoped
 * by. Every change goes straight out through the callbacks; the device half of the configuration
 * has no form state of its own.
 */
@Composable
internal fun DocTypeConfigCard(
    type: DocumentType,
    enabled: Boolean,
    code: String,
    filterMode: DocTypeFilterMode,
    onEnabledChange: (Boolean) -> Unit,
    onFilterModeChange: (DocTypeFilterMode) -> Unit,
) {
    ConfigCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = if (enabled) 0.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                type.localizedDisplay(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) PrimaPalette.Ink else PrimaPalette.Ink3,
                ),
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = PrimaPalette.Slate,
                    checkedThumbColor = Color.White,
                ),
            )
        }
        if (enabled) {
            ConfigField(
                label = stringResource(R.string.ext_config_doc_type_code),
                hint  = stringResource(R.string.ext_config_hint_doc_type_code),
                value = code,
                onValueChange = {},
                readOnly = true,
            )
            ConfigDivider()
            ConfigSegmentedChoice(
                title = stringResource(R.string.ext_config_filter_by),
                options = listOf(DocTypeFilterMode.LOCATION, DocTypeFilterMode.RESPONSIBILITY_CENTER),
                selected = filterMode,
                optionLabel = { mode ->
                    when (mode) {
                        DocTypeFilterMode.LOCATION -> stringResource(R.string.ext_config_filter_location)
                        DocTypeFilterMode.RESPONSIBILITY_CENTER -> stringResource(R.string.ext_config_filter_rc)
                    }
                },
                onSelect = onFilterModeChange,
            )
        }
    }
}

@Composable
internal fun ConfigSectionHeader(title: String) {
    Text(
        title.uppercase(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
        style = monoLabel.copy(color = PrimaPalette.Ink3),
    )
}

@Composable
internal fun ConfigCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White),
        content = content,
    )
}

@Composable
internal fun ConfigField(
    label: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    readOnly: Boolean = false,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium, color = PrimaPalette.Ink,
            ),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(hint, style = monoLabel.copy(color = PrimaPalette.Ink4)) },
            singleLine = true,
            readOnly = readOnly,
            modifier = Modifier.fillMaxWidth(),
            textStyle = monoLabel.copy(color = if (readOnly) PrimaPalette.Ink3 else PrimaPalette.Ink),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            shape = RoundedCornerShape(8.dp),
            colors = if (readOnly) OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = PrimaLine.InputBorder,
                unfocusedBorderColor = PrimaLine.InputBorder,
                focusedContainerColor   = PrimaLine.ReadOnlyFill,
                unfocusedContainerColor = PrimaLine.ReadOnlyFill,
            ) else OutlinedTextFieldDefaults.colors(),
        )
    }
}

@Composable
internal fun ConfigDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = PrimaLine.Divider)
}
