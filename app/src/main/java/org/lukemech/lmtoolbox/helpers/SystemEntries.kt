package org.lukemech.lmtoolbox.helpers

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.vector.ImageVector
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.finance.currencyconv.CurrencyConverterFeature
import org.lukemech.lmtoolbox.features.media.file_hash.FileHashFeature
import org.lukemech.lmtoolbox.features.media.image_resize.ImageResizerFeature
import org.lukemech.lmtoolbox.features.media.md_edit.MetadataEditorFeature
import org.lukemech.lmtoolbox.features.media.media_dl.MediaDownloaderFeature
import org.lukemech.lmtoolbox.features.media.qr_gen.QrGenFeature
import org.lukemech.lmtoolbox.features.media.video_enc.VideoEncoderFeature

/**
 * An entry [aliasName] (a manifest activity-alias) adds to the system, "Share to" or "Open in": on
 * while its tool is installed and [offKey] doesn't switch it off (the tool's System settings,
 * [title] and [description] with [icon]).
 */
data class SystemEntry(
    val aliasName: String,
    val offKey: String,
    @param:StringRes val title: Int,
    @param:StringRes val description: Int,
    val icon: ImageVector,
)

/** The tools' [SystemEntry]s, and keeping Android's in step with what's installed. */
object SystemEntries {
    private fun shareTo(alias: String, offKey: String, @StringRes description: Int = R.string.s_shareTo_desc) =
        SystemEntry(alias, offKey, R.string.s_shareTo, description, Icons.Default.Share)

    /** By tool route. */
    val byFeature: Map<String, List<SystemEntry>> = mapOf(
        QrGenFeature.route to listOf(shareTo("share.QR.Gen", QrGenFeature.S_DSHKEY)),
        MetadataEditorFeature.route to listOf(
            SystemEntry("openIn.Metadata.Edit", MetadataEditorFeature.S_DONKEY, R.string.s_openIn, R.string.s_openIn_desc, Icons.Default.FileOpen),
            shareTo("share.Metadata.Edit", MetadataEditorFeature.S_DSHKEY),
        ),
        CurrencyConverterFeature.route to listOf(shareTo("share.Currency.Convert", CurrencyConverterFeature.S_DSHKEY)),
        MediaDownloaderFeature.route to listOf(shareTo("share.Media.Download", MediaDownloaderFeature.S_DSHKEY, R.string.f_MediaDl_share_desc)),
        VideoEncoderFeature.route to listOf(shareTo("share.Video.Encode", VideoEncoderFeature.S_DSHKEY, R.string.f_VidEnc_share_desc)),
        ImageResizerFeature.route to listOf(shareTo("share.Image.Resize", ImageResizerFeature.S_DSHKEY, R.string.f_ImgRes_share_desc)),
        FileHashFeature.route to listOf(shareTo("share.File.Hash", FileHashFeature.S_DSHKEY, R.string.f_FileHash_share_desc)),
    )

    /** The tool an entry ([SystemEntry.aliasName]) opens. */
    fun featureOf(aliasName: String): AppFeature? =
        byFeature.entries.firstOrNull { (_, entries) -> entries.any { it.aliasName == aliasName } }?.let { FeatureRegistry.byRoute(it.key) }

    /** Android's entries as [feature]'s (all tools' without one) installation and switches have them. */
    fun apply(ctx: Context, feature: AppFeature? = null) {
        for (tool in if (feature != null) listOf(feature) else FeatureRegistry.features) {
            val entries = byFeature[tool.route] ?: continue
            // A tool that isn't there takes nothing shared or opened
            val installed = tool.isInstalled(ctx)

            for ((aliasName, offKey) in entries) {
                // Uninstalled, its entries are as new: on again once it's installed
                if (!installed) PreferenceHelper.remove(ctx, offKey)
                val state =
                    if (installed && !PreferenceHelper.get(ctx, offKey, false)) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED

                val component = ComponentName(ctx, aliasName)
                // Only when it changes: called whenever a task ends
                if (ctx.packageManager.getComponentEnabledSetting(component) == state) continue
                ctx.packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
