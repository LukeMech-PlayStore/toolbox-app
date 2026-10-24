package org.lukemech.lmtoolbox.ui.pages.features.media.qr_gen

import org.lukemech.lmtoolbox.ui.components.ExpandableButton
import org.lukemech.lmtoolbox.ui.components.SwitchButton
import androidx.compose.material.icons.filled.ToggleOn
import android.content.Context
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.R as AppR
import org.lukemech.lmtoolbox.features.pypkgs.R
import org.lukemech.lmtoolbox.features.media.qr_gen.QrCodeUi
import org.lukemech.lmtoolbox.features.media.qr_gen.QrGenFeature
import org.lukemech.lmtoolbox.linux.LinuxScripts
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.ui.components.ActionButton
import org.lukemech.lmtoolbox.ui.components.BigButton
import org.lukemech.lmtoolbox.ui.components.StepWithButtons
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.Steps
import org.lukemech.lmtoolbox.ui.components.Tile
import org.lukemech.lmtoolbox.ui.components.TileGrid
import org.lukemech.lmtoolbox.ui.helpers.rememberFileImage
import android.os.Environment
import org.lukemech.lmtoolbox.helpers.saveToMediaStore
import org.lukemech.lmtoolbox.helpers.shareFile
import org.lukemech.lmtoolbox.features.media.qr_gen.QrStyle
import org.lukemech.lmtoolbox.ui.components.InputField

private enum class Step { Types, Form, Result }

/**
 * QR generator in three steps: the kind of code ([QrType]), its form (with the look, see
 * [QrStyleEditor]), and the code to save, share or edit.
 */
@Composable
fun QrGenPage(providedInput: String? = "") {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var step by rememberSaveable { mutableStateOf(Step.Types) }
    var type by rememberSaveable { mutableStateOf(QrType.Text) }
    // Each kind keeps what was typed for it, as "<type>.<field>"
    val values = remember { mutableStateMapOf<String, String>() }
    var style by remember { mutableStateOf(QrCodeUi.loadStyle(context)) }
    var file by remember { mutableStateOf<File?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    fun valuesOf(t: QrType) = t.fields.associate { field ->
        field.key to (values["${t.name}.${field.key}"] ?: (field as? QrField.Choice)?.options?.first()?.first.orEmpty())
    }

    fun generate() {
        val payload = type.encode(valuesOf(type))
        coroutineScope.launch {
            isGenerating = true
            val dir = QrGenFeature.cacheDir(context).apply { deleteRecursively(); mkdirs() }
            // A new name each time, so the image isn't taken from a cache
            val output = File(dir, "${UUID.randomUUID()}.png")
            val result = LinuxTasks.run(
                context,
                listOf(
                    "python3", LinuxScripts.script(context, "features/qr_gen/gen.py"),
                    // Joined to its option: text starting with "-" ("-5 °C") isn't taken for one
                    "--input=$payload", "-o", output.absolutePath,
                    "-s", style.size.toString(), "-ec", style.errorCorrection.toString(),
                    "-b", style.border.toString(), "-st", style.style
                ),
                QrGenFeature
            )
            isGenerating = false
            if (result.isSuccess && output.exists()) {
                file = output
                step = Step.Result
            } else if (result.isSuccess) {
                // It ran but made nothing: told in the one place errors are, the bottom bar (a failed run, it tells already)
                LinuxTasks.reportFailure(QrGenFeature, "", "No code was made")
            }
        }
    }

    // Shared text: straight to its code
    LaunchedEffect(Unit) {
        val shared = providedInput?.trim()
        if (!shared.isNullOrEmpty() && step == Step.Types) {
            type = if (Regex("""^(https?://|www\.)\S+$""", RegexOption.IGNORE_CASE).matches(shared)) QrType.Url else QrType.Text
            values["${type.name}.${type.fields.first().key}"] = shared
            step = Step.Form
            generate()
        }
    }

    BackHandler(enabled = step != Step.Types) {
        step = if (step == Step.Result) Step.Form else Step.Types
    }

    // Where it stands, under each step's title; a tap outside the text fields hides the keyboard
    val steps = Steps(Step.entries.size, step.ordinal, isGenerating)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
    ) {
        Box(modifier = Modifier.weight(1f)) {
            when (step) {
                Step.Types -> PageHeading(steps = steps, title = stringResource(AppR.string.fp_QREncode)) {
                    TileGrid(QrType.entries) { t, modifier ->
                        Tile(stringResource(t.title), stringResource(t.description), t.icon, modifier) {
                            type = t
                            step = Step.Form
                        }
                    }
                }

                Step.Form -> {
                    // The keyboard's Enter on the last field (or once all are filled) creates it too
                    fun submit() {
                        focusManager.clearFocus()
                        if (type.isComplete(valuesOf(type)) && !isGenerating) generate()
                    }
                    StepWithButtons(
                        content = {
                            PageHeading(steps = steps, title = stringResource(type.title), onBack = { step = Step.Types }) {
                                QrForm(type, valuesOf(type), onSubmit = ::submit) { key, value -> values["${type.name}.$key"] = value }
                                Spacer(modifier = Modifier.height(16.dp))

                                ExpandableButton(stringResource(R.string.qr_appearance), Icons.Default.Palette) {
                                    QrStyleEditor(style, { style = it }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                                }
                            }
                        },
                        buttons = {
                            // Only greyed out while it's made: the step bar shows the progress
                            BigButton(
                                Icons.Default.QrCode2, stringResource(R.string.qr_create),
                                enabled = type.isComplete(valuesOf(type)) && !isGenerating
                            ) { submit() }
                        }
                    )
                }

                Step.Result -> QrResult(type, valuesOf(type), file, steps, onBack = { step = Step.Form }, onEdit = { step = Step.Form })
            }
        }
    }
}

/** Inputs of [type], filled with [values]; changes go to [onChange] (field key, value). */
@Composable
private fun QrForm(type: QrType, values: Map<String, String>, onSubmit: () -> Unit, onChange: (String, String) -> Unit) {
    val focusManager = LocalFocusManager.current
    val textFields = type.fields.filterIsInstance<QrField.Text>()
    val allFilled = textFields.all { values[it.key].orEmpty().isNotBlank() }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        type.formNote?.let {
            Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        type.fields.forEach { field ->
            val value = values[field.key].orEmpty()
            when (field) {
                // Its name above, staying while typing; whether it's needed and its note below
                is QrField.Text -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(field.label), style = MaterialTheme.typography.labelLarge)
                    val below = listOfNotNull(
                        if (field.required) stringResource(R.string.qr_required) else null,
                        field.note?.let { stringResource(it) }
                    ).joinToString(" · ")
                    InputField(
                        value = value,
                        onValueChange = { onChange(field.key, it) },
                        singleLine = !field.multiline,
                        minLines = if (field.multiline) 3 else 1,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = field.keyboard.type,
                            imeAction = when {
                                field.multiline -> ImeAction.Default
                                field == textFields.last() || allFilled -> ImeAction.Done
                                else -> ImeAction.Next
                            }
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(FocusDirection.Down) },
                            onDone = { onSubmit() }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Not the field's supportingText, which is indented: lined up with the name
                    if (below.isNotEmpty()) {
                        Text(below, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                is QrField.Choice -> Column {
                    Text(stringResource(field.label), style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        field.options.forEachIndexed { i, (option, label) ->
                            SegmentedButton(
                                selected = value == option,
                                onClick = { onChange(field.key, option) },
                                shape = SegmentedButtonDefaults.itemShape(i, field.options.size)
                            ) { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }

                is QrField.Toggle -> SwitchButton(stringResource(field.label), Icons.Default.ToggleOn, value == "true") {
                    onChange(field.key, it.toString())
                }
            }
        }
    }
}

/** The made code with what it holds up top, and at the bottom (as every tool's buttons) what to do with it. */
@Composable
private fun QrResult(type: QrType, values: Map<String, String>, file: File?, steps: Steps, onBack: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var saved by remember(file) { mutableStateOf(false) }

    StepWithButtons(content = { PageHeading(steps = steps, title = stringResource(type.title), onBack = onBack) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        // On white whatever the theme, like printed codes, so it scans well off the screen
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White, tonalElevation = 2.dp) {
            val image = rememberFileImage(file)
            Image(
                painter = image?.let { BitmapPainter(it) } ?: ColorPainter(Color.Transparent),
                contentDescription = stringResource(type.title),
                modifier = Modifier.size(220.dp).padding(12.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        values[type.summaryKey]?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(
            type.encode(values),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
    } }, buttons = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ActionButton(Icons.Default.Save, stringResource(R.string.f_QREncode_Save), enabled = file != null && !saved) {
                val name = "QR_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.png"
                val uri = file?.let { runCatching {
                    saveToMediaStore(
                        context, MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), Environment.DIRECTORY_DOWNLOADS,
                        name, "image/png"
                    ) { out -> it.inputStream().use { input -> input.copyTo(out) } }
                }.getOrNull() }
                saved = uri != null
                // A failure, told in the one place errors are: the bottom bar
                if (saved) Toast.makeText(context, resources.getString(R.string.f_QREncode_Saved), Toast.LENGTH_SHORT).show()
                else LinuxTasks.reportFailure(QrGenFeature, name, "Couldn't save the code")
            }
            ActionButton(Icons.Default.Share, stringResource(R.string.qr_share), enabled = file != null) {
                file?.let { share(context, it) }
            }
            ActionButton(Icons.Default.Edit, stringResource(R.string.qr_edit), onClick = onEdit)
        }
    })
}

/** Module style, size, error correction and border of a code; also the defaults in the settings. */
@Composable
fun QrStyleEditor(style: QrStyle, onChange: (QrStyle) -> Unit, modifier: Modifier = Modifier) {
    val names = mapOf(
        "square" to R.string.qr_style_square, "gapped_square" to R.string.qr_style_gapped,
        "circle" to R.string.qr_style_circle, "rounded" to R.string.qr_style_rounded,
        "vertical" to R.string.qr_style_vertical, "horizontal" to R.string.qr_style_horizontal
    )

    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.f_QREncode_Style), style = MaterialTheme.typography.labelLarge)
            QrStyle.STYLES.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { name ->
                        val selected = name == style.style
                        Surface(
                            onClick = { onChange(style.copy(style = name)) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            border = BorderStroke(
                                if (selected) 2.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                            ) {
                                QrStylePreview(name, MaterialTheme.colorScheme.onSurface, Modifier.size(44.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    stringResource(names.getValue(name)),
                                    style = MaterialTheme.typography.labelMedium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }

        Column {
            LabelWithValue(stringResource(R.string.f_QREncode_Size), "${style.size} px")
            Slider(
                value = style.size.toFloat(),
                onValueChange = {
                    onChange(style.copy(size = (it / QrStyle.SIZE_STEP).roundToInt() * QrStyle.SIZE_STEP))
                },
                valueRange = QrStyle.MIN_SIZE.toFloat()..QrStyle.MAX_SIZE.toFloat()
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.f_QREncode_EC), style = MaterialTheme.typography.labelLarge)
            val levels = listOf("7%", "15%", "25%", "30%")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                levels.forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = style.errorCorrection == i,
                        onClick = { onChange(style.copy(errorCorrection = i)) },
                        shape = SegmentedButtonDefaults.itemShape(i, levels.size)
                    ) { Text(label) }
                }
            }
        }

        Column {
            LabelWithValue(stringResource(R.string.f_QREncode_BS), style.border.toString())
            Slider(
                value = style.border.toFloat(),
                onValueChange = { onChange(style.copy(border = it.roundToInt())) },
                valueRange = 0f..QrStyle.MAX_BORDER.toFloat(),
                steps = QrStyle.MAX_BORDER - 1
            )
        }
    }
}

@Composable
private fun LabelWithValue(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/** Shares [file] (a PNG in the cache) with other apps, this one's "share to QR" left out. */
private fun share(context: Context, file: File) =
    shareFile(context, FileProvider.getUriForFile(context, "${context.packageName}.files", file), "image/png", "share.QR.Gen")

/** A corner of a code (finder pattern and a few modules), as gen.py's module styles draw it. */
private val SAMPLE = listOf(
    "1111111010",
    "1000001001",
    "1011101011",
    "1011101000",
    "1011101101",
    "1000001010",
    "1111111011",
    "0000000010",
    "1101011101",
    "0110110011",
)

/**
 * Preview of a [QrStyle.STYLES] module style, drawn like the qrcode library's module drawers:
 * squares, squares with gaps, circles, squares rounded where they've no neighbors, and bars
 * joining the modules above each other (vertical) or side by side (horizontal).
 */
@Composable
fun QrStylePreview(style: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cell = minOf(size.width, size.height) / SAMPLE.size
        val origin = Offset((size.width - cell * SAMPLE.size) / 2, (size.height - cell * SAMPLE.size) / 2)
        fun on(row: Int, col: Int) = SAMPLE.getOrNull(row)?.getOrNull(col) == '1'

        for (row in SAMPLE.indices) for (col in SAMPLE[row].indices) {
            if (!on(row, col)) continue
            val topLeft = origin + Offset(col * cell, row * cell)
            when (style) {
                "gapped_square" -> {
                    val gap = cell * 0.1f
                    drawRect(color, topLeft + Offset(gap, gap), Size(cell - 2 * gap, cell - 2 * gap))
                }
                "circle" -> drawCircle(color, cell / 2, topLeft + Offset(cell / 2, cell / 2))
                "rounded" -> {
                    // A corner rounds when neither module beside it (that way) is there
                    val r = CornerRadius(cell / 2)
                    fun corner(rounded: Boolean) = if (rounded) r else CornerRadius.Zero
                    val up = on(row - 1, col)
                    val down = on(row + 1, col)
                    val left = on(row, col - 1)
                    val right = on(row, col + 1)
                    drawPath(
                        Path().apply {
                            addRoundRect(
                                RoundRect(
                                    topLeft.x, topLeft.y, topLeft.x + cell, topLeft.y + cell,
                                    topLeftCornerRadius = corner(!up && !left),
                                    topRightCornerRadius = corner(!up && !right),
                                    bottomRightCornerRadius = corner(!down && !right),
                                    bottomLeftCornerRadius = corner(!down && !left)
                                )
                            )
                        },
                        color
                    )
                }
                "vertical" -> bar(color, topLeft, cell, vertical = true, before = on(row - 1, col), after = on(row + 1, col))
                "horizontal" -> bar(color, topLeft, cell, vertical = false, before = on(row, col - 1), after = on(row, col + 1))
                else -> drawRect(color, topLeft, Size(cell, cell))
            }
        }
    }
}

/** Piece of a bar 80% as thick as a module, rounded at the ends where it has no neighbor ([before], [after]). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.bar(
    color: Color, topLeft: Offset, cell: Float, vertical: Boolean, before: Boolean, after: Boolean
) {
    val inset = cell * 0.1f
    val r = CornerRadius((cell - 2 * inset) / 2)
    fun corner(rounded: Boolean) = if (rounded) r else CornerRadius.Zero
    val rect = if (vertical) {
        RoundRect(
            topLeft.x + inset, topLeft.y, topLeft.x + cell - inset, topLeft.y + cell,
            topLeftCornerRadius = corner(!before), topRightCornerRadius = corner(!before),
            bottomRightCornerRadius = corner(!after), bottomLeftCornerRadius = corner(!after)
        )
    } else {
        RoundRect(
            topLeft.x, topLeft.y + inset, topLeft.x + cell, topLeft.y + cell - inset,
            topLeftCornerRadius = corner(!before), bottomLeftCornerRadius = corner(!before),
            topRightCornerRadius = corner(!after), bottomRightCornerRadius = corner(!after)
        )
    }
    drawPath(Path().apply { addRoundRect(rect) }, color)
}
