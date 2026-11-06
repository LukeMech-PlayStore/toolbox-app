package org.lukemech.lmtoolbox.features.media.usb_writer.windows

import java.util.Calendar
import org.lukemech.lmtoolbox.features.media.usb_writer.RandomSource

/**
 * A Windows installation ISO (Vista on: UDF, not a hybrid image, so not to be written as it is) made into a USB
 * drive UEFI boots from, as Rufus and WoeUSB make it: its files on a FAT32 partition, install.wim split
 * ([WimSplit]) when it's over FAT32's 4 GiB. No BIOS boot (Windows 11 needs UEFI anyway).
 */
object WindowsMedia {
    /** The CPUs UEFI boots an ISO on, by its boot loader (efi/boot/boot<arch>.efi): nothing here depends on which. */
    enum class Architecture(val loader: String, val label: String) {
        X64("bootx64.efi", "x64"),
        ARM64("bootaa64.efi", "ARM64"),
        X86("bootia32.efi", "x86"),
    }

    /**
     * What's to say of an ISO: its volume's name, its files' size, whether its install.wim is split, which CPUs it
     * boots on (none: it has no UEFI boot loader, so it won't boot from the drive).
     */
    class Inspection(
        val udf: Udf, val label: String, val filesSize: Long, val splitsWim: Boolean, val architectures: List<Architecture>,
    )

    /** [iso] as Windows' installation media, or null when it isn't (no UDF, or not Windows' files on it). */
    fun inspect(iso: RandomSource): Inspection? {
        val udf = Udf.open(iso) ?: return null
        if (udf.root.find("sources/boot.wim") == null) return null
        val files = udf.root.walk().filter { !it.isDirectory }
        val architectures = Architecture.entries.filter { udf.root.find("efi/boot/${it.loader}")?.isDirectory == false }
        return Inspection(udf, udf.volumeName, files.sumOf { it.size }, (installWim(udf)?.size ?: 0) > Fat32.MAX_FILE, architectures)
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
