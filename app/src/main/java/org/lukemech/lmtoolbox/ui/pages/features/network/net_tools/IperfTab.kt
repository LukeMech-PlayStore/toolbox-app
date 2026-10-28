package org.lukemech.lmtoolbox.ui.pages.features.network.net_tools

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.network.net_tools.Iperf
import org.lukemech.lmtoolbox.features.network.net_tools.IperfEvent
import org.lukemech.lmtoolbox.features.network.net_tools.IperfState
import org.lukemech.lmtoolbox.features.network.net_tools.NetworkToolsFeature
import org.lukemech.lmtoolbox.features.servers.common.Reach
import org.lukemech.lmtoolbox.helpers.PreferenceHelper
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.ui.components.BigButton
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.MainResult
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.ResourceTiles
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.RevealSection
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.ui.components.SwitchButton
import org.lukemech.lmtoolbox.ui.pages.features.servers.common.networkChanges

/** The iperf3 tab's choices, its run (installing iperf3 first, if it isn't) and its test; the page's, as the other tabs'. */
internal class IperfTabState(private val context: Context, private val scope: CoroutineScope) {
    var server by mutableStateOf(false)
    var host by mutableStateOf("")
    var port by mutableStateOf("${Iperf.PORT}")
    var seconds by mutableStateOf("10")
    var streams by mutableStateOf("1")
    var udp by mutableStateOf(false)
    var udpMbps by mutableStateOf("100")
    var reverse by mutableStateOf(false)
    var test by mutableStateOf<IperfState?>(null)
    var installing by mutableStateOf(false)
    var job by mutableStateOf<Job?>(null)
    val running get() = job?.isActive == true

    init {
        runCatching { JSONObject(PreferenceHelper.get(context, NetworkToolsFeature.S_IPERF, "{}")) }.getOrNull()?.let {
            server = it.optBoolean("server", server)
            host = it.optString("host", host)
            port = it.optString("port", port)
            seconds = it.optString("seconds", seconds)
            streams = it.optString("streams", streams)
            udp = it.optBoolean("udp", udp)
            udpMbps = it.optString("udpMbps", udpMbps)
            reverse = it.optBoolean("reverse", reverse)
        }
    }

    private val portNumber get() = port.toIntOrNull()?.takeIf { it in 1..65_535 } ?: Iperf.PORT

    val canStart get() = server || host.isNotBlank()

    fun start() {
        if (!canStart || running) return
        PreferenceHelper.put(
            context, NetworkToolsFeature.S_IPERF,
            JSONObject().put("server", server).put("host", host).put("port", port).put("seconds", seconds).put("streams", streams)
                .put("udp", udp).put("udpMbps", udpMbps).put("reverse", reverse).toString()
        )
        val isServer = server
        val cmd = if (isServer) Iperf.serverCommand(portNumber) else Iperf.clientCommand(
            Iperf.ClientOptions(
                host, portNumber,
                seconds = seconds.toIntOrNull()?.coerceIn(1, Iperf.MAX_SECONDS) ?: 10,
                streams = streams.toIntOrNull()?.coerceIn(1, Iperf.MAX_STREAMS) ?: 1,
                udp = udp,
                udpMbps = udpMbps.toIntOrNull()?.coerceAtLeast(1) ?: 100,
                reverse = reverse,
            )
        )
        test = null
        job = scope.launch {
            // Its part first, asked (declined, or failed: told in the bottom bar as any installation)
            if (!Iperf.isInstalled(context)) {
                installing = true
                val installed = try { Iperf.install(context).ok } finally { installing = false }
                if (!installed) return@launch
            }
            test = IperfState()
            Iperf.run(context, cmd, isServer) { test = it }
        }
    }

    fun stop() = job?.cancel()
}

/**
 * iperf3: a client measuring to a server (TCP or UDP, how long, how many streams, either way), or a server on the
 * phone for another device's client, at its addresses (the Wi-Fi's, the tailnet's). The rate each second as it
 * comes, then the test's totals.
 */
@Composable
internal fun IperfTab(state: IperfTabState) {
    val context = LocalContext.current
    // Again once its part's installed or removed
    val tasks by LinuxTasks.tasks.collectAsState()
    val available = remember { Iperf.isAvailable(context) }
    val installed = remember(tasks) { Iperf.isInstalled(context) }
    val enabled = !state.running && available
    val test = state.test
    Column(verticalArrangement = RowArrangement) {
        Note(stringResource(R.string.f_Net_iperf_unavailable), visible = !available)
        Note(stringResource(R.string.f_Net_iperf_install), visible = available && !installed)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            FilterChip(!state.server, { state.server = false }, { Text(stringResource(R.string.f_Net_iperf_client)) }, enabled = enabled)
            FilterChip(state.server, { state.server = true }, { Text(stringResource(R.string.f_Net_iperf_server)) }, enabled = enabled)
        }
        RevealSection(visible = !state.server) {
            HostField(state.host, { state.host = it }, stringResource(R.string.f_Net_iperf_server_host), enabled = enabled, onGo = state::start)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                NumberField(state.port, { state.port = it }, stringResource(R.string.f_Net_port), enabled, Modifier.weight(1f))
                NumberField(state.seconds, { state.seconds = it }, stringResource(R.string.f_Net_iperf_time), enabled, Modifier.weight(1f), suffix = "s", maxLength = 4)
                NumberField(state.streams, { state.streams = it }, stringResource(R.string.f_Net_iperf_streams), enabled, Modifier.weight(1f), maxLength = 3)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                FilterChip(!state.udp, { state.udp = false }, { Text("TCP") }, enabled = enabled)
                FilterChip(state.udp, { state.udp = true }, { Text("UDP") }, enabled = enabled)
            }
            // UDP's rate to send at (iperf3's own is 1 Mbit/s)
            RevealSection(visible = state.udp) {
                NumberField(
                    state.udpMbps, { state.udpMbps = it }, stringResource(R.string.f_Net_iperf_udp_rate), enabled,
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp), suffix = "Mbit/s"
                )
            }
            SwitchButton(
                title = stringResource(R.string.f_Net_iperf_reverse),
                subtitle = stringResource(R.string.f_Net_iperf_reverse_desc),
                icon = Icons.Default.SwapVert,
                checked = state.reverse,
                enabled = enabled
            ) { state.reverse = it }
        }
        RevealSection(visible = state.server) {
            NumberField(state.port, { state.port = it }, stringResource(R.string.f_Net_port), enabled, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            ServerAddresses(state.port.toIntOrNull() ?: Iperf.PORT)
        }
        // The test as it goes: the rate now over its chart, its totals once it's over
        Reveal(visible = test != null) {
            Column(verticalArrangement = RowArrangement) {
                IperfResults(test ?: IperfState(), state.server, state.running)
                RateChart(test?.intervals.orEmpty())
            }
        }
        Reveal(visible = state.running && !state.installing) { ResourceTiles(NetworkToolsFeature) }
    }
}

/** The phone's addresses a client reaches its server at: the Wi-Fi's, the tailnet's (again as the networks change); copied with a tap. */
@Composable
private fun ServerAddresses(port: Int) {
    val context = LocalContext.current
    val changes = networkChanges(context)
    val lan = remember(changes) { Reach.lanAddresses(context) }
    val tailnet = remember(changes) { Reach.tailscaleAddresses(context) }
    val suffix = if (port == Iperf.PORT) "" else " -p $port"
    Column(verticalArrangement = RowArrangement) {
        lan.forEach { AddressRow(stringResource(R.string.f_Net_iperf_on_lan), it, "iperf3 -c $it$suffix", Icons.Default.Wifi) }
        tailnet.forEach { AddressRow(stringResource(R.string.f_Net_iperf_on_tailnet), it, "iperf3 -c $it$suffix", Icons.Default.VpnLock) }
        Note(stringResource(R.string.f_Net_iperf_no_address), visible = lan.isEmpty() && tailnet.isEmpty())
    }
}

/** An address of the phone, large, what it's for small above it; its client's command copied with a tap. */
@Composable
private fun AddressRow(title: String, address: String, command: String, icon: ImageVector) {
    val context = LocalContext.current
    FeatureButton(
        title = title,
        subtitle = address,
        titleSubtitleSwap = true,
        icon = icon,
        buttonContent = { Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.f_Net_copy), tint = MaterialTheme.colorScheme.primary) },
        onClick = { copy(context, command) }
    )
}

/** The rate now (while it runs, or waiting for it), who's at the other end, and the totals: sent, received, TCP's retransmits, UDP's jitter and loss. */
@Composable
private fun IperfResults(test: IperfState, server: Boolean, running: Boolean) {
    val s = test.summary
    // Failed, it's told in the bottom bar (its command's), not here
    Results {
        when {
            s == null && test.now != null -> MainResult(stringResource(R.string.f_Net_iperf_now), formatRate(test.now!!))
            s != null -> (s.receivedBitsPerSecond ?: s.sentBitsPerSecond)?.let { MainResult(stringResource(R.string.f_Net_iperf_rate), formatRate(it)) }
            server && running -> MainResult(stringResource(R.string.f_Net_iperf_waiting), "${test.listening ?: ""}")
            running -> MainResult(stringResource(R.string.f_Net_iperf_connecting), "")
        }
        test.peer?.let { ResultRow(stringResource(if (server) R.string.f_Net_iperf_client_at else R.string.f_Net_iperf_server_at), it) }
        s?.sentBitsPerSecond?.let { ResultRow(stringResource(R.string.f_Net_iperf_sent), formatRate(it)) }
        s?.receivedBitsPerSecond?.let { ResultRow(stringResource(R.string.f_Net_iperf_received), formatRate(it)) }
        s?.retransmits?.let { ResultRow(stringResource(R.string.f_Net_iperf_retransmits), "$it") }
        s?.jitterMs?.let { ResultRow(stringResource(R.string.f_Net_iperf_jitter), formatMs(it)) }
        s?.lostPercent?.let { ResultRow(stringResource(R.string.f_Net_loss), "%.1f%%".format(it)) }
    }
}

/** Each interval's rate as a bar, the latest on the right, scaled to the fastest. */
@Composable
private fun RateChart(intervals: List<IperfEvent.Interval>) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
            .padding(8.dp)
    ) {
        val shown = intervals.takeLast(BARS)
        val top = shown.maxOfOrNull { it.bitsPerSecond }?.takeIf { it > 0 } ?: return@Canvas
        val slot = size.width / BARS
        shown.forEachIndexed { i, interval ->
            val h = (interval.bitsPerSecond / top * size.height).toFloat()
            drawRect(color, Offset(size.width - (shown.size - i) * slot + slot * 0.15f, size.height - h), Size(slot * 0.7f, h))
        }
    }
}

private const val BARS = 30

@Composable
internal fun IperfButtons(state: IperfTabState) {
    val context = LocalContext.current
    val available = remember { Iperf.isAvailable(context) }
    when {
        state.running -> BigButton(Icons.Default.Stop, stringResource(R.string.f_Net_stop), enabled = !state.installing, onClick = state::stop)
        state.server -> BigButton(Icons.Default.PlayArrow, stringResource(R.string.f_Net_iperf_serve), enabled = available, onClick = state::start)
        else -> BigButton(Icons.Default.PlayArrow, stringResource(R.string.f_Net_iperf_start), enabled = available && state.canStart, onClick = state::start)
    }
}
