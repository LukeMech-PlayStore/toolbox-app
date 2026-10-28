package org.lukemech.lmtoolbox.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ContactSupport
import androidx.compose.material.icons.filled.SyncDisabled
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import org.lukemech.lmtoolbox.R

/**
 * A bar on top of a page telling of something to do (e.g. calibrate, allow location), in [color], folding away;
 * tapping it explains or fixes it ([onClick]). A row like any other: as far from what's under it as its page's
 * RowArrangement puts rows (no gap of its own), none left as it folds away.
 */
@Composable
fun PromptBar(visible: Boolean, title: String, color: Color, icon: ImageVector, icon2: ImageVector, onClick: () -> Unit) {
    Reveal(visible = visible) {
        FeatureButton(
            title = title,
            colors = RowColors.tinted(color),
            onClick = onClick,
            icon = icon,
            icon2 = icon2
        )
    }
}

/**
 * A measuring tool's sensors' warning, on top of its page: a red bar while the device lacks them ([hasSensors]
 * false, [missingMessage]) or they aren't [calibrated] ([calibrationTitle], [calibrationMessage], [calibrationIcon]),
 * explained in a dialog when it's tapped.
 */
@Composable
fun SensorWarning(
    hasSensors: Boolean,
    calibrated: Boolean,
    @StringRes calibrationTitle: Int,
    @StringRes calibrationMessage: Int,
    calibrationIcon: ImageVector,
    @StringRes missingMessage: Int,
) {
    var explained by remember { mutableStateOf(false) }
    val warning = !hasSensors || !calibrated
    val title = stringResource(if (hasSensors) calibrationTitle else R.string.f_missing_sensors_title)
    if (warning && explained) {
        AlertDialog(
            onDismissRequest = { explained = false },
            confirmButton = {
                Button(onClick = { explained = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            title = { Text(title) },
            text = { Text(stringResource(if (hasSensors) calibrationMessage else missingMessage)) }
        )
    }
    PromptBar(
        visible = warning,
        title = title,
        color = MaterialTheme.colorScheme.error,
        icon = if (hasSensors) calibrationIcon else Icons.Default.SyncDisabled,
        icon2 = Icons.AutoMirrored.Default.ContactSupport,
        onClick = { explained = true }
    )
}
