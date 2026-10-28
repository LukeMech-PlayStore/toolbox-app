package org.lukemech.lmtoolbox.features.measure.common

import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The meters' PDFs: made whole, with a graph or without, however wide its values go; the graph's scale as the page's. */
class MeterPdfTest {

    private val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources

    private fun pdf(report: MeterReport): ByteArray {
        val out = ByteArrayOutputStream()
        MeterPdf(resources, "Meter").write(report, out)
        return out.toByteArray()
    }

    private fun assertPdf(bytes: ByteArray) = assertTrue("no PDF", bytes.size > 500 && String(bytes, 0, 5) == "%PDF-")

    @Test
    fun everyKindPrints() {
        val rows = MeterReport.Section(null, listOf("Min" to "40.0 dB", "Max" to "72.5 dB"))
        // A sound meter's: its 30 s, one far over its scale
        assertPdf(pdf(MeterReport("55.1", "dB(A)", listOf(rows), MeterReport.Graph(List(300) { 40f + it % 50 } + 130f, 100, 30f, 110f, 20f, "dB(A)"), listOf("A note"))))
        // A light meter's: no graph, its references, one marked
        val references = MeterReport.Section("For comparison", List(9) { "Light $it" to "$it lx" }, marked = 3)
        assertPdf(pdf(MeterReport("320", "lx", listOf(rows, references))))
        // A single point, no line to draw: the rest still printed
        assertPdf(pdf(MeterReport("0.012", "m/s²", listOf(rows), MeterReport.Graph(listOf(0.01f), 50, 0f, 1f, 0.25f, "m/s²"))))
    }

    @Test
    fun scaleAsThePage() {
        val calm = GraphScale.of(listOf(45f, 60f), 30f, 110f, 20f)
        assertEquals(listOf(30f, 50f, 70f, 90f, 110f), calm.lines)
        // Widened by a loud one, in whole steps, a line every few: never more than six
        val loud = GraphScale.of(listOf(45f, 200f), 30f, 110f, 20f)
        assertEquals(30f, loud.low)
        assertEquals(200f, loud.high)
        assertTrue(loud.lines.size <= 7)
        assertEquals("40", GraphScale.tick(40f))
        assertEquals("0.25", GraphScale.tick(0.25f).replace(',', '.'))
        assertEquals("0.5", GraphScale.tick(0.5f).replace(',', '.'))
    }
}
