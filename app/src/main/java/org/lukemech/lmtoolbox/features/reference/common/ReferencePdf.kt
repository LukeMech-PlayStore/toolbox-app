package org.lukemech.lmtoolbox.features.reference.common

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import java.io.OutputStream
import org.lukemech.lmtoolbox.R

/**
 * A reference's sheet as a PDF, drawn as it's printed, not a picture of the page: its text sharp and found by a
 * search. A pinout: its board's specs, the connector as on its page (turned the same way), what its colors mean, its
 * notes, then every pin in a table. A table: its columns as wide as they need (the page turned when they're many),
 * its headings again on every page. A4, [title] (the tool's) over each page, its number under.
 */
class ReferencePdf(private val resources: Resources, private val title: String) {

    private val document = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var number = 0
    private var landscape = false

    // Where the next thing goes, from the top of the page's content
    private var y = 0f
    private val width get() = (if (landscape) A4_HEIGHT else A4_WIDTH) - 2 * MARGIN
    private val bottom get() = (if (landscape) A4_WIDTH else A4_HEIGHT) - MARGIN - FOOTER
    private val canvas: Canvas get() = page!!.canvas

    private fun paint(size: Float, bold: Boolean = false, color: Int = INK) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        this.color = color
    }

    private val titlePaint = paint(15f, bold = true)
    private val headingPaint = paint(11f, bold = true)
    private val textPaint = paint(9f)
    private val quietPaint = paint(8f, color = QUIET)
    private val cellPaint = paint(8f)
    private val cellBoldPaint = paint(8f, bold = true)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /** Writes [sheet] (a pinout's or a table's) to [out]. */
    fun write(sheet: Sheet, out: OutputStream) {
        try {
            when (sheet) {
                is PinoutSheet -> pinout(sheet)
                is TableSheet -> table(sheet)
                is ToolSheet -> error("A tool's sheet has nothing to print")
            }
            finishPage()
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    // --- Pages ---

    private fun newPage(landscape: Boolean = this.landscape) {
        finishPage()
        this.landscape = landscape
        number++
        val (w, h) = if (landscape) A4_HEIGHT to A4_WIDTH else A4_WIDTH to A4_HEIGHT
        page = document.startPage(PdfDocument.PageInfo.Builder(w.toInt(), h.toInt(), number).create())
        canvas.drawText(title, MARGIN, MARGIN + 6f, quietPaint)
        y = MARGIN + 18f
    }

    private fun finishPage() {
        val current = page ?: return
        val h = if (landscape) A4_WIDTH else A4_HEIGHT
        val footer = resources.getString(R.string.rf_pdf_footer, resources.getString(R.string.app_name), number)
        current.canvas.drawText(footer, MARGIN, h - MARGIN + 4f, quietPaint)
        document.finishPage(current)
        page = null
    }

    /** Room for [height] more on this page, or a new one. */
    private fun room(height: Float) {
        if (page == null || y + height > bottom) newPage()
    }

    private fun layout(text: String, paint: TextPaint, width: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()

    /** [text], wrapped to the page's width, at its left margin. */
    private fun paragraph(text: String, paint: TextPaint = textPaint, after: Float = 6f) {
        val laid = layout(text, paint, width)
        room(laid.height.toFloat())
        canvas.save()
        canvas.translate(MARGIN, y)
        laid.draw(canvas)
        canvas.restore()
        y += laid.height + after
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
        val half = width / 2
        specs.chunked(2).forEach { pair ->
            room(14f)
            pair.forEachIndexed { i, spec ->
                val x = MARGIN + i * half
                val label = resources.getString(spec.kind.label) + ": "
                canvas.drawText(label, x, y + 9f, textPaint)
                canvas.drawText(spec.value.resolve(resources), x + textPaint.measureText(label), y + 9f, cellBoldPaint.apply { textSize = 9f })
            }
            y += 14f
        }
        cellBoldPaint.textSize = 8f
        y += 8f
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

    private fun drawLayout(layout: StaticLayout, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    private companion object {
        // A4 in points, and the page's margins
        const val A4_WIDTH = 595f
        const val A4_HEIGHT = 842f
        const val MARGIN = 36f
        const val FOOTER = 14f
        const val MAX_PITCH = 30f
        const val MAX_COLUMN = 220f
        const val CELL_PAD = 4f

        // Printed: dark on white, whatever the app's theme
        const val INK = 0xFF212121.toInt()
        const val QUIET = 0xFF616161.toInt()
        const val BODY = 0xFFF1F3F4.toInt()
        const val EDGE = 0xFF9E9E9E.toInt()
        const val HEAD = 0xFFE3E7EA.toInt()
        const val STRIPE = 0xFFF6F7F8.toInt()
    }
}
