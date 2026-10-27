package org.lukemech.lmtoolbox.features.reference.common

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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

/**
 * A connector drawn from its [pins], each tapped for what it does, listed under it. [inOrder]: drawn in number
 * order where the face's own layout isn't (round connectors), said so under it.
 */
class PinoutSheet(
    override val name: Txt,
    val pins: List<Pin>,
    val outline: Outline = Outline.Box,
    override val note: Txt? = null,
    val inOrder: Boolean = false,
    override val group: Txt? = null,
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
