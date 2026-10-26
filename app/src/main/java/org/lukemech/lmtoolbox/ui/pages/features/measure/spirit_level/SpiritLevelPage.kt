package org.lukemech.lmtoolbox.ui.pages.features.measure.spirit_level

import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import org.lukemech.lmtoolbox.features.measure.spirit_level.SpiritLevelFeature
import kotlin.math.roundToInt
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storm
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.lukemech.lmtoolbox.R
import androidx.compose.ui.res.stringResource
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.ResultRow
import org.lukemech.lmtoolbox.ui.components.Results
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.ui.pages.features.measure.common.HoldAction
import kotlin.math.abs
import org.lukemech.lmtoolbox.ui.components.SensorWarning

@Composable
fun SpiritLevelPage() {
    val context = LocalContext.current
    val pitchState = remember { mutableFloatStateOf(0f) }
    val rollState = remember { mutableFloatStateOf(0f) }
    val isCalibratedState = remember { mutableStateOf(true) }
    var holding by remember { mutableStateOf(false) }

    RememberLevelSensorUpdates(
        context = context,
        pitch = pitchState,
        roll = rollState,
        isCalibrated = isCalibratedState,
        holding = holding
    )

    val pitch = pitchState.floatValue
    val roll = rollState.floatValue
    val isCalibrated = isCalibratedState.value

    val hasRequiredSensors = remember { SpiritLevelFeature.hasSensors(context) }

    PageHeading(title = stringResource(R.string.fp_spirit_level), action = { HoldAction(holding) { holding = it } }) {
        // The bar on top (explained in a dialog when tapped), the level, its angles below it
        Column(verticalArrangement = RowArrangement) {
            SensorWarning(
                hasSensors = hasRequiredSensors, calibrated = isCalibrated,
                calibrationTitle = R.string.f_level_accuracy_title,
                calibrationMessage = R.string.f_level_accuracy_message,
                calibrationIcon = Icons.Default.Storm,
                missingMessage = R.string.f_level_missing_sensors_message
            )
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                SpiritLevel(pitch, roll, lowAccuracy = !isCalibrated && hasRequiredSensors)
            }
            Results {
                ResultRow("X", "${"%.1f".format(roll)}°")
                ResultRow("Y", "${"%.1f".format(pitch)}°")
            }
        }
    }
}

@Composable
private fun SpiritLevel(pitch: Float, roll: Float, lowAccuracy: Boolean) {
    val outlineColor = MaterialTheme.colorScheme.outline
    val bubbleColor = MaterialTheme.colorScheme.tertiary
    val centerColor = MaterialTheme.colorScheme.primary

    val animX by animateFloatAsState(targetValue = tiltFraction(roll), animationSpec = tween(400, easing = LinearOutSlowInEasing))
    val animY by animateFloatAsState(targetValue = tiltFraction(pitch), animationSpec = tween(400, easing = LinearOutSlowInEasing))

    val ringColor by animateColorAsState(
        targetValue = when {
            // Red while the readings may be off, like the widget
            lowAccuracy -> MaterialTheme.colorScheme.error
            isNearCenter(animX, animY) -> centerColor
            else -> outlineColor
        },
        animationSpec = tween(300)
    )

    // The layers (res-features/measure/spirit_level/drawable/level_*), shared with the home screen widget
    val context = LocalContext.current
    val layers = remember {
        listOf(R.drawable.level_ring, R.drawable.level_cross, R.drawable.level_bubble)
            .map { ContextCompat.getDrawable(context, it)!!.mutate() }
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .aspectRatio(1f)
    ) {
        // The ring's radius is a third of the area
        val side = size.minDimension / 3 / RING_RADIUS
        val left = ((size.width - side) / 2).roundToInt()
        val top = ((size.height - side) / 2).roundToInt()
        val travel = side * BUBBLE_TRAVEL
        val colors = listOf(ringColor, outlineColor, bubbleColor)
        drawIntoCanvas { canvas ->
            layers.forEachIndexed { i, layer ->
                // The bubble moves against the tilt horizontally, with it vertically
                val dx = if (i == 2) (-animX * travel).roundToInt() else 0
                val dy = if (i == 2) (animY * travel).roundToInt() else 0
                layer.setBounds(left + dx, top + dy, left + dx + side.roundToInt(), top + dy + side.roundToInt())
                layer.setTint(colors[i].toArgb())
                layer.draw(canvas.nativeCanvas)
            }
        }
    }
}

/** Tilt as -1..1 of the bubble's way to the ring (45° and more reach it). */
fun tiltFraction(degrees: Float) = degrees.coerceIn(-45f, 45f) / 45f

/** Whether the bubble (as [tiltFraction]s) sits in the middle. */
fun isNearCenter(x: Float, y: Float) = abs(x) < 0.1f && abs(y) < 0.1f

/** The level_* drawables: the ring's radius and the bubble's way, as parts of their size. */
const val RING_RADIUS = 80f / 168f
const val BUBBLE_TRAVEL = 68f / 168f

@Composable
private fun RememberLevelSensorUpdates(
    context: Context,
    pitch: MutableFloatState,
    roll: MutableFloatState,
    isCalibrated: MutableState<Boolean>,
    /** The tilt stays as it was while it's true (the accuracy's state goes on). */
    holding: Boolean
) {
    val held by rememberUpdatedState(holding)
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }

    DisposableEffect(Unit) {
        // Shared with the widget, so both show the same tilt and accuracy state
        val state = SpiritLevelFeature.SensorState()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                state.onSensorChanged(event)
                if (!held) {
                    pitch.floatValue = state.pitch
                    roll.floatValue = state.roll
                }
                isCalibrated.value = state.isCalibrated
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                state.onAccuracyChanged(sensor, accuracy)
                isCalibrated.value = state.isCalibrated
            }
        }

        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }
}

