package org.lukemech.lmtoolbox.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// A terminal's colors, the same in light and dark themes
private val Background = Color(0xFF0D1117)
private val Foreground = Color(0xFFC9D1D9)
private val Prompt = Color(0xFF7EE787)
private val Error = Color(0xFFFF7B72)
private val Warning = Color(0xFFE3B341)

/**
 * A command's output as a terminal shows it: [command] after a prompt, then [output], errors in red
 * and warnings in yellow. Scrolls both ways (lines aren't wrapped), opens at the end, selectable.
 */
@Composable
fun Terminal(output: String, modifier: Modifier = Modifier, command: String? = null) {
    val text = remember(output, command) {
        buildAnnotatedString {
            command?.let {
                withStyle(SpanStyle(color = Prompt)) { append("$ ") }
                append(it)
                append('\n')
            }
            output.lines().forEachIndexed { i, line ->
                if (i > 0) append('\n')
                val color = when {
                    Regex("""\b(ERROR|Error|Traceback|FAILED|Exception)\b""").containsMatchIn(line) -> Error
                    Regex("""\b(WARNING|Warning|warning)\b""").containsMatchIn(line) -> Warning
                    else -> null
                }
                if (color != null) withStyle(SpanStyle(color = color)) { append(line) } else append(line)
            }
        }
    }
    val vertical = rememberScrollState()
    // The end is what matters (where it failed)
    LaunchedEffect(text) { vertical.scrollTo(vertical.maxValue) }

    Surface(color = Background, contentColor = Foreground, shape = MaterialTheme.shapes.small, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .listBox(vertical)
                .horizontalScroll(rememberScrollState())
                .padding(10.dp)
        ) {
            SelectionContainer {
                Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp, softWrap = false)
            }
        }
    }
}
