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
    fun merged() {
        assertEquals(
            listOf("qpdf", "--empty", "--pages", "--file=/c/a.pdf", "--file=/c/b.pdf", "--password=x y", "--", "/o/out.pdf"),
            PdfCommands.merge(listOf(PdfInput("/c/a.pdf"), PdfInput("/c/b.pdf", "x y")), "/o/out.pdf")
        )
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
        assertEquals(listOf("qpdf", "--rotate=+90", "/c/in.pdf", "/o/out.pdf"), PdfCommands.rotate(doc, 90, null, "/o/out.pdf"))
        assertEquals(
            listOf("qpdf", "--rotate=+270:2,4-6", "/c/in.pdf", "/o/out.pdf"),
            PdfCommands.rotate(doc, 270, listOf(Group(2, 2), Group(4, 6)), "/o/out.pdf")
        )
        assertThrows(IllegalArgumentException::class.java) { PdfCommands.rotate(doc, 45, null, "/o/out.pdf") }
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
    fun actionsForTheFiles() {
        assertEquals(listOf(PdfAction.Split, PdfAction.Compress, PdfAction.Rotate, PdfAction.Protect), PdfOptions.actions(1, encrypted = false))
        assertEquals(PdfAction.Unlock, PdfOptions.actions(1, encrypted = true).last())
        assertEquals(PdfAction.Merge, PdfOptions.actions(2, encrypted = true).first())
        assertEquals(PdfAction.Split, PdfOptions(action = PdfAction.Merge).effective(1, encrypted = false).action)
        // Unlocking takes one file
        assertEquals(PdfAction.Merge, PdfOptions(action = PdfAction.Unlock).effective(2, encrypted = true).action)
        // No files yet (the page's intro): nothing to do with them, what's picked kept
        assertEquals(PdfAction.Merge, PdfOptions(action = PdfAction.Merge).effective(0, encrypted = false).action)
    }

    @Test
    fun readyOnceTyped() {
        assertEquals(false, PdfOptions(action = PdfAction.Split, splitMode = SplitMode.Ranges, ranges = "1-20").isReady(12))
        assertEquals(true, PdfOptions(action = PdfAction.Split, splitMode = SplitMode.Ranges, ranges = "1-2").isReady(12))
        assertEquals(true, PdfOptions(action = PdfAction.Split, ranges = "nonsense").isReady(12))
        assertEquals(true, PdfOptions(action = PdfAction.Rotate).isReady(12))
        assertEquals(false, PdfOptions(action = PdfAction.Rotate, rotatePages = "13").isReady(12))
        assertEquals(false, PdfOptions(action = PdfAction.Protect).isReady(12))
    }

    @Test
    fun planned() {
        val a = PdfInput("/c/a.pdf")
        val b = PdfInput("/c/b.pdf", "pw")
        // Several files: joined first, then compressed (the joined one has no password)
        assertEquals(
            PdfPlan(
                listOf(
                    PdfCommands.merge(listOf(a, b), "/w/joined.pdf"),
                    PdfCommands.compress(PdfInput("/w/joined.pdf"), Preset.Printer, "/o/doc.pdf"),
                ),
                7
            ),
            plan(listOf(a, b), 7, PdfOptions(action = PdfAction.Compress, preset = Preset.Printer), "/w", "/o", "doc", "owner")
        )
        assertEquals(
            listOf(PdfCommands.merge(listOf(a, b), "/o/doc.pdf")),
            plan(listOf(a, b), 7, PdfOptions(action = PdfAction.Merge), "/w", "/o", "doc", "owner")?.steps
        )
        // A range typed twice is one file
        assertEquals(
            PdfCommands.splitRanges(b, listOf(Group(1, 2), Group(3, 7))) { "/o/doc-$it.pdf" },
            plan(listOf(b), 7, PdfOptions(action = PdfAction.Split, splitMode = SplitMode.Ranges, ranges = "1-2,3-z,1-2"), "/w", "/o", "doc", "owner")?.steps
        )
        assertEquals(
            listOf(PdfCommands.protect(a, "open", "owner", "/o/doc.pdf")),
            plan(listOf(a), 3, PdfOptions(action = PdfAction.Protect, password = "open"), "/w", "/o", "doc", "owner")?.steps
        )
        assertNull(plan(listOf(a), 3, PdfOptions(action = PdfAction.Split, splitMode = SplitMode.Extract, ranges = "4"), "/w", "/o", "doc", "owner"))
        assertNull(plan(emptyList(), 0, PdfOptions(), "/w", "/o", "doc", "owner"))
    }
}
