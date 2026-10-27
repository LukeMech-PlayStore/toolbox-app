package org.lukemech.lmtoolbox.ui.pages.features.measure.compass

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ContactSupport
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.math.roundToInt
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.measure.compass.CompassFeature
import org.lukemech.lmtoolbox.features.measure.compass.CompassFeature.AltitudeStatus
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.MainResult
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.ui.pages.features.measure.common.HoldAction
import org.lukemech.lmtoolbox.ui.components.SensorWarning
import org.lukemech.lmtoolbox.ui.components.PromptBar
import org.lukemech.lmtoolbox.ui.components.PermissionDialog
import org.lukemech.lmtoolbox.ui.components.RowColors

/**
 * Amber of what the altitude is missing (location), on the page and the widget; red stays
 * for what the compass itself is missing.
 */
val WarningColor = Color(0xFFE8A317)

/**
 * The arrow shaft's part as far as the altitude may be off (compass_altitude_band): the fill's
 * [color] blended towards the text's ([text]: lighter in the dark, darker in the light), solid
 * so it stands out on any wallpaper.
 */
fun bandColor(color: Color, text: Color) = lerp(color, text, 0.5f)

/**
 * The arrow's fill per altitude scale (CompassFeature.altitudeScale: up to 100, 200, 500,
 * 1000, 2000, 5000, 10000 m), in the colors of physical maps: lowland green up to ice.
 */
val AltitudeColors = listOf(
    Color(0xFF3FA34D), Color(0xFF8BC34A), Color(0xFFE6C229), Color(0xFFF08A24),
    Color(0xFFA0522D), Color(0xFF9E9E9E), Color(0xFFB3D4F5),
)

/** Whether [azimuth] points (roughly) at one of the four cardinal directions: the ring lights up. */
fun isDirectionAligned(azimuth: Float): Boolean {
    val heading = CompassFeature.normalize(azimuth)
    return listOf(0f, 90f, 180f, 270f).any { angle ->
        val delta = kotlin.math.abs(heading - angle)
        delta <= ALIGNED_MARGIN || delta >= 360f - ALIGNED_MARGIN
    }
}

private const val ALIGNED_MARGIN = 20f

/** [AltitudeColors] at [scale], blended between two while the scale animates. */
fun altitudeColor(scale: Float): Color {
    val s = scale.coerceIn(0f, AltitudeColors.lastIndex.toFloat())
    val below = s.toInt()
    return lerp(AltitudeColors[below], AltitudeColors[minOf(below + 1, AltitudeColors.lastIndex)], s - below)
}

@Composable
fun CompassPage() {
    val context = LocalContext.current
    val azimuthState = remember { mutableFloatStateOf(0f) }
    val isCalibratedState = remember { mutableStateOf(true) }
    var locationDialog by remember { mutableStateOf(false) }
    var readingInfo by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }

    RememberCompassSensorUpdates(
        context = context,
        azimuth = azimuthState,
        isCalibrated = isCalibratedState,
        holding = holding
    )
    val azimuth = azimuthState.floatValue
    val isCalibrated = isCalibratedState.value
    val (altitudeStatus, liveAltitude) = rememberAltitude()
    // Held: the altitude stays as it was too
    var heldAltitude by remember { mutableStateOf(liveAltitude) }
    LaunchedEffect(liveAltitude, holding) { if (!holding) heldAltitude = liveAltitude }
    val altitude = heldAltitude?.takeIf { altitudeStatus == AltitudeStatus.Known }

    val hasRequiredSensors = remember { CompassFeature.hasSensors(context) }

    val textColor = MaterialTheme.colorScheme.onBackground

    if (locationDialog) LocationDialog(onDismiss = { locationDialog = false })
    PageHeading(title = stringResource(R.string.fp_compass), action = { HoldAction(holding) { holding = it } }) {
        // Bars on top (explained in a dialog when tapped): red for the compass, amber for the altitude
        Column(verticalArrangement = RowArrangement) {
            SensorWarning(
                hasSensors = hasRequiredSensors, calibrated = isCalibrated,
                calibrationTitle = R.string.f_compass_calibration_title,
                calibrationMessage = R.string.f_compass_calibration_message,
                calibrationIcon = Icons.Default.CompassCalibration,
                missingMessage = R.string.f_compass_missing_sensors_message
            )

            // Location off: straight to its switch, nothing to explain
            val locationOff = altitudeStatus == AltitudeStatus.LocationOff
            PromptBar(
                visible = altitudeStatus == AltitudeStatus.NoPermission || locationOff,
                title = stringResource(if (locationOff) R.string.f_compass_location_off else R.string.f_compass_location_needed),
                color = WarningColor,
                icon = Icons.Default.LocationOff,
                icon2 = if (locationOff) Icons.AutoMirrored.Default.OpenInNew else Icons.AutoMirrored.Default.ContactSupport,
                onClick = {
                    if (locationOff) context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    else locationDialog = true
                }
            )

            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                // The arrow's altitude scale, once there is one
                Compass(azimuth, textColor, needsCalibration = !isCalibrated && hasRequiredSensors, altitude = altitude)
            }

            Results {
                MainResult(stringResource(R.string.f_compass_heading), "${"%.1f".format(azimuth)}°")
                // The altitude once it's looked for: GPS… until it's known, then with its accuracy
                val searching = altitudeStatus == AltitudeStatus.Searching
                val lastAltitude = remember { arrayOfNulls<CompassFeature.Altitude>(1) }
                if (altitude != null) lastAltitude[0] = altitude
                Reveal(visible = altitude != null || searching) {
                    val shown = altitude ?: lastAltitude[0]
                    Column {
                        ResultRow(
                            stringResource(R.string.f_compass_altitude),
                            if (shown == null || searching) stringResource(R.string.f_compass_altitude_searching)
                            else CompassFeature.altitudeText(context, shown)
                        )
                        shown?.accuracy?.takeIf { !searching }?.let {
                            ResultRow(stringResource(R.string.f_compass_accuracy), stringResource(R.string.f_compass_altitude_accuracy, it.roundToInt().coerceAtLeast(1)))
                        }
                    }
                }
            }
            FeatureButton(
                title = stringResource(R.string.f_compass_info_title),
                icon = Icons.AutoMirrored.Default.ContactSupport,
                onClick = { readingInfo = true }
            )
        }
    }
    if (readingInfo) {
        ReadingInfoDialog(
            altitude = altitude,
            showAltitude = altitudeStatus != AltitudeStatus.Hidden,
            onDismiss = { readingInfo = false }
        )
    }
}

/**
 * What the reading means: the heading, the altitude (meters above sea level, from GPS) and how
 * the arrow shows it, with every scale's head and color ([altitude]'s one marked).
 */
@Composable
private fun ReadingInfoDialog(altitude: CompassFeature.Altitude?, showAltitude: Boolean, onDismiss: () -> Unit) {
    val current = altitude?.let { CompassFeature.altitudeScale(it.meters) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.f_compass_info_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.f_compass_info_heading))
                if (showAltitude) {
                    Text(stringResource(R.string.f_compass_info_altitude))
                    altitude?.accuracy?.let {
                        Text(
                            stringResource(R.string.f_compass_info_accuracy, it.roundToInt().coerceAtLeast(1)),
                            style = MaterialTheme.typography.bodySmall, color = muted
                        )
                    }
                    Text(stringResource(R.string.f_compass_info_arrow))
                    val outline = MaterialTheme.colorScheme.outline
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompassFeature.ALTITUDE_SCALES.forEachIndexed { i, top ->
                            val marked = i == current
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // The head as long as on the arrow for this scale
                                Canvas(modifier = Modifier.size(width = 40.dp, height = 24.dp)) {
                                    val f = 0.55f + 0.12f * i
                                    val unit = size.height / 48f
                                    val tip = Offset(size.width / 2, 2 * unit)
                                    val dx = 30.622f * f * unit / 1.4f
                                    val dy = 38.277f * f * unit / 1.4f
                                    val stroke = 2.dp.toPx()
                                    drawLine(outline, tip, Offset(tip.x - dx, tip.y + dy), stroke, StrokeCap.Round)
                                    drawLine(outline, tip, Offset(tip.x + dx, tip.y + dy), stroke, StrokeCap.Round)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(width = 6.dp, height = 20.dp)
                                        .background(AltitudeColors[i], RoundedCornerShape(3.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    stringResource(R.string.f_compass_info_scale, top.roundToInt(), (top / 5).roundToInt()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (marked) FontWeight.Bold else FontWeight.Normal,
                                    color = if (marked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Text(stringResource(R.string.f_compass_info_band))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        }
    )
}

/** A bar above the heading, in [color], shown and hidden with an animation (with its gap below). */
/**
 * The altitude while the page is open, with what stands in its place (see [AltitudeStatus]).
 * The state is checked again when location is switched or the app comes back (e.g. from
 * the permission dialog or the settings); a newly granted permission reaches the widget too.
 */
@Composable
private fun rememberAltitude(): Pair<AltitudeStatus, CompassFeature.Altitude?> {
    val context = LocalContext.current
    var altitude by remember { mutableStateOf<CompassFeature.Altitude?>(null) }
    var status by remember { mutableStateOf(CompassFeature.altitudeStatus(context, null)) }

    fun recheck() {
        val before = status
        status = CompassFeature.altitudeStatus(context, altitude)
        if (before == AltitudeStatus.NoPermission && status != AltitudeStatus.NoPermission) CompassFeature.refreshWidget(context)
    }
    LifecycleResumeEffect(Unit) {
        recheck()
        onPauseOrDispose {}
    }
    // Location switched in the quick settings, which doesn't pause the app
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = recheck()
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(LocationManager.MODE_CHANGED_ACTION), ContextCompat.RECEIVER_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    if (status == AltitudeStatus.Searching || status == AltitudeStatus.Known) {
        DisposableEffect(Unit) {
            val tracker = CompassFeature.AltitudeTracker(context) {
                altitude = it
                status = CompassFeature.altitudeStatus(context, it)
            }
            tracker.start()
            onDispose { tracker.stop() }
        }
    }
    return status to altitude
}

/**
 * Why the altitude needs location, and asking for it (or opening the app's settings once
 * Android stops asking). Whoever shows it checks the permission again on return.
 */
@Composable
fun LocationDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    PermissionDialog(
        icon = Icons.Default.Landscape,
        title = stringResource(R.string.f_compass_location_title),
        message = stringResource(R.string.f_compass_location_message),
        allowLabel = stringResource(R.string.f_compass_location_allow),
        laterLabel = stringResource(R.string.f_compass_location_later),
        // Android 12+ only offers precise location when approximate is asked too
        permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        askedKey = CompassFeature.S_LOCATION_ASKED,
        settings = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        onDismiss = onDismiss
    )
}

/**
 * Compass setting: show the altitude. Without location, it stays on but greyed out, and a tap
 * asks for location instead.
 */
@Composable
fun AltitudeSetting() {
    val context = LocalContext.current
    var show by remember { mutableStateOf(CompassFeature.showAltitude(context)) }
    var allowed by remember { mutableStateOf(CompassFeature.hasLocationPermission(context)) }
    var dialog by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        val was = allowed
        allowed = CompassFeature.hasLocationPermission(context)
        if (!was && allowed) CompassFeature.refreshWidget(context)
        onPauseOrDispose {}
    }

    fun toggle() {
        if (!allowed) {
            dialog = true
            return
        }
        show = !show
        CompassFeature.setShowAltitude(context, show)
    }

    // Without location: off and greyed out, saying why
    FeatureButton(
        title = stringResource(R.string.f_compass_setting_altitude),
        subtitle = stringResource(if (allowed) R.string.f_compass_setting_altitude_desc else R.string.f_compass_altitude_permission),
        colors = if (allowed) RowColors.default else RowColors.default.disabled(),
        icon = Icons.Default.Landscape,
        buttonContent = {
            Switch(checked = show && allowed, enabled = allowed, onCheckedChange = { toggle() })
        },
        onClick = { toggle() }
    )
    if (dialog) LocationDialog(onDismiss = { dialog = false })
}

/**
 * The dial; with an [altitude] the arrow gets its scale (see CompassFeature.altitudeScale):
 * its ticks, the head's length and the fill's color telling which scale, and its shaft colored
 * up to the altitude, faintly as far as it may be off (CompassFeature.altitudeBand).
 */
@Composable
private fun Compass(azimuth: Float, textColor: Color, needsCalibration: Boolean, altitude: CompassFeature.Altitude?) {
    val band = altitude?.let(CompassFeature::altitudeBand)
    val scale = altitude?.let { CompassFeature.altitudeScale(it.meters) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    // The head grows or shrinks to the scale, the fill's color going along
    val headScale by animateFloatAsState(
        targetValue = scale?.toFloat() ?: CompassFeature.PLAIN_ARROW_HEAD,
        animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing)
    )
    val fillColor = altitudeColor(headScale)

    // Unwrapped heading (may leave 0..360): turning by the shortest delta instead of going
    // straight to the new value keeps crossing north (359° -> 1°) from spinning the dial around
    var continuousAzimuth by remember { mutableFloatStateOf(azimuth) }
    LaunchedEffect(azimuth) {
        continuousAzimuth += CompassFeature.shortestDelta(continuousAzimuth, azimuth)
    }

    val animatedAzimuth by animateFloatAsState(
        targetValue = continuousAzimuth,
        animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing)
    )

    // Red while it needs calibrating, like the widget; the ring also lights up while the arrow
    // points at a cardinal direction
    val arrowColor by animateColorAsState(
        targetValue = if (needsCalibration) MaterialTheme.colorScheme.error else outlineColor,
        animationSpec = tween(durationMillis = 500)
    )
    val ringColor by animateColorAsState(
        targetValue = when {
            needsCalibration -> MaterialTheme.colorScheme.error
            isDirectionAligned(animatedAzimuth) -> MaterialTheme.colorScheme.tertiary
            else -> outlineColor
        },
        animationSpec = tween(durationMillis = 500)
    )

    // Up from the tail when the altitude comes, and along as it (and its accuracy) changes
    val animatedFill by animateFloatAsState(
        targetValue = band?.first ?: 0f,
        animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing)
    )
    val animatedBand by animateFloatAsState(
        targetValue = band?.second ?: 0f,
        animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing)
    )

    // The dial's layers (res/drawable/compass_*), shared with the home screen widget: the ring
    // and arrow stay (with the altitude scale on it), the rest turns by its level
    val context = LocalContext.current
    val layers = remember {
        listOf(
            R.drawable.compass_ring, R.drawable.compass_arrow_shaft, R.drawable.compass_arrow_head,
            R.drawable.compass_altitude_ticks, R.drawable.compass_altitude_band, R.drawable.compass_altitude_fill,
            R.drawable.compass_scale, R.drawable.compass_north
        ).map { ContextCompat.getDrawable(context, it)!!.mutate() }
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .padding(vertical = 8.dp)
            .aspectRatio(1f)
    ) {
        val level = (CompassFeature.normalize(animatedAzimuth) / 360f * 10000).roundToInt()
        val colors = listOf(
            ringColor, arrowColor, arrowColor, arrowColor,
            bandColor(fillColor, textColor), fillColor, textColor, primaryColor
        )
        val levels = listOf(
            0, 0, CompassFeature.arrowHeadLevel(headScale), 0,
            CompassFeature.altitudeLevel(animatedBand), CompassFeature.altitudeLevel(animatedFill), level, level
        )
        drawIntoCanvas { canvas ->
            layers.forEachIndexed { i, layer ->
                // The altitude scale only with an altitude
                if (band == null && i in 3..5) return@forEachIndexed
                layer.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
                layer.setTint(colors[i].toArgb())
                layer.level = levels[i]
                layer.draw(canvas.nativeCanvas)
            }
        }
    }
}

@Composable
private fun RememberCompassSensorUpdates(
    context: Context,
    azimuth: MutableFloatState,
    isCalibrated: MutableState<Boolean>,
    /** The heading stays as it was while it's true (the calibration's state goes on). */
    holding: Boolean,
) {
    val held by rememberUpdatedState(holding)
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    DisposableEffect(Unit) {
        // Shared with the widget, so both show the same heading and calibration state
        val state = CompassFeature.SensorState()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                state.onSensorChanged(event)?.let { if (!held) azimuth.floatValue = it }
                isCalibrated.value = state.isCalibrated
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                state.onAccuracyChanged(sensor, accuracy)
                isCalibrated.value = state.isCalibrated
            }
        }

        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnet = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(listener, magnet, SensorManager.SENSOR_DELAY_UI)

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }
}

