import java.io.File
import java.net.URI
import java.util.Properties
import org.gradle.api.GradleException
import org.gradle.api.Project

private const val BUNDLETOOL_VERSION = "1.18.3"

/**
 * installLocalTesting: the debug app ([debugApplicationId]) with its Play modules on the connected device, Play's
 * installs done by bundletool's local testing (the modules are put on the device and installed from there), then
 * started. Android Studio's "app (Play local testing)" runs it (.idea/runConfigurations); its plain "app" installs
 * every module along with the app, so there's nothing to download.
 */
@Suppress("unused") // app/build.gradle.kts
fun Project.registerInstallLocalTesting(debugApplicationId: String) = tasks.register("installLocalTesting") {
    group = "install"
    description = "Installs the debug bundle on the connected device, with its Play modules installed from it (local testing)."
    dependsOn("bundleDebug")
    val bundletool = rootProject.layout.buildDirectory.file("tools/bundletool-$BUNDLETOOL_VERSION.jar")
    val bundle = layout.buildDirectory.file("outputs/bundle/debug/app-debug.aab")
    val apks = layout.buildDirectory.file("outputs/apks/app-debug-local.apks")
    val sdkDir = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use(::load) }
        .getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME")
    // The device is -Pdevice=<serial> (or ANDROID_SERIAL), else the one Android Studio's picker chose: its
    // serial isn't passed to Gradle, only its spec (the Android App configuration's Gradle-aware Make step
    // injects these), so it's the connected device with that spec. Else the only one connected
    val requestedDevice = providers.gradleProperty("device").orElse(providers.environmentVariable("ANDROID_SERIAL"))
    val pickedApi = providers.gradleProperty("android.injected.build.api")
    val pickedCodename = providers.gradleProperty("android.injected.build.codename")
    val pickedAbis = providers.gradleProperty("android.injected.build.abi")
    doLast {
        val jar = bundletool.get().asFile
        if (!jar.exists()) {
            jar.parentFile.mkdirs()
            URI("https://github.com/google/bundletool/releases/download/$BUNDLETOOL_VERSION/bundletool-all-$BUNDLETOOL_VERSION.jar")
                .toURL().openStream().use { input -> jar.outputStream().use { input.copyTo(it) } }
        }
        val adbName = if (System.getProperty("os.name").startsWith("Windows")) "adb.exe" else "adb"
        val adb = File(sdkDir ?: throw GradleException("No Android SDK (sdk.dir or ANDROID_HOME)"), "platform-tools/$adbName").path
        // providers.exec captures the output, so it's passed on (else a failure is only "exit value 1")
        fun bundletool(vararg args: String) = providers.exec {
            commandLine(listOf(File(System.getProperty("java.home"), "bin/java").path, "-jar", jar.path) + args)
            isIgnoreExitValue = true
        }.run {
            logger.lifecycle(standardOutput.asText.get())
            if (result.get().exitValue != 0) throw GradleException("bundletool ${args.first()} failed:\n${standardError.asText.get()}")
        }
        val connected = providers.exec { commandLine(adb, "devices", "-l") }.standardOutput.asText.get().lines().drop(1)
            .map { it.trim().split(Regex("\\s+")) }.filter { it.size > 1 && it[1] == "device" }
            .map { it[0] to (it.firstOrNull { f -> f.startsWith("model:") }?.removePrefix("model:") ?: "") }
        val listing = connected.joinToString("\n") { (serial, model) -> "  $serial  $model" }
        fun prop(serial: String, name: String) =
            providers.exec { commandLine(adb, "-s", serial, "shell", "getprop", name) }.standardOutput.asText.get().trim()
        fun pickedDevice() = pickedApi.orNull?.let { api ->
            val codename = pickedCodename.orNull?.takeIf { it.isNotBlank() && it != "REL" }
            val abis = pickedAbis.orNull?.split(",")?.filter { it.isNotBlank() }.orEmpty()
            connected.map { it.first }.singleOrNull { serial ->
                (if (codename != null) prop(serial, "ro.build.version.codename") == codename
                else prop(serial, "ro.build.version.sdk") == api) &&
                    prop(serial, "ro.product.cpu.abilist").split(",").let { abis.isEmpty() || it.containsAll(abis) }
            } ?: throw GradleException(
                "No single connected device matches the one picked (API $api ${abis.joinToString()}), give -Pdevice=<serial>:\n$listing"
            )
        }
        val device = requestedDevice.orNull?.takeIf { it.isNotBlank() }?.also { serial ->
            if (connected.none { it.first == serial }) throw GradleException("Device $serial isn't connected, connected:\n$listing")
        } ?: pickedDevice() ?: when (connected.size) {
            0 -> throw GradleException("No device connected (an emulator has to be started first)")
            1 -> connected.single().first
            else -> throw GradleException("Several devices connected, pick one with -Pdevice=<serial>:\n$listing")
        }
        logger.lifecycle("Installing on $device")
        bundletool(
            "build-apks", "--bundle=${bundle.get().asFile}", "--output=${apks.get().asFile}", "--overwrite",
            "--local-testing", "--connected-device", "--device-id=$device", "--adb=$adb"
        )
        // --allow-test-only: a build from Android Studio (with the picked device's spec) is marked test-only.
        // The core module installed with the app, for real: PRoot is executed from nativeLibraryDir, and what
        // local testing installs only gets its libraries unpacked into the app's data, where Android won't
        // execute them. The other modules come from local testing, as from Play
        bundletool(
            "install-apks", "--apks=${apks.get().asFile}", "--device-id=$device", "--allow-test-only", "--adb=$adb",
            "--modules=core"
        )
        // The modules local testing installed before are SplitCompat's copies in the app's files, kept as long as
        // the version code is the same: gone, so this build's modules are downloaded again (as Play updates them with the app)
        providers.exec {
            commandLine(adb, "-s", device, "shell", "run-as", debugApplicationId, "rm", "-rf", "files/splitcompat")
            isIgnoreExitValue = true
        }.result.get()
        // And starts it, as Android Studio's Run would
        providers.exec {
            commandLine(adb, "-s", device, "shell", "monkey", "-p", debugApplicationId, "-c", "android.intent.category.LAUNCHER", "1")
        }.result.get()
    }
}
