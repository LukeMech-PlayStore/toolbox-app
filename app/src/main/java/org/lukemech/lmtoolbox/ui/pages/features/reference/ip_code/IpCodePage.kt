package org.lukemech.lmtoolbox.ui.pages.features.reference.ip_code

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.ip_code.IpCodeFeature
import org.lukemech.lmtoolbox.ui.components.MainResult
import org.lukemech.lmtoolbox.ui.components.OptionSetting
import org.lukemech.lmtoolbox.ui.components.Results

/** An IP code from its two digits, each picked, and what they say. */
@Composable
fun IpCodeDecoder() {
    var solids by rememberSaveable { mutableStateOf("6") }
    var water by rememberSaveable { mutableStateOf("7") }

    OptionSetting(
        title = stringResource(R.string.rf_ip_first),
        icon = Icons.Default.Grain,
        selected = solids,
        options = IpCodeFeature.SOLIDS.map { (digit, _) -> digit to digit },
        onSelect = { solids = it }
    )
    OptionSetting(
        title = stringResource(R.string.rf_ip_second),
        icon = Icons.Default.WaterDrop,
        selected = water,
        options = IpCodeFeature.WATER.map { (digit, _) -> digit to digit },
        onSelect = { water = it }
    )
    Results {
        MainResult(stringResource(R.string.rf_code), "IP$solids$water")
        Meaning(stringResource(R.string.rf_ip_first), stringResource(IpCodeFeature.SOLIDS.first { it.first == solids }.second))
        Meaning(stringResource(R.string.rf_ip_second), stringResource(IpCodeFeature.WATER.first { it.first == water }.second))
    }
}

/** A digit's meaning under what it's about: too long for a result row's right-hand side. */
@Composable
private fun Meaning(title: String, meaning: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(meaning, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
    }
}
