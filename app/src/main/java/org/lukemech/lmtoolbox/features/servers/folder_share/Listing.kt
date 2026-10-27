package org.lukemech.lmtoolbox.features.servers.folder_share

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * A folder's page as the folder share serves it: where it is (each folder above it a link back), what's in it
 * (folders first, each file's size and date, a link to download it), a ZIP of all of it, and with uploads on, a
 * button sending files into it. For phones first; light or dark as the browser is. No Android in it (tested
 * as it is): its words come from the app's strings ([Labels]).
 */
object Listing {
    /** What's in a folder: a file or a folder, its size and when it last changed, if known. */
    data class Entry(val name: String, val folder: Boolean, val size: Long? = null, val modified: Long? = null)

    /** The page's words, in the app's language as the share started; [uploading]'s "{name}" is the file's. */
    data class Labels(
        val lang: String,
        /** The folder shared, as the first of the path's links. */
        val home: String,
        val empty: String,
        val zip: String,
        val download: String,
        val upload: String,
        val uploading: String,
        val uploaded: String,
        val uploadFailed: String,
        val footer: String,
    )

    /** The page of the folder at [path] (its names from the one shared; none: that one), with [entries] in it. */
    fun page(
        path: List<String>, entries: List<Entry>, labels: Labels, uploads: Boolean,
        zone: TimeZone = TimeZone.getDefault(), locale: Locale = Locale.getDefault()
    ): String = buildString {
        val title = path.lastOrNull() ?: labels.home
        append("<!doctype html>\n<html lang=\"").append(escape(labels.lang)).append("\">\n<head>\n")
        append("<meta charset=\"utf-8\">\n")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        append("<meta name=\"robots\" content=\"noindex, nofollow\">\n")
        append("<meta name=\"color-scheme\" content=\"light dark\">\n")
        append("<title>").append(escape(title)).append("</title>\n")
        append("<style>").append(STYLE).append("</style>\n</head>\n<body>\n")

        // Where it is: the folder shared, then each one down to this one (itself not a link)
        append("<header>\n<nav class=\"crumbs\">")
        val names = listOf(labels.home) + path
        names.forEachIndexed { i, name ->
            if (i > 0) append("<span class=\"sep\">/</span>")
            if (i == names.lastIndex) append("<b>").append(escape(name)).append("</b>")
            else append("<a href=\"").append(escape(Http.path(path.take(i), folder = true))).append("\">").append(escape(name)).append("</a>")
        }
        append("</nav>\n")
        if (entries.isNotEmpty()) {
            append("<a class=\"button\" href=\"?zip\" download>").append(ICON_ZIP).append(escape(labels.zip)).append("</a>\n")
        }
        append("</header>\n<main>\n")

        if (entries.isEmpty()) {
            append("<p class=\"empty\">").append(escape(labels.empty)).append("</p>\n")
        } else {
            append("<ul>\n")
            sorted(entries).forEach { e ->
                val href = Http.encodeSegment(e.name) + if (e.folder) "/" else ""
                append("<li><a class=\"item\" href=\"").append(escape(href)).append("\">")
                append(if (e.folder) ICON_FOLDER else ICON_FILE)
                append("<span class=\"text\"><span class=\"name\">").append(escape(e.name)).append("</span>")
                val meta = listOfNotNull(
                    e.size?.takeIf { !e.folder }?.let { size(it, locale) },
                    e.modified?.takeIf { it > 0 }?.let { date(it, zone) }
                ).joinToString(" · ")
                if (meta.isNotEmpty()) append("<span class=\"meta\">").append(escape(meta)).append("</span>")
                append("</span></a>")
                if (!e.folder) {
                    append("<a class=\"download\" href=\"").append(escape(href)).append("?download\" download title=\"")
                        .append(escape(labels.download)).append("\" aria-label=\"").append(escape(labels.download)).append("\">")
                        .append(ICON_DOWNLOAD).append("</a>")
                }
                append("</li>\n")
            }
            append("</ul>\n")
        }
        append("</main>\n")

        if (uploads) {
            // Each file PUT into this folder (its name, as the server keeps it free), how far it is as it goes
            append("<form id=\"upload\" data-uploading=\"").append(escape(labels.uploading))
                .append("\" data-uploaded=\"").append(escape(labels.uploaded))
                .append("\" data-failed=\"").append(escape(labels.uploadFailed)).append("\">\n")
            append("<label class=\"button\">").append(ICON_UPLOAD).append(escape(labels.upload))
                .append("<input id=\"files\" type=\"file\" multiple hidden></label>\n")
            append("<p id=\"status\" class=\"meta\" role=\"status\"></p>\n</form>\n")
            append("<script>").append(UPLOAD_SCRIPT).append("</script>\n")
        }
        append("<footer>").append(escape(labels.footer)).append("</footer>\n")
        append("</body>\n</html>\n")
    }

    /** Folders first, then files; each by name, as people read it (case aside). */
    fun sorted(entries: List<Entry>): List<Entry> =
        entries.sortedWith(compareBy<Entry> { !it.folder }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    /** A size as people read it: bytes, then kB, MB... (by 1000s, as Android shows them), e.g. "1.5 MB", "532 B". */
    fun size(bytes: Long, locale: Locale = Locale.getDefault()): String {
        if (bytes < 1000) return "$bytes B"
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1000 && unit < UNITS.lastIndex) {
            value /= 1000
            unit++
        }
        // Rounded to "1000 kB": a unit up
        if (value >= 999.95 && unit < UNITS.lastIndex) {
            value /= 1000
            unit++
        }
        return (if (value < 100) String.format(locale, "%.1f", value) else String.format(locale, "%.0f", value)) + " " + UNITS[unit]
    }

    /** When something changed, e.g. "2026-10-26 14:05" (the same in any language), in [zone]. */
    fun date(millis: Long, zone: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = zone }.format(Date(millis))

    /** [s] safe in HTML's text and its attributes' values. */
    fun escape(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(c)
        }
    }

    private val UNITS = listOf("B", "kB", "MB", "GB", "TB", "PB")

    // Material's icons (folder, description, download, folder_zip, upload), as small inline SVGs
    private fun icon(path: String, cls: String = "icon") =
        "<svg class=\"$cls\" viewBox=\"0 0 24 24\" aria-hidden=\"true\"><path d=\"$path\"/></svg>"

    private val ICON_FOLDER = icon("M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z", "icon folder")
    private val ICON_FILE = icon("M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z")
    private val ICON_DOWNLOAD = icon("M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z")
    private val ICON_ZIP = icon("M20 6h-8l-2-2H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-2 6h-2v2h2v2h-2v2h-2v-2h2v-2h-2v-2h2v-2h-2V8h2v2h2v2z", "icon small")
    private val ICON_UPLOAD = icon("M9 16h6v-6h4l-7-7-7 7h4zm-4 2h14v2H5z", "icon small")

    private const val STYLE = """
:root{--bg:#fafafa;--card:#fff;--text:#1c1b1f;--muted:#6b6874;--line:#e6e1e5;--accent:#4a5bd4;--folder:#e0a100}
@media (prefers-color-scheme:dark){:root{--bg:#141218;--card:#1e1c22;--text:#e6e1e5;--muted:#a19ca8;--line:#2f2c35;--accent:#b8c3ff;--folder:#f2c14e}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--text);font:16px/1.4 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
header,main,form,footer{max-width:760px;margin:0 auto;padding:0 16px}
header{display:flex;flex-wrap:wrap;align-items:center;gap:8px 12px;padding-top:16px;padding-bottom:12px}
.crumbs{flex:1 1 auto;min-width:0;font-size:20px;overflow-wrap:anywhere}
.crumbs a{color:var(--accent);text-decoration:none}
.sep{color:var(--muted);margin:0 6px}
.button{display:inline-flex;align-items:center;gap:6px;padding:8px 14px;border-radius:20px;background:var(--accent);color:var(--bg);text-decoration:none;font-size:14px;cursor:pointer}
ul{list-style:none;margin:0;padding:0;background:var(--card);border-radius:16px;overflow:hidden}
li{display:flex;align-items:center;border-top:1px solid var(--line)}
li:first-child{border-top:0}
.item{flex:1;min-width:0;display:flex;align-items:center;gap:14px;padding:12px 16px;color:inherit;text-decoration:none}
.item:hover,.download:hover{background:var(--line)}
.text{min-width:0;display:flex;flex-direction:column}
.name{overflow-wrap:anywhere}
.meta{color:var(--muted);font-size:13px}
.icon{width:28px;height:28px;flex:none;fill:var(--muted)}
.icon.folder{fill:var(--folder)}
.icon.small{width:18px;height:18px;fill:currentColor}
.download{display:flex;padding:14px 16px;color:var(--accent)}
.download .icon{fill:var(--accent);width:24px;height:24px}
.empty{color:var(--muted);text-align:center;padding:48px 0}
form{padding-top:16px}
footer{color:var(--muted);font-size:12px;padding-top:24px;padding-bottom:24px;text-align:center}
"""

    private const val UPLOAD_SCRIPT = """
(function(){
var form=document.getElementById('upload'),input=document.getElementById('files'),status=document.getElementById('status');
input.addEventListener('change',function(){
var files=Array.prototype.slice.call(input.files),i=0;
function next(){
if(i>=files.length){status.textContent=form.dataset.uploaded;location.reload();return;}
var file=files[i++],x=new XMLHttpRequest();
x.open('PUT',encodeURIComponent(file.name));
x.upload.onprogress=function(e){if(e.lengthComputable)status.textContent=form.dataset.uploading.replace('{name}',file.name)+' '+Math.floor(e.loaded*100/e.total)+'%';};
x.onload=function(){if(x.status>=200&&x.status<300)next();else status.textContent=form.dataset.failed+': '+file.name;};
x.onerror=function(){status.textContent=form.dataset.failed+': '+file.name;};
x.send(file);
}
next();
});
})();
"""
}
