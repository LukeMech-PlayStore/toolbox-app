package org.lukemech.lmtoolbox.features.reference.common

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.reference.common.ReferencePage

/**
 * A reference's text: as it's written (a value, a pin's name, "GND"), or a string resource (words to
 * translate), or both together.
 */
sealed interface Txt {
    data class Raw(val text: String) : Txt
    data class Res(@param:StringRes val id: Int) : Txt
    /** [parts] one after another, [separator] between them: "TXD" and "Transmit" as "TXD · Transmit". */
    data class Join(val parts: List<Txt>, val separator: String = " · ") : Txt
    /** [label] after a swatch of [colors] (two: a wire's base and its stripe). */
    data class Swatch(val colors: List<Color>, val label: Txt) : Txt

    fun resolve(resources: Resources): String = when (this) {
        is Raw -> text
        is Res -> resources.getString(id)
        is Join -> parts.joinToString(separator) { it.resolve(resources) }
        is Swatch -> label.resolve(resources)
    }
}

fun raw(text: String): Txt = Txt.Raw(text)

fun res(@StringRes id: Int): Txt = Txt.Res(id)

/** What a pin does, as its color in a pinout. */
enum class PinKind(@param:StringRes val label: Int, val color: Color) {
    Power(R.string.rf_kind_power, Color(0xFFE53935)),
    Ground(R.string.rf_kind_ground, Color(0xFF757575)),
    Signal(R.string.rf_kind_signal, Color(0xFF43A047)),
    Data(R.string.rf_kind_data, Color(0xFF1E88E5)),
    Clock(R.string.rf_kind_clock, Color(0xFF8E24AA)),
    Control(R.string.rf_kind_control, Color(0xFFFB8C00)),
    Analog(R.string.rf_kind_analog, Color(0xFF00897B)),
    Audio(R.string.rf_kind_audio, Color(0xFFD81B60)),
    Shield(R.string.rf_kind_shield, Color(0xFF8D6E63)),
    Unused(R.string.rf_kind_unused, Color(0xFFBDBDBD)),
}

/**
 * A connector's pin: its [number] (or a short name, Arduino's "A0"), its [name] and what it does
 * ([description]), where it's drawn ([x], [y], in pin pitches) and, for a wire, its [wire] colors.
 */
data class Pin(
    val number: String,
    val name: String,
    val kind: PinKind,
    val description: Txt? = null,
    val x: Float = 0f,
    val y: Float = 0f,
    val wire: List<Color>? = null,
)

/** The outline a pinout's pins are drawn in: a plain box, or a D-sub's trapezoid (DB9, OBD-II, HDMI). */
enum class Outline { Box, Trapezoid }

/**
 * One page of a reference: what's picked at its top when there are several (USB's A, B, micro-B, C), found there by
 * its [name], its [group] or its [note]. [group]: the family it's listed under (Arduino's Nano, Nano Every... under
 * "Nano"), none for a sheet of its own.
 */
sealed interface Sheet {
    val name: Txt
    val group: Txt?
    val note: Txt?
}

/** A table: its [columns]' headings, its [rows] (as many cells each), searched as it's typed. */
class TableSheet(
    override val name: Txt,
    val columns: List<Txt>,
    val rows: List<List<Txt>>,
    override val note: Txt? = null,
    override val group: Txt? = null,
) : Sheet

/** What a board's [Spec] tells, its tile's label and icon. */
enum class SpecKind(@param:StringRes val label: Int, val icon: ImageVector) {
    Chip(R.string.rf_spec_chip, Icons.Default.Memory),
    Clock(R.string.rf_spec_clock, Icons.Default.Speed),
    Flash(R.string.rf_spec_flash, Icons.Default.Storage),
    Ram(R.string.rf_spec_ram, Icons.Default.ViewModule),
    Logic(R.string.rf_spec_logic, Icons.Default.ElectricBolt),
    Supply(R.string.rf_spec_supply, Icons.Default.Power),
    Wireless(R.string.rf_spec_wireless, Icons.Default.Wifi),
}

/** A board's chip, memory or voltage, a tile over its pinout: [value] as short as it reads ("32 KB", "7–12 V"). */
class Spec(val kind: SpecKind, val value: Txt)

/** [kind]'s [Spec] of [value], as it's written. */
fun spec(kind: SpecKind, value: String) = Spec(kind, raw(value))

/**
 * A connector drawn from its [pins], each tapped for what it does, listed under it. [inOrder]: drawn in number
 * order where the face's own layout isn't (round connectors), said so under it. [specs]: a board's, as tiles over it.
 */
class PinoutSheet(
    override val name: Txt,
    val pins: List<Pin>,
    val outline: Outline = Outline.Box,
    override val note: Txt? = null,
    val inOrder: Boolean = false,
    override val group: Txt? = null,
    val specs: List<Spec> = emptyList(),
) : Sheet

/** A page of its own (a decoder: a capacitor's code, an IP rating). */
class ToolSheet(override val name: Txt, override val group: Txt? = null, val content: @Composable () -> Unit) : Sheet {
    override val note: Txt? = null
}

/** [this] in one row, in order. */
fun List<Pin>.inRow(): List<Pin> = mapIndexed { i, pin -> pin.copy(x = i.toFloat(), y = 0f) }

/** [this] in rows of [counts] pins one under another, each row [shift] pitches further right than the last. */
fun List<Pin>.inRows(vararg counts: Int, shift: Float = 0f): List<Pin> {
    var i = 0
    return counts.flatMapIndexed { row, count ->
        (0 until count).map { col -> this[i++].copy(x = col + row * shift, y = row.toFloat()) }
    }
}

/** A box header's numbering (IDC): 1 and 2 across from each other, 3 and 4 next... [vertical]: in two columns. */
fun List<Pin>.dualRow(vertical: Boolean = false): List<Pin> = mapIndexed { i, pin ->
    val along = (i / 2).toFloat()
    val across = (i % 2).toFloat()
    if (vertical) pin.copy(x = across, y = along) else pin.copy(x = along, y = across)
}

/** Odd pins on top, even ones under and between them (HDMI's 1, 3, 5... over 2, 4, 6...). */
fun List<Pin>.staggered(): List<Pin> = mapIndexed { i, pin -> pin.copy(x = i * 0.5f, y = (i % 2).toFloat()) }

/** A connector's [pins] as they're drawn (on its page, in its PDF), and whether they're [turned] for it. */
class Fitted(val pins: List<Pin>, val turned: Boolean)

/** Rows a connector has before it's drawn turned (when it's much taller than wide). */
private const val TURN_FROM_ROWS = 8

/** The widest a gap between rows of pins is drawn (a board's two sides, a pill's), in pitches: no strip of nothing. */
private const val MAX_ROW_GAP = 2f

/**
 * [this] laid out to fit a page: a board much taller than wide (a Nano's two long rows) turned on its side, its top
 * on the left, so it's as wide as the page and short, not pages tall; the gaps between its rows no wider than
 * [MAX_ROW_GAP].
 */
fun List<Pin>.fitted(): Fitted {
    val columns = maxOf { it.x } + 1f
    val rows = maxOf { it.y } + 1f
    if (rows <= TURN_FROM_ROWS || rows < columns * 1.5f) return Fitted(closer(this), false)
    val right = maxOf { it.x }
    return Fitted(closer(map { it.copy(x = it.y, y = right - it.x) }), true)
}

/** [pins] with the gaps between their rows no wider than [MAX_ROW_GAP]. */
private fun closer(pins: List<Pin>): List<Pin> {
    val rows = pins.map { it.y }.distinct().sorted()
    var shift = 0f
    val moved = rows.mapIndexed { i, y ->
        if (i > 0) shift += maxOf(0f, y - rows[i - 1] - MAX_ROW_GAP)
        y to y - shift
    }.toMap()
    return pins.map { it.copy(y = moved.getValue(it.y)) }
}

/** A reference tool: what it shows is [sheets], the first open, the others picked at the top of its page. */
abstract class ReferenceFeature : AppFeature {
    override val category = FeatureCategory.Reference

    abstract val sheets: List<Sheet>

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        ReferencePage(stringResource(titleStringResId), sheets, icon = icon)
    }
}

// The colors of wires (cables, connectors' leads), named in their standards
object Wire {
    val White = Color(0xFFF5F5F5)
    val Black = Color(0xFF212121)
    val Red = Color(0xFFD32F2F)
    val Green = Color(0xFF388E3C)
    val Blue = Color(0xFF1976D2)
    val Yellow = Color(0xFFFBC02D)
    val Orange = Color(0xFFF57C00)
    val Brown = Color(0xFF6D4C41)
    val Grey = Color(0xFF9E9E9E)
    val Violet = Color(0xFF7B1FA2)
    val Pink = Color(0xFFF06292)
    val Tan = Color(0xFFD7B98E)
    val Natural = Color(0xFFECEFF1)
    val Aqua = Color(0xFF26A69A)
}
