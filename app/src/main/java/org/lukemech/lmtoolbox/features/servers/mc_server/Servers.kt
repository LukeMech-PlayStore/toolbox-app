package org.lukemech.lmtoolbox.features.servers.mc_server

import android.content.Context
import java.io.File
import java.security.SecureRandom
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
import org.lukemech.lmtoolbox.linux.parts.Java
import org.lukemech.lmtoolbox.linux.LinuxScripts
import org.lukemech.lmtoolbox.helpers.PreferenceHelper
import org.lukemech.lmtoolbox.helpers.deleteTree

/** What a server runs: Mojang's own, or with a mod loader or plugins. [addons]: the folder its mods or plugins go to. */
enum class ServerType(val label: String, val addons: String?) {
    Vanilla("Vanilla", null),
    Paper("Paper", "plugins"),
    Fabric("Fabric", "mods"),
    Forge("Forge", "mods"),
    NeoForge("NeoForge", "mods");

    /** Its mods (loaders: Fabric, Forge, NeoForge), else plugins (Paper). */
    val hasMods get() = addons == "mods"
}

/**
 * A server: its folder (its world, settings, mods) and how it's started. [launch]: what follows
 * Java's own options, relative to its folder (e.g. "-jar", "server.jar", "nogui").
 */
data class ServerProfile(
    val id: String,
    val name: String,
    val type: ServerType,
    val version: String,
    /** The loader's (or Paper's build) version, if it has one. */
    val loader: String?,
    val memoryMb: Int,
    val launch: List<String>,
    val created: Long = System.currentTimeMillis(),
    /** The hostname players connect to through the Cloudflare tunnel (with Modflared), e.g. mc.example.com. */
    val tunnelHostname: String = "",
    /** Its Cloudflare tunnel is turned on (cloudflared installed for it, see `Tunnel.install`): its settings shown, it starts along with the server once its token is in. */
    val tunnelEnabled: Boolean = false,
    /** Its bore tunnel is turned on (bore installed for it, see `Bore.install`): it starts along with the server. */
    val boreEnabled: Boolean = false,
    /** The bore server it connects to; empty: `bore.pub` (Bore.SERVER). */
    val boreServer: String = "",
    /** The bore server's port it had last, asked for again (the same address); 0: any. */
    val borePort: Int = 0,
    /** The Java it was made with (a part of [Java.JDKS]); it runs on the one its version is made for ([Java.forNeeds]). */
    val jdk: String? = null,
    /** The Java version its Minecraft needs (Mojang's metadata, see Installer.javaNeeds); null: as its version goes. */
    val javaNeeds: Int? = null,
) {
    /** The Java version it needs, as known. */
    val needs get() = javaNeeds ?: Installer.javaFor(version)

    /** e.g. "Fabric 26.3 (0.17.2)", "Paper 1.21.10 (#130)" */
    val description get() = "${type.label} $version" + (loader?.let { " (${Installer.loaderLabel(type, it)})" } ?: "")

    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("type", type.name).put("version", version).put("loader", loader)
        .put("memoryMb", memoryMb).put("launch", JSONArray(launch)).put("created", created)
        .put("tunnelHostname", tunnelHostname).put("tunnelEnabled", tunnelEnabled)
        .put("boreEnabled", boreEnabled).put("boreServer", boreServer).put("borePort", borePort)
        .put("jdk", jdk).put("javaNeeds", javaNeeds)

    companion object {
        fun fromJson(id: String, o: JSONObject): ServerProfile? = runCatching {
            ServerProfile(
                id = id,
                name = o.getString("name"),
                type = ServerType.valueOf(o.getString("type")),
                version = o.getString("version"),
                loader = o.optString("loader").takeIf { o.has("loader") && !o.isNull("loader") && it.isNotEmpty() },
                memoryMb = o.optInt("memoryMb", Memory.default()),
                launch = o.getJSONArray("launch").let { a -> (0 until a.length()).map { a.getString(it) } },
                created = o.optLong("created"),
                tunnelHostname = o.optString("tunnelHostname"),
                // Before it could be turned off: on where it was set up
                tunnelEnabled = o.optBoolean("tunnelEnabled", o.optString("tunnelHostname").isNotEmpty()),
                boreEnabled = o.optBoolean("boreEnabled"),
                boreServer = o.optString("boreServer"),
                borePort = o.optInt("borePort"),
                jdk = o.optString("jdk").takeIf { o.has("jdk") && !o.isNull("jdk") && it.isNotEmpty() },
                javaNeeds = o.optInt("javaNeeds").takeIf { o.has("javaNeeds") && !o.isNull("javaNeeds") && it > 0 },
            )
        }.getOrNull()
    }
}

/**
 * The servers, each in a folder of its own in the tool's folder of the Linux subsystem (the same
 * path inside it, so Java runs there): its files as the server keeps them, and ours next to them
 * (the profile, the tunnel's token, bore's secret), which aren't exported.
 */
object Servers {
    /** The profile; a folder without it (an installation that didn't finish) isn't a server. */
    private const val PROFILE = "lmtoolbox.json"
    /** The Cloudflare tunnel's token (see Tunnel.tokenFile). */
    const val TOKEN = ".tunnel-token"
    /** Its own bore server's secret (see Bore.secretFile). */
    const val BORE_SECRET = ".bore-secret"
    /** Java's process id while it runs. */
    private const val PID = ".pid"
    /** Its world's seed, as the server told it (see ServerRunner): known while it's stopped too. */
    private const val SEED = ".seed"
    /** Its world's copies, made as its Minecraft version changes (see `Installer.change`). */
    const val BACKUPS = "backups"
    /** Ours, left out of exports. */
    val OWN_FILES = setOf(PROFILE, TOKEN, BORE_SECRET, PID, SEED)

    private fun root(context: Context) = File(LinuxScripts.scriptDir(context, "Minecraft"), "servers")

    fun dir(context: Context, id: String) = File(root(context), id)

    /** Oldest first. */
    fun list(context: Context): List<ServerProfile> =
        root(context).listFiles { f -> f.isDirectory }.orEmpty()
            .mapNotNull { load(context, it.name) }
            .sortedBy { it.created }

    fun load(context: Context, id: String): ServerProfile? {
        val file = File(dir(context, id), PROFILE).takeIf { it.exists() } ?: return null
        return runCatching { ServerProfile.fromJson(id, JSONObject(file.readText())) }.getOrNull()
    }

    fun save(context: Context, profile: ServerProfile) {
        val dir = dir(context, profile.id).apply { mkdirs() }
        // Whole or not at all: a half-written profile would lose the server
        val tmp = File(dir, "$PROFILE.tmp")
        tmp.writeText(profile.toJson().toString(2))
        tmp.renameTo(File(dir, PROFILE))
    }

    /** A new server's folder name: short, unique, nothing the user typed. */
    fun newId(): String = "s" + System.currentTimeMillis().toString(36)

    /** Deletes the server: running, it's stopped first (its folder goes once it has, see [ServerRunner.kill]); its Java with it if no other server runs on it. */
    fun delete(context: Context, id: String) {
        // Its profile first: it's not a server anymore, whatever's left until it stops
        File(dir(context, id), PROFILE).delete()
        if (ServerRunner.isRunning(id)) ServerRunner.kill(delete = true) else dir(context, id).deleteTree()
        if (PreferenceHelper.get(context, MinecraftServerFeature.S_SELECTED, "") == id) PreferenceHelper.remove(context, MinecraftServerFeature.S_SELECTED)
        ServerRunner.pruneJava(context)
        ServerRunner.pruneTunnel(context)
        ServerRunner.pruneBore(context)
    }

    /** Its Cloudflare tunnel's token, next to its files (see ServerRunner.tunnelOf). */
    fun tokenFile(context: Context, id: String) = File(dir(context, id), TOKEN)

    /** Its bore server's secret, next to its files (see ServerRunner.boreOf). */
    fun boreSecretFile(context: Context, id: String) = File(dir(context, id), BORE_SECRET)

    fun pidFile(context: Context, id: String) = File(dir(context, id), PID)

    /** Its world's seed, once the server has told it; null before (or when its world was replaced since). */
    fun seed(context: Context, profile: ServerProfile): String? =
        File(dir(context, profile.id), SEED).takeIf { worldDirs(context, profile).isNotEmpty() }
            ?.let { runCatching { it.readText().trim() }.getOrNull() }?.ifEmpty { null }

    fun setSeed(context: Context, id: String, seed: String?) {
        val file = File(dir(context, id), SEED)
        if (seed == null) file.delete() else file.writeText(seed)
    }

    fun properties(context: Context, id: String) = ServerProperties(File(dir(context, id), "server.properties"))

    /**
     * The folders of its world: level-name's, and on Paper the Nether's and the End's next to it (it
     * keeps those apart, as Bukkit did).
     */
    fun worldDirs(context: Context, profile: ServerProfile): List<File> {
        val dir = dir(context, profile.id)
        val level = properties(context, profile.id).get("level-name")?.ifBlank { null } ?: "world"
        val names = if (profile.type == ServerType.Paper) listOf(level, "${level}_nether", "${level}_the_end") else listOf(level)
        return names.map { File(dir, it) }.filter { it.isDirectory }
    }

    /** A password for the server's RCON (the app's console of it), which listens on the phone alone. */
    fun newPassword(): String {
        val chars = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return (1..24).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }
}

/**
 * A server.properties, kept as it's written (comments, order) with the values changed in place;
 * keys it doesn't have yet are added at its end. Values as Java's Properties escapes them.
 */
class ServerProperties(private val file: File) {
    private val lines: MutableList<String> = if (file.exists()) file.readLines().toMutableList() else mutableListOf()

    private fun keyOf(line: String): String? {
        val t = line.trimStart()
        if (t.isEmpty() || t.startsWith("#") || t.startsWith("!")) return null
        val end = t.indexOfFirst { it == '=' || it == ':' }.takeIf { it >= 0 } ?: t.length
        return unescape(t.substring(0, end).trim())
    }

    private fun valueOf(line: String): String {
        val t = line.trimStart()
        val end = t.indexOfFirst { it == '=' || it == ':' }.takeIf { it >= 0 } ?: return ""
        return unescape(t.substring(end + 1).trimStart())
    }

    /** Every key with its value, in the file's order. */
    val all: List<Pair<String, String>> get() = lines.mapNotNull { line -> keyOf(line)?.let { it to valueOf(line) } }

    fun get(key: String): String? = lines.firstOrNull { keyOf(it) == key }?.let(::valueOf)

    fun set(key: String, value: String): ServerProperties {
        val line = "${escape(key, true)}=${escape(value.replace('\n', ' '), false)}"
        val i = lines.indexOfFirst { keyOf(it) == key }
        if (i >= 0) lines[i] = line else lines += line
        return this
    }

    fun save() {
        file.parentFile?.mkdirs()
        file.writeText(lines.joinToString("\n", postfix = "\n"))
    }

    private fun escape(s: String, key: Boolean) = buildString {
        s.forEachIndexed { i, c ->
            when {
                c == '\\' -> append("\\\\")
                key && (c == '=' || c == ':' || c == ' ') -> append('\\').append(c)
                // Leading spaces would be dropped
                !key && i == 0 && c == ' ' -> append("\\ ")
                // Beyond ASCII as \uXXXX: older servers read the file as ISO-8859-1 (Properties.load), not UTF-8
                c.code > 0x7E -> append("\\u%04X".format(c.code))
                else -> append(c)
            }
        }
    }

    private fun unescape(s: String) = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'u' -> {
                        val code = if (i + 6 <= s.length) s.substring(i + 2, i + 6).toIntOrNull(16) else null
                        if (code != null) {
                            append(code.toChar())
                            i += 6
                            continue
                        }
                        append(n)
                    }
                    'n' -> append('\n')
                    't' -> append('\t')
                    else -> append(n)
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}

/** How much memory a server may take: Java's heap (-Xmx), in MB, in steps of [STEP]. */
object Memory {
    const val MIN = 512
    const val STEP = 256

    /** At most what Java should ask for on the phone (see Java.maxHeapMb). */
    fun max(context: Context?): Int = ((context?.let(Java::maxHeapMb) ?: 2048) / STEP * STEP).coerceAtLeast(MIN)

    /** A new server's: 40% of what it may take (a quarter of the phone's memory), 1-4 GB; on the slider's scale ([snap]). */
    fun default(context: Context? = null): Int =
        snap((max(context) * 4 / 10).coerceIn(1024, 4096), max(context))

    /** [mb] as the slider's nearest mark: [MIN] and steps of [STEP] from it, up to [max]. */
    fun snap(mb: Int, max: Int): Int =
        (MIN + ((mb - MIN).toFloat() / STEP).roundToInt() * STEP).coerceIn(MIN, max.coerceAtLeast(MIN))
}
