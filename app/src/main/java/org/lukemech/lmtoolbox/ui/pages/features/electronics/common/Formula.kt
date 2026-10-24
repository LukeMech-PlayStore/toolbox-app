package org.lukemech.lmtoolbox.ui.pages.features.electronics.common

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.ui.components.PageHeading

/**
 * A formula a calculator uses: [tex] in a little of TeX's notation (see [MathText]), and what it
 * gives ([caption]).
 */
data class Formula(val tex: String, val caption: String? = null)

/** A formula's parts: text, a group, a fraction, a root, a superscript or a subscript. */
private sealed interface MathNode {
    data class Run(val text: String, val upright: Boolean) : MathNode
    data class Group(val items: List<MathNode>) : MathNode
    data class Fraction(val numerator: MathNode, val denominator: MathNode) : MathNode
    data class Root(val body: MathNode) : MathNode
    data class Script(val body: MathNode, val superscript: Boolean) : MathNode
}

/**
 * Reads [tex]: \frac{a}{b}, \sqrt{x}, x^{2} or x^2, R_{in} or R_1, {groups} and \text{upright words};
 * anything else (spaces too) is as written, Unicode for Greek letters and symbols (π, Ω, −, ·).
 */
private class MathParser(private val tex: String) {
    private var i = 0

    fun parse(): MathNode = MathNode.Group(sequence(upright = false))

    private fun sequence(upright: Boolean): List<MathNode> {
        val items = ArrayList<MathNode>()
        val run = StringBuilder()
        fun flush() {
            if (run.isNotEmpty()) items.add(MathNode.Run(run.toString(), upright))
            run.clear()
        }
        while (i < tex.length && tex[i] != '}') {
            when (val c = tex[i]) {
                '\\' -> {
                    flush()
                    i++
                    val name = tex.substring(i).takeWhile { it.isLetter() }
                    i += name.length
                    items.add(
                        when (name) {
                            "frac" -> MathNode.Fraction(argument(upright), argument(upright))
                            "sqrt" -> MathNode.Root(argument(upright))
                            "text" -> MathNode.Run(raw(), upright = true)
                            else -> MathNode.Run(name, upright)
                        }
                    )
                }
                '^', '_' -> {
                    flush()
                    i++
                    // Subscripts name things (U_in, R_L), upright; superscripts are maths
                    val scriptUpright = upright || c == '_'
                    val body = if (i < tex.length && tex[i] == '{') argument(scriptUpright) else MathNode.Run(tex[i++].toString(), scriptUpright)
                    items.add(MathNode.Script(body, superscript = c == '^'))
                }
                '{' -> {
                    flush()
                    items.add(argument(upright))
                }
                else -> {
                    run.append(c)
                    i++
                }
            }
        }
        flush()
        return items
    }

    /** {…} at [i] as a group. */
    private fun argument(upright: Boolean): MathNode {
        if (i >= tex.length || tex[i] != '{') return MathNode.Group(emptyList())
        i++
        val items = sequence(upright)
        i++
        return MathNode.Group(items)
    }

    /** {…} at [i] as it's written. */
    private fun raw(): String {
        if (i >= tex.length || tex[i] != '{') return ""
        val end = tex.indexOf('}', i).takeIf { it >= 0 } ?: tex.length
        return tex.substring(i + 1, end).also { i = end + 1 }
    }
}

/** Variables are italic: Latin letters and lowercase Greek but π (a constant). */
private fun Char.isVariable() = (this in 'a'..'z' || this in 'A'..'Z' || this in 'α'..'ω') && this != 'π'

@Composable
private fun MathNodeView(node: MathNode, style: TextStyle) {
    when (node) {
        is MathNode.Run -> Text(
            buildAnnotatedString {
                node.text.forEach { c ->
                    if (!node.upright && c.isVariable()) withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(c) } else append(c)
                }
            },
            style = style,
            softWrap = false
        )
        is MathNode.Group -> Row(verticalAlignment = Alignment.CenterVertically) {
            node.items.forEach { MathNodeView(it, style) }
        }
        is MathNode.Fraction -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(IntrinsicSize.Max).padding(horizontal = 3.dp)
        ) {
            MathNodeView(node.numerator, style)
            Box(
                Modifier
                    .padding(vertical = 2.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(LocalContentColor.current)
            )
            MathNodeView(node.denominator, style)
        }
        is MathNode.Root -> {
            val color = LocalContentColor.current
            val sign = with(LocalDensity.current) { (style.fontSize * 0.55f).toDp() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .drawBehind {
                        // The radical's tick, as tall as what's under it, and the bar over it
                        val s = sign.toPx()
                        val h = size.height
                        val path = Path().apply {
                            moveTo(0f, h * 0.6f)
                            lineTo(s * 0.3f, h * 0.5f)
                            lineTo(s * 0.6f, h - 1.dp.toPx())
                            lineTo(s, 1.dp.toPx())
                            lineTo(size.width, 1.dp.toPx())
                        }
                        drawPath(path, color, style = Stroke(width = 1.2.dp.toPx()))
                    }
                    .padding(start = sign + 2.dp, top = 4.dp, end = 2.dp)
            ) {
                MathNodeView(node.body, style)
            }
        }
        is MathNode.Script -> {
            // Its own height shifted off the line's middle: up for a superscript, down for a subscript
            val shift = with(LocalDensity.current) { (style.fontSize * 0.45f).toDp() }
            Box(Modifier.padding(start = 1.dp).then(if (node.superscript) Modifier.padding(bottom = shift) else Modifier.padding(top = shift))) {
                MathNodeView(node.body, style.copy(fontSize = style.fontSize * 0.7f))
            }
        }
    }
}

/** [tex] (see [MathParser]) typeset: fractions, roots, scripts, italic variables. */
@Composable
fun MathText(tex: String, fontSize: TextUnit = 22.sp) {
    val node = remember(tex) { MathParser(tex).parse() }
    MathNodeView(node, TextStyle(fontFamily = FontFamily.Serif, fontSize = fontSize, color = LocalContentColor.current))
}

/** A [PageHeading]'s action showing [formulas] in a window, each under its caption. */
@Composable
fun FormulasButton(formulas: List<Formula>) {
    if (formulas.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Default.Functions, contentDescription = stringResource(R.string.ec_formulas))
    }
    if (!open) return
    AlertDialog(
        onDismissRequest = { open = false },
        icon = { Icon(Icons.Default.Functions, contentDescription = null) },
        title = { Text(stringResource(R.string.ec_formulas), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                formulas.forEach { formula ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        formula.caption?.let {
                            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Centered, scrolling sideways when it's wider than the window
                            BoxWithConstraints {
                                val width = maxWidth
                                Box(Modifier.horizontalScroll(rememberScrollState())) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.widthIn(min = width).padding(horizontal = 16.dp, vertical = 12.dp)
                                    ) {
                                        MathText(formula.tex)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { open = false }) { Text(stringResource(android.R.string.ok)) }
        }
    )
}
