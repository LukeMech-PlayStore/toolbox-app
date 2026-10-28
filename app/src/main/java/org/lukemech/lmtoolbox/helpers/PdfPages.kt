package org.lukemech.lmtoolbox.helpers

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withTranslation
import java.io.OutputStream
import org.lukemech.lmtoolbox.R

/**
 * A PDF drawn as it's printed, not a picture of a page: its text sharp and found by a search. A4 pages (portrait,
 * or turned: [landscape]), [title] (the tool's) over each, its number under; what's drawn goes down a page from
 * [y], to a new one when there's no [room] left.
 */
open class PdfPages(protected val resources: Resources, private val title: String) {

    private val document = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var number = 0
    protected var landscape = false

    // Where the next thing goes, from the top of the page's content
    protected var y = 0f
    protected val width get() = (if (landscape) A4_HEIGHT else A4_WIDTH) - 2 * MARGIN
    protected val bottom get() = (if (landscape) A4_WIDTH else A4_HEIGHT) - MARGIN - FOOTER
    protected val canvas: Canvas get() = page!!.canvas

    protected fun paint(size: Float, bold: Boolean = false, color: Int = INK) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        this.color = color
    }

    protected val titlePaint = paint(15f, bold = true)
    protected val headingPaint = paint(11f, bold = true)
    protected val textPaint = paint(9f)
    protected val quietPaint = paint(8f, color = QUIET)
    protected val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /** Draws it all ([draw]: from its first page on) and writes it to [out]. */
    protected fun write(out: OutputStream, draw: () -> Unit) {
        try {
            draw()
            finishPage()
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    protected fun newPage(landscape: Boolean = this.landscape) {
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
        val footer = resources.getString(R.string.pdf_footer, resources.getString(R.string.app_name), number)
        current.canvas.drawText(footer, MARGIN, h - MARGIN + 4f, quietPaint)
        document.finishPage(current)
        page = null
    }

    /** Room for [height] more on this page, or a new one. */
    protected fun room(height: Float) {
        if (page == null || y + height > bottom) newPage()
    }

    protected fun layout(text: String, paint: TextPaint, width: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()

    /** [text], wrapped to the page's width, at its left margin. */
    protected fun paragraph(text: String, paint: TextPaint = textPaint, after: Float = 6f) {
        val laid = layout(text, paint, width)
        room(laid.height.toFloat())
        drawLayout(laid, MARGIN, y)
        y += laid.height + after
    }

    protected fun drawLayout(layout: StaticLayout, x: Float, y: Float) {
        canvas.withTranslation(x, y) { layout.draw(this) }
    }

    companion object {
        // A4 in points, and the page's margins
        const val A4_WIDTH = 595f
        const val A4_HEIGHT = 842f
        const val MARGIN = 36f
        const val FOOTER = 14f

        // Printed: dark on white, whatever the app's theme
        const val INK = 0xFF212121.toInt()
        const val QUIET = 0xFF616161.toInt()
        const val BODY = 0xFFF1F3F4.toInt()
        const val EDGE = 0xFF9E9E9E.toInt()
        const val HEAD = 0xFFE3E7EA.toInt()
        const val STRIPE = 0xFFF6F7F8.toInt()
        const val TILE_EDGE = 0xFFD5DADD.toInt()
        // The app's teal, as its tiles' icons
        const val ACCENT = 0xFF00897B.toInt()
    }
}
