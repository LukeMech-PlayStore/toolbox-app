package org.lukemech.lmtoolbox.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lukemech.lmtoolbox.R

/**
 * One of a [ChoiceList]'s choices: its [value] and [label], the [group] it's listed under (the ones of a group one
 * after another), and [keywords] it's found by besides its label and group (a board's chip).
 */
class Choice<T>(val value: T, val label: String, val group: String? = null, val keywords: String? = null)

/** From this many choices on, a [ChoiceList] has a search over it. */
const val SEARCH_FROM = 8

/** A line of a [ChoiceList]: a group's heading, or a choice (by its place in the list). */
private sealed interface Line {
    data class Heading(val text: String) : Line
    data class Item(val index: Int) : Line
}

/**
 * Choices to pick one of (an [OptionSetting]'s, a reference's sheets), each a radio button and its label, the
 * [selected] one marked: in a box of their own, scrolling as they don't fit, opened at the selected one. Lazy, only
 * the lines in sight built (Minecraft's some 800 versions). Each group under its heading; from [searchFrom] choices on,
 * a search over them by their labels, groups and keywords ([placeholder] in it). [onChoose]: tapped.
 */
@Composable
fun <T> ChoiceList(
    choices: List<Choice<T>>,
    selected: T,
    searchFrom: Int = SEARCH_FROM,
    placeholder: String = stringResource(R.string.search_choices),
    onChoose: (T) -> Unit
) {
    // Its own while it's open: a search starts anew as it opens again
    var query by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val q = query.trim()
    val lines = remember(choices, q) {
        buildList {
            var group: String? = null
            choices.forEachIndexed { i, it ->
                if (q.isNotEmpty() && listOfNotNull(it.label, it.group, it.keywords).none { text -> text.contains(q, true) }) return@forEachIndexed
                if (it.group != null && it.group != group) add(Line.Heading(it.group))
                group = it.group
                add(Line.Item(i))
            }
        }
    }
    if (choices.size >= searchFrom) {
        SearchField(query, { query = it }, placeholder, modifier = Modifier.padding(bottom = 4.dp))
    }
    // A couple of lines above the selected one in sight
    val scroll = rememberLazyListState(
        initialFirstVisibleItemIndex = (lines.indexOfFirst { it is Line.Item && choices[it.index].value == selected } - 2).coerceAtLeast(0)
    )
    LazyColumn(state = scroll, modifier = Modifier.heightIn(max = LIST_MAX_HEIGHT).edgeFade(scroll)) {
        items(lines, key = { if (it is Line.Item) it.index else "h:${(it as Line.Heading).text}" }) { line ->
            when (line) {
                is Line.Heading -> Text(
                    line.text,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp)
                )
                is Line.Item -> {
                    val choice = choices[line.index]
                    fun choose() {
                        focusManager.clearFocus()
                        onChoose(choice.value)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = ::choose).padding(horizontal = 8.dp)
                    ) {
                        RadioButton(selected = choice.value == selected, onClick = ::choose)
                        Text(choice.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        if (lines.isEmpty()) item { EmptyText(stringResource(R.string.search_no_results)) }
    }
}
