package org.lukemech.lmtoolbox.linux

/**
 * A package of the Linux subsystem: the core, or an extension (a feature's tools, e.g. yt-dlp and
 * FFmpeg, unpacked over the core on its first use). [size] is unpacked, [archiveSize] its archive
 * (what Play downloads of it, and what stays on the device with it), in bytes; [components]
 * what it holds ("yt-dlp 2026.8.19"); [version] the subsystem release it last changed in (see the
 * subsystem's parts.json).
 */
data class LinuxExtension(
    val name: String,
    val requires: List<String>,
    val size: Long,
    val components: List<String>,
    val version: String = "",
    val archiveSize: Long = 0
) {
    /**
     * The archive's second copy, where Play unpacks its module's native libraries, estimated a fifth
     * over (Play takes about 2.2 times the archive in all, with the APK's).
     */
    val extractedSize get() = archiveSize * 6 / 5

    /** What unpacking it takes, as estimated: the archive's second copy and what it unpacks to. */
    val unpackSize get() = extractedSize + size
}

/**
 * A part of the subsystem on the device ([LinuxParts.installedParts]): its [archive] there (its Play
 * module downloaded; the core's comes with PRoot), and whether it's [unpacked];
 * its [version] as it's unpacked (until it's updated, maybe not the one the app ships), else the app's.
 */
data class InstalledPart(
    val part: LinuxExtension,
    val archive: Boolean,
    val unpacked: Boolean,
    val version: String = part.version
) {
    /**
     * What it takes on the device, as the installation page estimates it: its archive, twice (in its
     * module's APK, and the copy Play unpacks, see [LinuxExtension.extractedSize]), and what's unpacked.
     */
    val size get() = (if (archive) part.archiveSize + part.extractedSize else 0) + (if (unpacked) part.size else 0)
}

/** Result of a command run inside the Linux environment. */
data class ExecResult(val exitCode: Int, val stdout: String, val stderr: String) {
    val isSuccess get() = exitCode == 0

    /**
     * Not run: the subsystem is still being unpacked or updated (see LinuxTasks.envNotReady,
     * which takes the user back from the page meanwhile).
     */
    val isEnvNotReady get() = exitCode == EXIT_ENV_NOT_READY

    /** Not run: the feature's tools aren't unpacked yet, and weren't to be (see [LinuxTasks.run]). */
    val isExtensionMissing get() = exitCode == EXIT_EXTENSION_MISSING

    /** stdout followed by stderr, for logs and error messages. */
    val output get() = listOf(stdout, stderr).filter { it.isNotBlank() }.joinToString("\n").trim()

    companion object {
        const val EXIT_ENV_NOT_READY = -100
        const val EXIT_EXTENSION_MISSING = -101
    }
}
