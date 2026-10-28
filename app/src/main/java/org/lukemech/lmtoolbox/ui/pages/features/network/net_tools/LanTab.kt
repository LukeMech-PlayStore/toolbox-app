package org.lukemech.lmtoolbox.ui.pages.features.network.net_tools

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.network.net_tools.LanScan
import org.lukemech.lmtoolbox.features.network.net_tools.Services
import org.lukemech.lmtoolbox.ui.components.BigButton
import org.lukemech.lmtoolbox.ui.components.EmptyText
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.Note
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.ui.components.RowColors
import org.lukemech.lmtoolbox.ui.components.SectionTitle
import org.lukemech.lmtoolbox.ui.pages.features.servers.common.networkChanges

/** The LAN scan's run and the devices it found; the page's, as the other tabs'. */
internal class LanState(private val context: Context, private val scope: CoroutineScope) {
    var result by mutableStateOf<LanScan.Result?>(null)
    private val run = TabRun(scope)
    val running get() = run.running

    fun start(lan: LanScan.Lan) {
        if (running) return
        result = LanScan.Result(lan)
        run.start { LanScan.run(context, lan).collect { result = it } }
    }

    fun stop() = run.stop()

    @Composable
    fun steps() = result?.takeIf { running }?.let {
        scanSteps(stringResource(R.string.f_Net_lan_scanning), it.progress, "${it.scanned}/${it.total}")
    }
}

/**
 * The devices on the phone's Wi-Fi: its network on top (again as it changes), then each device found, with its
 * name, address, open ports and what it seems to be; a device's row opens its ports' scan ([onPorts]).
 */
@Composable
internal fun LanTab(state: LanState, onPorts: (String) -> Unit) {
    val context = LocalContext.current
    val changes = networkChanges(context)
    val lan = remember(changes) { LanScan.lan(context) }
    val result = state.result
    Column(verticalArrangement = RowArrangement) {
        // The network it scans, or why there's none (Wi-Fi off: straight to its switch)
        val wifiOff = remember(changes) { context.getSystemService(WifiManager::class.java)?.isWifiEnabled == false }
        Reveal(visible = lan == null) {
            FeatureButton(
                title = stringResource(if (wifiOff) R.string.f_Net_lan_wifi_off else R.string.f_Net_lan_none),
                subtitle = stringResource(R.string.f_Net_lan_none_desc),
                colors = RowColors.alert,
                icon = Icons.Default.WifiOff,
                icon2 = Icons.AutoMirrored.Default.OpenInNew,
                onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) } }
            )
        }
        // The last one while it folds away
        val shown = remember { arrayOfNulls<LanScan.Lan>(1) }
        if (lan != null) shown[0] = lan
        Reveal(visible = lan != null) {
            shown[0]?.let { l ->
                FeatureButton(
                    title = l.subnet.toString(),
                    subtitle = listOfNotNull(
                        stringResource(R.string.f_Net_lan_self, l.self),
                        l.gateway?.let { stringResource(R.string.f_Net_lan_router, it) }
                    ).joinToString(" · "),
                    icon = Icons.Default.Lan
                )
            }
        }
        Reveal(visible = result != null) {
            Column(verticalArrangement = RowArrangement) {
                SectionTitle(stringResource(R.string.f_Net_lan_devices, result?.devices?.size ?: 0))
                result?.devices.orEmpty().forEach { device -> key(device.address) { Appearing { DeviceRow(device, onPorts) } } }
            }
        }
        Reveal(visible = result?.done == true && result.devices.size <= 1) { EmptyText(stringResource(R.string.f_Net_lan_empty)) }
        Note(stringResource(R.string.f_Net_lan_note))
    }
}

/** A device: its name (or address), its address, what it seems to be and its open ports; its ports' scan on a tap. */
@Composable
private fun DeviceRow(device: LanScan.Device, onPorts: (String) -> Unit) {
    val kind = device.kind
    val ports = device.ports.joinToString(", ") { p -> Services.name(p)?.let { "$p $it" } ?: "$p" }
    FeatureButton(
        title = device.name ?: device.address,
        subtitle = listOfNotNull(
            device.address.takeIf { device.name != null },
            kind?.let { stringResource(kindName(it)) },
            ports.ifEmpty { null }
        ).joinToString(" · ").ifEmpty { null },
        icon = kindIcon(kind),
        icon2 = Icons.AutoMirrored.Default.KeyboardArrowRight,
        colors = if (device.isSelf) RowColors.highlighted else RowColors.default,
        onClick = { onPorts(device.address) }
    )
}

private fun kindIcon(kind: LanScan.Kind?): ImageVector = when (kind) {
    LanScan.Kind.Phone -> Icons.Default.Smartphone
    LanScan.Kind.Router -> Icons.Default.Router
    LanScan.Kind.Printer -> Icons.Default.Print
    LanScan.Kind.Cast -> Icons.Default.Cast
    LanScan.Kind.Apple -> Icons.Default.Laptop
    LanScan.Kind.Computer -> Icons.Default.Computer
    LanScan.Kind.Storage -> Icons.Default.Storage
    LanScan.Kind.Camera -> Icons.Default.Videocam
    LanScan.Kind.Server -> Icons.Default.Terminal
    LanScan.Kind.SmartHome -> Icons.Default.Home
    null -> Icons.Default.DeviceUnknown
}

private fun kindName(kind: LanScan.Kind): Int = when (kind) {
    LanScan.Kind.Phone -> R.string.f_Net_kind_phone
    LanScan.Kind.Router -> R.string.f_Net_kind_router
    LanScan.Kind.Printer -> R.string.f_Net_kind_printer
    LanScan.Kind.Cast -> R.string.f_Net_kind_cast
    LanScan.Kind.Apple -> R.string.f_Net_kind_apple
    LanScan.Kind.Computer -> R.string.f_Net_kind_computer
    LanScan.Kind.Storage -> R.string.f_Net_kind_storage
    LanScan.Kind.Camera -> R.string.f_Net_kind_camera
    LanScan.Kind.Server -> R.string.f_Net_kind_server
    LanScan.Kind.SmartHome -> R.string.f_Net_kind_smart_home
}

@Composable
internal fun LanButtons(state: LanState) {
    val context = LocalContext.current
    val lan = remember(networkChanges(context)) { LanScan.lan(context) }
    if (state.running) BigButton(Icons.Default.Stop, stringResource(R.string.f_Net_stop), enabled = true, onClick = state::stop)
    else BigButton(Icons.Default.PlayArrow, stringResource(R.string.f_Net_lan_start), enabled = lan != null) { lan?.let(state::start) }
}
