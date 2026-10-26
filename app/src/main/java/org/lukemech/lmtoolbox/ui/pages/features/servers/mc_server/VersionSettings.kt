package org.lukemech.lmtoolbox.ui.pages.features.servers.mc_server

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.servers.mc_server.Installer
import org.lukemech.lmtoolbox.features.servers.mc_server.ServerProfile
import org.lukemech.lmtoolbox.features.servers.mc_server.ServerRunner
import org.lukemech.lmtoolbox.features.servers.mc_server.ServerType
import org.lukemech.lmtoolbox.ui.components.ConfirmDialog
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.RowColors
import org.lukemech.lmtoolbox.ui.components.RowArrangement

/**
 * Its Minecraft version and its loader's: an update when there's one (the loader's newest stable),
 * either picked anew (Minecraft's newer ones alone: a world doesn't go back), confirmed first; while
 * it's stopped only (grayed out else). How a change goes is under the page's title (its steps); why
 * one failed, under them. Then its Java: the one its version is made for.
 */
@Composable
internal fun VersionSettings(profile: ServerProfile, run: ServerRunner.Run?, onChanged: () -> Unit) {
    val context = LocalContext.current
    val running = run?.isActive == true
    val install by Installer.state.collectAsState()
    val change = install?.takeIf { it.existing && it.id == profile.id }
    val enabled = !running && install?.isActive != true
    // Changed: its profile read again
    LaunchedEffect(change?.stage) {
        if (change?.stage == Installer.Stage.Done) {
            Installer.clear()
            onChanged()
        }
    }
    // Snapshots too, when they're shown, or when it runs one (not among the releases)
    var snapshots by remember { mutableStateOf(snapshotsShown(context)) }
    var versions by remember(profile.type, snapshots) { mutableStateOf<List<String>?>(null) }
    var loaders by remember(profile.type, profile.version) { mutableStateOf<List<Installer.Loader>?>(null) }
    // Asked for and not answered yet: their settings grayed out with a spinner, as a new server's
    var versionsLoading by remember(profile.type, snapshots) { mutableStateOf(true) }
    var loadersLoading by remember(profile.type, profile.version, profile.loader) { mutableStateOf(true) }
    LaunchedEffect(profile.type, snapshots) {
        versions = runCatching { Installer.versions(profile.type, snapshots) }.getOrNull()
        versionsLoading = false
    }
    LaunchedEffect(profile.type, profile.version, profile.loader) {
        loaders = runCatching { Installer.loaders(profile.type, profile.version) }.getOrNull()
        loadersLoading = false
    }
    // The version and loader to change to, being confirmed
    var confirm by remember { mutableStateOf<Pair<String, String?>?>(null) }
    val installedTag = stringResource(R.string.f_Mc_installed)
    val newestTag = stringResource(R.string.f_Mc_latest)
    val recommendedTag = stringResource(R.string.f_Mc_java_recommended)
    fun label(text: String, vararg tags: String?) = (listOf(text) + tags.filterNotNull()).joinToString(" · ")

    Column(verticalArrangement = RowArrangement) {
        // Its loader's newest stable, newer than its own (or its own too old to be listed)
        val current = profile.loader
        val preferred = loaders?.let(Installer::preferred)
        val update = preferred?.takeIf { p ->
            val all = loaders.orEmpty()
            val i = all.indexOfFirst { it.version == current }
            current != null && p.version != current && (i < 0 || all.indexOf(p) < i)
        }
        val offer = update?.let { u -> current?.let { it to u.version } }
        // The last one while it folds away (updated, or no longer newer)
        val lastOffer = remember { arrayOfNulls<Pair<String, String>>(1) }
        if (offer != null) lastOffer[0] = offer
        Reveal(visible = offer != null) {
            lastOffer[0]?.let { (from, to) ->
                FeatureButton(
                    title = stringResource(R.string.f_Mc_update_available),
                    subtitle = "${profile.type.label} ${Installer.loaderLabel(profile.type, from)} → ${Installer.loaderLabel(profile.type, to)}",
                    icon = Icons.Default.Update,
                    colors = RowColors.highlighted,
                    buttonContent = {
                        TextButton(onClick = { Installer.change(context, profile, profile.version, to) }, enabled = enabled && offer != null) {
                            Text(stringResource(R.string.f_Mc_update))
                        }
                    }
                )
            }
        }

        // Minecraft's: its own and those newer
        val offered = versions?.let { all -> all.indexOf(profile.version).takeIf { it >= 0 }?.let { all.take(it + 1) } } ?: listOf(profile.version)
        OptionSetting(
            title = stringResource(R.string.f_Mc_version),
            icon = Icons.Default.Update,
            selected = profile.version,
            options = offered.mapIndexed { i, v ->
                v to label(v, newestTag.takeIf { i == 0 && offered.size > 1 }, installedTag.takeIf { v == profile.version })
            },
            enabled = enabled,
            loading = versionsLoading,
            onSelect = { if (enabled && it != profile.version) confirm = it to null }
        )

        // Its loader's
        if (profile.type != ServerType.Vanilla && current != null) {
            val list = loaders?.takeIf { l -> l.any { it.version == current } } ?: (loaders.orEmpty() + Installer.Loader(current, true))
            OptionSetting(
                title = stringResource(R.string.f_Mc_loader_version, profile.type.label),
                icon = Icons.Default.Extension,
                selected = current,
                options = list.map { l ->
                    l.version to label(
                        Installer.loaderLabel(profile.type, l.version),
                        recommendedTag.takeIf { l == preferred }, installedTag.takeIf { l.version == current }
                    )
                },
                enabled = enabled,
                loading = loadersLoading,
                onSelect = { if (enabled && it != current) confirm = profile.version to it }
            )
        }
        SnapshotsSetting(snapshots, enabled) { snapshots = it }
        Note(stringResource(R.string.f_Mc_version_stop_first), visible = running)

        // Its Java: the one its version is made for, installed as it starts (asked first)
        Spacer(modifier = Modifier.height(4.dp))
        JavaSetting(profile.needs)
    }

    confirm?.let { (version, loader) ->
        val minecraft = version != profile.version
        ConfirmDialog(
            Icons.Default.Update,
            if (minecraft) stringResource(R.string.f_Mc_change_version_title, version)
            else stringResource(R.string.f_Mc_change_loader_title, "${profile.type.label} ${Installer.loaderLabel(profile.type, loader.orEmpty())}"),
            if (minecraft) stringResource(R.string.f_Mc_change_version_text, profile.version, version) else stringResource(R.string.f_Mc_change_loader_text),
            stringResource(if (minecraft) R.string.f_Mc_change else R.string.f_Mc_install),
            onDismiss = { confirm = null }
        ) {
            confirm = null
            Installer.change(context, profile, version, loader)
        }
    }
}
