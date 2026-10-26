package org.lukemech.lmtoolbox.features.reference.hdmi

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.Pin
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Clock
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Control
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Data
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Ground
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Power
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Shield
import org.lukemech.lmtoolbox.features.reference.common.PinKind.Unused
import org.lukemech.lmtoolbox.features.reference.common.PinoutSheet
import org.lukemech.lmtoolbox.features.reference.common.ReferenceFeature
import org.lukemech.lmtoolbox.features.reference.common.raw
import org.lukemech.lmtoolbox.features.reference.common.res
import org.lukemech.lmtoolbox.features.reference.common.staggered

/** HDMI's type A connector: its 19 pins, the odd ones in one row and the even ones between them in the other. */
object HdmiFeature : ReferenceFeature() {

    override val route = "hdmi"
    override val titleStringResId: Int = R.string.fp_hdmi
    override val keywordsResId: Int = R.string.fk_hdmi
    override val descriptionResId: Int = R.string.fd_hdmi
    override val icon @Composable get() = Icons.Default.Tv

    private val TMDS = res(R.string.rf_hdmi_tmds)
    private val SHIELD = res(R.string.rf_hdmi_shield)

    override val sheets by lazy {
        listOf(
            PinoutSheet(
                raw("Type A"),
                listOf(
                    Pin("1", "TMDS Data2+", Data, TMDS),
                    Pin("2", "TMDS Data2 Shield", Shield, SHIELD),
                    Pin("3", "TMDS Data2−", Data, TMDS),
                    Pin("4", "TMDS Data1+", Data, TMDS),
                    Pin("5", "TMDS Data1 Shield", Shield, SHIELD),
                    Pin("6", "TMDS Data1−", Data, TMDS),
                    Pin("7", "TMDS Data0+", Data, TMDS),
                    Pin("8", "TMDS Data0 Shield", Shield, SHIELD),
                    Pin("9", "TMDS Data0−", Data, TMDS),
                    Pin("10", "TMDS Clock+", Clock, res(R.string.rf_hdmi_clock)),
                    Pin("11", "TMDS Clock Shield", Shield, SHIELD),
                    Pin("12", "TMDS Clock−", Clock, res(R.string.rf_hdmi_clock)),
                    Pin("13", "CEC", Control, res(R.string.rf_hdmi_cec)),
                    Pin("14", "Utility", Unused, res(R.string.rf_hdmi_utility)),
                    Pin("15", "SCL", Clock, res(R.string.rf_hdmi_scl)),
                    Pin("16", "SDA", Data, res(R.string.rf_hdmi_sda)),
                    Pin("17", "DDC/CEC GND", Ground, res(R.string.rf_hdmi_ground)),
                    Pin("18", "+5V", Power, res(R.string.rf_hdmi_5v)),
                    Pin("19", "HPD", Control, res(R.string.rf_hdmi_hpd)),
                ).staggered(),
                note = res(R.string.rf_hdmi_note)
            )
        )
    }
}
