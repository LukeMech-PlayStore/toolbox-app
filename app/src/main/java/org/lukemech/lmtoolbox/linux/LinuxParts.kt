package org.lukemech.lmtoolbox.linux

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipFile
import org.json.JSONObject
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.linux.LinuxPaths.linuxDir

/**
 * The subsystem's parts: what the app ships of them (this device's ABI's), and what's on the device, each
 * downloaded (its Play module's archive) and unpacked or not. Unpacking them is [LinuxInstaller]'s.
 */
object LinuxParts {
    private const val TAG = "LinuxEnv"
    /**
     * The part every other builds on (dash, and what several link), in its Play module of the same name
     * with PRoot: installed with the first tool on Linux, like any part.
     */
    const val CORE = "core"

    /**
     * Whether what's unpacked is what the app ships: each package unpacked so far (see
     * [outdatedParts]), nothing the app doesn't ship anymore among them; else [LinuxInstaller.install] updates what
     * isn't. Nothing unpacked (the app needs none of it; the core comes with the first tool on Linux)
     * is up to date too.
     */
    fun isUpToDate(context: Context): Boolean =
        outdatedParts(context).isEmpty() && staleExtensions(context).isEmpty()

    /**
     * Parts unpacked that the app doesn't ship anymore (e.g. qrcode and currencyconverter, merged into
     * pypkgs): [LinuxInstaller.install] deletes them; the tools on them get their new part (see LinuxTasks).
     */
    fun staleExtensions(context: Context): List<String> {
        if (!isExtensionUnpacked(context, CORE)) return emptyList()
        val shipped = parts(context).keys
        return linuxDir(context).child("etc/extensions").listFiles { f -> f.name.endsWith(".installed") }.orEmpty()
            .map { it.name.removeSuffix(".installed") }.filter { it !in shipped }
    }

    /** The packages unpacked in another version than the app ships, what they build on first (see [parts]). */
    fun outdatedParts(context: Context): List<LinuxExtension> =
        parts(context).values.filter { part ->
            unpackedVersion(context, part.name).let { it != null && it != part.version }
        }

    /** What the app ships of the subsystem, see [parts]: a native library of the app's, its ABI's alone. */
    private const val PARTS_LIB = "libparts.json.so"

    @Volatile
    private var parts: Map<String, LinuxExtension>? = null

    /**
     * Every part of the subsystem the app ships, the core first, then the extensions in the order
     * they build on each other: from the subsystem release's list for this device's ABI (the app's
     * libparts.json.so, unpacked with its native libraries).
     */
    fun parts(context: Context): Map<String, LinuxExtension> = parts ?: run {
        val text = runCatching { nativeLibDir(context).child(PARTS_LIB).readText() }
            .onFailure { Log.e(TAG, "No $PARTS_LIB", it) }.getOrNull()
        (text?.let { parseParts(it, Build.SUPPORTED_ABIS.toList()) } ?: emptyMap()).also { parts = it }
    }

    /** The core, as the app ships it; null if it ships none (see [parts]). */
    fun core(context: Context): LinuxExtension? = parts(context)[CORE]

    /**
     * The parts in [json] (the subsystem's parts.json) for the first of [abis] it has; a part
     * without any of them is left out. The core first, then each extension after what it requires
     * (JSON keeps no order), so updating them in this order brings back what they build on first.
     */
    fun parseParts(json: String, abis: List<String>): Map<String, LinuxExtension> {
        val all = runCatching { JSONObject(json).getJSONObject("parts") }.getOrNull() ?: return emptyMap()
        val found = LinkedHashMap<String, LinuxExtension>()
        for (name in all.keys().asSequence().sorted()) {
            val part = all.getJSONObject(name)
            val perAbi = part.optJSONObject("abis") ?: continue
            val abi = abis.firstOrNull { perAbi.has(it) }?.let { perAbi.getJSONObject(it) } ?: continue
            fun JSONObject.list(key: String) = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            found[name] = LinuxExtension(
                name, part.list("requires"), abi.optLong("size"), abi.list("components"),
                part.optString("version"), abi.optLong("archive")
            )
        }
        val result = LinkedHashMap<String, LinuxExtension>()
        fun add(name: String) {
            val part = found[name] ?: return
            if (name in result) return
            part.requires.forEach(::add)
            result[name] = part
        }
        add(CORE)
        found.keys.forEach(::add)
        return result
    }

    /** The extensions the app ships (see the subsystem's config.yaml), by name, in [parts]' order. */
    fun extensions(context: Context): Map<String, LinuxExtension> = parts(context) - CORE

    /**
     * Version of part [name] (the core too) as it's unpacked (maybe not the one the app ships), or null
     * if it isn't (completely: its marker is written last). Nothing is unpacked without the core.
     */
    fun unpackedVersion(context: Context, name: String): String? {
        if (name != CORE && unpackedVersion(context, CORE) == null) return null
        return extensionMarker(context, name).takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null }
    }

    /** Whether [name] is unpacked (in whichever version: an outdated one is updated, see [LinuxInstaller.install]). */
    fun isExtensionUnpacked(context: Context, name: String): Boolean = unpackedVersion(context, name) != null

    /**
     * Whether [name]'s archive is on the device (its Play module downloaded): it can be unpacked. The
     * core's module brings PRoot along, see [nativeLib].
     */
    fun isExtensionDownloaded(context: Context, name: String): Boolean = archive(context, extensionLib(name)) != null

    /** Whether [name] is ready: downloaded and unpacked. */
    fun isExtensionInstalled(context: Context, name: String): Boolean =
        isExtensionUnpacked(context, name) && isExtensionDownloaded(context, name)

    /**
     * [names] and what they build on (their requirements, through and through), what they build on first:
     * the core first of all (every part builds on it, said or not); none: just the core.
     */
    fun extensionWithRequired(context: Context, names: List<String>): List<LinuxExtension> {
        val all = parts(context)
        val order = mutableListOf<LinuxExtension>()
        fun visit(n: String) {
            val ext = all[n] ?: return
            if (order.any { it.name == n }) return
            ext.requires.forEach(::visit)
            order += ext
        }
        visit(CORE)
        names.forEach(::visit)
        return order
    }

    /**
     * What [names] need downloaded or unpacked yet, what they build on first: the core (with PRoot)
     * first, until it is; empty when they're ready. None: just the core (e.g. a command of no tool).
     */
    fun missingExtensions(context: Context, names: List<String>): List<LinuxExtension> =
        extensionWithRequired(context, names).filterNot { isExtensionInstalled(context, it.name) }

    /** Whether the app ships all of [names] for this device's ABI (one may not be built for it, e.g. jdk25 for x86). */
    fun isAvailable(context: Context, names: List<String>): Boolean = names.all { it in parts(context) }

    /**
     * Whether [feature] can run here: all its packages shipped for this device's ABI, and one of its
     * extra ones at least, if it has those (e.g. a Java version).
     */
    fun isAvailable(context: Context, feature: AppFeature): Boolean =
        isAvailable(context, feature.linuxExtensions) &&
            (feature.extraExtensions.isEmpty() || feature.extraExtensions.any { it in parts(context) })

    /**
     * The parts on the device, every one by the same rule (installed when it's downloaded and
     * unpacked, see [isExtensionInstalled]): those installed, and those only downloaded (deleted,
     * their module waiting for Play to uninstall it). One unpacked without its archive isn't
     * installed (it's unpacked afresh when it is again, see LinuxTasks): left out, as the
     * installation page counts it missing.
     */
    fun installedParts(context: Context): List<InstalledPart> =
        parts(context).values.mapNotNull { part ->
            if (!isExtensionDownloaded(context, part.name)) null
            else {
                val unpacked = unpackedVersion(context, part.name)
                InstalledPart(part, archive = true, unpacked = unpacked != null, version = unpacked ?: part.version)
            }
        }

    internal fun extensionMarker(ctx: Context, name: String): File = linuxDir(ctx).child("etc/extensions/$name.installed")

    /** In its Play module's jniLibs, see the subsystem's workflow. */
    internal fun extensionLib(name: String) = "libpackage-$name.tar.xz.so"

    /** A .tar.xz to unpack: its [size] (for the progress) and a way to read it. */
    internal class Archive(val name: String, val size: Long, val open: () -> InputStream)

    /**
     * Where Android unpacked the app's native libraries, the installed modules' too (PRoot, with the
     * core module): fresh, as installing a module can move it while the app runs.
     */
    internal fun nativeLibDir(ctx: Context): File = File(
        runCatching { ctx.packageManager.getApplicationInfo(ctx.packageName, 0).nativeLibraryDir }.getOrNull()
            ?: ctx.applicationInfo.nativeLibraryDir
    )

    /** The modules SplitCompat installed itself (local testing): their APKs, and the native libraries it unpacked. */
    internal fun splitCompatFiles(ctx: Context): List<File> =
        File(ctx.filesDir, "splitcompat").walkTopDown().filter { it.isFile }.toList()

    /**
     * Native library [lib] of an installed module (e.g. PRoot, the core's) as a file: where Android
     * unpacked it, else where SplitCompat did (local testing); null if neither did.
     */
    internal fun nativeLib(ctx: Context, lib: String): File? =
        nativeLibDir(ctx).child(lib).takeIf { it.exists() }
            ?: splitCompatFiles(ctx).firstOrNull { it.name == lib }

    /**
     * Archive [lib] (a part's): unpacked by Android with the app's libs (the modules installed),
     * or still in its module's APK, as Play installed it or as SplitCompat keeps it (local testing).
     */
    internal fun archive(ctx: Context, lib: String): Archive? {
        // Unpacked as native libraries, by Android or by SplitCompat (on some devices)
        nativeLib(ctx, lib)?.let { file ->
            return Archive(lib, file.length()) { FileInputStream(file) }
        }
        // Fresh: the installed splits change while the app runs
        val splits = runCatching { ctx.packageManager.getApplicationInfo(ctx.packageName, 0).splitSourceDirs }.getOrNull().orEmpty()
            .map(::File)
        val emulated = splitCompatFiles(ctx).filter { it.extension == "apk" }
        for (apk in splits + emulated) {
            val entry = runCatching {
                ZipFile(apk).use { zip -> Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi -> zip.getEntry("lib/$abi/$lib")?.name } }
            }.getOrNull() ?: continue
            return Archive(lib, ZipFile(apk).use { it.getEntry(entry).size }) {
                val zip = ZipFile(apk)
                // Closing the entry's stream closes the APK too
                object : FilterInputStream(zip.getInputStream(zip.getEntry(entry))) {
                    override fun close() {
                        super.close()
                        zip.close()
                    }
                }
            }
        }
        return null
    }
}
