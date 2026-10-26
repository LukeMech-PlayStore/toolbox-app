package org.lukemech.lmtoolbox.features.media.file_hash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.components.ExpandableButton
import org.lukemech.lmtoolbox.ui.pages.features.media.file_hash.AlgorithmChoice
import org.lukemech.lmtoolbox.ui.pages.features.media.file_hash.FileHashPage

/** File hash: checksums of files (or text) and checking them, with the platform's digests, no Linux (see [FileHash]). */
object FileHashFeature : AppFeature {
    override val route = "file_hash"
    override val titleStringResId: Int = R.string.fp_FileHash
    override val keywordsResId: Int = R.string.fk_FileHash
    override val descriptionResId: Int = R.string.fd_FileHash
    override val category = FeatureCategory.Media
    override val icon @Composable get() = Icons.Default.Fingerprint

    const val S_DSHKEY = "FileHash_!ShareTo"
    /** The algorithms picked last, for the next time (HashAlgorithm names). */
    const val S_ALGORITHMS = "FileHash_algorithms"

    override val preferenceKeys = listOf(S_DSHKEY, S_ALGORITHMS)

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        FileHashPage(arguments?.getOrElse("sharedText") { null })
    }

    override val hasSettingsContent get() = true

    /** The algorithms computed (as the page has them), and its "Share to" entry. */
    @Composable
    override fun SettingsContent() {
        val context = LocalContext.current
        var algorithms by remember { mutableStateOf(FileHash.algorithms(context)) }

        ExpandableButton(stringResource(R.string.f_FileHash_algorithms), Icons.Default.Tune) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                AlgorithmChoice(algorithms) {
                    algorithms = it
                    FileHash.saveAlgorithms(context, it)
                }
            }
        }
    }
}
