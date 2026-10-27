package org.lukemech.lmtoolbox.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.Screen
import org.lukemech.lmtoolbox.ui.helpers.LocalAppNav

/**
 * Page with its [title] on top; subpages ([back]) get an arrow back next to it, going back in the
 * navigation, or doing [onBack] for steps within a page. A page with [steps] shows them right under
 * the title (see [StepBar]).
 */
@Composable
fun PageHeading(
    title: String,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    back: Boolean = false,
    onBack: (() -> Unit)? = null,
    /** At the end of the title's row, e.g. a "Clear" button. */
    action: (@Composable () -> Unit)? = null,
    steps: Steps? = null,
    /** Under the title, staying there while the content scrolls (e.g. a search field). */
    header: (@Composable ColumnScope.() -> Unit)? = null,
    /**
     * Pulling the content down calls it (e.g. reloading what it shows), while [refreshing] shows
     * that it's at it; its indicator comes down under the title and header, over the content.
     */
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false,
    /** False: [content] fills the rest of the page and scrolls itself (a lazy list's, only what's seen built). */
    scrolls: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // As tall as the back button with or without it, so every page's content starts at the same height
            modifier = Modifier
                .padding(start = if (back || onBack != null) 4.dp else 16.dp, top = 8.dp, bottom = 12.dp, end = 16.dp)
                .heightIn(min = 48.dp)
        ) {
            if (back || onBack != null) {
                val navController = LocalAppNav.current.controller
                IconButton(onClick = {
                    // Opened straight from outside (e.g. a widget), there's nothing to go back to
                    onBack?.invoke() ?: run { if (!navController.popBackStack()) navController.navigate(Screen.Main.route) }
                }) {
                    Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
            // One line: too long for the row, it scrolls sideways (faded where there's more)
            val titleScroll = rememberScrollState()
            Text(
                title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, softWrap = false,
                modifier = Modifier.weight(1f).sideFade(titleScroll).horizontalScroll(titleScroll)
            )
            action?.invoke()
        }
        // At the bottom of the app, over its bars (StepBarSlot); here only without one
        val host = LocalStepBarHost.current
        if (host == null) steps?.let { StepBar(it) } else ShowSteps(host, steps)
        header?.let { Column(content = it) }
        val scroll = rememberScrollState()
        val body = @Composable {
            if (!scrolls) {
                Column(modifier = modifier.fillMaxSize(), content = content)
            } else {
                Column(
                    // The space at the bottom scrolls with the content: outside, it would cut the content
                    // off above an empty strip. It fades out under the title (or header) and over the bottom bar
                    modifier = modifier
                        .fillMaxSize()
                        .edgeFade(scroll)
                        .verticalScroll(scroll)
                        .padding(bottom = 16.dp),
                    verticalArrangement = verticalArrangement,
                    content = content
                )
            }
        }
        if (onRefresh != null) {
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) { body() }
        } else {
            body()
        }
    }
}
