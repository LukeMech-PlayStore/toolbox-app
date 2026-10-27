package org.lukemech.lmtoolbox.ui.pages.settings

import org.lukemech.lmtoolbox.helpers.PreferenceHelper
import org.lukemech.lmtoolbox.helpers.SystemEntries
import org.lukemech.lmtoolbox.helpers.isInstalled
import org.lukemech.lmtoolbox.ui.components.SwitchButton
import androidx.compose.runtime.collectAsState
import android.text.format.Formatter
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.helpers.FeatureRegistry
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.AppReset
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.helpers.FeatureUsage
import org.lukemech.lmtoolbox.linux.LinuxParts
import org.lukemech.lmtoolbox.helpers.Screen
import org.lukemech.lmtoolbox.helpers.SettingsSubPage
import org.lukemech.lmtoolbox.helpers.clearData
import org.lukemech.lmtoolbox.ui.components.ConfirmedActionButton
import org.lukemech.lmtoolbox.ui.components.EmptyText
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.ExtensionStatus
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.SectionTitle
import org.lukemech.lmtoolbox.ui.helpers.LocalAppNav
import org.lukemech.lmtoolbox.ui.widgets.helpers.WidgetBackgroundSetting
import org.lukemech.lmtoolbox.ui.pages.all
import org.lukemech.lmtoolbox.ui.components.RowArrangement

/** Features whose settings are offered: the ones opened at least once. */
val settingsFeatures: List<AppFeature>
    get() = all.filter { FeatureUsage.isUsed(it) }

/** Settings of the feature on the middle tab, if [settingsFeatures] has it. */
@Composable
fun CurrentFeatureSettingsSection() {
    val appNav = LocalAppNav.current
    val feature = appNav.lastFeature?.takeIf { it in settingsFeatures } ?: return
    FeatureSettingsButton(feature)
}

/** Opens [feature]'s settings; [labelled] where it's not among others alone (the settings' page, its search). */
@Composable
fun FeatureSettingsButton(feature: AppFeature, labelled: Boolean = true) {
    val navController = LocalAppNav.current.controller
    FeatureButton(
        // Its name large, what it opens small above it (in the list of them, what the page says already: its name alone)
        title = if (labelled) stringResource(R.string.s_feature_settings) else stringResource(feature.titleStringResId),
        subtitle = if (labelled) stringResource(feature.titleStringResId) else null,
        titleSubtitleSwap = true,
        icon = feature.icon,
        icon2 = Icons.AutoMirrored.Default.KeyboardArrowRight,
        onClick = { navController.navigate(Screen.SettingsFeatures.createRoute(feature)) }
    )
}

@Composable
fun ToolSettingsSection() {
    val navController = LocalAppNav.current.controller
    FeatureButton(
        title = stringResource(R.string.s_feature_settings),
        icon = Icons.Default.Tune,
        icon2 = Icons.AutoMirrored.Default.KeyboardArrowRight,
        onClick = { navController.navigate(SettingsSubPage.ToolSettings.route) }
    )
}

/** [features] under their category titles, sorted by name, each drawn by [item]. */
@Composable
private fun ByCategory(features: List<AppFeature>, item: @Composable (AppFeature) -> Unit) {
    val resources = LocalResources.current
    FeatureCategory.entries.forEach { category ->
        val inCategory = features.filter { it.category == category }.sortedBy { resources.getString(it.titleStringResId) }
        if (inCategory.isEmpty()) return@forEach
        SectionTitle(stringResource(category.titleRes))
        Column(verticalArrangement = RowArrangement) {
            inCategory.forEach { item(it) }
        }
    }
}

/** Settings of every feature opened so far; the others show up after their first use. */
@Composable
fun ToolSettingsPage() {
    PageHeading(stringResource(R.string.s_feature_settings), back = true) {
        val features = settingsFeatures
        if (features.isEmpty()) {
            EmptyText(stringResource(R.string.s_feature_settings_empty))
        } else {
            ByCategory(features) { FeatureSettingsButton(it, labelled = false) }
        }
    }
}

/** Settings of [feature] (none: the page says so). */
@Composable
fun FeatureSettingsPage(feature: AppFeature?) {
    val feat = feature ?: return PageHeading(stringResource(R.string.undefined), back = true) {}
    val context = LocalContext.current
    // Until its packages are installed and unpacked, only what doesn't need them (again once a task changes)
    val tasks by LinuxTasks.tasks.collectAsState()
    val ready = remember(tasks) { feat.linuxExtensions.isEmpty() || LinuxParts.missingExtensions(context, feat.linuxExtensions).isEmpty() }
    // Its own (its packages may stay for another tool), again as tools are removed
    val removed by LinuxTasks.extensionsRemoved.collectAsState()
    val installed = remember(tasks, removed) { feat.isInstalled(context) }
    PageHeading(stringResource(feat.titleStringResId), back = true) {
        ExtensionStatus(feat)
        // Every feature's, always first
        SectionTitle(stringResource(R.string.s_feature_system))
        Column(verticalArrangement = RowArrangement) {
            val favourite = FeatureUsage.isFavourite(feat)
            SwitchButton(stringResource(R.string.s_feature_favourite), Icons.Default.Star, favourite) {
                FeatureUsage.toggleFavourite(context, feat)
            }
            // Its "Share to" / "Open in" entries: there (and switchable) once it's installed
            SystemEntries.byFeature[feat.route]?.forEach { rule ->
                AppEntrySetting(feat, rule.offKey, stringResource(rule.title), stringResource(rule.description), rule.icon, enabled = installed)
            }
            DeleteFeatureDataButton(feat)
        }

        // The rest unfolds once it's ready
        Reveal(visible = ready) { Column {
            if (feat.hasSettingsContent) {
                SectionTitle(stringResource(R.string.s_feature_options))
                // Spaced as the System section's: the tools' own rows add none
                Column(verticalArrangement = RowArrangement) { feat.SettingsContent() }
            }

            feat.widget?.let { widget ->
                SectionTitle(stringResource(R.string.s_feature_widget))
                Column(verticalArrangement = RowArrangement) {
                    WidgetBackgroundSetting(widget)
                }
            }

            // Its packages, as the installation page showed them, and the button to all of them
            if (feat.linuxExtensions.isNotEmpty()) {
                SectionTitle(stringResource(R.string.s_feature_info))
                // With its extra ones it unpacked (e.g. a Java version)
                FeaturePackages(feat.linuxExtensions + feat.extraExtensions.filter { LinuxParts.isExtensionUnpacked(context, it) })
            }
        } }
    }
}

/**
 * An entry [feature] adds to the system (e.g. "Share to", "Open in"), on unless switched off here:
 * [key] holds whether it's off. Not [enabled] (its tool isn't installed): grayed out, showing it'll be
 * on once it is.
 */
@Composable
fun AppEntrySetting(feature: AppFeature, key: String, title: String, subtitle: String, icon: ImageVector, enabled: Boolean = true) {
    val context = LocalContext.current
    // Read again as it's installed or removed (removing it resets the choice)
    var on by remember(enabled) { mutableStateOf(!PreferenceHelper.get(context, key, false)) }
    SwitchButton(title = title, subtitle = subtitle, icon = icon, checked = on, enabled = enabled) {
        on = it
        PreferenceHelper.put(context, key, !it)
        SystemEntries.apply(context, feature)
    }
}

/** Deletes everything [feature] keeps (see [AppFeature.clearData]), after asking. */
@Composable
private fun DeleteFeatureDataButton(feature: AppFeature) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val navController = LocalAppNav.current.controller
    ConfirmedActionButton(
        title = stringResource(R.string.s_feature_delete),
        subtitle = null,
        icon = Icons.Default.DeleteForever,
        confirmTitle = stringResource(R.string.s_feature_delete_confirm_title, stringResource(feature.titleStringResId)),
        confirmText = stringResource(R.string.s_feature_delete_confirm_msg) + programsNote(listOf(feature)),
        confirmLabel = stringResource(R.string.s_feature_delete_confirm)
    ) {
        feature.clearData(context)
        Toast.makeText(context, resources.getString(R.string.s_feature_deleted), Toast.LENGTH_SHORT).show()
        // Its settings are gone until it's opened again
        navController.popBackStack()
    }
}

/** That [features]' unpacked programs go too, and how much they take; empty if none are unpacked. */
@Composable
private fun programsNote(features: List<AppFeature>): String {
    val context = LocalContext.current
    // What it frees now: the unpacked ones (downloaded modules go when Play uninstalls them)
    val removed = LinuxTasks.unused(context, features).filter { LinuxParts.isExtensionUnpacked(context, it.name) }
    if (removed.isEmpty()) return ""
    return "\n\n" + stringResource(R.string.s_feature_delete_programs, Formatter.formatShortFileSize(context, removed.sumOf { it.size }))
}

/** Every feature's data at once (shown when there's any, see Settings). */
@Composable
fun ClearAllFeaturesButton() {
    val context = LocalContext.current
    val resources = LocalResources.current
    ConfirmedActionButton(
        title = stringResource(R.string.s_features_clear_all),
        subtitle = null,
        icon = Icons.Default.DeleteSweep,
        confirmTitle = stringResource(R.string.s_features_clear_all_confirm_title),
        confirmText = stringResource(R.string.s_features_clear_all_confirm_msg) + programsNote(FeatureRegistry.features),
        confirmLabel = stringResource(R.string.s_features_clear_all_confirm)
    ) {
        AppReset.features(context)
        Toast.makeText(context, resources.getString(R.string.s_features_cleared), Toast.LENGTH_SHORT).show()
    }
}

