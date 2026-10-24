package org.lukemech.lmtoolbox.ui.components

import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.AutoDelete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.linux.LinuxExtension
import org.lukemech.lmtoolbox.linux.LinuxTasks

/** A package in a [PackageTable]: with its [archive] (downloaded, or to be), and how it's installing ([state]). */
data class PackageTableRow(
    val ext: LinuxExtension,
    val archive: Boolean,
    val state: LinuxTasks.PackageState? = null,
    /** On the device (installed packages, a feature's settings): whether it's unpacked; null: to be installed. */
    val unpacked: Boolean? = null,
    /** Its module waits for Google Play to uninstall it (its data deleted; only the archive is left). */
    val removalQueued: Boolean = false,
    /** Its version: on the device, as it's unpacked; else as the app ships it. */
    val version: String = ext.version,
    /** Picked among the table's (a choice, see [PackageTable]'s onSelect: e.g. a server's Java); null: no choice. */
    val selected: Boolean? = null,
    /** A word by its name, e.g. "recommended" (among a choice's). */
    val tag: String? = null,
)

/**
 * Packages as a table: each with its sizes (its archive and unpacked) and what it holds, with their
 * versions (as the subsystem's build listed them) - on the installation page (what's to get, how
 * it's doing), the installed packages page and a feature's settings (what's there). [scrollable]:
 * it scrolls itself (fading at its edges, as the pages do), else the page it's on does.
 */
@Composable
fun PackageTable(
    rows: List<PackageTableRow>, modifier: Modifier = Modifier, scrollable: Boolean = false,
    /** Its rows are a choice ([PackageTableRow.selected]): a tap picks one, its badge and chevron open it. */
    onSelect: ((LinuxExtension) -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        val scroll = rememberScrollState()
        Column(
            modifier = (if (scrollable) Modifier.edgeFade(scroll).verticalScroll(scroll) else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            rows.forEachIndexed { i, row -> PackageRow(row, first = i == 0, onSelect = onSelect.takeIf { row.selected != null }) }
        }
    }
}

/**
 * A package in a [PackageTable]: its name, then its archive (downloaded or to be, if [PackageTableRow.archive])
 * and what it unpacks to (on the device: if it's unpacked), each with its size and, while installing,
 * how that step is doing - then what it holds with their versions; last, whether Google Play is to remove it.
 */
@Composable
private fun PackageRow(row: PackageTableRow, first: Boolean, onSelect: ((LinuxExtension) -> Unit)? = null) {
    val context = LocalContext.current
    val ext = row.ext
    val download = row.archive
    val state = row.state
    if (!first) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
    // Each step while installing: waiting, under way, done (nothing before it starts). On the device
    // just its steps' icons: only what's there (its archive, if it is; what's unpacked, if it is -
    // else, its archive there, the archive's copy Play unpacked, e.g. waiting for Play to remove it)
    val unpacked = row.unpacked != false || row.archive
    // Unpacking takes the archive's second copy too (see LinuxExtension.extractedSize) and what it
    // unpacks to - on the device, what of it is there: as InstalledPart.size counts it. To be installed,
    // already downloaded: just what it unpacks to (the copy is there already)
    val unpackSize = (if (row.archive) ext.extractedSize else 0) + (if (row.unpacked != false) ext.size else 0)
    val downloadStep = when {
        row.unpacked != null -> null
        state == null -> null
        state == LinuxTasks.PackageState.Waiting -> Step.Waiting
        state is LinuxTasks.PackageState.Downloading -> Step.Running
        else -> Step.Done
    }
    val unpackStep = when {
        row.unpacked != null -> null
        state == null -> null
        state is LinuxTasks.PackageState.Unpacking -> Step.Running
        state == LinuxTasks.PackageState.Done -> Step.Done
        // Waiting its turn: the next step, if downloading comes first
        state == LinuxTasks.PackageState.Waiting -> if (download) null else Step.Waiting
        else -> Step.Waiting
    }
    // What it holds, folded away (it can be dozens): its header opens it, a chevron telling so
    val holds = ext.components.isNotEmpty()
    var expanded by rememberSaveable(ext.name) { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    // The package as a list item (the whole of it opens what it holds): its name, standing out; under
    // it, quieter, its version and sizes (their icons telling how its steps are doing); on the right
    // how many packages it holds, as a badge, and the chevron opening them. Then what it holds
    // A choice: the row picks it, a radio button telling which is; what it holds opens with its badge
    val choice = onSelect != null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (choice) Modifier.clickable { onSelect(ext) }
                else Modifier.clickable(
                    enabled = holds,
                    onClickLabel = stringResource(if (expanded) R.string.s_packages_hide else R.string.s_packages_show)
                ) { expanded = !expanded }
            )
            .padding(vertical = 6.dp)
    ) {
        if (choice) {
            RadioButton(selected = row.selected == true, onClick = { onSelect(ext) })
            Spacer(modifier = Modifier.width(4.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    ext.name,
                    style = MaterialTheme.typography.titleMedium,
                    // Red, as the line telling it's to be removed
                    color = if (row.removalQueued) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                row.tag?.let {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            val dot = @Composable { Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (row.version.isNotEmpty()) StepSize(null, row.version, null)
                if (row.version.isNotEmpty() && (download || unpacked)) dot()
                if (download) StepSize(Icons.Outlined.Download, Formatter.formatShortFileSize(context, ext.archiveSize), downloadStep)
                // Unpacking comes after a download, always
                if (download) dot()
                if (unpacked) StepSize(Icons.Outlined.Unarchive, Formatter.formatShortFileSize(context, unpackSize), unpackStep)
            }
        }
        if (holds) {
            // How many it holds, by the chevron opening them (on their own, in a choice)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (!choice) Modifier else Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = stringResource(if (expanded) R.string.s_packages_hide else R.string.s_packages_show)) { expanded = !expanded }
                    .padding(4.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (row.removalQueued) MaterialTheme.colorScheme.error.copy(alpha = 0.16f) else MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        "${ext.components.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (row.removalQueued) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp).rotate(chevron)
                )
            }
        }
    }
    // Under it, from the left edge, opened and folded with an animation
    Reveal(visible = expanded) {
        Column(modifier = Modifier.listBox(rememberScrollState())) {
            ext.components.forEach { component ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    // A bullet each, as a list of what the package holds
                    Text(
                        "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        component.substringBefore(' '),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        component.substringAfter(' ', ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    // Deleted, its archive still here until Play uninstalls its module (it does when it needs the space)
    Reveal(visible = row.removalQueued) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
            Icon(Icons.Outlined.AutoDelete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.s_packages_removal), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** How a package's step (downloading, unpacking) is doing on the installation page. */
private sealed interface Step {
    data object Waiting : Step
    data object Running : Step
    data object Done : Step
}

/**
 * A step of getting a package (its [icon]: downloading, unpacking) and its [size]. Once the installation
 * started ([step]), it tells how that step is doing: under way, a spinner and its size in the
 * theme's color; done, a check.
 */
@Composable
private fun StepSize(icon: ImageVector?, size: String, step: Step?) {
    val color = if (step == Step.Running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val iconColor = when (step) {
        Step.Running, Step.Done -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null || step != null) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(15.dp)) {
                if (step == Step.Running) {
                    CircularProgressIndicator(color = iconColor, strokeWidth = 1.5.dp, modifier = Modifier.size(12.dp))
                } else {
                    Icon(if (step == Step.Done) Icons.Default.Check else icon!!, contentDescription = null, tint = iconColor, modifier = Modifier.size(15.dp))
                }
            }
            Spacer(modifier = Modifier.width(3.dp))
        }
        Text(size, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1)
    }
}
