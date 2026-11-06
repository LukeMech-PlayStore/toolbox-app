package org.lukemech.lmtoolbox.features.media.usb_writer.windows

import java.util.Calendar
import org.lukemech.lmtoolbox.features.media.usb_writer.RandomSource

/**
 * A Windows installation ISO (Vista on: UDF, not a hybrid image, so not to be written as it is) made into a USB
 * drive UEFI boots from, as Rufus and WoeUSB make it: its files on a FAT32 partition, install.wim split
 * ([WimSplit]) when it's over FAT32's 4 GiB. No BIOS boot (Windows 11 needs UEFI anyway).
 */
object WindowsMedia {
    /** The CPUs UEFI boots an ISO on, by its bootloader (efi/boot/boot<arch>.efi): nothing here depends on which. */
    enum class Architecture(val loader: String, val label: String) {
        X64("bootx64.efi", "x64"),
        ARM64("bootaa64.efi", "ARM64"),
        X86("bootia32.efi", "x86"),
    }

    /**
     * What's to say of an ISO: its volume's name, its files' size, whether its install.wim is split, which CPUs it
     * boots on (none: it has no UEFI bootloader, so it will not boot from the drive).
     */
    class Inspection(
        val udf: Udf, val label: String, val filesSize: Long, val splitsWim: Boolean, val architectures: List<Architecture>,
        /** The parts install.wim's split into (0: it isn't). */
        val wimParts: Int = 0,
        /**
         * A file over FAT32's 4 GiB that can't be split, so the drive can't be made (e.g. a Media Creation Tool ISO's
         * install.esd: solid, one LZMS block for all), or null.
         */
        val tooLarge: String? = null,
    )

    /** [iso] as Windows' installation media, or null when it isn't (no UDF, or not Windows' files on it). */
    fun inspect(iso: RandomSource): Inspection? {
        val udf = Udf.open(iso) ?: return null
        if (udf.root.find("sources/boot.wim") == null) return null
        val files = udf.root.walk().filter { !it.isDirectory }
        val architectures = Architecture.entries.filter { udf.root.find("efi/boot/${it.loader}")?.isDirectory == false }
        val wim = installWim(udf)?.takeIf { it.size > Fat32.MAX_FILE }
        // Laid out only (its header and lookup table read), nothing copied
        val parts = wim?.let { runCatching { WimSplit.split(it.data!!).size }.getOrDefault(0) } ?: 0
        val tooLarge = files.firstOrNull { it.size > Fat32.MAX_FILE && !(it === wim && parts > 0) }?.name
        return Inspection(udf, udf.volumeName, files.sumOf { it.size }, wim != null, architectures, parts, tooLarge)
    }

    private fun installWim(udf: Udf) = udf.root.find("sources/install.wim")?.takeIf { !it.isDirectory }

    /** Its files as a FAT32 tree, install.wim split if it must be, for a drive of [deviceSize] in [sectorSize] sectors. */
    fun plan(inspection: Inspection, deviceSize: Long, sectorSize: Int, now: Calendar = Calendar.getInstance()): Fat32.Result {
        val wim = installWim(inspection.udf)
        fun dir(entry: Udf.Entry): FatDir {
            val files = entry.children.filter { !it.isDirectory }.flatMap { file ->
                if (file === wim && file.size > Fat32.MAX_FILE) {
                    WimSplit.split(file.data!!, file.name.substringBeforeLast('.')).map { FatFile(it.name, it.content) }
                } else listOf(FatFile(file.name, file.data!!.range()))
            }
            return FatDir(entry.name, entry.children.filter { it.isDirectory }.map(::dir), files)
        }
        return Fat32.plan(deviceSize, sectorSize, inspection.label, dir(inspection.udf.root), now)
    }
}
