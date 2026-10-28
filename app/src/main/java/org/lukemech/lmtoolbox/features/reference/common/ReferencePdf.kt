package org.lukemech.lmtoolbox.features.reference.common

import android.content.res.Resources
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import java.io.OutputStream
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.PdfPages

/**
 * A reference's sheet as a PDF ([PdfPages]). A pinout: its board's specs, the connector as on its page (turned the
 * same way), what its colors mean, its notes, then every pin in a table. A table: its columns as wide as they need
 * (the page turned when they're many), its headings again on every page.
 */
class ReferencePdf(resources: Resources, title: String) : PdfPages(resources, title) {

    private val cellPaint = paint(8f)
    private val cellBoldPaint = paint(8f, bold = true)
    private val valuePaint = paint(11f, bold = true)

    /** Writes [sheet] (a pinout's or a table's) to [out]. */
    fun write(sheet: Sheet, out: OutputStream) = write(out) {
        when (sheet) {
            is PinoutSheet -> pinout(sheet)
            is TableSheet -> table(sheet)
            is ToolSheet -> error("A tool's sheet has nothing to print")
        }
    }

    private fun heading(sheet: Sheet) {
        newPage()
        val name = listOfNotNull(sheet.group?.resolve(resources), sheet.name.resolve(resources)).joinToString(" · ")
        paragraph(name, titlePaint, after = 10f)
        sheet.note?.let { paragraph(it.resolve(resources), quietPaint, after = 10f) }
    }

    // --- A pinout ---

    private fun pinout(sheet: PinoutSheet) {
        heading(sheet)
        if (sheet.specs.isNotEmpty()) specs(sheet.specs)
        val fitted = sheet.pins.fitted()
        drawing(sheet, fitted.pins)
        legend(sheet.pins)
        listOfNotNull(
            resources.getString(R.string.rf_drawn_in_order).takeIf { sheet.inOrder },
            resources.getString(R.string.rf_drawn_turned).takeIf { fitted.turned },
        ).forEach { paragraph(it, quietPaint) }
        y += 8f
        rows(
            listOf(resources.getString(R.string.rf_pin), resources.getString(R.string.rf_pdf_name),
                resources.getString(R.string.rf_pdf_kind), resources.getString(R.string.rf_pdf_function)),
            sheet.pins.map { pin ->
                listOf(
                    Cell(pin.number, pin.wire ?: listOf(pin.kind.color)),
                    Cell(pin.name),
                    Cell(resources.getString(pin.kind.label)),
                    Cell(pin.description?.resolve(resources).orEmpty()),
                )
            },
            fractions = listOf(0.14f, 0.2f, 0.14f, 0.52f)
        )
    }

    /** The board's specs, two to a line: each one's label, its value bold. */
    private fun specs(specs: List<Spec>) {
        // As the app's tiles: three to a row, each a card with its icon and label over its value, a row as tall as
        // its tallest
        val gap = 6f
        val tileWidth = (width - gap * (SPECS_PER_ROW - 1)) / SPECS_PER_ROW
        val inner = tileWidth - 2 * TILE_PAD
        specs.chunked(SPECS_PER_ROW).forEach { row ->
            val values = row.map { layout(it.value.resolve(resources), valuePaint, inner) }
            val height = TILE_PAD + 10f + 4f + values.maxOf { it.height } + TILE_PAD
            room(height + gap)
            row.forEachIndexed { i, spec ->
                val left = MARGIN + i * (tileWidth + gap)
                val box = RectF(left, y, left + tileWidth, y + height)
                fill.color = BODY
                canvas.drawRoundRect(box, TILE_CORNER, TILE_CORNER, fill)
                stroke.color = TILE_EDGE
                stroke.strokeWidth = 0.75f
                canvas.drawRoundRect(box, TILE_CORNER, TILE_CORNER, stroke)
                icon(spec.kind.icon, left + TILE_PAD, y + TILE_PAD, 9f, ACCENT)
                canvas.drawText(resources.getString(spec.kind.label), left + TILE_PAD + 13f, y + TILE_PAD + 8f, quietPaint)
                drawLayout(values[i], left + TILE_PAD, y + TILE_PAD + 14f)
            }
            y += height + gap
        }
        y += 6f
    }

    /** [vector] (a Material icon) at [x], [y], [size] across, in [color]: drawn as its paths, sharp at any zoom. */
    private fun icon(vector: ImageVector, x: Float, y: Float, size: Float, color: Int) {
        val scale = size / vector.viewportWidth
        fill.color = color
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(scale, scale)
        fun draw(group: VectorGroup) {
            group.forEach { node ->
                when (node) {
                    is VectorPath -> canvas.drawPath(PathParser().addPathNodes(node.pathData).toPath().asAndroidPath(), fill)
                    is VectorGroup -> draw(node)
                }
            }
        }
        draw(vector.root)
        canvas.restore()
    }

    /** The connector: its outline and its pins at their [places], as on its page, scaled to the page's width. */
    private fun drawing(sheet: PinoutSheet, places: List<Pin>) {
        val columns = places.maxOf { it.x } + 1f
        val rows = places.maxOf { it.y } + 1f
        val pitch = minOf(MAX_PITCH, width / (columns + 1.2f))
        val w = pitch * (columns + 1.2f)
        val h = pitch * (rows + 1.2f)
        room(h + 8f)
        val left = MARGIN + (width - w) / 2
        val top = y
        val inset = pitch * 0.25f
        fill.color = BODY
        stroke.color = EDGE
        stroke.strokeWidth = 1f
        when (sheet.outline) {
            Outline.Box -> {
                val box = RectF(left + inset, top + inset, left + w - inset, top + h - inset)
                canvas.drawRoundRect(box, pitch * 0.3f, pitch * 0.3f, fill)
                canvas.drawRoundRect(box, pitch * 0.3f, pitch * 0.3f, stroke)
            }
            Outline.Trapezoid -> {
                val slant = pitch * 0.45f
                val path = Path().apply {
                    moveTo(left + inset, top + inset)
                    lineTo(left + w - inset, top + inset)
                    lineTo(left + w - inset - slant, top + h - inset)
                    lineTo(left + inset + slant, top + h - inset)
                    close()
                }
                canvas.drawPath(path, fill)
                canvas.drawPath(path, stroke)
            }
        }
        val label = paint(pitch * 0.3f, bold = true)
        places.forEach { pin ->
            val cx = left + (pin.x + 1.1f) * pitch
            val cy = top + (pin.y + 1.1f) * pitch
            val r = pitch * 0.38f
            val colors = pin.wire ?: listOf(pin.kind.color)
            if (pin.kind == PinKind.Unused && pin.wire == null) {
                stroke.color = PinKind.Unused.color.toArgb()
                stroke.strokeWidth = pitch * 0.06f
                canvas.drawCircle(cx, cy, r, stroke)
            } else {
                dot(cx, cy, r, colors)
            }
            // The number, shrunk to fit the pin ("AREF")
            label.textSize = pitch * 0.3f
            val textWidth = label.measureText(pin.number)
            if (textWidth > r * 1.7f) label.textSize *= r * 1.7f / textWidth
            label.color = if (colors.first().luminance() > 0.5f || pin.kind == PinKind.Unused) INK else android.graphics.Color.WHITE
            canvas.drawText(pin.number, cx - label.measureText(pin.number) / 2, cy + label.textSize * 0.35f, label)
        }
        y += h + 8f
    }

    /** A dot of [colors]: one, or a wire's two (its base and its stripe), halves. */
    private fun dot(cx: Float, cy: Float, r: Float, colors: List<Color>) {
        fill.color = colors.first().toArgb()
        canvas.drawCircle(cx, cy, r, fill)
        if (colors.size > 1) {
            fill.color = colors[1].toArgb()
            canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -90f, 180f, true, fill)
        }
        stroke.color = 0x40000000
        stroke.strokeWidth = 0.5f
        canvas.drawCircle(cx, cy, r, stroke)
    }

    /** What the colors stand for: the kinds of pins there are (but where wires color them), in a line. */
    private fun legend(pins: List<Pin>) {
        val kinds = pins.filter { it.wire == null }.map { it.kind }.distinct().sortedBy { it.ordinal }
        if (kinds.isEmpty()) return
        room(14f)
        var x = MARGIN
        kinds.forEach { kind ->
            val text = resources.getString(kind.label)
            val step = 12f + textPaint.measureText(text) + 14f
            if (x + step > MARGIN + width) {
                x = MARGIN
                y += 14f
                room(14f)
            }
            dot(x + 4f, y + 6f, 4f, listOf(kind.color))
            canvas.drawText(text, x + 12f, y + 9f, textPaint)
            x += step
        }
        y += 20f
    }

    // --- A table ---

    /** A table's cell: its [text], after a dot of [colors] (a wire's, a pin's) when it has them. */
    private class Cell(val text: String, val colors: List<Color>? = null)

    private fun cell(txt: Txt): Cell = when (txt) {
        is Txt.Swatch -> Cell(txt.label.resolve(resources), txt.colors)
        else -> Cell(txt.resolve(resources))
    }

    private fun table(sheet: TableSheet) {
        val headings = sheet.columns.map { it.resolve(resources) }
        val cells = sheet.rows.map { row -> row.map(::cell) }
        // Each column as wide as its widest cell needs (to a point); turned when they don't fit a portrait page
        val natural = headings.indices.map { c ->
            maxOf(cellBoldPaint.measureText(headings[c]), cells.maxOfOrNull { cellPaint.measureText(it[c].text) + (if (it[c].colors != null) 12f else 0f) } ?: 0f)
                .coerceAtMost(MAX_COLUMN) + 2 * CELL_PAD
        }
        landscape = natural.sum() > A4_WIDTH - 2 * MARGIN
        heading(sheet)
        val total = natural.sum()
        rows(headings, cells, fractions = natural.map { it / total })
    }

    /** [cells]' rows under [headings], columns [fractions] of the page's width; the headings again on every page. */
    private fun rows(headings: List<String>, cells: List<List<Cell>>, fractions: List<Float>) {
        val widths = fractions.map { it * width }
        fun headingRow() {
            val height = headings.indices.maxOf { layout(headings[it], cellBoldPaint, widths[it] - 2 * CELL_PAD).height } + 2 * CELL_PAD
            fill.color = HEAD
            canvas.drawRect(MARGIN, y, MARGIN + width, y + height, fill)
            var x = MARGIN
            headings.forEachIndexed { c, text ->
                drawLayout(layout(text, cellBoldPaint, widths[c] - 2 * CELL_PAD), x + CELL_PAD, y + CELL_PAD)
                x += widths[c]
            }
            y += height
        }
        room(40f)
        headingRow()
        cells.forEachIndexed { r, row ->
            val laid = row.mapIndexed { c, cell ->
                layout(cell.text, cellPaint, widths[c] - 2 * CELL_PAD - (if (cell.colors != null) 12f else 0f))
            }
            val height = laid.maxOf { it.height } + 2 * CELL_PAD
            if (y + height > bottom) {
                newPage()
                headingRow()
            }
            if (r % 2 == 1) {
                fill.color = STRIPE
                canvas.drawRect(MARGIN, y, MARGIN + width, y + height, fill)
            }
            var x = MARGIN
            row.forEachIndexed { c, cell ->
                var textX = x + CELL_PAD
                cell.colors?.let {
                    dot(textX + 4f, y + CELL_PAD + 5f, 4f, it)
                    textX += 12f
                }
                drawLayout(laid[c], textX, y + CELL_PAD)
                x += widths[c]
            }
            y += height
        }
        stroke.color = EDGE
        stroke.strokeWidth = 0.5f
        canvas.drawLine(MARGIN, y, MARGIN + width, y, stroke)
        y += 8f
    }

    private companion object {
        const val MAX_PITCH = 30f
        const val MAX_COLUMN = 220f
        const val CELL_PAD = 4f
        const val SPECS_PER_ROW = 3
        const val TILE_PAD = 8f
        const val TILE_CORNER = 8f
    }
}
