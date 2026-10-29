package org.lukemech.lmtoolbox.features.media.pdf_tools

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import org.lukemech.lmtoolbox.e2e.LinuxToolTest

/** The PDF tools, installed and gone through (see LinuxToolTest) with a PDF to work on. */
class PdfToolsTest : LinuxToolTest(PdfToolsFeature) {
    /** A 3-page A4 PDF with a line of text on each, made by Android's own PdfDocument. */
    override fun files(): List<Pair<String, File>> {
        val out = File(context.cacheDir, "e2e/sample.pdf").apply { parentFile?.mkdirs(); delete() }
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 24f }
        for (n in 1..3) {
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, n).create())
            page.canvas.drawText("Page $n", 72f, 100f, paint)
            document.finishPage(page)
        }
        out.outputStream().use { document.writeTo(it) }
        document.close()
        return listOf("application/pdf" to out)
    }
}
