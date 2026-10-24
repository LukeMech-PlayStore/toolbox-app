package org.lukemech.lmtoolbox.ui.pages.features.measure.ruler

import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.platform.LocalResources

@Composable
fun RulerPage() {
    // Get the real DPI from the device
    val displayMetrics = LocalResources.current.displayMetrics
    val screenWidthPx = displayMetrics.widthPixels.toFloat()
    val density = LocalDensity.current
    val rulerWidthDp = 100.dp
    val rulerWidthPx = with(density) { rulerWidthDp.toPx() }

    val realDpi = LocalResources.current.displayMetrics.xdpi // Horizontal DPI (or use ydpi for vertical)

    val pixelsPerCm = realDpi / 2.54f  // Convert cm to pixels based on the actual DPI
    val pixelsPerMm = pixelsPerCm / 10f // Pixels per millimeter
    var offsetX by remember { mutableFloatStateOf(0f) } // Start on the left side
    // offsetY is not needed as we only want left-right drag

    val rulerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 1f) // Ensure text is fully opaque
    val horizontalLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) // Different color for horizontal line

    // The Scaffold's content padding is not used here, so we can remove it for simplicity if no other Scaffold features (like TopAppBar) are needed.
    // If Scaffold is kept for future additions (e.g., a TopAppBar), ensure its contentPadding is applied to the direct child of Scaffold.
    // For now, let's assume Scaffold is not strictly necessary for just displaying the Ruler.
    // Scaffold {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)  // Apply background to the entire Box
            .pointerInput(Unit) {                              // Make the entire Box draggable

                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val newOffsetX = offsetX + dragAmount.x
                    // Ensure the ruler stays within the screen boundaries
                    offsetX = newOffsetX.coerceIn(0f, screenWidthPx - rulerWidthPx)
                    // offsetY is not updated to restrict vertical dragging
                }
            },
        contentAlignment = Alignment.TopStart // Align to top start for offset calculations
    ) {
        Column(
            modifier = Modifier
                .offset { IntOffset(offsetX.toInt(), 0) } // Only apply offsetX for horizontal dragging
        ) {
            val isRulerOnRightSide = offsetX > screenWidthPx / 2 - rulerWidthPx / 2
            val animationProgress by animateFloatAsState(
                targetValue = if (isRulerOnRightSide) 1f else 0f,
                animationSpec = tween(durationMillis = 200),
                label = "RulerSideAnimation"
            )
            Canvas(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(100.dp) // Arbitrary width for the ruler, will be overridden by fillMaxHeight if width > height
                    .padding(vertical = 16.dp) // Outer padding for space around the ruler // Removed horizontal padding
                    .clip(RoundedCornerShape(12.dp))
                    .background(rulerColor) // Background color
//                    .pointerInput(Unit) { // Make the ruler itself draggable
//                        detectDragGestures { change, dragAmount ->
//                            change.consume()
//                            val newOffsetX = offsetX + dragAmount.x
//                            // Ensure the ruler stays within the screen boundaries
//                            offsetX = newOffsetX.coerceIn(0f, screenWidthPx - rulerWidthPx)
//                            // offsetY is not updated to restrict vertical dragging
//
//                        }
//                    }
                    .padding(vertical = 8.dp, horizontal = 2.dp) // Inner padding for the ruler content
            ) {
                val canvasWidth = size.width

                val textPaint =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = lineColor.hashCode() // Use the color from MaterialTheme
                        textSize = 12.sp.toPx()
                        // textAlign will be set dynamically
                    }

                // Draw horizontal line, respecting internal padding
                drawLine(
                    color = horizontalLineColor, // Use theme-based color
                    start = Offset(canvasWidth / 2, 4.dp.toPx()), // Start from the top edge of inner padding
                    end = Offset(canvasWidth / 2, size.height - 4.dp.toPx()), // End at the bottom edge of inner padding
                    strokeWidth = 2f
                )

                val startOffsetPx = 4.dp.toPx() // Start drawing marks from the beginning of the inner padded area

                // Draw millimeter and centimeter marks
                val maxMm = ( size.height - 2 * 4.dp.toPx() / pixelsPerMm ).toInt() // Calculate maxMm based on drawable height
                for (mm in 0..maxMm) {
                    val yPosOnRuler =
                        mm.toFloat() * pixelsPerMm // y position relative to the start of marks
                    val yPosCanvas =
                        startOffsetPx + yPosOnRuler // y position on the canvas, considering inner padding

                    // Stop drawing if marks go beyond the drawable canvas area (considering inner padding)
                    if (yPosCanvas > size.height - 4.dp.toPx()) break

                    val isCmMark = mm % 10 == 0
                    val isHalfCmMark = mm % 5 == 0 && !isCmMark

                    val lineWidth = when {
                        isCmMark -> canvasWidth / 2 - 2.dp.toPx()
                        isHalfCmMark -> canvasWidth / 3
                        else -> canvasWidth / 4
                    }

                    val stroke = when {
                        isCmMark -> 2f
                        isHalfCmMark -> 1.5f
                        else -> 1f
                    }

                    // Animate line drawing
                    val lineStartX = lerp(
                        canvasWidth / 2, // Start X for left side
                        canvasWidth / 2, // End X for right side
                        animationProgress // This will effectively keep it at canvasWidth / 2, but good for consistency
                    )

                    drawLine(
                        color = lineColor,
                        start = Offset(lineStartX, yPosCanvas),
                        end = Offset(if (isRulerOnRightSide) canvasWidth / 2 + lineWidth * animationProgress else canvasWidth / 2 - lineWidth * (1-animationProgress) , yPosCanvas),
                        strokeWidth = stroke
                    )

                    // Draw text for centimeter marks
                    if (isCmMark) {
                        val cmValue = mm / 10
                        val text = cmValue.toString()

                        val textX = lerp(
                            canvasWidth / 2 + 5.dp.toPx(), // Text X for left side (text on right)
                            canvasWidth / 2 - 5.dp.toPx(), // Text X for right side (text on left)
                            animationProgress
                        )
                        textPaint.textAlign = if (animationProgress > 0.5f) { // More towards right side
                            Paint.Align.RIGHT
                        } else Paint.Align.LEFT

                        drawContext.canvas.nativeCanvas.drawText(
                            text,
                            textX,
                            yPosCanvas + textPaint.textSize / 3, // Vertically align text with the mark
                            textPaint
                        )
                    }
                }
            }
        }
    }
}

