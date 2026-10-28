package org.lukemech.lmtoolbox.ui.pages.features.network.net_tools

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.json.JSONObject
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.network.net_tools.NetworkToolsFeature
import org.lukemech.lmtoolbox.features.network.net_tools.Ping
import org.lukemech.lmtoolbox.features.network.net_tools.PingResult
import org.lukemech.lmtoolbox.features.network.net_tools.PingRow
import org.lukemech.lmtoolbox.helpers.PreferenceHelper
import org.lukemech.lmtoolbox.ui.components.BigButton
import org.lukemech.lmtoolbox.ui.components.MainResult
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.FieldArrangement
import org.lukemech.lmtoolbox.ui.components.RowArrangement

/** The ping tab's fields, its run and its results; the page's (its [scope]), so it goes on as the tabs change. */
internal class PingState(private val context: Context, private val scope: CoroutineScope) {
    var host by mutableStateOf("")
    var count by mutableStateOf("4")
    var interval by mutableStateOf("1")
    var size by mutableStateOf("56")
    var result by mutableStateOf<PingResult?>(null)
    /** Android's ping ran (else Java's check, timed by the app). */
    var native by mutableStateOf(true)
    private val run = TabRun(scope)
    val running get() = run.running

    init {
        // The last ping's, for the next one
        runCatching { JSONObject(PreferenceHelper.get(context, NetworkToolsFeature.S_PING, "{}")) }.getOrNull()?.let {
            host = it.optString("host", host)
            count = it.optString("count", count)
            interval = it.optString("interval", interval)
            size = it.optString("size", size)
        }
    }

    val options
        get() = Ping.Options(
            count = count.toIntOrNull()?.coerceIn(1, MAX_COUNT) ?: 4,
            interval = interval.toDoubleOrNull()?.coerceIn(Ping.MIN_INTERVAL, MAX_INTERVAL) ?: 1.0,
            size = size.toIntOrNull()?.coerceIn(0, Ping.MAX_SIZE) ?: 56,
        )

    fun start() {
        if (host.isBlank() || running) return
        PreferenceHelper.put(
            context, NetworkToolsFeature.S_PING,
            JSONObject().put("host", host).put("count", count).put("interval", interval).put("size", size).toString()
        )
        result = PingResult()
        run.start {
            Ping.run(host, options) { native = it }.collect { result = it }
        }
    }

    fun stop() = run.stop()

    companion object {
        const val MAX_COUNT = 1000
        const val MAX_INTERVAL = 60.0
    }
}

/** Ping: a host, how many probes, how often and how large; each reply as it comes, then their statistics. */
@Composable
internal fun PingTab(state: PingState) {
    val result = state.result
    Column(verticalArrangement = FieldArrangement) {
        Fields {
            HostField(state.host, { state.host = it }, stringResource(R.string.f_Net_host), enabled = !state.running, onGo = state::start)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(state.count, { state.count = it }, stringResource(R.string.f_Net_count), !state.running, Modifier.weight(1f), maxLength = 4)
                NumberField(
                    state.interval, { state.interval = it }, stringResource(R.string.f_Net_interval), !state.running, Modifier.weight(1f),
                    suffix = "s", decimal = true, maxLength = 4
                )
                NumberField(state.size, { state.size = it }, stringResource(R.string.f_Net_size), !state.running, Modifier.weight(1f), suffix = "B")
            }
        }
        Note(stringResource(R.string.f_Net_ping_fallback), visible = !state.native)
        // Its statistics once a probe's back (or lost), its replies under them
        Reveal(visible = result != null && (result.rows.isNotEmpty() || result.failure != null)) {
            Column(verticalArrangement = RowArrangement) {
                PingResults(result ?: PingResult())
                LinesCard { (result?.rows ?: emptyList()).forEach { row -> key(row.seq) { Appearing { PingRowLine(row) } } } }
            }
        }
    }
}

/** Min/avg/max and the loss; what failed instead, if ping did. */
@Composable
private fun PingResults(result: PingResult) {
    Results {
        result.failure?.let { MainResult(stringResource(R.string.f_Net_failed), it) }
        result.avg?.let { MainResult(stringResource(R.string.f_Net_avg), formatMs(it)) }
        result.address?.let { ResultRow(stringResource(R.string.f_Net_address), it) }
        result.min?.let { ResultRow(stringResource(R.string.f_Net_min), formatMs(it)) }
        result.max?.let { ResultRow(stringResource(R.string.f_Net_max), formatMs(it)) }
        result.lossPercent?.let { loss ->
            ResultRow(stringResource(R.string.f_Net_loss), stringResource(R.string.f_Net_loss_value, "%.0f".format(loss), result.received, result.sent))
        }
    }
}

/** A probe: its number, its time (or lost, or a router's error) and who answered. */
@Composable
private fun PingRowLine(row: PingRow) {
    val text = when {
        row.timeMs != null -> listOfNotNull(formatMs(row.timeMs), row.ttl?.let { "ttl $it" }, row.from).joinToString(" · ")
        row.error != null -> listOfNotNull(row.error, row.from).joinToString(" · ")
        else -> stringResource(R.string.f_Net_lost)
    }
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
        Text(
            "#${row.seq}", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(56.dp)
        )
        Text(
            text, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (row.isReply) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
internal fun PingButtons(state: PingState) {
    if (state.running) BigButton(Icons.Default.Stop, stringResource(R.string.f_Net_stop), enabled = true, onClick = state::stop)
    else BigButton(Icons.Default.PlayArrow, stringResource(R.string.f_Net_ping_start), enabled = state.host.isNotBlank(), onClick = state::start)
}
