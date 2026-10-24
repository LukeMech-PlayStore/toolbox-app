package org.lukemech.lmtoolbox.ui.pages

import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.linux.LinuxTasks
import androidx.compose.runtime.collectAsState
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppReset
import org.lukemech.lmtoolbox.helpers.Search
import org.lukemech.lmtoolbox.ui.components.EmptyText
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.SearchField
import org.lukemech.lmtoolbox.ui.components.SectionTitle
import org.lukemech.lmtoolbox.ui.pages.settings.AppResetButton
import org.lukemech.lmtoolbox.ui.pages.settings.BugReportSection
import org.lukemech.lmtoolbox.ui.pages.settings.ClearAllFeaturesButton
import org.lukemech.lmtoolbox.ui.pages.settings.CurrentFeatureSettingsSection
import org.lukemech.lmtoolbox.ui.pages.settings.DefaultToolSetting
import org.lukemech.lmtoolbox.ui.pages.settings.DiscussionGroupSection
import org.lukemech.lmtoolbox.ui.pages.settings.EnvManagerSection
import org.lukemech.lmtoolbox.ui.pages.settings.FeatureSettingsButton
import org.lukemech.lmtoolbox.ui.pages.settings.GeneralResetButton
import org.lukemech.lmtoolbox.ui.pages.settings.OpenSourceLicensesSection
import org.lukemech.lmtoolbox.ui.pages.settings.PkgInfoSection
import org.lukemech.lmtoolbox.ui.pages.settings.StartPageSetting
import org.lukemech.lmtoolbox.ui.pages.settings.ToolSettingsSection
import org.lukemech.lmtoolbox.ui.pages.settings.settingsFeatures
import org.lukemech.lmtoolbox.ui.components.ROW_SPACING
import org.lukemech.lmtoolbox.ui.components.RowArrangement

/** A setting on this page, found by its [titles] and [keywords] (string resources, in every app language). */
private class Entry(
    @param:StringRes val section: Int,
    @param:StringRes val titles: List<Int>,
    @param:StringRes val keywords: Int,
    /** Whether it's worth showing now (e.g. a reset only when there's something to reset); slides in and out. */
    val visible: @Composable () -> Boolean = { true },
    val content: @Composable () -> Unit
)

// Sections show in the order of their first entry
private val entries = listOf(
    Entry(R.string.s_section_features, listOf(R.string.s_feature_settings), R.string.sk_feature_settings) {
        // The middle tab's feature first, if it has settings yet
        Column(verticalArrangement = RowArrangement) {
            CurrentFeatureSettingsSection()
            ToolSettingsSection()
        }
    },
    Entry(
        R.string.s_section_features, listOf(R.string.s_features_clear_all), R.string.sk_features_clear_all,
        visible = {
            // Again once deleted programs are gone (that's in the background)
            LinuxTasks.extensionsRemoved.collectAsState().value
            AppReset.featuresHaveData(LocalContext.current)
        }
    ) { ClearAllFeaturesButton() },
    Entry(R.string.s_section_general, listOf(R.string.s_start_page), R.string.sk_start_page) { StartPageSetting() },
    Entry(R.string.s_section_general, listOf(R.string.s_default_tool), R.string.sk_default_tool) { DefaultToolSetting() },
    Entry(
        R.string.s_section_general, listOf(R.string.s_general_reset), R.string.sk_general_reset,
        visible = { AppReset.generalChanged }
    ) { GeneralResetButton() },
    Entry(R.string.s_section_app, listOf(R.string.app_name), R.string.sk_app_version) { PkgInfoSection() },
    Entry(R.string.s_section_app, listOf(R.string.s_env_manager, R.string.s_tasks), R.string.sk_env_manager) { EnvManagerSection() },
    Entry(R.string.s_section_app, listOf(R.string.s_osl_title), R.string.sk_licenses) { OpenSourceLicensesSection() },
    Entry(R.string.s_section_app, listOf(R.string.s_discussion_group), R.string.sk_discussion_group) { DiscussionGroupSection() },
    Entry(R.string.s_section_app, listOf(R.string.s_bug_report), R.string.sk_bug_report) { BugReportSection() },
    Entry(R.string.s_section_app, listOf(R.string.s_app_reset), R.string.sk_app_reset) { AppResetButton() },
)

@Composable
fun SettingsPage() {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }

    val searchIndex = remember { entries.map { it to Search.entry(context, it.titles, listOf(it.keywords)) } }
    // Settings of the opened features are found by the features' own names and keywords
    val features = settingsFeatures
    val featureIndex = remember(features) {
        features.map { it to Search.entry(context, listOf(it.titleStringResId), listOf(it.keywordsResId, it.category.titleRes)) }
    }

    PageHeading(
        title = stringResource(id = R.string.nav_settings),
        // Stays on top while the settings scroll, as on the home page
        header = {
            SearchField(query, { query = it }, stringResource(R.string.search_settings))
            Spacer(modifier = Modifier.height(8.dp))
        }
    ) {
        if (query.isBlank()) {
            Sections(entries) { entry -> entry.content() }
            return@PageHeading
        }

        val found = searchIndex.filter { Search.score(query, it.second) > 0 }.map { it.first }
        val foundFeatures = featureIndex.filter { Search.score(query, it.second) > 0 }.map { it.first }
        if (found.isEmpty() && foundFeatures.isEmpty()) {
            EmptyText(stringResource(R.string.search_no_results))
            return@PageHeading
        }
        if (foundFeatures.isNotEmpty()) {
            SectionTitle(stringResource(R.string.s_feature_settings))
            Column(verticalArrangement = RowArrangement) {
                foundFeatures.forEach { FeatureSettingsButton(it) }
            }
        }
        Sections(found) { entry -> entry.content() }
    }
}

/** [items] under their section titles, in the sections' order. */
@Composable
private fun Sections(items: List<Entry>, content: @Composable (Entry) -> Unit) {
    items.groupBy { it.section }.forEach { (section, inSection) ->
        SectionTitle(stringResource(section))
        Column {
            inSection.forEachIndexed { i, entry ->
                // The gap goes with the entry, so a hidden one leaves none
                Reveal(visible = entry.visible()) {
                    Column {
                        if (i > 0) Spacer(modifier = Modifier.height(ROW_SPACING))
                        content(entry)
                    }
                }
            }
        }
    }
}
