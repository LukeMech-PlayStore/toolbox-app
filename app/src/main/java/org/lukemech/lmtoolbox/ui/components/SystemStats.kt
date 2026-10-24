package org.lukemech.lmtoolbox.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.lukemech.lmtoolbox.linux.LinuxInstaller
import org.lukemech.lmtoolbox.helpers.ResourceTile

/**
 * What a heavy job takes, while it runs: how fast it writes ([writtenBytes], a running total of
 * what it wrote so far, e.g. [LinuxInstaller.unpackedBytes]), then the app's CPU and memory (see [ResourceTiles]).
 */
@Composable
fun SystemStats(writtenBytes: () -> Long, modifier: Modifier = Modifier) =
    ResourceTiles(listOf(ResourceTile.Disk, ResourceTile.Cpu, ResourceTile.Ram), modifier = modifier, writtenBytes = writtenBytes)
