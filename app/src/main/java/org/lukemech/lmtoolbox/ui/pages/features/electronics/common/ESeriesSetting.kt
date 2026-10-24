package org.lukemech.lmtoolbox.ui.pages.features.electronics.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.electronics.common.ESeries
import org.lukemech.lmtoolbox.features.electronics.common.formatValue
import org.lukemech.lmtoolbox.ui.components.OptionSetting

/**
 * A field's value [ESeriesSetting] shows the nearest standard value of, a tap on which puts it in the
 * field ([use]); [label] tells several apart (R1, R2).
 */
class Nearest(val value: Double?, val label: String? = null, val use: (Double) -> Unit)

/**
 * The E-series standard values are picked from, as a setting showing "E24 (5%)", with the [nearest]
 * standard values (in [unit]) of fields' values in it.
 */
@Composable
fun ESeriesSetting(
    selected: ESeries.Series,
    nearest: List<Nearest> = emptyList(),
    unit: String = "Ω",
    onSelect: (ESeries.Series) -> Unit
) = OptionSetting(
    title = stringResource(R.string.ec_eseries),
    icon = Icons.Default.FormatListNumbered,
    selected = selected,
    options = ESeries.all.map { it to "${it.name} (${it.tolerance})" },
    trailing = if (nearest.isEmpty()) null else {
        {
            // Small chips: not padded out to a full touch target's height, the row stays as tall as the others
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.End) {
                nearest.forEach { field ->
                    val value = field.value?.takeIf { it > 0 }?.let { ESeries.nearest(selected, it) } ?: return@forEach
                    Surface(
                        onClick = { field.use(value) },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    ) {
                        Text(
                            listOfNotNull(field.label, formatValue(value, unit)).joinToString(" · "),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            }
        }
    },
    onSelect = onSelect
)
