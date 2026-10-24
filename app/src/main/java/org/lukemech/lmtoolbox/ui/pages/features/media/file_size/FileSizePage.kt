package org.lukemech.lmtoolbox.ui.pages.features.media.file_size

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.media.file_size.FileSizeRequest
import org.lukemech.lmtoolbox.features.media.image_resize.ImageResizerFeature
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.Screen
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.StepWithButtons
import org.lukemech.lmtoolbox.ui.components.ToolIntro
import org.lukemech.lmtoolbox.ui.helpers.LocalAppNav
import org.lukemech.lmtoolbox.features.media.video_enc.VideoEncoderFeature

/** What kind of file to make smaller, then on to its tool, set to aim at a size (see [FileSizeRequest]). */
@Composable
fun FileSizePage() {
    val navController = LocalAppNav.current.controller

    fun open(feature: AppFeature) {
        FileSizeRequest.request(feature)
        navController.navigate(Screen.Dynamic.createRoute(feature))
    }

    StepWithButtons(
        content = {
            PageHeading(title = stringResource(R.string.fp_FileSize), verticalArrangement = Arrangement.Center) {
                ToolIntro(
                    Icons.Default.Compress, stringResource(R.string.f_FileSize_intro_title),
                    listOf(
                        Icons.Default.Movie to stringResource(R.string.f_FileSize_intro_media),
                        Icons.Default.Image to stringResource(R.string.f_FileSize_intro_image),
                    ),
                    stringResource(R.string.f_FileSize_intro_tip)
                )
            }
        },
        buttons = {
            // The question over its answers
            Text(
                stringResource(R.string.f_FileSize_question),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                KindButton(Icons.Default.Movie, stringResource(R.string.f_FileSize_media)) { open(VideoEncoderFeature) }
                KindButton(Icons.Default.Image, stringResource(R.string.f_FileSize_image)) { open(ImageResizerFeature) }
            }
        }
    )
}

/** One of the kinds of file, its [icon] over its [label], as big as a step's main button is wide. */
@Composable
private fun RowScope.KindButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(vertical = 20.dp, horizontal = 8.dp),
        modifier = Modifier.weight(1f)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}
