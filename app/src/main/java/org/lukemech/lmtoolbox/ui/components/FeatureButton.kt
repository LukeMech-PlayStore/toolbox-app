package org.lukemech.lmtoolbox.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The space between rows (a [FeatureButton] and what's built on it: settings, switches, choices) one
 * under the other: the same everywhere in the app, every column of them spaced by it.
 */
val ROW_SPACING = 4.dp

/**
 * Rows [ROW_SPACING] apart, as spacedBy, but none for what shows nothing (a [Reveal] hidden: else two
 * spaces where it is); one coming in gets its space as it grows (at most as tall as it is so far).
 */
val RowArrangement = object : Arrangement.Vertical {
    override val spacing = ROW_SPACING

    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        val space = ROW_SPACING.roundToPx()
        var y = 0
        var any = false
        sizes.forEachIndexed { i, size ->
            if (any && size > 0) y += minOf(space, size)
            outPositions[i] = y
            y += size
            if (size > 0) any = true
        }
    }

    override fun toString() = "RowArrangement"
}

@Composable
fun FeatureButton(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    titleSubtitleSwap: Boolean = false,
    /** The subtitle on one line, its text smaller as far as it has to (e.g. an address, read whole). */
    subtitleOneLine: Boolean = false,
    colors: RowColors = RowColors.default,
    icon: ImageVector,
    icon2: ImageVector? = null,
    image: Painter? = null,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    buttonContent: (@Composable () -> Unit)? = null,
    buttonContent2: (@Composable () -> Unit)? = null,
    buttonsInColumn: Boolean = true,
    popupContent: (@Composable () -> Unit)? = null,
    popupAnimTime: Int = REVEAL_MS,
    popupAnimOutTime: Int = popupAnimTime,
    expanded: Boolean = false,
    buttonFavouriteClicked: Boolean = true,
    buttonFavouriteOnClick: (() -> Unit)? = null
) {
    val interactionModifier = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onClick)
    }

    // The expanded content hangs under the card as one piece with it, also while it folds away
    val popupState = remember { MutableTransitionState(expanded && popupContent != null) }
    popupState.targetState = expanded && popupContent != null
    val joined = popupState.currentState || popupState.targetState
    val noCorner = CornerSize(0.dp)

    Column(modifier = modifier) {
        Surface(
            shape = if (joined) MaterialTheme.shapes.medium.copy(bottomStart = noCorner, bottomEnd = noCorner)
            else MaterialTheme.shapes.medium,
            color = colors.background,
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .then(interactionModifier)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .animateContentSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.content,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                if (subtitle != null) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(title,
                            style = if (!titleSubtitleSwap) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                            color = colors.content
                        )
                        val subtitleStyle = if (!titleSubtitleSwap) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
                        Text(subtitle,
                            style = subtitleStyle,
                            color = colors.content,
                            fontStyle = if (!titleSubtitleSwap) FontStyle.Italic else FontStyle.Normal,
                            maxLines = if (subtitleOneLine) 1 else Int.MAX_VALUE,
                            autoSize = if (subtitleOneLine) TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = subtitleStyle.fontSize) else null
                        )
                    }
                } else {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.content,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (image != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Image(
                        painter = image,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                if (icon2 != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = icon2,
                        contentDescription = null,
                        tint = colors.content,
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (buttonContent != null || buttonContent2 != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    if (buttonsInColumn) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            buttonContent?.invoke()
                            buttonContent2?.invoke()
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            buttonContent?.invoke()
                            buttonContent2?.invoke()
                        }
                    }
                }

                if (buttonFavouriteOnClick != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = buttonFavouriteOnClick,
                        enabled = true
                    ) {
                        Icon(
                            imageVector = if (buttonFavouriteClicked) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (buttonFavouriteClicked) MaterialTheme.colorScheme.primary else colors.content
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visibleState = popupState,
            enter = revealEnter(popupAnimTime),
            exit = revealExit(popupAnimOutTime),
            modifier = Modifier
                .fillMaxWidth()
        ) {
            // A box of its own shade under a line, so it's clear where the expanded part ends
            Surface(
                shape = MaterialTheme.shapes.medium.copy(topStart = noCorner, topEnd = noCorner),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        popupContent?.invoke()
                    }
                }
            }
        }
    }
}
