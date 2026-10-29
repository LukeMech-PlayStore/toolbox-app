package org.lukemech.lmtoolbox.ui.pages.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Outbond
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.SettingsSubPage
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.helpers.LocalAppNav
import org.lukemech.lmtoolbox.ui.components.RowArrangement
import org.lukemech.lmtoolbox.helpers.openLink

@Composable
fun OpenSourceLicensesSection() {
    val navController = LocalAppNav.current.controller
    val coroutineScope = rememberCoroutineScope()

    FeatureButton(
        title = stringResource(R.string.s_osl_title),
        icon = Icons.Default.Book,
        icon2 = Icons.AutoMirrored.Default.KeyboardArrowRight,
        onClick = {
            coroutineScope.launch {
                navController.navigate(SettingsSubPage.OpenSourceLicenses.route)
            }
        }
    )
}

@Composable
fun OpenSourceLicensesPage() {
    val context = LocalContext.current

    val openUrl: (String) -> Unit = { openLink(context, it) }

    PageHeading(
        title = stringResource(id = R.string.s_osl_title),
        back = true
    ) {
        Column(verticalArrangement = RowArrangement) {
            FeatureButton(
                title = "PRoot",
                subtitle = "GPL-2.0",
                icon = Icons.Default.Outbond,
                icon2 = Icons.AutoMirrored.Default.OpenInNew,
                onClick = { openUrl("https://github.com/termux/proot/blob/228a5f28b078f4e2504de46758ce17948f73f507/COPYING") }
            )
            FeatureButton(
                title = "Termux packages",
                subtitle = "Apache-2.0, GPL-3.0",
                icon = ImageVector.vectorResource(R.drawable.termux),
                icon2 = Icons.AutoMirrored.Default.OpenInNew,
                onClick = { openUrl("https://github.com/termux/termux-packages") }
            )
        }
    }
}
