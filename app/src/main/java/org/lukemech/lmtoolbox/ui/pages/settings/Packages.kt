package org.lukemech.lmtoolbox.ui.pages.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.linux.InstalledPart
import org.lukemech.lmtoolbox.linux.LinuxParts
import org.lukemech.lmtoolbox.tasks.AppTask
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.helpers.SettingsSubPage
import org.lukemech.lmtoolbox.ui.components.EmptyText
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.PackageTable
import org.lukemech.lmtoolbox.ui.components.PackageTableRow
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.helpers.LocalAppNav
import org.lukemech.lmtoolbox.ui.helpers.openSettingsPage
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.tasks.AppTasks
import org.lukemech.lmtoolbox.ui.components.installsKey

/** What of the Linux subsystem is on the device, read again as tasks change (e.g. once something's unpacked or deleted). */
@Composable
private fun rememberInstalledParts(): List<InstalledPart> {
    val context = LocalContext.current
    val removed by LinuxTasks.extensionsRemoved.collectAsState()
    // Not with every task's progress: it looks through what's installed
    val installs = installsKey()
    return remember(installs, removed) { LinuxParts.installedParts(context) }
}

/** [this] as an installed package's row: whether it's unpacked, whether Play is to remove it. */
@Composable
private fun InstalledPart.row(): PackageTableRow {
    val tasks by AppTasks.tasks.collectAsState()
    val installing = tasks.any { it.type == AppTask.Type.Extension && it.isActive }
    return PackageTableRow(part, archive = archive, unpacked = unpacked, removalQueued = archive && !unpacked && !installing, version = version)
}

/** Opens the installed packages page; with how many there are and what they take. */
@Composable
fun InstalledPackagesButton() {
    val context = LocalContext.current
    val navController = LocalAppNav.current.controller
    val parts = rememberInstalledParts()
    FeatureButton(
        title = stringResource(R.string.s_packages),
        subtitle = stringResource(R.string.s_packages_summary, parts.size, Formatter.formatShortFileSize(context, parts.sumOf { it.size })),
        icon = Icons.Outlined.Inventory2,
        icon2 = Icons.AutoMirrored.Default.KeyboardArrowRight,
        onClick = { navController.openSettingsPage(SettingsSubPage.Packages) }
    )
}

/**
 * The Linux subsystem's parts on the device: what they take in all, then each as the installation
 * page shows it (its archive, if it's there, and what it unpacked to; what it holds).
 */
@Composable
fun InstalledPackagesPage() {
    val context = LocalContext.current
    val parts = rememberInstalledParts()
    PageHeading(
        title = stringResource(R.string.s_packages),
        back = true,
        // What they take stays in sight: only the list scrolls
        header = if (parts.isEmpty()) null else {
            {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                ) {
                    Text(
                        Formatter.formatShortFileSize(context, parts.sumOf { it.size }),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        stringResource(R.string.s_packages_taken, parts.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) {
        if (parts.isEmpty()) {
            EmptyText(stringResource(R.string.s_packages_empty))
            return@PageHeading
        }
        PackageTable(parts.map { it.row() }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
    }
}

/**
 * In a feature's settings: its packages (its own, and what that builds on) as the installation page
 * shows them, and the button to all of them. Only once they're there (see FeatureSettingsPage).
 */
@Composable
fun FeaturePackages(extensions: List<String>) {
    val context = LocalContext.current
    val parts = rememberInstalledParts()
    // Every part it needs (its requires, through and through: the core too), as on the device or, not
    // there yet, what it would take
    val required = remember(parts) { LinuxParts.extensionWithRequired(context, extensions) }
    Column(verticalArrangement = RowArrangement) {
        PackageTable(
            required.map { ext -> parts.firstOrNull { it.part.name == ext.name }?.row() ?: PackageTableRow(ext, archive = true) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        InstalledPackagesButton()
    }
}
