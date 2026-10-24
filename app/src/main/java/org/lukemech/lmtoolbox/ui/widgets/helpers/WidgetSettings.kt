package org.lukemech.lmtoolbox.ui.widgets.helpers

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import kotlin.math.roundToInt
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.PreferenceHelper
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.InputField

/** Feature setting: [widget]'s background, transparent or a picked color (read by [FeatureWidget.applyBackground]). */
@Composable
fun WidgetBackgroundSetting(widget: FeatureWidget) {
    val context = LocalContext.current
    var background by remember { mutableIntStateOf(PreferenceHelper.get(context, widget.backgroundKey, FeatureWidget.TRANSPARENT)) }
    var editing by remember { mutableStateOf(false) }

    FeatureButton(
        title = stringResource(R.string.w_setting_background),
        subtitle = if (background == FeatureWidget.TRANSPARENT) stringResource(R.string.w_setting_background_transparent)
        else toHex(background),
        icon = Icons.Default.FormatColorFill,
        buttonContent = {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(background))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        },
        onClick = { editing = !editing },
        expanded = editing,
        popupContent = {
            // Afresh from the saved color each time it opens
            key(editing) {
                BackgroundEditor(
                    initial = background,
                    onDismiss = { editing = false },
                    onConfirm = {
                        background = it
                        PreferenceHelper.put(context, widget.backgroundKey, it)
                        widget.updateAll(context)
                        editing = false
                    }
                )
            }
        }
    )
}

@Composable
private fun BackgroundEditor(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val context = LocalContext.current
    // Starts from a dark, slightly see-through color when there was none yet
    val start = if (initial == FeatureWidget.TRANSPARENT) DEFAULT_COLOR else initial
    val startHsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(start, it) } }
    var hue by remember { mutableFloatStateOf(startHsv[0]) }
    var saturation by remember { mutableFloatStateOf(startHsv[1]) }
    var value by remember { mutableFloatStateOf(startHsv[2]) }
    var alpha by remember { mutableFloatStateOf(if (initial == FeatureWidget.TRANSPARENT) 0f else android.graphics.Color.alpha(start) / 255f) }
    val color = Color.hsv(hue, saturation, value, alpha)

    // Typed (or pasted) code; follows the palette until it's edited
    var hex by remember { mutableStateOf(toHex(color.toArgb())) }
    fun picked() { hex = toHex(Color.hsv(hue, saturation, value, alpha).toArgb()) }
    fun typed(text: String) {
        hex = text
        val argb = parseHex(text) ?: return
        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(argb, it) }
        hue = hsv[0]; saturation = hsv[1]; value = hsv[2]
        alpha = android.graphics.Color.alpha(argb) / 255f
    }
    val clipboard = remember { context.getSystemService(ClipboardManager::class.java) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        SaturationValuePicker(hue, saturation, value) { s, v -> saturation = s; value = v; picked() }
        HuePicker(hue) { hue = it; picked() }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.w_setting_background_opacity, (alpha * 100).roundToInt()),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { alpha = 0f; picked() }) {
                Text(stringResource(R.string.w_setting_background_transparent))
            }
        }
        Slider(value = alpha, onValueChange = { alpha = it; picked() })

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            )
            InputField(
                value = hex,
                onValueChange = { typed(it.trim()) },
                singleLine = true,
                isError = parseHex(hex) == null,
                modifier = Modifier.weight(1f)
            )
            // No property: getPrimaryClip() is nullable, its setter isn't
            @Suppress("UsePropertyAccessSyntax")
            IconButton(onClick = { clipboard.setPrimaryClip(ClipData.newPlainText("color", hex)) }) {
                Icon(Icons.Default.ContentCopy, stringResource(R.string.w_setting_background_copy))
            }
            IconButton(onClick = {
                clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.let { typed(it.trim()) }
            }) {
                Icon(Icons.Default.ContentPaste, stringResource(R.string.w_setting_background_paste))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            // Fully see-through is stored as "transparent"
            TextButton(onClick = { onConfirm(if (alpha == 0f) FeatureWidget.TRANSPARENT else color.toArgb()) }) {
                Text(stringResource(android.R.string.ok))
            }
        }
    }
}

/** #AARRGGBB, the form [parseHex] reads back. */
internal fun toHex(argb: Int) = "#%08X".format(argb)

/** #RRGGBB or #AARRGGBB (the # optional), or null. */
private fun parseHex(text: String): Int? {
    val digits = text.removePrefix("#")
    if (digits.length != 6 && digits.length != 8) return null
    return runCatching { "#$digits".toColorInt() }.getOrNull()
}

/** Square of the [hue]'s saturation (left to right) and brightness (bottom to top), picked with a dot. */
@Composable
private fun SaturationValuePicker(hue: Float, saturation: Float, value: Float, onChange: (Float, Float) -> Unit) {
    fun pick(position: Offset, width: Float, height: Float) =
        onChange((position.x / width).coerceIn(0f, 1f), 1f - (position.y / height).coerceIn(0f, 1f))

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .pointerInput(Unit) { detectTapGestures { pick(it, size.width.toFloat(), size.height.toFloat()) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> pick(change.position, size.width.toFloat(), size.height.toFloat()) }
            }
    ) {
        // Only the gradient is rounded, so the dot stays whole at the edges
        val corner = CornerRadius(8.dp.toPx())
        drawRoundRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))), cornerRadius = corner)
        drawRoundRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)), cornerRadius = corner)
        val dot = Offset(saturation * size.width, (1f - value) * size.height)
        drawCircle(Color.White, radius = 9.dp.toPx(), center = dot, style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black, radius = 11.dp.toPx(), center = dot, style = Stroke(1.dp.toPx()))
    }
}

/** Rainbow bar picking the hue with a dot. */
@Composable
private fun HuePicker(hue: Float, onChange: (Float) -> Unit) {
    val hues = remember { (0..360 step 30).map { Color.hsv(it.toFloat(), 1f, 1f) } }
    fun pick(x: Float, width: Float) = onChange((x / width).coerceIn(0f, 1f) * 360f)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) { detectTapGestures { pick(it.x, size.width.toFloat()) } }
            .pointerInput(Unit) { detectDragGestures { change, _ -> pick(change.position.x, size.width.toFloat()) } }
    ) {
        drawRoundRect(Brush.horizontalGradient(hues), cornerRadius = CornerRadius(size.height / 2))
        val dot = Offset(hue / 360f * size.width, size.height / 2)
        drawCircle(Color.White, radius = 9.dp.toPx(), center = dot, style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black, radius = 11.dp.toPx(), center = dot, style = Stroke(1.dp.toPx()))
    }
}

/** Dark gray at 80%: readable under the light parts of the widgets on any wallpaper. */
private const val DEFAULT_COLOR = 0xCC202020.toInt()
