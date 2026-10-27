package org.lukemech.lmtoolbox.ui.components

import android.os.SystemClock
import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.ResourceCounters
import org.lukemech.lmtoolbox.helpers.ResourceMonitor
import org.lukemech.lmtoolbox.helpers.ResourceSample
import org.lukemech.lmtoolbox.helpers.ResourceScope
import org.lukemech.lmtoolbox.helpers.ResourceTile

/** How often the tiles sample, and how many samples their charts keep. */
private const val SAMPLE_MS = 1000L
/** The first, sooner: a page's tiles show what they measure right away. */
private const val FIRST_SAMPLE_MS = 250L
private const val HISTORY = 30

/** A tile of its own beside the measured ones (e.g. a server's uptime, its players): just its value. */
data class InfoTile(val label: String, val value: String, val icon: ImageVector)

/**
 * What [feature]'s work takes, as its own tiles ([AppFeature.resourceTiles]) say, of [scope]'s
 * processes (e.g. its task's, ResourceScope.Task); [extra] tiles after them. See the other
 * ResourceTiles for the rest ([downloadRate]).
 */
@Composable
fun ResourceTiles(
    feature: AppFeature,
    modifier: Modifier = Modifier,
    scope: ResourceScope = ResourceScope.App,
    extra: List<InfoTile> = emptyList(),
    downloadRate: (() -> Long?)? = null,
) = ResourceTiles(feature.resourceTiles, modifier, scope, extra, downloadRate = downloadRate)

/**
 * What a job takes while it runs, in tiles: each of [tiles] with its value now (and the details under
 * it: down and up, read and written) over its recent samples as a chart; [extra] tiles after them.
 * Three to a row (four as two by two). [writtenBytes]: the disk's as a running total of what the job
 * wrote (e.g. the subsystem unpacking), instead of [scope]'s processes' reads and writes.
 * The network: Android counts it by app alone, so it's the whole app's (and says so) unless the job
 * tells its own ([downloadRate], bytes a second: e.g. yt-dlp's speed).
 */
@Composable
fun ResourceTiles(
    tiles: List<ResourceTile>,
    modifier: Modifier = Modifier,
    scope: ResourceScope = ResourceScope.App,
    extra: List<InfoTile> = emptyList(),
    writtenBytes: (() -> Long)? = null,
    downloadRate: (() -> Long?)? = null,
) {
    val context = LocalContext.current
    var history by remember(scope) { mutableStateOf(emptyList<ResourceSample>()) }
    val written by rememberUpdatedState(writtenBytes)
    val download by rememberUpdatedState(downloadRate)

    LaunchedEffect(scope, tiles) {
        var before: ResourceCounters = withContext(Dispatchers.IO) { ResourceMonitor.counters(scope, tiles) }
        var beforeWritten = written?.invoke()
        var beforeAt = SystemClock.elapsedRealtime()
        // What needs no second sample (memory) at once; the rest soon after, then every SAMPLE_MS
        if (history.isEmpty()) history = listOf(ResourceSample(null, before.rssBytes, null, null, null, null))
        var first = true
        while (true) {
            delay((if (first) FIRST_SAMPLE_MS else SAMPLE_MS).milliseconds)
            first = false
            val now = withContext(Dispatchers.IO) { ResourceMonitor.counters(scope, tiles) }
            var sample = ResourceMonitor.sample(before, now)
            written?.let { total ->
                val bytes = total()
                val at = SystemClock.elapsedRealtime()
                val seconds = (at - beforeAt) / 1000.0
                val perSec = beforeWritten?.let { b -> ((bytes - b).coerceAtLeast(0) / seconds.coerceAtLeast(0.001)).toLong() }
                sample = sample.copy(readPerSec = null, writePerSec = perSec)
                beforeWritten = bytes
                beforeAt = at
            }
            // The job's own download, as it tells it (nothing it sends is known)
            download?.let { rate -> sample = sample.copy(rxPerSec = rate(), txPerSec = null) }
            history = (history + sample).takeLast(HISTORY)
            before = now
        }
    }

    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
    fun rate(bytes: Long?) = bytes?.let { size(it) + "/s" }
    val last = history.lastOrNull()
    val specs: List<TileSpec> = tiles.map { tile ->
        when (tile) {
            ResourceTile.Cpu -> TileSpec(
                stringResource(R.string.s_stat_cpu), Icons.Default.Speed, last?.cpuPercent?.let { "${it.toInt()}%" } ?: "–", null,
                history.map { it.cpuPercent }, max = 100f, MaterialTheme.colorScheme.secondary
            )
            ResourceTile.Ram -> TileSpec(
                stringResource(R.string.s_stat_ram), Icons.Default.Memory, last?.rssBytes?.let(::size) ?: "–", null,
                history.map { it.rssBytes?.toFloat() }, max = null, MaterialTheme.colorScheme.primary
            )
            ResourceTile.Disk -> {
                fun ResourceSample.disk() = if (readPerSec == null && writePerSec == null) null else (readPerSec ?: 0) + (writePerSec ?: 0)
                TileSpec(
                    stringResource(if (writtenBytes != null) R.string.s_stat_disk else R.string.s_stat_storage),
                    if (writtenBytes != null) Icons.Default.SaveAs else Icons.Default.Storage,
                    rate(last?.disk()) ?: "–",
                    last?.takeIf { writtenBytes == null && it.readPerSec != null }?.let { listOf("R ${size(it.readPerSec ?: 0)}", "W ${size(it.writePerSec ?: 0)}") },
                    history.map { it.disk()?.toFloat() }, max = null, MaterialTheme.colorScheme.tertiary
                )
            }
            ResourceTile.Network -> {
                fun ResourceSample.net() = rxPerSec?.let { it + (txPerSec ?: 0) }
                TileSpec(
                    // Its own download, or the app's traffic (all of it: the processes' own isn't known), saying so
                    stringResource(if (downloadRate != null) R.string.s_stat_network else R.string.s_stat_network_app),
                    Icons.Default.SwapVert, rate(last?.net()) ?: "–",
                    last?.takeIf { downloadRate == null && it.rxPerSec != null }?.let { listOf("↓ ${size(it.rxPerSec ?: 0)}", "↑ ${size(it.txPerSec ?: 0)}") },
                    history.map { it.net()?.toFloat() }, max = null, NETWORK_COLOR
                )
            }
        }
    } + extra.map { TileSpec(it.label, it.icon, it.value, null, null, null, MaterialTheme.colorScheme.outline) }

    ResourceTileGrid(specs, modifier)
}

/**
 * [tiles] alone, nothing measured (a board's chip, memory and voltages), laid out and drawn as the measured ones:
 * three to a row, their icons in [color].
 */
@Composable
fun InfoTiles(tiles: List<InfoTile>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) =
    ResourceTileGrid(tiles.map { TileSpec(it.label, it.icon, it.value, null, null, null, color) }, modifier)

/** A tile as [ChartTile] draws it. */
private data class TileSpec(
    val label: String, val icon: ImageVector, val value: String,
    /** Under the value, a line each (e.g. read and written, per second as the value is). */
    val detail: List<String>?,
    val samples: List<Float?>?, val max: Float?, val color: Color,
)

/** Tiles, three to a row (four as two by two), each row as tall as its tallest. */
@Composable
private fun ResourceTileGrid(specs: List<TileSpec>, modifier: Modifier) {
    val perRow = if (specs.size == 4) 2 else 3
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        specs.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                row.forEach { ChartTile(it, Modifier.weight(1f).fillMaxHeight()) }
                // A last row short of tiles keeps theirs as wide as the others'
                repeat(perRow - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/** Teal: none of the theme's colors is free for it (the others' are), and it reads in light and dark alike. */
private val NETWORK_COLOR = Color(0xFF1FA195)

/**
 * A tile: its icon and label in its color, its value (and detail under it), and its samples (null: a
 * tile without a chart) as a line over its filled area, up to its max (null: their own highest).
 */
@Composable
private fun ChartTile(spec: TileSpec, modifier: Modifier) {
    val (label, icon, value, detail, samples, max, color) = spec
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = if (samples != null) 0.dp else 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            // Alone in its tile (a board's chip), it may wrap; a measured one keeps to its line over its chart
            Text(
                value, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace,
                maxLines = if (samples == null && detail == null) 2 else 1, overflow = TextOverflow.Ellipsis
            )
            detail?.forEach {
                Text(
                    it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (samples != null) {
                Spacer(modifier = Modifier.weight(1f).height(8.dp))
                Sparkline(samples, max, color, Modifier.fillMaxWidth().height(32.dp))
            }
        }
    }
}

/** [samples] (newest on the right, [HISTORY] of them across) as a line over its area fading down; those null left out. */
@Composable
private fun Sparkline(samples: List<Float?>, max: Float?, color: Color, modifier: Modifier) {
    val fill = Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0f)))
    Canvas(modifier = modifier) {
        val known = samples.filterNotNull()
        if (known.size < 2) return@Canvas
        val top = (max ?: (known.max() * 1.2f)).takeIf { it > 0f } ?: 1f
        val step = size.width / (HISTORY - 1)
        val start = HISTORY - samples.size
        // Kept off the edges by half the line's width
        val stroke = 2.dp.toPx()
        fun point(i: Int, v: Float) = Offset((start + i) * step, size.height - stroke / 2 - (v / top).coerceIn(0f, 1f) * (size.height - stroke))
        val points = samples.mapIndexedNotNull { i, v -> v?.let { point(i, it) } }
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }
        drawPath(area, fill)
        drawPath(line, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
