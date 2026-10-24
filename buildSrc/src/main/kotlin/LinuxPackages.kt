import groovy.json.JsonSlurper
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import org.gradle.api.GradleException
import org.gradle.api.Project

/** Where the Linux subsystem's releases are (PRoot and the packages, built by toolbox-linux-subsystem). */
private const val LINUX_REPO = "LukeMech-PlayStore/toolbox-linux-subsystem"
private const val ASSET = "rootfs.tar.gz"
private val LINUX_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

/** A module's placeholder for an ABI its part isn't built for (see updateLinuxPackages). */
private const val ABSENT_SUFFIX = ".none.so"

/** What the app knows of the Linux packages (LinuxEnv.parts), as a native library: per ABI. */
private const val PARTS_LIB = "libparts.json.so"

private val PACKAGE_LIB = Regex("""libpackage-(.+)\.tar\.xz\.so""")

/**
 * The app's tasks for the Linux subsystem's release, the one gradle.properties pins (linuxRelease):
 * updateLinuxPackages puts it where it goes (before every build, only downloading it when it isn't there),
 * updateLinuxRelease pins the newest one. [parts]: the Play modules with a part's archive (features/<name>);
 * [prootLibsDir]: where PRoot goes (the core's module).
 */
@Suppress("unused") // app/build.gradle.kts
fun Project.registerLinuxPackageTasks(parts: List<String>, prootLibsDir: File) {
    val release = property("linuxRelease").toString()
    val appLibsDir = file("src/main/jniLibs")
    val tempFile = layout.buildDirectory.file("tmp/$ASSET").get().asFile

    /** The parts the release has for [abi] (its list says), or null without that list. */
    fun partsOf(abi: String): List<String>? = runCatching {
        @Suppress("UNCHECKED_CAST")
        ((JsonSlurper().parse(File(appLibsDir, "$abi/$PARTS_LIB")) as Map<String, Any?>)["parts"] as Map<String, Any?>).keys.toList()
    }.getOrNull()

    /** The release what's there is of, as each ABI's list says ("release": its tag); null if they differ. */
    fun installed(): String? = LINUX_ABIS.map { abi ->
        runCatching {
            @Suppress("UNCHECKED_CAST")
            (JsonSlurper().parse(File(appLibsDir, "$abi/$PARTS_LIB")) as Map<String, Any?>)["release"] as String?
        }.getOrNull()
    }.distinct().singleOrNull()

    // Each ABI's parts as its list says (one may be built for some ABIs only, e.g. jdk not for x86): every one of
    // them, and the modules' parts with no list yet
    fun requiredLibs(abi: String) = listOf("libproot.so", "libproot-loader.so", PARTS_LIB) +
        (partsOf(abi)?.filter { it in parts } ?: parts).map { "libpackage-$it.tar.xz.so" }
    fun ownLib(name: String) = name.startsWith("libproot") || name == PARTS_LIB || PACKAGE_LIB.matches(name) || name.endsWith(ABSENT_SUFFIX)
    /** Where [lib] goes: PRoot in the core's module, a part in its own, the parts' list in the app's. */
    fun dirOf(lib: String): File =
        if (lib.startsWith("libproot")) prootLibsDir
        else PACKAGE_LIB.matchEntire(lib)?.groupValues?.get(1)?.let { rootProject.file("features/$it/src/main/jniLibs") } ?: appLibsDir

    tasks.register("updateLinuxPackages") {
        group = "automation"
        description = "Puts the Linux packages and PRoot of the release gradle.properties pins (linuxRelease) into the app's and the Play modules' jniLibs, downloading it unless it's there."
        // Each part (libpackage-<name>.tar.xz.so) in its module, PRoot (with its loaders) in the core's, and what the parts
        // are (versions, sizes, what they hold: libparts.json.so, each ABI's own) in the app's jniLibs, so Play ships a
        // device only its ABI's (LinuxEnv.parts): one release has them all
        doLast {
            val filesExist = LINUX_ABIS.all { abi -> requiredLibs(abi).all { File(dirOf(it), "$abi/$it").exists() } }
            val current = if (filesExist) installed() else null
            if (current == release) {
                println("Linux packages are up to date ($release).")
            } else {
                println("Updating the Linux packages: ${current ?: "none"} -> $release")
                tempFile.parentFile.mkdirs()
                val url = "https://github.com/$LINUX_REPO/releases/download/$release/$ASSET"
                try {
                    URI(url).toURL().openStream().use { input -> tempFile.outputStream().use { input.copyTo(it) } }
                } catch (e: Exception) {
                    throw GradleException("Could not download $url (linuxRelease in gradle.properties)", e)
                }
                (LINUX_ABIS.flatMap(::requiredLibs).map(::dirOf) + appLibsDir).distinct().forEach { dir ->
                    LINUX_ABIS.forEach { abi -> File(dir, abi).listFiles { f -> ownLib(f.name) }?.forEach { it.delete() } }
                }
                project.copy {
                    from(project.tarTree(project.resources.gzip(tempFile)))
                    into(appLibsDir)
                }
                tempFile.delete()
                // Each where it belongs; a part the release has ahead of the app (its tool not in it yet: no module
                // for it) is left out
                LINUX_ABIS.forEach { abi ->
                    File(appLibsDir, abi).listFiles()?.forEach { lib ->
                        val name = PACKAGE_LIB.matchEntire(lib.name)?.groupValues?.get(1)
                        if (name != null && name !in parts) {
                            logger.warn("The release has ${lib.name}, but there's no features/$name module for it: left out")
                            lib.delete()
                            return@forEach
                        }
                        val dir = dirOf(lib.name).takeIf { it != appLibsDir } ?: return@forEach
                        val target = File(dir, "$abi/${lib.name}").apply { parentFile.mkdirs() }
                        if (!lib.renameTo(target)) throw GradleException("Could not move ${lib.name} to $target")
                    }
                }
                val missing = LINUX_ABIS.flatMap { abi -> requiredLibs(abi).map { "$abi/$it" to dirOf(it) } }
                    .filterNot { (lib, dir) -> File(dir, lib).exists() }.map { it.first }
                if (missing.isNotEmpty()) throw GradleException("$ASSET ($release) is missing: $missing")
                // Every module's part is in the release, for one ABI at least
                val released = LINUX_ABIS.flatMap { partsOf(it).orEmpty() }.toSet()
                (parts - released).takeIf { it.isNotEmpty() }?.let {
                    throw GradleException("The release has no part for features/${it.joinToString()}")
                }
            }
            // A bundle's modules with native libraries must all have the same ABIs: a module whose part isn't built
            // for one (jdk for x86) gets a placeholder there (LinuxEnv reads only the archive's name, and that ABI's
            // list doesn't have the part anyway)
            LINUX_ABIS.forEach { abi ->
                val released = partsOf(abi) ?: return@forEach
                for (name in parts) {
                    val dir = rootProject.file("features/$name/src/main/jniLibs/$abi")
                    val placeholder = File(dir, "libpackage-$name$ABSENT_SUFFIX")
                    if (name in released) {
                        placeholder.delete()
                    } else if (!placeholder.exists()) {
                        dir.mkdirs()
                        placeholder.writeText("$name isn't built for $abi (the subsystem's config.yaml): no archive\n")
                    }
                }
            }
        }
    }

    tasks.register("updateLinuxRelease") {
        group = "automation"
        description = "Pins the newest published release of the Linux subsystem in gradle.properties (linuxRelease); the next build downloads it."
        val properties = rootProject.file("gradle.properties")
        doLast {
            val connection = (URI("https://api.github.com/repos/$LINUX_REPO/releases?per_page=50").toURL().openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "application/vnd.github+json")
                // Without one, GitHub allows 60 calls an hour
                System.getenv("GITHUB_TOKEN")?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
            @Suppress("UNCHECKED_CAST")
            val newest = (JsonSlurper().parseText(connection.inputStream.bufferedReader().use { it.readText() }) as List<Map<String, Any?>>)
                .filter { it["draft"] != true && it["prerelease"] != true }
                .filter { (it["assets"] as List<Map<String, Any?>>).any { a -> a["name"] == ASSET } }
                // The API orders by the tagged commit's date, not by publishing: the newest published
                .maxByOrNull { it["published_at"] as String }
                ?.get("tag_name") as String? ?: throw GradleException("No release of $LINUX_REPO has $ASSET")
            if (newest == release) {
                println("linuxRelease is the newest already ($release).")
                return@doLast
            }
            val text = properties.readText()
            properties.writeText(text.replace(Regex("""(?m)^linuxRelease=.*$"""), "linuxRelease=$newest"))
            println("linuxRelease: $release -> $newest (gradle.properties)")
        }
    }

    tasks.named("preBuild") { dependsOn("updateLinuxPackages") }
}
