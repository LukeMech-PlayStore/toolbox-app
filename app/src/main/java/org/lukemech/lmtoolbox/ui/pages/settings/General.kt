package org.lukemech.lmtoolbox.ui.pages.settings

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppReset
import org.lukemech.lmtoolbox.helpers.FeatureUsage
import org.lukemech.lmtoolbox.helpers.FeatureUsage.StartPage
import org.lukemech.lmtoolbox.ui.components.ConfirmedActionButton
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.helpers.LocalAppNav

/** Page the app opens on: home, the favourites, or the bottom bar's feature (pinned, else the last one). */
@Composable
fun StartPageSetting() {
    val context = LocalContext.current
    OptionSetting(
        title = stringResource(R.string.s_start_page),
        icon = Icons.Default.RocketLaunch,
        selected = FeatureUsage.start,
        options = listOf(
            StartPage.Home to stringResource(R.string.nav_main_page),
            StartPage.Favourites to stringResource(R.string.favourites),
            StartPage.Feature to stringResource(
                if (FeatureUsage.defaultFeature != null) R.string.s_start_pinned_feature else R.string.s_start_last_feature
            ),
        ),
        onSelect = { FeatureUsage.setStart(context, it) }
    )
}

/** Pins the feature on the middle tab, to be in the bottom bar when the app starts (unpinned: the last used one). */
@Composable
fun DefaultToolSetting() {
    val context = LocalContext.current
    val current = LocalAppNav.current.lastFeature
    val isPinned = current != null && current == FeatureUsage.defaultFeature

    FeatureButton(
        title = if (current == null) stringResource(R.string.s_default_tool)
        else stringResource(R.string.s_pin, stringResource(current.titleStringResId)),
        subtitle = stringResource(R.string.s_default_tool_desc),
        icon = Icons.Default.PushPin,
        onClick = { if (current != null) FeatureUsage.setDefaultFeature(context, if (isPinned) null else current) },
        buttonContent = {
            Switch(
                checked = isPinned,
                enabled = current != null,
                onCheckedChange = { FeatureUsage.setDefaultFeature(context, if (it) current else null) }
            )
        }
    )
}

/** Start page and pinning back to how they come (shown when either isn't, see Settings). */
@Composable
fun GeneralResetButton() {
    val context = LocalContext.current
    val resources = LocalResources.current
    ConfirmedActionButton(
        title = stringResource(R.string.s_general_reset),
        subtitle = null,
        icon = Icons.Default.SettingsBackupRestore,
        confirmTitle = stringResource(R.string.s_general_reset_confirm_title),
        confirmText = stringResource(R.string.s_general_reset_confirm_msg),
        confirmLabel = stringResource(R.string.s_general_reset_confirm)
    ) {
        AppReset.general(context)
        Toast.makeText(context, resources.getString(R.string.s_general_reset_done), Toast.LENGTH_SHORT).show()
    }
}
