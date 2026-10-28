package org.lukemech.lmtoolbox.features.servers.mc_server

import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.linux.ExecResult
import org.lukemech.lmtoolbox.linux.InstallResult
import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import org.json.JSONArray
import org.json.JSONObject
import org.lukemech.lmtoolbox.BuildConfig
import org.lukemech.lmtoolbox.linux.parts.Java
import org.lukemech.lmtoolbox.helpers.deleteTree
import org.lukemech.lmtoolbox.linux.LinuxTasks

/** HTTP for the version lists and the servers' files. */
internal object Http {
    /** PaperMC's API refuses clients that don't say who they are. */
    private const val USER_AGENT ="LMToolbox/${BuildConfig.VERSION_NAME} (Android)"

    /** How many times a download's tried (a dropped connection, a server's hiccup), the next after a longer wait each. */
    const val DOWNLOAD_TRIES = 3
    val RETRY_DELAY = 3.seconds

    fun open(url: String): HttpURLConnection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        setRequestProperty("User-Agent", USER_AGENT)
        connectTimeout = 15_000
        readTimeout = 30_000
    }

    suspend fun text(url: String, accept: String? = null): String = withContext(Dispatchers.IO) {
        val c = open(url)
        accept?.let { c.setRequestProperty("Accept", it) }
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}: $url")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    suspend fun json(url: String) = JSONObject(text(url))
    suspend fun jsonArray(url: String) = JSONArray(text(url))

    /**
     * Downloads [url] to [file] (through a .part, so a half one never counts), checked against [sha1]
     * or [sha256] when given; [onProgress] gets 0..1, or null while the size isn't known. Cancellable.
     */
    suspend fun download(url: String, file: File, sha1: String? = null, sha256: String? = null, onProgress: (Float?) -> Unit = {}) {
        // A connection dropped midway ("unexpected end of stream", a stall past the read timeout), a server's 5xx: tried
        // again, from where it stopped. A try that got further isn't held against it: Forge's maven, slow, stalls every
        // few minutes, and its installer took longer than three tries from the start each
        val part = partOf(file)
        part.delete()
        var tries = 0
        while (true) {
            val had = part.length()
            try {
                return downloadOnce(url, file, sha1, sha256, onProgress)
            } catch (e: IOException) {
                if (part.length() > had) tries = 0
                if (++tries >= DOWNLOAD_TRIES || e is HttpStatus && e.code < 500) {
                    part.delete()
                    throw e
                }
                delay(RETRY_DELAY * tries)
            }
        }
    }

    private fun partOf(file: File) = File(file.parentFile, file.name + ".part")

    /** An answer other than 2xx. */
    class HttpStatus(val code: Int, url: String) : IOException("HTTP $code: $url")

    private suspend fun downloadOnce(url: String, file: File, sha1: String?, sha256: String?, onProgress: (Float?) -> Unit) =
        withContext(Dispatchers.IO) {
            val part = partOf(file)
            val from = part.length()
            val c = open(url)
            if (from > 0) c.setRequestProperty("Range", "bytes=$from-")
            try {
                // What it has isn't a start of what's there now: from the start, the next try
                if (c.responseCode == 416) {
                    part.delete()
                    throw IOException("HTTP 416: $url")
                }
                if (c.responseCode !in 200..299) throw HttpStatus(c.responseCode, url)
                // The rest of it (206), or all of it again (200: a server without ranges)
                val resumed = c.responseCode == 206
                val digest = MessageDigest.getInstance(if (sha1 != null) "SHA-1" else "SHA-256")
                if (resumed) part.inputStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        digest.update(buf, 0, n)
                    }
                }
                var done = if (resumed) from else 0L
                val total = c.contentLengthLong.takeIf { it > 0 }?.let { it + done }
                var last = -1
                c.inputStream.use { input ->
                    FileOutputStream(part, resumed).use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            done += n
                            val percent = total?.let { (done * 100 / it).toInt() } ?: -1
                            if (percent != last) {
                                last = percent
                                onProgress(total?.let { done.toFloat() / it })
                            }
                        }
                    }
                }
                val expected = sha1 ?: sha256
                val got = digest.digest().joinToString("") { "%02x".format(it) }
                if (expected != null && !expected.equals(got, ignoreCase = true)) {
                    part.delete()
                    throw IOException("${file.name}: checksum doesn't match")
                }
                if (!part.renameTo(file)) throw IOException("Couldn't save ${file.name}")
            } catch (e: Exception) {
                // A dropped connection's part kept, the next try going on from it; canceled, it goes
                if (e !is IOException) part.delete()
                throw e
            } finally {
                c.disconnect()
            }
        }

    fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")
}

/**
 * Which Minecraft versions each [ServerType] has, from its own API (Mojang's, PaperMC's, Fabric's,
 * Forge's and NeoForge's), and getting a server ready ([start]): its files downloaded, and for Forge
 * and NeoForge their installer run (it downloads the rest).
 */
object Installer {
    private const val MOJANG = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    private const val PAPER = "https://fill.papermc.io/v3/projects/paper"
    private const val FABRIC = "https://meta.fabricmc.net/v2/versions"
    private const val FORGE_PROMOS = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json"
    private const val FORGE_MAVEN = "https://maven.minecraftforge.net/net/minecraftforge/forge"
    private const val FORGE_VERSIONS = "https://files.minecraftforge.net/net/minecraftforge/forge/maven-metadata.json"
    private const val NEOFORGE_VERSIONS = "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge"
    private const val NEOFORGE_MAVEN = "https://maven.neoforged.net/releases/net/neoforged/neoforge"
    /** Minecraft 1.2.5's release: the first Mojang has a server of (Java 8 runs it, as everything up to 1.16.5). */
    private const val OLDEST = "2012-03-29"
    /**
     * Paper's versions whose server can't be made anymore: their newest builds download Minecraft's from where
     * Mojang no longer has it ("Error downloading original jar").
     */
    private val PAPER_GONE = setOf("1.12", "1.12.1")
    private val SNAPSHOT_ID = Regex("""^(\d{2})w(\d{2})[a-z]""")
    /** How many of a loader's versions are offered, newest first. */
    private const val LOADERS_SHOWN = 30
    /** Forge and NeoForge's installers download their libraries: it takes a while on a phone. */
    private const val INSTALLER_TIMEOUT_MS = 30 * 60_000L
    /**
     * Its own downloads' timeouts, as [Http.open]'s: Java's are none, so a library stalled on Forge's maven hung it until
     * [INSTALLER_TIMEOUT_MS]. Timed out, it's a library that "failed to download": the installer's run again
     */
    private val INSTALLER_NET_TIMEOUTS = listOf("-Dsun.net.client.defaultConnectTimeout=15000", "-Dsun.net.client.defaultReadTimeout=30000")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // By kind, and with snapshots or not
    private val versionCache = ConcurrentHashMap<Pair<ServerType, Boolean>, List<String>>()
    private var mojangCache: List<Pair<String, String>>? = null
    private val javaCache = ConcurrentHashMap<String, Int>()
    private var job: Job? = null

    /** Minecraft's versions (id, Mojang's type: release, snapshot, ...) that run on the subsystem's Java, newest first. */
    private suspend fun mojangVersions(): List<Pair<String, String>> = mojangCache ?: run {
        val versions = Http.json(MOJANG).getJSONArray("versions")
        (0 until versions.length()).map { versions.getJSONObject(it) }
            .filter { it.optString("releaseTime") >= OLDEST }
            .map { it.getString("id") to it.optString("type") }
            .also { mojangCache = it }
    }

    /**
     * [type]'s Minecraft versions, newest first (as Mojang orders them); [snapshots]: with Mojang's snapshots,
     * pre-releases and release candidates (those [type] has: Fabric's unstable ones). Throws when its API can't be reached.
     */
    suspend fun versions(type: ServerType, snapshots: Boolean = false): List<String> {
        versionCache[type to snapshots]?.let { return it }
        val minecraft = mojangVersions().filter { (_, kind) -> kind == "release" || (snapshots && kind == "snapshot") }.map { it.first }
        val offered: Set<String> = when (type) {
            ServerType.Vanilla -> minecraft.toSet()
            ServerType.Paper -> {
                val groups = Http.json(PAPER).getJSONObject("versions")
                groups.keys().asSequence().flatMap { g -> groups.getJSONArray(g).let { a -> (0 until a.length()).map { a.getString(it) } } }.toSet() - PAPER_GONE
            }
            ServerType.Fabric -> Http.jsonArray("$FABRIC/game").let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.filter { snapshots || it.optBoolean("stable") }.map { it.getString("version") }.toSet()
            }
            ServerType.Forge -> Http.json(FORGE_PROMOS).getJSONObject("promos").keys().asSequence()
                .map { it.substringBeforeLast('-') }.toSet()
            ServerType.NeoForge -> neoForgeVersions().keys
        }
        return minecraft.filter { it in offered }.also { versionCache[type to snapshots] = it }
    }

    /**
     * The Java version Minecraft [version] needs, as Mojang's metadata says (javaVersion: e.g. 17 for
     * 1.18, 21 for 1.20.5, 25 for 26.1); as its version goes ([javaFor]) when that can't be reached.
     */
    suspend fun javaNeeds(version: String): Int = javaCache[version] ?: runCatching {
        val entry = Http.json(MOJANG).getJSONArray("versions").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            .first { it.getString("id") == version }
        Http.json(entry.getString("url")).getJSONObject("javaVersion").getInt("majorVersion")
    }.getOrElse { javaFor(version) }.also { javaCache[version] = it }

    /** The Java version Minecraft [version] needs, as Mojang has them: 8 to 1.16.5, 16 for 1.17, 17 to 1.20.4, 21 to 1.21.11, 25 from 26.1. */
    fun javaFor(version: String): Int {
        // A snapshot's id is its year and week ("24w14a"): 16 from 20w45a, 17 from 21w37a, 21 from 24w14a
        SNAPSHOT_ID.find(version)?.destructured?.let { (y, w) ->
            val week = y.toInt() * 100 + w.toInt()
            return when {
                week < 2045 -> 8
                week < 2137 -> 16
                week < 2414 -> 17
                week < 2600 -> 21
                else -> 25
            }
        }
        val n = version.split('.', '-').mapNotNull { it.toIntOrNull() }
        val major = n.getOrNull(0) ?: return 25
        if (major >= 26) return 25
        val minor = n.getOrNull(1) ?: 0
        val patch = n.getOrNull(2) ?: 0
        return when {
            minor >= 21 || (minor == 20 && patch >= 5) -> 21
            minor >= 18 -> 17
            minor == 17 -> 16
            else -> 8
        }
    }

    /** NeoForge's versions by the Minecraft version each is for, newest last. */
    private suspend fun neoForgeVersions(): Map<String, List<String>> {
        val all = Http.json(NEOFORGE_VERSIONS).getJSONArray("versions").let { a -> (0 until a.length()).map { a.getString(it) } }
        return all.mapNotNull { v -> neoForgeMinecraft(v)?.let { it to v } }.groupBy({ it.first }, { it.second })
    }

    /**
     * The Minecraft version a NeoForge one is for: 21.1.77 is 1.21.1's, 20.4.237 1.20.4's, 21.0.x 1.21's;
     * from 2026 on, its first numbers are Minecraft's own (26.1.1.x is 26.1.1's, 26.3.0.x 26.3's). Null
     * for April Fools' and snapshot builds (0.25w14craftmine...).
     */
    internal fun neoForgeMinecraft(version: String): String? {
        val n = version.substringBefore('-').split('.').map { it.toIntOrNull() }
        val major = n.getOrNull(0) ?: return null
        val minor = n.getOrNull(1) ?: return null
        if (major == 0) return null
        return if (major < 26) {
            if (minor == 0) "1.$major" else "1.$major.$minor"
        } else {
            val patch = n.getOrNull(2) ?: 0
            if (patch == 0) "$major.$minor" else "$major.$minor.$patch"
        }
    }

    /** A version of a server's loader (Paper's build, Fabric's loader, Forge's, NeoForge's), [stable] as its project marks it (Forge: its recommended). */
    data class Loader(val version: String, val stable: Boolean)

    /**
     * [type]'s loader versions for Minecraft [version], newest first (Vanilla has none); throws when
     * its API can't be reached. Not cached: they're what tells a server is out of date.
     */
    suspend fun loaders(type: ServerType, version: String): List<Loader> {
        val v = Http.encode(version)
        val all = when (type) {
            ServerType.Vanilla -> emptyList()
            ServerType.Paper -> Http.jsonArray("$PAPER/versions/$v/builds").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
                .map { Loader(it.getInt("id").toString(), it.optString("channel") in setOf("STABLE", "RECOMMENDED")) }
                .sortedByDescending { it.version.toInt() }
            ServerType.Fabric -> Http.jsonArray("$FABRIC/loader/$v").let { a -> (0 until a.length()).map { a.getJSONObject(it).getJSONObject("loader") } }
                .map { Loader(it.getString("version"), it.optBoolean("stable")) }
            ServerType.Forge -> {
                val promos = Http.json(FORGE_PROMOS).getJSONObject("promos")
                val recommended = promos.optString("$version-recommended").ifEmpty { promos.optString("$version-latest") }
                Http.json(FORGE_VERSIONS).optJSONArray(version)?.let { a -> (0 until a.length()).map { a.getString(it).removePrefix("$version-") } }.orEmpty()
                    .reversed().map { Loader(it, it == recommended) }
            }
            ServerType.NeoForge -> neoForgeVersions()[version].orEmpty().reversed().map { Loader(it, '-' !in it) }
        }
        return all.take(LOADERS_SHOWN)
    }

    /** The one a server gets unless another is picked: the newest stable (Forge's recommended), else the newest. */
    fun preferred(loaders: List<Loader>): Loader? = loaders.firstOrNull { it.stable } ?: loaders.firstOrNull()

    /** A loader's version as shown: Paper's builds as "#130", the others' as they are. */
    fun loaderLabel(type: ServerType, loader: String) = if (type == ServerType.Paper) "#$loader" else loader

    /**
     * A server to make (or to change, see [change]): what the new-server page asks for, [jdk] the Java
     * it runs on (for Java [javaNeeds]); [loader]: its loader's version, null for the [preferred].
     */
    data class Draft(
        val name: String, val type: ServerType, val version: String, val memoryMb: Int, val jdk: Java.Jdk, val javaNeeds: Int,
        val loader: String? = null,
    )

    /** Java first, if it isn't there yet; the world backed up (a server changing version); then the server's files; Forge's and NeoForge's installer. */
    enum class Stage { InstallingJava, BackingUp, Downloading, Installing, Done, Failed }

    data class Install(
        val id: String,
        val draft: Draft,
        val stage: Stage = Stage.Downloading,
        /** 0..1 of the download, null while unknown. */
        val progress: Float? = null,
        val taskId: Long? = null,
        val error: String? = null,
        /** A server there already, changing its version or its loader's ([change]); else one being made. */
        val existing: Boolean = false,
        /** The stages it goes through, in order (its steps on the page): those of [Stage]'s it needs. */
        val stages: List<Stage> = listOf(Stage.Downloading),
    ) {
        val isActive get() = stage == Stage.InstallingJava || stage == Stage.BackingUp || stage == Stage.Downloading || stage == Stage.Installing
    }

    private val _state = MutableStateFlow<Install?>(null)
    /** The installation under way (or the last one), one at a time: a server made, or one changed. */
    val state: StateFlow<Install?> = _state.asStateFlow()

    /**
     * Makes the server: Java (its version, installed if it isn't), its folder with the EULA accepted
     * (the user did, on the new-server page), its files, then its profile (last: a folder without one
     * isn't a server). On failure nothing is left of it.
     */
    fun start(context: Context, draft: Draft) {
        if (_state.value?.isActive == true) return
        val app = context.applicationContext
        val id = Servers.newId()
        val javaThere = Java.isInstalled(app, draft.jdk)
        _state.value = Install(
            id, draft, stage = if (javaThere) Stage.Downloading else Stage.InstallingJava,
            stages = stages(draft.type, backup = false)
        )
        job = scope.launch {
            val dir = Servers.dir(app, id).apply { mkdirs() }
            try {
                if (!Java.isInstalled(app, draft.jdk)) {
                    when (Java.install(app, MinecraftServerFeature, draft.jdk)) {
                        // Not wanted: as canceled, nothing to tell
                        InstallResult.Declined -> throw kotlinx.coroutines.CancellationException()
                        InstallResult.Failed -> throw Reported("Java couldn't be installed")
                        InstallResult.Installed -> {}
                    }
                    _state.update { it?.copy(stage = Stage.Downloading, progress = null) }
                }
                File(dir, "eula.txt").writeText("# Accepted in LMToolbox: https://aka.ms/MinecraftEULA\neula=true\n")
                Servers.properties(app, id)
                    .set("motd", draft.name)
                    // Lighter than the defaults (10, 10): a phone's CPU
                    .set("view-distance", "8")
                    .set("simulation-distance", "6")
                    .set("max-players", "10")
                    .save()
                val (loader, launch) = install(app, draft, dir)
                val profile = ServerProfile(
                    id, draft.name, draft.type, draft.version, loader, draft.memoryMb, launch,
                    jdk = draft.jdk.part, javaNeeds = draft.javaNeeds
                )
                Servers.save(app, profile)
                _state.update { it?.copy(stage = Stage.Done) }
            } catch (e: Exception) {
                dir.deleteTree()
                val cancelled = e is kotlinx.coroutines.CancellationException
                _state.update { it?.copy(stage = Stage.Failed, error = if (cancelled) null else e.message ?: e.javaClass.simpleName) }
                if (!cancelled) report(e, draft.name)
            }
        }
    }

    /**
     * Changes a stopped server to Minecraft [version] (its own, for another loader) with [loader]'s
     * version (null: the [preferred]): the Java that version is made for ([Java.forNeeds]), its world backed up
     * first when Minecraft's changes ([backup]), then its files replaced as [start] gets them; its
     * profile last. On failure the server stays as it was (but what the loader's installer left).
     */
    fun change(context: Context, profile: ServerProfile, version: String, loader: String?) {
        if (_state.value?.isActive == true || ServerRunner.isRunning(profile.id)) return
        val app = context.applicationContext
        val current = Java.byPart(profile.jdk)
        val draft = Draft(profile.name, profile.type, version, profile.memoryMb, Java.forNeeds(app, profile.needs) ?: current ?: return, profile.needs, loader)
        val backup = version != profile.version && Servers.worldDirs(app, profile).isNotEmpty()
        _state.value = Install(profile.id, draft, existing = true, stages = stages(profile.type, backup))
        job = scope.launch {
            val dir = Servers.dir(app, profile.id)
            try {
                // Its Java: the one the version is made for
                val needs = if (version == profile.version) profile.needs else javaNeeds(version)
                val jdk = Java.forNeeds(app, needs) ?: throw IOException(app.getString(R.string.f_Mc_java_none, needs))
                val d = draft.copy(jdk = jdk, javaNeeds = needs)
                _state.update { it?.copy(draft = d) }
                if (!Java.isInstalled(app, jdk)) {
                    _state.update { it?.copy(stage = Stage.InstallingJava) }
                    when (Java.install(app, MinecraftServerFeature, jdk)) {
                        // Not wanted: as canceled, nothing to tell
                        InstallResult.Declined -> throw kotlinx.coroutines.CancellationException()
                        InstallResult.Failed -> throw Reported("Java couldn't be installed")
                        InstallResult.Installed -> {}
                    }
                }
                if (backup) {
                    _state.update { it?.copy(stage = Stage.BackingUp, progress = null) }
                    backup(app, profile)
                }
                _state.update { it?.copy(stage = Stage.Downloading, progress = null) }
                val (installed, launch) = install(app, d, dir)
                Servers.save(app, profile.copy(version = version, loader = installed, launch = launch, jdk = jdk.part, javaNeeds = needs))
                _state.update { it?.copy(stage = Stage.Done) }
                // The Java it ran on, if no other server does now
                if (jdk != current) ServerRunner.pruneJava(app)
            } catch (e: Exception) {
                val cancelled = e is kotlinx.coroutines.CancellationException
                _state.update { it?.copy(stage = Stage.Failed, error = if (cancelled) null else e.message ?: e.javaClass.simpleName) }
                if (!cancelled) report(e, draft.name)
            }
        }
    }

    /**
     * The stages an installation goes through, as its steps: the world's backup, the files, a loader's
     * installer (Forge's, NeoForge's). Not Java's, before them: its own question and table tell of it.
     */
    private fun stages(type: ServerType, backup: Boolean) = listOfNotNull(
        Stage.BackingUp.takeIf { backup },
        Stage.Downloading,
        Stage.Installing.takeIf { type == ServerType.Forge || type == ServerType.NeoForge },
    )

    /**
     * Copies its world's folders ([Servers.worldDirs]) to backups/<its version>-<when>/ in its folder:
     * a world opened on a newer Minecraft doesn't go back, nor do the blocks of mods a version drops.
     */
    private suspend fun backup(context: Context, profile: ServerProfile) = withContext(Dispatchers.IO) {
        val worlds = Servers.worldDirs(context, profile)
        if (worlds.isEmpty()) return@withContext
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        val to = File(Servers.dir(context, profile.id), "${Servers.BACKUPS}/${profile.version}-$stamp")
        try {
            worlds.forEach { world ->
                currentCoroutineContext().ensureActive()
                world.copyRecursively(File(to, world.name))
            }
        } catch (e: Exception) {
            to.deleteTree()
            throw e
        }
    }

    /** A failure a task told already (Java's installation, the loader's installer): the bottom bar shows it. */
    private class Reported(message: String) : IOException(message)

    /** Tells a failure in the one place errors are told, the bottom bar ([LinuxTasks.reportFailure]); unless a task did. */
    private fun report(e: Exception, label: String) {
        if (e !is Reported) LinuxTasks.reportFailure(MinecraftServerFeature, label, e.message ?: e.javaClass.simpleName, e.stackTraceToString())
    }

    /** Stops the installation (its download, or the loader's installer). */
    fun cancel() {
        _state.value?.taskId?.let(LinuxTasks::cancel)
        job?.cancel()
        _state.update { if (it?.isActive == true) it.copy(stage = Stage.Failed) else it }
    }

    /** Forgets a finished installation. */
    fun clear() {
        if (_state.value?.isActive != true) _state.value = null
    }

    private fun progress(p: Float?) = _state.update { it?.copy(progress = p) }

    /** Downloads (and installs) the server into [dir]: its loader's version, and how it's started. */
    private suspend fun install(context: Context, d: Draft, dir: File): Pair<String?, List<String>> = when (d.type) {
        ServerType.Vanilla -> {
            val entry = Http.json(MOJANG).getJSONArray("versions").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
                .firstOrNull { it.getString("id") == d.version } ?: throw IOException("No Minecraft ${d.version}")
            val server = Http.json(entry.getString("url")).getJSONObject("downloads").getJSONObject("server")
            Http.download(server.getString("url"), File(dir, "server.jar"), sha1 = server.optString("sha1").ifEmpty { null }, onProgress = ::progress)
            null to listOf("-jar", "server.jar", "nogui")
        }
        ServerType.Paper -> {
            val build = Http.json("$PAPER/versions/${Http.encode(d.version)}/builds/${d.loader ?: preferred(loaders(d.type, d.version))?.version ?: "latest"}")
            val file = build.getJSONObject("downloads").getJSONObject("server:default")
            Http.download(
                file.getString("url"), File(dir, "server.jar"),
                sha256 = file.optJSONObject("checksums")?.optString("sha256")?.ifEmpty { null }, onProgress = ::progress
            )
            build.optInt("id").toString() to listOf("-jar", "server.jar", "nogui")
        }
        ServerType.Fabric -> {
            fun JSONArray.firstStable(get: (JSONObject) -> JSONObject = { it }) =
                (0 until length()).map { get(getJSONObject(it)) }.let { all -> all.firstOrNull { it.optBoolean("stable") } ?: all.firstOrNull() }
                    ?.getString("version")
            val loader = d.loader ?: Http.jsonArray("$FABRIC/loader/${Http.encode(d.version)}").firstStable { it.getJSONObject("loader") }
                ?: throw IOException("No Fabric loader for ${d.version}")
            val installer = Http.jsonArray("$FABRIC/installer").firstStable() ?: throw IOException("No Fabric installer")
            // Fabric's launcher: it downloads Minecraft's server itself on its first start
            Http.download("$FABRIC/loader/${Http.encode(d.version)}/$loader/$installer/server/jar", File(dir, "fabric-server-launch.jar"), onProgress = ::progress)
            loader to listOf("-jar", "fabric-server-launch.jar", "nogui")
        }
        ServerType.Forge -> {
            val promos = Http.json(FORGE_PROMOS).getJSONObject("promos")
            val forge = d.loader ?: promos.optString("${d.version}-recommended").ifEmpty { promos.optString("${d.version}-latest") }
                .ifEmpty { throw IOException("No Forge for ${d.version}") }
            val full = "${d.version}-$forge"
            forge to runInstaller(context, d.jdk, dir, "$FORGE_MAVEN/$full/forge-$full-installer.jar", "net/minecraftforge/forge", full)
        }
        ServerType.NeoForge -> {
            val neo = d.loader ?: neoForgeVersions()[d.version]?.let { all -> all.lastOrNull { '-' !in it } ?: all.last() }
                ?: throw IOException("No NeoForge for ${d.version}")
            neo to runInstaller(context, d.jdk, dir, "$NEOFORGE_MAVEN/$neo/neoforge-$neo-installer.jar", "net/neoforged/neoforge", neo)
        }
    }

    /**
     * Forge's or NeoForge's installer, run in the server's folder with the subsystem's Java (it
     * downloads Minecraft's server and the libraries), then how what it made is started: as its run.sh
     * does, its argument file ([libraries]/[full]/unix_args.txt), or its shim jar (newer Forge). What
     * another version installed before (a server changed) goes: its libraries, its jars.
     */
    private suspend fun runInstaller(context: Context, jdk: Java.Jdk, dir: File, url: String, libraries: String, full: String): List<String> {
        val installer = File(dir, "installer.jar")
        Http.download(url, installer, onProgress = ::progress)
        _state.update { it?.copy(stage = Stage.Installing, progress = null) }
        // A library it couldn't download (a mirror's hiccup: "These libraries failed to download. Try again."): run
        // again, what it has already kept (checked, not fetched again)
        var tries = 0
        fun retried(r: ExecResult) = tries + 1 < Http.DOWNLOAD_TRIES && "failed to download" in r.stdout + r.stderr
        while (true) {
            val result = Java.runInBackground(
                context, MinecraftServerFeature, jdk, listOf("-jar", installer.name, "--installServer"),
                dir = dir,
                options = INSTALLER_NET_TIMEOUTS,
                label = context.getString(R.string.f_Mc_installing_loader),
                timeoutMs = INSTALLER_TIMEOUT_MS,
                // Megabytes of stdout (every library it fetches), of which only the end is read (the task's log): kept
                // whole, it ran a 16 MB heap out of memory
                maxOutput = 64 * 1024,
                onStart = { id -> _state.update { it?.copy(taskId = id) } },
                retried = ::retried,
            )
            if (LinuxTasks.isCancelled(_state.value?.taskId)) throw kotlinx.coroutines.CancellationException()
            if (result.isSuccess) break
            if (retried(result)) {
                delay(Http.RETRY_DELAY * ++tries)
                continue
            }
            throw Reported(result.stderr.lineSequence().lastOrNull { it.isNotBlank() } ?: "The installer failed")
        }
        installer.delete()
        File(dir, "installer.jar.log").delete()
        // What only a shell (run.sh, run.bat) or a GUI would need
        listOf("run.sh", "run.bat").forEach { File(dir, it).delete() }
        // Another version's, from before
        File(dir, "libraries/$libraries").listFiles()?.filter { it.name != full }?.forEach { it.deleteTree() }
        val jars = dir.listFiles { f -> f.extension == "jar" && (f.name.startsWith("forge-") || f.name.startsWith("neoforge-")) }.orEmpty()
        jars.filter { "-$full" !in it.name }.forEach { it.delete() }
        val ours = jars.filter { "-$full" in it.name }

        ours.firstOrNull { it.name.endsWith("-shim.jar") }?.let { return listOf("-jar", it.name, "nogui") }
        val args = File(dir, "libraries/$libraries/$full/unix_args.txt")
        if (args.exists()) return listOf("@" + args.relativeTo(dir).path, "nogui")
        // Older ones: a jar of their own
        ours.firstOrNull()?.let { return listOf("-jar", it.name, "nogui") }
        throw IOException("The installer made nothing to start")
    }
}
