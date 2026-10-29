package org.lukemech.lmtoolbox.features.media.pdf_tools

/**
 * A page of the arrangement made of the open files (the page grid): page [index] (from 0) of file [file], turned
 * clockwise by [rotation] (0, 90, 180, 270) more than it is. [key]: its own, as it moves (a page twice is two).
 */
data class PageRef(val key: Long, val file: Long, val index: Int, val rotation: Int = 0) {
    /** Turned by [angle] more (negative: counterclockwise). */
    fun turned(angle: Int) = copy(rotation = ((rotation + angle) % 360 + 360) % 360)
}

/** What's made of the arranged pages: one PDF, a PDF of each page or range, or a picture of each page. */
enum class PdfOutput { Pdf, Split, Images }

/** How [PdfOutput.Split] splits: a file a page, or a file a range typed. */
enum class SplitMode { Pages, Ranges }

/** What's picked to make; each output's own choices kept while another's picked (back to it, they're there). */
data class PdfOptions(
    val output: PdfOutput = PdfOutput.Pdf,
    val preset: Preset = Preset.AsIs,
    /** The PDF made opens with [password] only. */
    val protect: Boolean = false,
    val password: String = "",
    val splitMode: SplitMode = SplitMode.Pages,
    /** Split's ranges as typed (see [PageRanges]), of the pages made. */
    val ranges: String = "",
    val imageType: ImageType = ImageType.Png,
    val dpi: Int = 150,
    /** Only the pages selected in the grid, not all. */
    val selectedOnly: Boolean = false,
) {
    /** The ranges typed for a split, as groups of a document of [pageCount] pages; null: none needed or not pages there. */
    fun groups(pageCount: Int): List<PageRanges.Group>? =
        if (output == PdfOutput.Split && splitMode == SplitMode.Ranges) PageRanges.parse(ranges, pageCount) else null

    /** Whether it can be made of [pageCount] pages: some, the ranges typed of them, a password typed. */
    fun isReady(pageCount: Int): Boolean = pageCount > 0 && when (output) {
        PdfOutput.Pdf -> !protect || password.isNotEmpty()
        PdfOutput.Split -> splitMode == SplitMode.Pages || groups(pageCount) != null
        PdfOutput.Images -> true
    }

    companion object {
        val DPIS = listOf(72, 150, 300)
    }
}

/**
 * The commands it takes, one after another (see [PdfCommands.script]), and the pages Ghostscript goes through (for its
 * progress). What they make lands in the output folder ([plan]'s outDir), read from there.
 */
data class PdfPlan(val steps: List<List<String>>, val pages: Int)

/**
 * [pages] made into [o] as commands, the files read as [inputs] (by their ids): pages of several files, or moved,
 * left out or turned, are put together first (into [workDir]); one file's every page as it is is read as it is (its
 * bookmarks kept). What's made goes to [outDir] as [name].pdf, as [name]-<pages>.pdf for each part of a split, or as
 * [name]-<page>.<type> for pictures. Null when [o] can't be made (see [PdfOptions.isReady]). [ownerPassword]: a
 * protected file's (see [PdfCommands.protect]).
 */
fun plan(
    pages: List<PageRef>, inputs: Map<Long, PdfInput>, pageCounts: Map<Long, Int>, o: PdfOptions,
    workDir: String, outDir: String, name: String, ownerPassword: String,
): PdfPlan? {
    if (!o.isReady(pages.size) || pages.any { it.file !in inputs }) return null
    val steps = mutableListOf<List<String>>()
    val file = pages.first().file
    val whole = pages.all { it.file == file && it.rotation == 0 } && pages.map { it.index } == (0 until (pageCounts[file] ?: -1)).toList()
    var source = inputs.getValue(file)
    if (!whole) {
        // Each run of a file's pages one after another a part of it
        val parts = mutableListOf<Pair<Long, MutableList<Int>>>()
        for (page in pages) {
            val last = parts.lastOrNull()
            if (last != null && last.first == page.file) last.second.add(page.index + 1) else parts.add(page.file to mutableListOf(page.index + 1))
        }
        source = PdfInput("$workDir/arranged.pdf")
        steps += PdfCommands.arrange(parts.map { (id, numbers) -> inputs.getValue(id) to PageRanges.of(numbers) }, source.path)
        val turns = pages.withIndex().filter { it.value.rotation != 0 }.groupBy({ it.value.rotation }, { it.index + 1 })
        if (turns.isNotEmpty()) {
            val turned = PdfInput("$workDir/turned.pdf")
            steps += PdfCommands.rotate(source, turns, turned.path)
            source = turned
        }
    }
    val output = "$outDir/$name.pdf"
    when (o.output) {
        PdfOutput.Pdf -> {
            var current = source
            if (o.preset != Preset.AsIs) {
                val compressed = if (o.protect) "$workDir/compressed.pdf" else output
                steps += PdfCommands.compress(current, o.preset, compressed)
                current = PdfInput(compressed)
            }
            when {
                o.protect -> steps += PdfCommands.protect(current, o.password, ownerPassword, output)
                o.preset == Preset.AsIs -> steps += PdfCommands.copy(current, output)
            }
        }
        PdfOutput.Split -> steps += when (o.splitMode) {
            SplitMode.Pages -> listOf(PdfCommands.splitPages(source, output))
            // A range typed twice is one file
            SplitMode.Ranges -> PdfCommands.splitRanges(source, o.groups(pages.size)!!.distinct()) { "$outDir/$name-$it.pdf" }
        }
        PdfOutput.Images -> steps += PdfCommands.images(source, o.imageType, o.dpi, "$outDir/$name-%03d.${o.imageType.extension}")
    }
    return PdfPlan(steps, pages.size)
}
