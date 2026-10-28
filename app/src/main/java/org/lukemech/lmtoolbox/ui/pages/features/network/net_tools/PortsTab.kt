package org.lukemech.lmtoolbox.ui.pages.features.network.net_tools

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.json.JSONObject
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.network.net_tools.NetworkToolsFeature
import org.lukemech.lmtoolbox.features.network.net_tools.PortRanges
import org.lukemech.lmtoolbox.features.network.net_tools.PortScan
import org.lukemech.lmtoolbox.features.network.net_tools.Services
import org.lukemech.lmtoolbox.helpers.PreferenceHelper
import org.lukemech.lmtoolbox.ui.components.BigButton
import org.lukemech.lmtoolbox.ui.components.EmptyText
import org.lukemech.lmtoolbox.ui.components.InputField
import org.lukemech.lmtoolbox.ui.components.MainResult
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.ChipChoice
import org.lukemech.lmtoolbox.ui.components.FieldArrangement
import org.lukemech.lmtoolbox.ui.components.RowArrangement

/** Which ports a scan knocks at. */
internal enum class PortPreset { Common, WellKnown, All, Custom }

/** The port scan's host, ports and timeout, its run and the open ports; the page's, as the other tabs'. */
internal class PortsState(private val context: Context, private val scope: CoroutineScope) {
    var host by mutableStateOf("")
    var preset by mutableStateOf(PortPreset.Common)
    var custom by mutableStateOf("")
    var timeout by mutableStateOf("$TIMEOUT_MS")
    var result by mutableStateOf<PortScan.Result?>(null)
    private val run = TabRun(scope)
    val running get() = run.running

    init {
        runCatching { JSONObject(PreferenceHelper.get(context, NetworkToolsFeature.S_PORTS, "{}")) }.getOrNull()?.let {
            host = it.optString("host", host)
            preset = PortPreset.entries.firstOrNull { p -> p.name == it.optString("preset") } ?: preset
            custom = it.optString("custom", custom)
            timeout = it.optString("timeout", timeout)
        }
    }

    /** The ports to scan; null while the custom ones typed aren't ports. */
    val ports: List<Int>?
        get() = when (preset) {
            PortPreset.Common -> Services.COMMON
            PortPreset.WellKnown -> (1..1024).toList()
            PortPreset.All -> (1..PortRanges.MAX_PORTS).toList()
            PortPreset.Custom -> PortRanges.parse(custom)
        }

    fun start() {
        val ports = ports ?: return
        if (host.isBlank() || running) return
        PreferenceHelper.put(
            context, NetworkToolsFeature.S_PORTS,
            JSONObject().put("host", host).put("preset", preset.name).put("custom", custom).put("timeout", timeout).toString()
        )
        val ms = timeout.toIntOrNull()?.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS) ?: TIMEOUT_MS
        result = PortScan.Result()
        run.start { PortScan.run(host, ports, ms).collect { result = it } }
    }

    fun stop() = run.stop()

    @Composable
    fun steps() = result?.takeIf { running && it.total > 0 }?.let {
        scanSteps(stringResource(R.string.f_Net_ports_scanning), it.progress, "${it.scanned}/${it.total}")
    }

    companion object {
        const val TIMEOUT_MS = 500
        const val MIN_TIMEOUT_MS = 50
        const val MAX_TIMEOUT_MS = 10_000
    }
}

/** Port scan: a host, which ports (the common ones, 1-1024, all, or typed), how long each may take; the open ones as they're found. */
@Composable
internal fun PortsTab(state: PortsState) {
    val result = state.result
    val enabled = !state.running
    Column(verticalArrangement = FieldArrangement) {
        Fields {
            HostField(state.host, { state.host = it }, stringResource(R.string.f_Net_host), enabled = enabled, onGo = state::start)
            ChipChoice(
                null,
                PortPreset.entries.map { p ->
                    p to when (p) {
                        PortPreset.Common -> stringResource(R.string.f_Net_ports_common, Services.COMMON.size)
                        PortPreset.WellKnown -> "1–1024"
                        PortPreset.All -> stringResource(R.string.f_Net_ports_all)
                        PortPreset.Custom -> stringResource(R.string.f_Net_ports_custom)
                    }
                },
                state.preset,
                enabled = enabled
            ) { state.preset = it }
            // Ports as typed: digits, commas and dashes alone
            Reveal(visible = state.preset == PortPreset.Custom) {
                InputField(
                    value = state.custom,
                    onValueChange = { typed -> state.custom = typed.filter { it.isDigit() || it in ",- " }.take(200) },
                    label = { Text(stringResource(R.string.f_Net_ports_range)) },
                    placeholder = { Text("22, 80, 8000-8100") },
                    isError = state.custom.isNotBlank() && PortRanges.parse(state.custom) == null,
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            NumberField(state.timeout, { state.timeout = it }, stringResource(R.string.f_Net_timeout), enabled, Modifier.fillMaxWidth(), suffix = "ms")
        }
        Reveal(visible = result?.failure != null) {
            Results { MainResult(stringResource(R.string.f_Net_failed), result?.failure.orEmpty()) }
        }
        Reveal(visible = !result?.open.isNullOrEmpty()) {
            Column(verticalArrangement = RowArrangement) {
                result?.address?.let { Results { MainResult(stringResource(R.string.f_Net_ports_open, result.open.size), it) } }
                LinesCard { result?.open.orEmpty().forEach { open -> key(open.port) { Appearing { PortLine(open) } } } }
            }
        }
        Reveal(visible = result?.done == true && result.failure == null && result.open.isEmpty()) {
            EmptyText(stringResource(R.string.f_Net_ports_none))
        }
    }
}

/** An open port: its number, what usually listens there, how long it took to answer. */
@Composable
private fun PortLine(open: PortScan.Open) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
        Text(
            "${open.port}", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(64.dp)
        )
        Text(open.service ?: "?", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("${open.ms} ms", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun PortsButtons(state: PortsState) {
    if (state.running) BigButton(Icons.Default.Stop, stringResource(R.string.f_Net_stop), enabled = true, onClick = state::stop)
    else BigButton(Icons.Default.PlayArrow, stringResource(R.string.f_Net_ports_start), enabled = state.host.isNotBlank() && state.ports != null, onClick = state::start)
}
