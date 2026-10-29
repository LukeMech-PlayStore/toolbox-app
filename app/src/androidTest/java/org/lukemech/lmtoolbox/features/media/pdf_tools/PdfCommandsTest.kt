package org.lukemech.lmtoolbox.features.media.pdf_tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.lukemech.lmtoolbox.features.media.pdf_tools.PageRanges.Group

/**
 * The page ranges typed and the qpdf and Ghostscript commands made of what's picked (see PdfCommands, PdfOptions):
 * what the tool runs, checked without running it (PdfToolsTest goes through the tool).
 */
class PdfCommandsTest {
    private val doc = PdfInput("/c/in.pdf")
    private val locked = PdfInput("/c/in.pdf", "s3cret")

    @Test
    fun rangesAsTyped() {
        assertEquals(listOf(Group(1, 3), Group(5, 5), Group(7, 12)), PageRanges.parse("1-3,5,7-z", 12))
        // The last pages counted from the end, spaces and capitals as they come
        assertEquals(listOf(Group(10, 12), Group(12, 1)), PageRanges.parse(" r3 - R1 , Z-1", 12))
        assertEquals("1-3,5,7-12", PageRanges.join(PageRanges.parse("1-3,5,7-z", 12)!!))
        assertEquals(12, PageRanges.count(PageRanges.parse("z-1", 12)!!))
        // Pages in their order as ranges: a run upwards one
        assertEquals(listOf(Group(1, 3), Group(5, 5), Group(4, 4), Group(7, 8)), PageRanges.of(listOf(1, 2, 3, 5, 4, 7, 8)))
    }

    @Test
    fun rangesNotOfItsPages() {
        listOf("", "0", "13", "1-13", "r13", "1,,2", "1-2-3", "a", "1-", "-2", "x2").forEach {
            assertNull("\"$it\" taken", PageRanges.parse(it, 12))
        }
        assertNull(PageRanges.parse("1", 0))
    }

    @Test
    fun rangesTypedWithoutLetters() {
        assertEquals("1-3,5,7-z r2", PageRanges.filter("1-3,5,7-Z r2"))
        assertEquals("12", PageRanges.filter("a1b2:odd"))
    }

    @Test
    fun inspectedInOneCommand() {
        assertEquals(
            listOf(
                "sh", "-c", "qpdf --requires-password \"$@\"; echo password=$?; qpdf --show-npages \"$@\" || true",
                "sh", "--password=s3cret", "/c/in.pdf"
            ),
            PdfCommands.inspect(locked)
        )
        assertEquals(Inspection(12, encrypted = false, needsPassword = false), PdfCommands.parseInspection("password=2\n12\n"))
        assertEquals(Inspection(3, encrypted = true, needsPassword = false), PdfCommands.parseInspection("password=3\n3"))
        assertEquals(Inspection(null, encrypted = true, needsPassword = true), PdfCommands.parseInspection("password=0\n"))
        // No qpdf, or nothing it reads
        assertNull(PdfCommands.parseInspection("sh: qpdf: not found\npassword=127"))
        assertNull(PdfCommands.parseInspection("password=2\n"))
        assertNull(PdfCommands.parseInspection(""))
    }

    @Test
    fun arranged() {
        assertEquals(
            listOf(
                "qpdf", "--empty", "--pages", "--file=/c/a.pdf", "--range=1-3", "--file=/c/b.pdf", "--password=x y", "--range=2",
                "--file=/c/a.pdf", "--range=5", "--", "/o/out.pdf"
            ),
            PdfCommands.arrange(
                listOf(
                    PdfInput("/c/a.pdf") to listOf(Group(1, 3)), PdfInput("/c/b.pdf", "x y") to listOf(Group(2, 2)),
                    PdfInput("/c/a.pdf") to listOf(Group(5, 5)),
                ),
                "/o/out.pdf"
            )
        )
        assertEquals(listOf("qpdf", "/c/in.pdf", "/o/out.pdf"), PdfCommands.copy(doc, "/o/out.pdf"))
    }

    @Test
    fun split() {
        assertEquals(listOf("qpdf", "--split-pages", "/c/in.pdf", "/o/doc.pdf"), PdfCommands.splitPages(doc, "/o/doc.pdf"))
        assertEquals(
            listOf("qpdf", "--password=s3cret", "--decrypt", "--split-pages", "/c/in.pdf", "/o/doc.pdf"),
            PdfCommands.splitPages(locked, "/o/doc.pdf")
        )
        assertEquals(
            listOf("qpdf", "--empty", "--pages", "--file=/c/in.pdf", "--range=2-4,9", "--", "/o/doc.pdf"),
            PdfCommands.extract(doc, listOf(Group(2, 4), Group(9, 9)), "/o/doc.pdf")
        )
        assertEquals(
            listOf(
                listOf("qpdf", "--empty", "--pages", "--file=/c/in.pdf", "--password=s3cret", "--range=1-3", "--", "/o/doc-1-3.pdf"),
                listOf("qpdf", "--empty", "--pages", "--file=/c/in.pdf", "--password=s3cret", "--range=5", "--", "/o/doc-5.pdf"),
            ),
            PdfCommands.splitRanges(locked, listOf(Group(1, 3), Group(5, 5))) { "/o/doc-$it.pdf" }
        )
    }

    @Test
    fun compressed() {
        assertEquals(
            listOf(
                "gs", "-dSAFER", "-dBATCH", "-dNOPAUSE", "-sDEVICE=pdfwrite", "-dCompatibilityLevel=1.5", "-dPDFSETTINGS=/ebook",
                "-dAutoRotatePages=/None", "-dDetectDuplicateImages=true", "-sPDFPassword=s3cret", "-sOutputFile=/o/out.pdf", "/c/in.pdf"
            ),
            PdfCommands.compress(locked, Preset.Ebook, "/o/out.pdf")
        )
        assertEquals("-dPDFSETTINGS=/screen", PdfCommands.compress(doc, Preset.Screen, "/o/out.pdf")[6])
        assertEquals(
            listOf(
                "qpdf", "--object-streams=generate", "--compress-streams=y", "--recompress-flate", "--compression-level=9",
                "/c/in.pdf", "/o/out.pdf"
            ),
            PdfCommands.compress(doc, Preset.Lossless, "/o/out.pdf")
        )
    }

    @Test
    fun rotated() {
        assertEquals(
            listOf("qpdf", "--rotate=+90:1", "--rotate=+270:2,4-6", "/c/in.pdf", "/o/out.pdf"),
            PdfCommands.rotate(doc, mapOf(90 to listOf(1), 270 to listOf(2, 4, 5, 6)), "/o/out.pdf")
        )
        assertThrows(IllegalArgumentException::class.java) { PdfCommands.rotate(doc, mapOf(45 to listOf(1)), "/o/out.pdf") }
    }

    @Test
    fun pictures() {
        assertEquals(
            listOf(
                "gs", "-dSAFER", "-dBATCH", "-dNOPAUSE", "-sDEVICE=jpeg", "-dJPEGQ=90", "-r150", "-dTextAlphaBits=4",
                "-dGraphicsAlphaBits=4", "-sOutputFile=/o/doc-%03d.jpg", "/c/in.pdf"
            ),
            PdfCommands.images(doc, ImageType.Jpg, 150, "/o/doc-%03d.jpg")
        )
        assertEquals("-sDEVICE=png16m", PdfCommands.images(doc, ImageType.Png, 72, "/o/doc-%03d.png")[4])
    }

    @Test
    fun passwords() {
        assertEquals(
            listOf("qpdf", "--encrypt", "--user-password=open", "--owner-password=owner", "--bits=256", "--", "/c/in.pdf", "/o/out.pdf"),
            PdfCommands.protect(doc, "open", "owner", "/o/out.pdf")
        )
        assertEquals(listOf("qpdf", "--password=s3cret", "--decrypt", "/c/in.pdf", "/o/out.pdf"), PdfCommands.unlock(locked, "/o/out.pdf"))
    }

    @Test
    fun stepsInOneCommand() {
        val one = listOf("qpdf", "--decrypt", "/c/in.pdf", "/o/out.pdf")
        assertEquals(one, PdfCommands.script(listOf(one)))
        assertEquals(
            listOf("sh", "-c", "'qpdf' '--password=it'\\''s' 'a.pdf' && 'gs' 'b.pdf'"),
            PdfCommands.script(listOf(listOf("qpdf", "--password=it's", "a.pdf"), listOf("gs", "b.pdf")))
        )
    }

    @Test
    fun ghostscriptsPages() {
        assertEquals(0.25f, PdfCommands.progress("Page 1", 4))
        assertEquals(1f, PdfCommands.progress("Page 9", 4))
        assertNull(PdfCommands.progress("Processing pages 1 through 4.", 4))
    }

    @Test
    fun pagesTurned() {
        val page = PageRef(1, 1, 0)
        assertEquals(270, page.turned(-90).rotation)
        assertEquals(0, page.turned(90).turned(270).rotation)
        assertEquals(180, page.turned(540).rotation)
    }

    @Test
    fun readyOnceTyped() {
        assertEquals(false, PdfOptions(output = PdfOutput.Split, splitMode = SplitMode.Ranges, ranges = "1-20").isReady(12))
        assertEquals(true, PdfOptions(output = PdfOutput.Split, splitMode = SplitMode.Ranges, ranges = "1-2").isReady(12))
        assertEquals(true, PdfOptions(output = PdfOutput.Split, ranges = "nonsense").isReady(12))
        assertEquals(false, PdfOptions(protect = true).isReady(12))
        assertEquals(true, PdfOptions(protect = true, password = "x").isReady(12))
        // No pages: nothing to make
        assertEquals(false, PdfOptions().isReady(0))
    }

    @Test
    fun planned() {
        val inputs = mapOf(1L to PdfInput("/c/a.pdf"), 2L to PdfInput("/c/b.pdf"))
        val counts = mapOf(1L to 3, 2L to 4)
        val a = (0..2).map { PageRef(it + 1L, 1, it) }
        val b = (0..3).map { PageRef(it + 10L, 2, it) }
        fun plan(pages: List<PageRef>, o: PdfOptions) = plan(pages, inputs, counts, o, "/w", "/o", "doc", "owner")

        // One file's every page as it is: read as it is
        assertEquals(PdfPlan(listOf(PdfCommands.copy(inputs.getValue(1), "/o/doc.pdf")), 3), plan(a, PdfOptions()))
        // Two files, a page of the first moved after the second's and turned: put together, turned, then compressed
        val arranged = PdfInput("/w/arranged.pdf")
        val turned = PdfInput("/w/turned.pdf")
        assertEquals(
            listOf(
                PdfCommands.arrange(
                    listOf(inputs.getValue(1) to listOf(Group(2, 3)), inputs.getValue(2) to listOf(Group(1, 4)), inputs.getValue(1) to listOf(Group(1, 1))),
                    arranged.path
                ),
                PdfCommands.rotate(arranged, mapOf(90 to listOf(7)), turned.path),
                PdfCommands.compress(turned, Preset.Printer, "/w/compressed.pdf"),
                PdfCommands.protect(PdfInput("/w/compressed.pdf"), "open", "owner", "/o/doc.pdf"),
            ),
            plan(a.drop(1) + b + a[0].turned(90), PdfOptions(preset = Preset.Printer, protect = true, password = "open"))?.steps
        )
        // A page left out: put together too; a range typed twice is one file
        assertEquals(
            listOf(
                PdfCommands.arrange(listOf(inputs.getValue(2) to listOf(Group(1, 3))), arranged.path),
            ) + PdfCommands.splitRanges(arranged, listOf(Group(1, 2), Group(3, 3))) { "/o/doc-$it.pdf" },
            plan(b.take(3), PdfOptions(output = PdfOutput.Split, splitMode = SplitMode.Ranges, ranges = "1-2,3-z,1-2"))?.steps
        )
        assertEquals(
            listOf(PdfCommands.images(inputs.getValue(2), ImageType.Png, 300, "/o/doc-%03d.png")),
            plan(b, PdfOptions(output = PdfOutput.Images, dpi = 300))?.steps
        )
        assertNull(plan(a, PdfOptions(output = PdfOutput.Split, splitMode = SplitMode.Ranges, ranges = "4")))
        assertNull(plan(emptyList(), PdfOptions()))
        // A page of a file not open
        assertNull(plan(listOf(PageRef(99, 3, 0)), PdfOptions()))
    }
}
