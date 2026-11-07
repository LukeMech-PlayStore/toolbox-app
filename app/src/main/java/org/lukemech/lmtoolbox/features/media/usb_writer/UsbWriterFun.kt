package org.lukemech.lmtoolbox.features.media.usb_writer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InstallDesktop
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.helpers.ResourceTile
import org.lukemech.lmtoolbox.ui.pages.features.media.usb_writer.UsbWriterPage

/**
 * Bootable USB drives from images, through OTG and without root (see [UsbDrives]): an image written as it is
 * (Linux, Raspberry Pi...), or a Windows ISO made into a drive UEFI boots from (see [UsbWriter]).
 */
object UsbWriterFeature : AppFeature {
    override val route = "usb_writer"
    override val titleStringResId: Int = R.string.fp_UsbWriter
    override val keywordsResId: Int = R.string.fk_UsbWriter
    override val descriptionResId: Int = R.string.fd_UsbWriter
    override val category = FeatureCategory.Media
    override val icon @Composable get() = Icons.Default.InstallDesktop

    /**
     * While it writes: how fast the drive's written (or read back), as the writer counts it; the phone's storage
     * (the image read from it) and CPU, as other tools' work: three in a row.
     */
    override val resourceTiles = listOf(ResourceTile.Usb, ResourceTile.Disk, ResourceTile.Cpu)

    /** Off switches of its "Open file in…" and "Share to…" entries (see SystemEntries). */
    const val S_DONKEY = "UsbWriter_!OpenIn"
    const val S_DSHKEY = "UsbWriter_!ShareTo"

    override val preferenceKeys = listOf(S_DONKEY, S_DSHKEY)

    /** An image opened or shared in from another app: straight to choosing the drive. */
    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        UsbWriterPage(arguments?.get("sharedText"))
    }
}
