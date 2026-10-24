package org.lukemech.lmtoolbox.features.electronics.pcb_trace

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Route
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.ui.pages.features.electronics.pcb_trace.PcbTracePage

/** PCB trace: the width a current needs. */
object PcbTraceFeature : AppFeature {

    override val route = "pcb_trace"
    override val titleStringResId: Int = R.string.fp_pcb_trace
    override val keywordsResId: Int = R.string.fk_pcb_trace
    override val descriptionResId: Int = R.string.fd_pcb_trace
    override val category = FeatureCategory.Electronics
    override val icon @Composable get() = Icons.Default.Route

    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        PcbTracePage()
    }
}
