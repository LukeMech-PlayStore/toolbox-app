package org.lukemech.lmtoolbox.ui.pages.features.network.net_tools

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ContactSupport
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.ui.helpers.SubPageRequest
import org.lukemech.lmtoolbox.features.network.net_tools.NetworkToolsFeature
import org.lukemech.lmtoolbox.helpers.PreferenceHelper
import org.lukemech.lmtoolbox.ui.components.FIELD_SPACING
import org.lukemech.lmtoolbox.ui.components.FieldArrangement
import org.lukemech.lmtoolbox.ui.components.InputField
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.PermissionDialog
import org.lukemech.lmtoolbox.ui.components.PromptBar
import org.lukemech.lmtoolbox.ui.components.StepWithButtons
import org.lukemech.lmtoolbox.ui.components.Steps
import org.lukemech.lmtoolbox.ui.components.listBox
import org.lukemech.lmtoolbox.ui.components.revealEnter
import org.lukemech.lmtoolbox.ui.pages.features.measure.compass.WarningColor
import org.lukemech.lmtoolbox.tasks.ShownWhile

/** The page's tabs; their names are the parts of the page a task shows on (AppTask.page). */
internal enum class Tab(val title: Int) {
    Speed(R.string.f_Net_tab_speed),
    Ping(R.string.f_Net_tab_ping),
    Trace(R.string.f_Net_tab_trace),
    Lan(R.string.f_Net_tab_lan),
    Ports(R.string.f_Net_tab_ports),
}

/**
 * Network tools, a tab each at the bottom: the internet's speed, ping, traceroute, the LAN's devices, a host's ports. Each tab's
 * work is a task (see TaskRun): it goes on while another tab is looked at, the page is left or the app goes to the back, in
 * the bottom bar (but on its own tab) and the notification with how far it is; tapped, they open its tab. Back on the
 * page, each tab is as it was left.
 */
@Composable
fun NetworkToolsPage() {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var tab by remember {
        mutableStateOf(Tab.entries.firstOrNull { it.name == PreferenceHelper.get(context, NetworkToolsFeature.S_TAB, "") } ?: Tab.Speed)
    }
    val tabs = remember { NetTabs.of(context) }
    val ping = tabs.ping
    val trace = tabs.trace
    val lan = tabs.lan
    val ports = tabs.ports
    val speed = tabs.speed
    // A tab asked for (the bottom bar's, a notification's)
    SubPageRequest(NetworkToolsFeature) { page -> Tab.entries.firstOrNull { it.name == page }?.let { tab = it } }
    // What a tab runs shows on it: in the bottom bar as another tab is looked at, as all are once the page is left
    tabs.runs.forEach { (t, run) -> ShownWhile(run, t == tab) }
    var permissionDialog by remember { mutableStateOf(false) }
    val localAllowed = localNetworkAllowed()

    StepWithButtons(
        content = {
            PageHeading(
                title = stringResource(R.string.fp_NetTools),
                back = true,
                steps = when (tab) {
                    Tab.Speed -> speed.steps()
                    Tab.Lan -> lan.steps()
                    Tab.Ports -> ports.steps()
                    else -> null
                },
                // The prompt above a tab as far as a form's fields are apart (none while it's hidden)
                verticalArrangement = FieldArrangement
            ) {
                if (permissionDialog) LocalNetworkDialog { permissionDialog = false }
                // Android 17: the network's devices only once the user lets the app reach them
                PromptBar(
                    // The speed test reaches the internet alone: no need for it there
                    visible = !localAllowed && tab != Tab.Speed,
                    title = stringResource(R.string.f_Net_local_needed),
                    color = WarningColor,
                    icon = Icons.Default.Wifi,
                    icon2 = Icons.AutoMirrored.Default.ContactSupport,
                    onClick = { permissionDialog = true }
                )
                when (tab) {
                    Tab.Speed -> SpeedTab(speed)
                    Tab.Ping -> PingTab(ping)
                    Tab.Trace -> TraceTab(trace)
                    Tab.Lan -> LanTab(lan) { host ->
                        // A device's ports, from its row
                        ports.host = host
                        tab = Tab.Ports
                    }
                    Tab.Ports -> PortsTab(ports)
                }
            }
        },
        buttons = {
            when (tab) {
                Tab.Speed -> SpeedButtons(speed)
                Tab.Ping -> PingButtons(ping)
                Tab.Trace -> TraceButtons(trace)
                Tab.Lan -> LanButtons(lan)
                Tab.Ports -> PortsButtons(ports)
            }
            // The tabs, under it all; their names alone (no icon, nor the selected one's check): five fit side by side
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Tab.entries.forEach { t ->
                    SegmentedButton(
                        selected = tab == t,
                        onClick = {
                            tab = t
                            focusManager.clearFocus()
                            PreferenceHelper.put(context, NetworkToolsFeature.S_TAB, t.name)
                        },
                        shape = SegmentedButtonDefaults.itemShape(t.ordinal, Tab.entries.size),
                        icon = {},
                        label = { Text(stringResource(t.title), maxLines = 1) }
                    )
                }
            }
        }
    )
}

/** Whether the app may reach the local network ([NetworkToolsFeature.hasLocalNetwork]), again as the app comes back (from the dialog, the settings). */
@Composable
private fun localNetworkAllowed(): Boolean {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(NetworkToolsFeature.hasLocalNetwork(context)) }
    LifecycleResumeEffect(Unit) {
        allowed = NetworkToolsFeature.hasLocalNetwork(context)
        onPauseOrDispose {}
    }
    return allowed
}

/** Why the local network's devices need the user's leave, and asking for it (or the app's settings, once Android stops asking). */
@SuppressLint("InlinedApi") // A name: shown on Android 17+ alone (see NetworkToolsFeature.asksForLocalNetwork)
@Composable
private fun LocalNetworkDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    PermissionDialog(
        icon = Icons.Default.Lan,
        title = stringResource(R.string.f_Net_local_title),
        message = stringResource(R.string.f_Net_local_message),
        allowLabel = stringResource(R.string.f_Net_local_allow),
        laterLabel = stringResource(R.string.f_Net_local_later),
        permissions = arrayOf(Manifest.permission.ACCESS_LOCAL_NETWORK),
        askedKey = NetworkToolsFeature.S_LOCAL_ASKED,
        settings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        onDismiss = onDismiss
    )
}

/** The tabs' fields, runs and results: the app's, made once, so the page finds them as they were (their runs going on). */
internal class NetTabs private constructor(app: Context) {
    val speed = SpeedState(app)
    val ping = PingState(app)
    val trace = TraceState(app)
    val lan = LanState(app)
    val ports = PortsState(app)
    /** Each tab's run. */
    val runs = listOf(Tab.Speed to speed.run, Tab.Ping to ping.run, Tab.Trace to trace.run, Tab.Lan to lan.run, Tab.Ports to ports.run)

    companion object {
        private var tabs: NetTabs? = null

        fun of(context: Context) = tabs ?: NetTabs(context.applicationContext).also { tabs = it }
    }
}

/** A tab's fields, as the app's forms are: in from the page's edges, [FIELD_SPACING] apart. */
@Composable
internal fun Fields(content: @Composable ColumnScope.() -> Unit) =
    Column(verticalArrangement = FieldArrangement, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), content = content)

/** Steps for a scan under way: how far, and [detail] (e.g. 37/254). */
internal fun scanSteps(caption: String, progress: Float, detail: String) =
    Steps(1, 0, loadingNext = false, working = true, progress = progress, caption = caption, detail = detail)

/** A host to reach, as typed; the keyboard's Go starts ([onGo]). */
@Composable
internal fun HostField(value: String, onChange: (String) -> Unit, label: String, enabled: Boolean, onGo: () -> Unit) {
    val focusManager = LocalFocusManager.current
    InputField(
        value = value,
        onValueChange = { onChange(it.filterNot(Char::isWhitespace)) },
        label = { Text(label) },
        placeholder = { Text(stringResource(R.string.f_Net_host_hint)) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = { focusManager.clearFocus(); onGo() }),
        modifier = Modifier.fillMaxWidth()
    )
}

/** A number as typed: digits only ([decimal]: and one point), at most [maxLength] of them. */
@Composable
internal fun NumberField(
    value: String, onChange: (String) -> Unit, label: String, enabled: Boolean, modifier: Modifier = Modifier,
    suffix: String? = null, decimal: Boolean = false, maxLength: Int = 5,
) = InputField(
    value = value,
    onValueChange = { onChange(digits(it, decimal).take(maxLength)) },
    label = { Text(label, maxLines = 1) },
    suffix = suffix?.let { { Text(it) } },
    singleLine = true,
    enabled = enabled,
    keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number, imeAction = ImeAction.Done),
    modifier = modifier
)

/** [text]'s digits (and, [decimal], its first point; a comma's one too). */
internal fun digits(text: String, decimal: Boolean): String {
    if (!decimal) return text.filter(Char::isDigit)
    val s = text.replace(',', '.').filter { it.isDigit() || it == '.' }
    val dot = s.indexOf('.')
    return if (dot < 0) s else s.take(dot + 1) + s.drop(dot + 1).replace(".", "")
}

/** Lines in a card (a ping's replies, a route's hops), as tall as they are up to a list's height, then scrolling. */
@Composable
internal fun LinesCard(content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
            // Lines coming and going (a route's past its host) grow and shrink it
            .animateContentSize()
            .listBox(scroll)
            .padding(vertical = 8.dp),
        content = content
    )
}

/** Something new in a list (a reply, a hop, a device): unfolding into it as it comes, never popping in. */
@Composable
internal fun Appearing(content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(visibleState = state, enter = revealEnter()) { content() }
}

/** A round trip, e.g. "12.3 ms" ("0.045 ms" under one). */
internal fun formatMs(ms: Double): String = if (ms < 1) "%.3f ms".format(ms) else if (ms < 100) "%.1f ms".format(ms) else "%.0f ms".format(ms)
