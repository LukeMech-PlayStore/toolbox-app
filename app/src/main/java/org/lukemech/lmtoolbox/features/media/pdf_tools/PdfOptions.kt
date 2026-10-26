package org.lukemech.lmtoolbox.features.media.pdf_tools

/** What's done with the open PDFs: several are joined first, in their order, for any but [Merge] itself. */
enum class PdfAction { Merge, Split, Compress, Rotate, Protect, Unlock }

/** How [PdfAction.Split] splits: a file a page, a file a range typed, or the pages typed in one file. */
enum class SplitMode { Pages, Ranges, Extract }

/** What's picked for an action; each one's own choices kept while another's picked (back to it, they're there). */
data class PdfOptions(
    val action: PdfAction = PdfAction.Compress,
    val splitMode: SplitMode = SplitMode.Pages,
    /** Split's ranges, or the pages to extract, as typed (see [PageRanges]). */
    val ranges: String = "",
    val preset: Preset = Preset.Ebook,
    /** Clockwise, added to how the pages are turned. */
    val angle: Int = 90,
    /** The pages to turn, as typed; blank: all. */
    val rotatePages: String = "",
    /** What a protected file opens with. */
    val password: String = "",
) {
    /** [action], or the first there is for these files when it isn't (see [actions]). */
    fun effective(files: Int, encrypted: Boolean): PdfOptions =
        if (action in actions(files, encrypted)) this else copy(action = actions(files, encrypted).first())

    /** The pages typed for [action] (a split's, a turn's of some pages), as groups of a document of [pageCount] pages; null: none needed or not pages there. */
    fun groups(pageCount: Int): List<PageRanges.Group>? = when {
        action == PdfAction.Split && splitMode != SplitMode.Pages -> PageRanges.parse(ranges, pageCount)
        action == PdfAction.Rotate && rotatePages.isNotBlank() -> PageRanges.parse(rotatePages, pageCount)
        else -> null
    }

    /** Whether it can run on a document of [pageCount] pages: the pages typed are its pages, a password is typed. */
    fun isReady(pageCount: Int): Boolean = when (action) {
        PdfAction.Split -> splitMode == SplitMode.Pages || groups(pageCount) != null
        PdfAction.Rotate -> rotatePages.isBlank() || groups(pageCount) != null
        PdfAction.Protect -> password.isNotEmpty()
        else -> true
    }

    companion object {
        val ANGLES = listOf(90, 180, 270)

        /**
         * What can be done with [files] PDFs: merging takes several; unlocking one, [encrypted] (a password or
         * restrictions: joined ones come out without either anyway).
         */
        fun actions(files: Int, encrypted: Boolean): List<PdfAction> = PdfAction.entries.filter {
            when (it) {
                PdfAction.Merge -> files > 1
                PdfAction.Unlock -> files == 1 && encrypted
                else -> files > 0
            }
        }
    }
}

/**
 * The commands an action takes, one after another (see [PdfCommands.script]), and the pages Ghostscript goes
 * through (for its progress). What they make lands in the output folder ([plan]'s outDir), read from there.
 */
data class PdfPlan(val steps: List<List<String>>, val pages: Int)

/**
 * [o] done with [inputs] (of [pageCount] pages together) as commands: several files joined first (into [workDir]),
 * then what's made goes to [outDir] as [name].pdf, or as [name]-<pages>.pdf for each part of a split. Null when [o]
 * can't run (see [PdfOptions.isReady]). [ownerPassword]: a protected file's (see [PdfCommands.protect]).
 */
fun plan(
    inputs: List<PdfInput>, pageCount: Int, o: PdfOptions, workDir: String, outDir: String, name: String, ownerPassword: String,
): PdfPlan? {
    if (inputs.isEmpty() || !o.isReady(pageCount)) return null
    val output = "$outDir/$name.pdf"
    if (o.action == PdfAction.Merge) return PdfPlan(listOf(PdfCommands.merge(inputs, output)), pageCount)
    // Several files: one document of them first, which opens without passwords
    val single = inputs.singleOrNull()
    val source = single ?: PdfInput("$workDir/joined.pdf")
    val join = if (single == null) listOf(PdfCommands.merge(inputs, source.path)) else emptyList()
    val groups = o.groups(pageCount)
    val steps = when (o.action) {
        PdfAction.Merge -> error("Merged above")
        PdfAction.Split -> when (o.splitMode) {
            SplitMode.Pages -> listOf(PdfCommands.splitPages(source, output))
            // A range typed twice is one file
            SplitMode.Ranges -> PdfCommands.splitRanges(source, groups!!.distinct()) { "$outDir/$name-$it.pdf" }
            SplitMode.Extract -> listOf(PdfCommands.extract(source, groups!!, output))
        }
        PdfAction.Compress -> listOf(PdfCommands.compress(source, o.preset, output))
        PdfAction.Rotate -> listOf(PdfCommands.rotate(source, o.angle, groups, output))
        PdfAction.Protect -> listOf(PdfCommands.protect(source, o.password, ownerPassword, output))
        PdfAction.Unlock -> listOf(PdfCommands.unlock(source, output))
    }
    return PdfPlan(join + steps, pageCount)
}
