package org.lukemech.lmtoolbox.features.media.qr_gen

import org.lukemech.lmtoolbox.features.pypkgs.R
import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lukemech.lmtoolbox.helpers.FeatureUi
import org.lukemech.lmtoolbox.helpers.PreferenceHelper
import org.lukemech.lmtoolbox.ui.components.ExpandableButton
import org.lukemech.lmtoolbox.ui.pages.features.media.qr_gen.QrStyleEditor
import org.lukemech.lmtoolbox.ui.pages.features.media.qr_gen.QrGenPage

/** The QR generator's pages (see [QrGenFeature]). */
object QrCodeUi : FeatureUi {
    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        QrGenPage(arguments?.getOrElse("sharedText") { null })
    }

    override val hasSettingsContent get() = true

    /** The default look of new codes, and its "Share to" entry. */
    @Composable
    override fun SettingsContent() {
        val context = LocalContext.current
        var style by remember { mutableStateOf(loadStyle(context)) }

        ExpandableButton(stringResource(R.string.qr_default_appearance), Icons.Default.Palette) {
            QrStyleEditor(
                style,
                {
                    style = it
                    saveStyle(context, it)
                },
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }

    /** Default look of new codes, set in the settings. */
    fun loadStyle(context: Context) = QrStyle().let { default ->
        QrStyle(
            size = PreferenceHelper.get(context, QrGenFeature.S_SIZE, default.size),
            errorCorrection = PreferenceHelper.get(context, QrGenFeature.S_EC, default.errorCorrection),
            border = PreferenceHelper.get(context, QrGenFeature.S_BORDER, default.border),
            style = PreferenceHelper.get(context, QrGenFeature.S_STYLE, default.style).takeIf { it in QrStyle.STYLES } ?: default.style
        )
    }

    private fun saveStyle(context: Context, style: QrStyle) {
        PreferenceHelper.put(context, QrGenFeature.S_SIZE, style.size)
        PreferenceHelper.put(context, QrGenFeature.S_EC, style.errorCorrection)
        PreferenceHelper.put(context, QrGenFeature.S_BORDER, style.border)
        PreferenceHelper.put(context, QrGenFeature.S_STYLE, style.style)
    }
}
