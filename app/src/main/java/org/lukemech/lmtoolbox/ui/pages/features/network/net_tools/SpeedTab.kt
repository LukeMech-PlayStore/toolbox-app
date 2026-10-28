package org.lukemech.lmtoolbox.ui.pages.features.network.net_tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.network.net_tools.SpeedTest
import org.lukemech.lmtoolbox.ui.components.BigButton
import org.lukemech.lmtoolbox.ui.components.FieldArrangement
import org.lukemech.lmtoolbox.ui.components.MainResult
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.ReadingResult
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.ui.components.resultCard

/** The speed test's run and its result; the page's, as the other tabs'. */
internal class SpeedState(scope: CoroutineScope) {
    var result by mutableStateOf<SpeedTest.Result?>(null)
    private val run = TabRun(scope)
    val running get() = run.running

    fun start() {
        if (running) return
        result = SpeedTest.Result()
        run.start { SpeedTest.run().collect { result = it } }
    }

    fun stop() = run.stop()

    @Composable
    fun steps() = result?.takeIf { running && !it.done }?.let {
        scanSteps(stringResource(phaseCaption(it.phase)), it.progress, "")
    }
}

/**
 * The internet's speed, as speedtest.net shows it: the rate now large (the ping's while it's measured), then the
 * ping, its jitter, the download's and the upload's, the one being measured marked; who measured it and through whom.
 */
@Composable
internal fun SpeedTab(state: SpeedState) {
    val r = state.result
    val dash = "–"
    Column(verticalArrangement = FieldArrangement) {
        Column(verticalArrangement = RowArrangement) {
            Results(apart = false) {
                when {
                    r == null -> ReadingResult(dash, MBPS)
                    r.phase == SpeedTest.Phase.Latency -> ReadingResult(r.pingMs?.let(::formatPing) ?: dash, "ms")
                    !r.done -> ReadingResult(r.nowBps?.let(::formatMbps) ?: dash, MBPS)
                    else -> ReadingResult(r.downloadBps?.let(::formatMbps) ?: dash, MBPS)
                }
                r?.failure?.let { MainResult(stringResource(R.string.f_Net_failed), it) }
                val phase = r?.phase.takeIf { state.running }
                ResultRow(stringResource(R.string.f_Net_speed_ping), r?.pingMs?.let { "${formatPing(it)} ms" } ?: dash, marked = phase == SpeedTest.Phase.Latency)
                ResultRow(stringResource(R.string.f_Net_speed_jitter), r?.jitterMs?.let { "${formatPing(it)} ms" } ?: dash)
                ResultRow(
                    stringResource(R.string.f_Net_speed_download), r?.downloadBps?.let { "${formatMbps(it)} $MBPS" } ?: dash,
                    marked = phase == SpeedTest.Phase.Download
                )
                ResultRow(
                    stringResource(R.string.f_Net_speed_upload), r?.uploadBps?.let { "${formatMbps(it)} $MBPS" } ?: dash,
                    marked = phase == SpeedTest.Phase.Upload
                )
                r?.server?.let { s ->
                    listOfNotNull(s.city, s.code?.let { "($it)" }).joinToString(" ").ifEmpty { null }
                        ?.let { ResultRow(stringResource(R.string.f_Net_speed_server), "Cloudflare · $it") }
                    s.isp?.let { ResultRow(stringResource(R.string.f_Net_speed_isp), it) }
                }
            }
            // The phase's rate as it goes: once there's one
            Reveal(visible = !r?.samples.isNullOrEmpty()) {
                SpeedChart(r?.samples.orEmpty(), upload = r?.phase == SpeedTest.Phase.Upload)
            }
        }
        Note(stringResource(R.string.f_Net_speed_note))
    }
}

/** Each [SpeedTest.SAMPLE_MS]'s rate as a bar, from the left, the phase's whole length across; scaled to the fastest. */
@Composable
private fun SpeedChart(samples: List<Double>, upload: Boolean) {
    val color = if (upload) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val slots = (SpeedTest.PHASE_MS / SpeedTest.SAMPLE_MS).toInt()
    Canvas(modifier = Modifier.fillMaxWidth().height(96.dp).resultCard().padding(8.dp)) {
        val top = samples.maxOrNull()?.takeIf { it > 0 } ?: return@Canvas
        val slot = size.width / slots
        samples.take(slots).forEachIndexed { i, rate ->
            val h = (rate / top * size.height).toFloat()
            drawRect(color, Offset(i * slot + slot * 0.15f, size.height - h), Size(slot * 0.7f, h))
        }
    }
}

@Composable
internal fun SpeedButtons(state: SpeedState) {
    if (state.running) BigButton(Icons.Default.Stop, stringResource(R.string.f_Net_stop), enabled = true, onClick = state::stop)
    else BigButton(Icons.Default.PlayArrow, stringResource(R.string.f_Net_speed_start), enabled = true, onClick = state::start)
}

private const val MBPS = "Mbit/s"

private fun phaseCaption(phase: SpeedTest.Phase) = when (phase) {
    SpeedTest.Phase.Latency -> R.string.f_Net_speed_measuring_ping
    SpeedTest.Phase.Download -> R.string.f_Net_speed_downloading
    else -> R.string.f_Net_speed_uploading
}

/** Megabits a second, as speed tests give them: two decimals under 10, one under 100, none above. */
private fun formatMbps(bitsPerSecond: Double): String {
    val m = bitsPerSecond / 1e6
    return when {
        m < 10 -> "%.2f".format(m)
        m < 100 -> "%.1f".format(m)
        else -> "%.0f".format(m)
    }
}

private fun formatPing(ms: Double) = if (ms < 10) "%.1f".format(ms) else "%.0f".format(ms)
