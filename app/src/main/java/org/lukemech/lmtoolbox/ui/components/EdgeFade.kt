package org.lukemech.lmtoolbox.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How far in scrolling content fades out at its edges, see [edgeFade]. */
private val EDGE_FADE = 32.dp

/**
 * For what [scroll] scrolls (before its verticalScroll): while there's more above / below, it fades
 * out at that edge instead of being cut off there (e.g. a [PageHeading]'s content, a list in a card).
 */
@Composable
fun Modifier.edgeFade(scroll: ScrollableState, length: Dp = EDGE_FADE) = fadeEdges(scroll, length, vertical = true)

/** [edgeFade] sideways: for what [scroll] scrolls horizontally (before its horizontalScroll). */
@Composable
fun Modifier.sideFade(scroll: ScrollableState, length: Dp = EDGE_FADE) = fadeEdges(scroll, length, vertical = false)

/** Fades what's drawn out over [length] at its start and end (top and bottom if [vertical]) while [scroll] has more there. */
@Composable
private fun Modifier.fadeEdges(scroll: ScrollableState, length: Dp, vertical: Boolean): Modifier {
    val start by animateDpAsState(if (scroll.canScrollBackward) length else 0.dp, label = "startFade")
    val end by animateDpAsState(if (scroll.canScrollForward) length else 0.dp, label = "endFade")
    return this
        // Its own layer, so the fade cuts only into the content, not into what's behind it
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val extent = if (vertical) size.height else size.width
            fun fade(length: Float, atEnd: Boolean) {
                if (length <= 0f) return
                val from = if (atEnd) extent - length else 0f
                val colors = if (atEnd) listOf(Color.Black, Color.Transparent) else listOf(Color.Transparent, Color.Black)
                drawRect(
                    if (vertical) Brush.verticalGradient(colors, startY = from, endY = from + length)
                    else Brush.horizontalGradient(colors, startX = from, endX = from + length),
                    topLeft = if (vertical) Offset(0f, from) else Offset(from, 0f),
                    size = if (vertical) Size(size.width, length) else Size(length, size.height),
                    blendMode = BlendMode.DstIn
                )
            }
            fade(start.toPx().coerceAtMost(extent / 2), atEnd = false)
            fade(end.toPx().coerceAtMost(extent / 2), atEnd = true)
        }
}
