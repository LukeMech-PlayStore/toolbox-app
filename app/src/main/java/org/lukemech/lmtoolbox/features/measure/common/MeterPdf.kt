package org.lukemech.lmtoolbox.features.measure.common

import android.content.res.Resources
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import java.io.OutputStream
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.PdfPages

/**
 * A meter's readings as they were when taken, for its PDF ([MeterPdf]), in the words and numbers its page shows
 * them: its [reading] (in [unit]), [sections] of results, the [graph] it drew, [notes] on how it measures.
 */
class MeterReport(
    val reading: String,
    val unit: String,
    val sections: List<Section>,
    val graph: Graph? = null,
    val notes: List<String> = emptyList(),
    val taken: Date = Date(),
) {
    /** [rows] of a label and its value, under [title] (none: the reading's own), [marked] the one it's like. */
    class Section(val title: String?, val rows: List<Pair<String, String>>, val marked: Int? = null)

    /** A LiveGraph's [values] (oldest first, one every [intervalMs]) and its scale ([GraphScale.of]), in [unit]. */
    class Graph(val values: List<Float>, val intervalMs: Long, val min: Float, val max: Float, val step: Float, val unit: String)
}

/**
 * A [MeterReport] as a PDF ([PdfPages], [title] the meter's): when it was taken, its reading large as on its page,
 * its results in rows, the graph's line over the seconds it went back (its scale the page's), its notes.
 */
class MeterPdf(resources: Resources, title: String) : PdfPages(resources, title) {

    private val readingPaint = paint(28f, bold = true)
    private val unitPaint = paint(14f, color = QUIET)
    private val labelPaint = paint(9f)
    private val valuePaint = paint(9f, bold = true)
    private val markedPaint = paint(9f, bold = true, color = ACCENT)

    /** Writes [report] to [out]. */
    fun write(report: MeterReport, out: OutputStream) = write(out) {
        newPage()
        val locale = resources.configuration.locales[0]
        val taken = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.MEDIUM, locale).format(report.taken)
        paragraph(resources.getString(R.string.f_meter_pdf_taken, taken), titlePaint, after = 12f)
        reading(report.reading, report.unit)
        report.sections.forEach(::section)
        report.graph?.takeIf { it.values.size >= 2 }?.let(::graph)
        report.notes.forEach { paragraph(it, quietPaint) }
    }

    /** The reading, large, its unit after it, on a card as the page's. */
    private fun reading(value: String, unit: String) {
        val height = 56f
        room(height + 12f)
        val box = RectF(MARGIN, y, MARGIN + width, y + height)
        fill.color = BODY
        canvas.drawRoundRect(box, CORNER, CORNER, fill)
        val baseline = y + height / 2 + readingPaint.textSize * 0.35f
        canvas.drawText(value, MARGIN + PAD * 2, baseline, readingPaint)
        canvas.drawText(unit, MARGIN + PAD * 2 + readingPaint.measureText(value) + 6f, baseline, unitPaint)
        y += height + 12f
    }

    /** A section's heading, then its rows: the label at the left, the value at the right; the marked one teal. */
    private fun section(section: MeterReport.Section) {
        section.title?.let {
            room(ROW + 20f)
            paragraph(it, headingPaint, after = 4f)
        }
        section.rows.forEachIndexed { i, (label, value) ->
            val marked = i == section.marked
            val values = if (marked) markedPaint else valuePaint
            val valueWidth = values.measureText(value)
            val laid = layout(label, if (marked) markedPaint else labelPaint, width - valueWidth - 4 * PAD)
            val height = maxOf(ROW, laid.height + 2 * PAD)
            room(height)
            if (marked || i % 2 == 1) {
                fill.color = if (marked) MARKED else STRIPE
                canvas.drawRect(MARGIN, y, MARGIN + width, y + height, fill)
            }
            drawLayout(laid, MARGIN + PAD, y + (height - laid.height) / 2)
            canvas.drawText(value, MARGIN + width - PAD - valueWidth, y + height / 2 + values.textSize * 0.35f, values)
            y += height
        }
        stroke.color = TILE_EDGE
        stroke.strokeWidth = 0.5f
        canvas.drawLine(MARGIN, y, MARGIN + width, y, stroke)
        y += 14f
    }

    /**
     * The graph, the page's width: its lines across, labeled as on the page, the values' line over them, and under
     * them how many seconds back each point was (the newest at the right, at 0).
     */
    private fun graph(graph: MeterReport.Graph) {
        val seconds = (graph.values.size - 1) * graph.intervalMs / 1000f
        room(16f + GRAPH + 24f)
        paragraph(resources.getString(R.string.f_meter_pdf_graph, GraphScale.tick(seconds)), headingPaint, after = 6f)
        val box = RectF(MARGIN, y, MARGIN + width, y + GRAPH)
        stroke.color = TILE_EDGE
        stroke.strokeWidth = 0.75f
        canvas.drawRoundRect(box, CORNER, CORNER, stroke)

        // Inside the card, as the page's: from its top to its bottom less a padding
        val left = box.left + PAD
        val right = box.right - PAD
        val top = box.top + PAD
        val bottom = box.bottom - PAD
        val scale = GraphScale.of(graph.values, graph.min, graph.max, graph.step)
        fun at(value: Float) = bottom - (bottom - top) * scale.fraction(value)
        stroke.strokeWidth = 0.5f
        scale.lines.forEach { line ->
            val lineY = at(line)
            canvas.drawLine(left, lineY, right, lineY, stroke)
            // Above its line, but the lowest's (else off the graph)
            canvas.drawText("${GraphScale.tick(line)} ${graph.unit}", left, (lineY - 2f).coerceAtLeast(top + quietPaint.textSize), quietPaint)
        }

        val dx = (right - left) / (graph.values.size - 1)
        val path = Path()
        graph.values.forEachIndexed { i, value ->
            if (i == 0) path.moveTo(left + dx * i, at(value)) else path.lineTo(left + dx * i, at(value))
        }
        stroke.color = ACCENT
        stroke.strokeWidth = 1.25f
        stroke.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(path, stroke)

        // Seconds back, at a round step: never more than a few labels
        val every = TIME_STEPS.firstOrNull { seconds / it <= MAX_TIME_LABELS } ?: TIME_STEPS.last()
        stroke.color = EDGE
        stroke.strokeWidth = 0.5f
        var back = 0f
        while (back <= seconds + 0.001f) {
            val x = right - (right - left) * back / seconds
            canvas.drawLine(x, box.bottom, x, box.bottom + 3f, stroke)
            val text = if (back == 0f) "0 s" else "−${GraphScale.tick(back)} s"
            val textWidth = quietPaint.measureText(text)
            canvas.drawText(text, (x - textWidth / 2).coerceIn(MARGIN, MARGIN + width - textWidth), box.bottom + 12f, quietPaint)
            back += every
        }
        y = box.bottom + 24f
    }

    private companion object {
        const val PAD = 8f
        const val CORNER = 8f
        const val ROW = 18f
        const val GRAPH = 200f
        const val MAX_TIME_LABELS = 6
        val TIME_STEPS = listOf(0.5f, 1f, 2f, 5f, 10f, 15f, 30f, 60f)

        // The marked row's: the teal, faint
        const val MARKED = 0x2200897B
    }
}

/** A meter's PDF's file name: [title] and when it was [taken] (as a name can hold it: no colons). */
fun meterPdfName(title: String, taken: Date) =
    "$title ${SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.ROOT).format(taken)}"
