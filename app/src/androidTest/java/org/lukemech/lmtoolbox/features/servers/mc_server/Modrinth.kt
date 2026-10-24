package org.lukemech.lmtoolbox.features.servers.mc_server

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import org.json.JSONArray

/** Plugins and mods for the servers under test, from Modrinth's API (what a user would add from there). */
object Modrinth {
    private const val API = "https://api.modrinth.com/v2"
    /** Modrinth asks clients to say who they are. */
    private const val USER_AGENT = "LMToolbox-e2e-tests (github.com/LukeMech-PlayStore/toolbox-app)"

    private fun open(url: String) = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        setRequestProperty("User-Agent", USER_AGENT)
        connectTimeout = 20_000
        readTimeout = 60_000
    }

    /**
     * [project]'s newest jar for [loader] ("paper", "fabric", "forge", "neoforge") on Minecraft [version], downloaded
     * into [dir] under its own name; null when it has none for that version.
     */
    fun download(project: String, loader: String, version: String, dir: File): File? {
        fun q(list: String) = URLEncoder.encode("[\"$list\"]", "UTF-8")
        val versions = retry { open("$API/project/$project/version?loaders=${q(loader)}&game_versions=${q(version)}").inputStream.bufferedReader().use { JSONArray(it.readText()) } }
        if (versions.length() == 0) return null
        val files = versions.getJSONObject(0).getJSONArray("files")
        val file = (0 until files.length()).map { files.getJSONObject(it) }.let { all -> all.firstOrNull { it.optBoolean("primary") } ?: all.first() }
        val target = File(dir.apply { mkdirs() }, file.getString("filename"))
        retry { open(file.getString("url")).inputStream.use { input -> target.outputStream().use { input.copyTo(it) } } }
        return target
    }

    private fun <T> retry(block: () -> T): T {
        var last: Exception? = null
        repeat(3) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                last = e
                Thread.sleep(3000L * (attempt + 1))
            }
        }
        throw last!!
    }
}
