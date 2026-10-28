package org.lukemech.lmtoolbox.ui.pages.features.network.net_tools

import android.content.Context
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.network.net_tools.NetworkToolsFeature
import org.lukemech.lmtoolbox.features.network.net_tools.Ping
import org.lukemech.lmtoolbox.features.network.net_tools.Traceroute
import org.lukemech.lmtoolbox.helpers.PreferenceHelper
import org.lukemech.lmtoolbox.ui.components.BigButton
import org.lukemech.lmtoolbox.ui.components.MainResult
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.FieldArrangement

/** The traceroute tab's host, its run and its hops; the page's, as the other tabs'. */
internal class TraceState(private val context: Context, private val scope: CoroutineScope) {
    var host by mutableStateOf(PreferenceHelper.get(context, NetworkToolsFeature.S_TRACE, ""))
    var result by mutableStateOf<Traceroute.Result?>(null)
    private val run = TabRun(scope)
    val running get() = run.running

    fun start() {
        if (host.isBlank() || running) return
        PreferenceHelper.put(context, NetworkToolsFeature.S_TRACE, host)
        result = Traceroute.Result()
        run.start { Traceroute.run(host).collect { result = it } }
    }

    fun stop() = run.stop()
}

/** Traceroute: each router on the way to a host, its name and round trips, hop by hop as they're found. */
@Composable
internal fun TraceTab(state: TraceState) {
    val hasPing = remember { Ping.hasNative() }
    val result = state.result
    Column(verticalArrangement = FieldArrangement) {
        Fields { HostField(state.host, { state.host = it }, stringResource(R.string.f_Net_host), enabled = !state.running && hasPing, onGo = state::start) }
        Note(stringResource(R.string.f_Net_trace_no_ping), visible = !hasPing)
        Note(stringResource(R.string.f_Net_trace_note), visible = hasPing)
        Reveal(visible = result?.failure != null) {
            Results { MainResult(stringResource(R.string.f_Net_failed), result?.failure.orEmpty()) }
        }
        Reveal(visible = !result?.hops.isNullOrEmpty()) {
            LinesCard { result?.hops.orEmpty().forEach { hop -> key(hop.ttl) { Appearing { HopLine(hop) } } } }
        }
    }
}

/** A hop: its number, who answered (its name over its address) and the round trips to it; "*" for no answer. */
@Composable
private fun HopLine(hop: Traceroute.Hop) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            "${hop.ttl}", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(32.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            val title = when {
                !hop.probed -> "…"
                hop.address == null -> "*"
                else -> hop.name ?: hop.address
            }
            Text(
                title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (hop.reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            // Its address (under its name) and round trips, once it's answered
            Reveal(visible = hop.address != null) {
                val rtts = hop.rtts.joinToString(" / ") { formatMs(it) }.ifEmpty { null }
                Text(
                    listOfNotNull(hop.address.takeIf { hop.name != null }, rtts).joinToString(" · ").ifEmpty { hop.address.orEmpty() },
                    style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun TraceButtons(state: TraceState) {
    if (state.running) BigButton(Icons.Default.Stop, stringResource(R.string.f_Net_stop), enabled = true, onClick = state::stop)
    else BigButton(Icons.Default.PlayArrow, stringResource(R.string.f_Net_trace_start), enabled = state.host.isNotBlank() && Ping.hasNative(), onClick = state::start)
}
