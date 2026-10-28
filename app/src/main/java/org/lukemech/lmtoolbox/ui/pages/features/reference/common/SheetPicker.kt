package org.lukemech.lmtoolbox.ui.pages.features.reference.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.features.reference.common.Sheet
import org.lukemech.lmtoolbox.ui.components.Choice
import org.lukemech.lmtoolbox.ui.components.ChoiceList
import org.lukemech.lmtoolbox.ui.components.FeatureButton

/** From this many sheets on, their list has a search: boards are looked for by name sooner than settings are. */
private const val SEARCH_SHEETS_FROM = 6

/**
 * What a page's [sheets] are, as a row showing the one [selected]: its name, over it its family and which of them
 * it is ("Nano · 5 of 8"). Tapped, the sheets are listed under it to pick from ([onSelect]), as an OptionSetting's
 * choices are (a [ChoiceList]): each family under its heading, found by their names, families and notes. The same
 * row on every page that has sheets to pick from: a reference's, a calculator's with tables. [expanded]: the list's
 * open, the page's to say ([onExpandedChange]), so it folds away as the page under it is used.
 */
@Composable
fun SheetPicker(
    sheets: List<Sheet>,
    selected: Int,
    icon: ImageVector,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (Int) -> Unit
) {
    val resources = LocalResources.current
    val sheet = sheets[selected]

    FeatureButton(
        title = listOfNotNull(
            sheet.group?.resolve(resources),
            stringResource(R.string.rf_sheet_position, selected + 1, sheets.size)
        ).joinToString(" · "),
        subtitle = sheet.name.resolve(resources),
        titleSubtitleSwap = true,
        icon = icon,
        icon2 = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
        onClick = { onExpandedChange(!expanded) },
        expanded = expanded,
        popupContent = {
            val choices = remember(sheets, resources) {
                sheets.mapIndexed { i, it -> Choice(i, it.name.resolve(resources), it.group?.resolve(resources), it.note?.resolve(resources)) }
            }
            ChoiceList(choices, selected, searchFrom = SEARCH_SHEETS_FROM, placeholder = stringResource(R.string.rf_search_sheets)) {
                onSelect(it)
                onExpandedChange(false)
            }
        }
    )
}
