package org.lukemech.lmtoolbox.features.media.pdf_tools

import kotlin.math.abs

/**
 * Pages as typed ("1-3,5,7-z"): groups split by commas, each a page or a range of them; a page is its
 * number, `z` (the last) or `r<n>` (the n-th from the end), as qpdf takes them. Spaces don't count.
 */
object PageRanges {
    /** Pages [first] to [last], counting down when [last] is lower (as qpdf reads "7-3"). */
    data class Group(val first: Int, val last: Int) {
        val pages get() = abs(last - first) + 1

        /** As qpdf takes it, its pages as numbers: "7-12", "5". */
        override fun toString() = if (first == last) "$first" else "$first-$last"
    }

    /** What can be typed of a selection: everything else is dropped as it's typed (no letters but `r` and `z`). */
    fun filter(text: String) = text.lowercase().filter { it.isDigit() || it in ",-rz " }

    /** [text]'s groups in a document of [pageCount] pages; null if it isn't a selection of its pages. */
    fun parse(text: String, pageCount: Int): List<Group>? {
        val groups = text.replace(" ", "").lowercase().split(',')
        if (pageCount < 1 || groups.any { it.isEmpty() }) return null
        return groups.map { group ->
            val ends = group.split('-')
            if (ends.size > 2) return null
            val first = page(ends.first(), pageCount) ?: return null
            Group(first, page(ends.last(), pageCount) ?: return null)
        }
    }

    /** All of [groups] as one qpdf range, e.g. "1-3,5,7-12". */
    fun join(groups: List<Group>) = groups.joinToString(",")

    /** How many pages [groups] take (a page in two of them twice, as qpdf copies it twice). */
    fun count(groups: List<Group>) = groups.sumOf { it.pages }

    private fun page(text: String, pageCount: Int): Int? {
        val n = when {
            text == "z" -> pageCount
            text.startsWith("r") -> text.drop(1).toIntOrNull()?.let { pageCount + 1 - it }
            else -> text.toIntOrNull()
        }
        return n?.takeIf { it in 1..pageCount }
    }
}

/** A PDF a command reads: its [path] (in the cache, under a plain name) and the [password] it opens with, if it needs one. */
data class PdfInput(val path: String, val password: String? = null)

/** What [PdfCommands.inspect] found: its [pages] (null: it can't be opened without its password, or at all), and whether it's [encrypted]. */
data class Inspection(val pages: Int?, val encrypted: Boolean, val needsPassword: Boolean)

/**
 * Ghostscript's pdfwrite settings (its -dPDFSETTINGS), from the smallest file to the most faithful, or
 * qpdf's lossless pass ([Lossless]): streams compressed again and objects packed, nothing else changed.
 */
enum class Preset(val setting: String?) {
    Screen("/screen"),
    Ebook("/ebook"),
    Printer("/printer"),
    Prepress("/prepress"),
    Lossless(null),
}

/**
 * The qpdf and Ghostscript commands the PDF tools run, as argument lists (nothing here runs them: the pdf
 * module's PdfJobs does): plain functions of what's picked, in the app for its tests (PdfCommandsTest).
 * Outputs are paths in the cache.
 */
object PdfCommands {
    /** What [inspect] prints before the page count: qpdf's --requires-password exit code. */
    private const val PASSWORD_LINE = "password="

    /**
     * Whether [input] needs a password (or the one it has is right) and its page count, in one command
     * that ends well either way: qpdf's exit codes are the answer, not a failure (the bottom bar's red
     * line). --requires-password: 0 needs one (other than given), 2 isn't encrypted, 3 opens.
     */
    fun inspect(input: PdfInput): List<String> = listOf(
        "sh", "-c", "qpdf --requires-password \"$@\"; echo $PASSWORD_LINE$?; qpdf --show-npages \"$@\" || true",
        "sh"
    ) + passwordArgs(input) + input.path

    /** [inspect]'s output read; null if it's not what it prints (qpdf missing, a file it can't read at all). */
    fun parseInspection(stdout: String): Inspection? {
        val lines = stdout.lines().map { it.trim() }
        val code = lines.firstOrNull { it.startsWith(PASSWORD_LINE) }?.removePrefix(PASSWORD_LINE)?.toIntOrNull() ?: return null
        if (code !in setOf(0, 2, 3)) return null
        val pages = lines.firstNotNullOfOrNull { it.toIntOrNull() }?.takeIf { it > 0 && code != 0 }
        // Neither locked nor counted: damaged beyond what qpdf reads
        if (code != 0 && pages == null) return null
        return Inspection(pages, encrypted = code != 2, needsPassword = code == 0)
    }

    /** [inputs] one after another, all their pages, into [output]; protected ones open with their passwords. */
    fun merge(inputs: List<PdfInput>, output: String): List<String> =
        listOf("qpdf", "--empty", "--pages") +
            inputs.flatMap { listOf("--file=${it.path}") + passwordArgs(it) } +
            listOf("--", output)

    /** The pages of [groups] (in their order) of [input], into one file, [output]. */
    fun extract(input: PdfInput, groups: List<PageRanges.Group>, output: String): List<String> =
        listOf("qpdf", "--empty", "--pages", "--file=${input.path}") + passwordArgs(input) +
            listOf("--range=${PageRanges.join(groups)}", "--", output)

    /**
     * Each page of [input] its own file: [output] with the page's number before ".pdf", zero-padded so they
     * sort ("doc-01.pdf" ... "doc-12.pdf"). Without the document's outline (as --split-pages makes them).
     */
    fun splitPages(input: PdfInput, output: String): List<String> =
        listOf("qpdf") + passwordArgs(input) + decrypt(input) + listOf("--split-pages", input.path, output)

    /** Each of [groups] its own file, named by [outputOf] (e.g. "doc-1-3.pdf"): a command each, see [script]. */
    fun splitRanges(input: PdfInput, groups: List<PageRanges.Group>, outputOf: (PageRanges.Group) -> String): List<List<String>> =
        groups.map { extract(input, listOf(it), outputOf(it)) }

    /**
     * [input]'s pages turned clockwise by [angle] (90, 180, 270; added to how they're turned already), those of
     * [groups] only, or all without; into [output].
     */
    fun rotate(input: PdfInput, angle: Int, groups: List<PageRanges.Group>?, output: String): List<String> {
        require(angle in setOf(90, 180, 270)) { "Not a quarter turn: $angle" }
        val range = groups?.let { ":${PageRanges.join(it)}" }.orEmpty()
        return listOf("qpdf") + passwordArgs(input) + decrypt(input) + listOf("--rotate=+$angle$range", input.path, output)
    }

    /**
     * [input] made smaller into [output]: by Ghostscript's pdfwrite with [preset]'s settings (pictures scaled down and
     * compressed again, fonts subset), or by qpdf alone, losslessly ([Preset.Lossless]). Pages keep how they're turned
     * (pdfwrite turns them by their text otherwise); Ghostscript prints "Page n" as it goes (see [progress]).
     */
    fun compress(input: PdfInput, preset: Preset, output: String): List<String> {
        val setting = preset.setting
            ?: return listOf("qpdf") + passwordArgs(input) + decrypt(input) +
                listOf("--object-streams=generate", "--compress-streams=y", "--recompress-flate", "--compression-level=9", input.path, output)
        return listOf(
            "gs", "-dSAFER", "-dBATCH", "-dNOPAUSE", "-sDEVICE=pdfwrite", "-dCompatibilityLevel=1.5",
            "-dPDFSETTINGS=$setting", "-dAutoRotatePages=/None", "-dDetectDuplicateImages=true"
        ) + listOfNotNull(input.password?.let { "-sPDFPassword=$it" }) + listOf("-sOutputFile=$output", input.path)
    }

    /**
     * [input] protected with [password] (AES-256), into [output]: it opens with it, nothing else asked. Its owner
     * password (what lifts restrictions, and there are none) is [ownerPassword], one no one needs to know: qpdf won't
     * take the same one twice, or none, without calling it insecure.
     */
    fun protect(input: PdfInput, password: String, ownerPassword: String, output: String): List<String> =
        listOf("qpdf") + passwordArgs(input) +
            listOf("--encrypt", "--user-password=$password", "--owner-password=$ownerPassword", "--bits=256", "--", input.path, output)

    /** [input] without its password and restrictions (as it opens: with its password, if it needs one), into [output]. */
    fun unlock(input: PdfInput, output: String): List<String> =
        listOf("qpdf") + passwordArgs(input) + listOf("--decrypt", input.path, output)

    /**
     * [steps] as one command: a single one as it is, several run one after another by sh, stopping at the first
     * that fails (e.g. the files joined, then compressed). Each argument quoted for sh: a password can be anything.
     */
    fun script(steps: List<List<String>>): List<String> {
        require(steps.isNotEmpty()) { "No steps" }
        return steps.singleOrNull() ?: listOf("sh", "-c", steps.joinToString(" && ") { step -> step.joinToString(" ") { quote(it) } })
    }

    /** [arg] as sh reads it back, whatever's in it: in single quotes, a quote in it closed, escaped and opened again. */
    fun quote(arg: String) = "'" + arg.replace("'", "'\\''") + "'"

    /**
     * How far Ghostscript is (0..1) after it printed [line]: its "Page n" of [pages] in all; null for any other
     * line (qpdf's commands take moments: they have none).
     */
    fun progress(line: String, pages: Int): Float? =
        Regex("""^Page (\d+)$""").find(line.trim())?.let { (it.groupValues[1].toFloat() / pages.coerceAtLeast(1)).coerceIn(0f, 1f) }

    private fun passwordArgs(input: PdfInput) = listOfNotNull(input.password?.let { "--password=$it" })

    /** What qpdf writes of a protected file it was given the password of comes out without it, as Ghostscript's does. */
    private fun decrypt(input: PdfInput) = if (input.password != null) listOf("--decrypt") else emptyList()
}
