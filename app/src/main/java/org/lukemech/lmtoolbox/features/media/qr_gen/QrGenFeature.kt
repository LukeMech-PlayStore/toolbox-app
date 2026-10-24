package org.lukemech.lmtoolbox.features.media.qr_gen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.helpers.ModuleFeature

/** QR generator: its pages are in the pypkgs module (features/pypkgs, its media/qr_gen). */
object QrGenFeature : ModuleFeature(
    route = "qr_gen",
    titleStringResId = R.string.fp_QREncode,
    keywordsResId = R.string.fk_QREncode,
    descriptionResId = R.string.fd_QREncode,
    category = FeatureCategory.Media,
    moduleIcon = Icons.Default.QrCode2,
    linuxExtensions = listOf("pypkgs"),
    uiClass = "org.lukemech.lmtoolbox.features.media.qr_gen.QrCodeUi",
    scriptDir = "qr_gen",
    cacheName = "qr",
) {
    const val S_DSHKEY = "QRGen_!ShareTo"

    // Default look of new codes (set in the module's settings)
    const val S_SIZE = "QRGen_size"
    const val S_EC = "QRGen_ec"
    const val S_BORDER = "QRGen_border"
    const val S_STYLE = "QRGen_style"

    override val preferenceKeys = listOf(S_DSHKEY, S_SIZE, S_EC, S_BORDER, S_STYLE)
}
