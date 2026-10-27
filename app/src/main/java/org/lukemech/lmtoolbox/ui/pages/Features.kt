package org.lukemech.lmtoolbox.ui.pages

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.helpers.FeatureRegistry
import org.lukemech.lmtoolbox.helpers.FeatureUsage
import org.lukemech.lmtoolbox.helpers.Screen
import org.lukemech.lmtoolbox.helpers.Search
import org.lukemech.lmtoolbox.ui.components.ClearAction
import org.lukemech.lmtoolbox.ui.components.EmptyText
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.ui.components.SearchField
import org.lukemech.lmtoolbox.ui.components.Tile
import org.lukemech.lmtoolbox.ui.components.TileGrid
import org.lukemech.lmtoolbox.ui.helpers.LocalAppNav
import org.lukemech.lmtoolbox.ui.helpers.NewsFeedSection
import org.lukemech.lmtoolbox.ui.components.ROW_SPACING
import org.lukemech.lmtoolbox.ui.components.edgeFade

/** How long a feature counts as new / recently updated. */

val all: List<AppFeature> = FeatureRegistry.features

/** A home page category: the user's own lists first, then every feature, then the [FeatureCategory]s. */
private class Category(val id: String, val title: String, val icon: ImageVector, val features: List<AppFeature>, val empty: String?)

@Composable
private fun categories(): List<Category> {
    val resources = LocalResources.current
    fun List<AppFeature>.byTitle() = sortedBy { resources.getString(it.titleStringResId) }
    return listOf(
        Category(
            Screen.Category.FAVOURITES, stringResource(R.string.favourites), Icons.Default.Star,
            FeatureUsage.favouriteFeatures, stringResource(R.string.favourites_empty)
        ),
        Category(
            Screen.Category.RECENT, stringResource(R.string.recent), Icons.Default.History,
            FeatureUsage.recentFeatures, stringResource(R.string.recent_empty)
        ),
        Category(Screen.Category.ALL, stringResource(R.string.all_features), Icons.Default.Apps, all.byTitle(), null)
    ) + FeatureCategory.entries.map { category ->
        Category(
            category.name, stringResource(category.titleRes), category.icon,
            all.filter { it.category == category }.byTitle(), null
        )
    }.filter { it.features.isNotEmpty() }
}

@Composable
fun FeaturesPage() {
    val navController = LocalAppNav.current.controller
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(false) }

    PageHeading(
        title = stringResource(R.string.nav_main_page),
        // Searching is in All (opened by tapping the field, typing there), a filter opens its category
        header = {
            SearchBar(
                "", {}, null,
                onSearch = { navController.navigate(Screen.Category.createRoute(Screen.Category.ALL, search = true)) }
            ) { id -> id?.let { navController.navigate(Screen.Category.createRoute(it)) } }
        },
        // News reloaded; under the search, the title and it's staying put
        onRefresh = {
            isRefreshing = true
            refreshTrigger = true
        },
        refreshing = isRefreshing
    ) {
        key(refreshTrigger) {
            NewsFeedSection(
                refreshTrigger,
                onRefreshFinished = {
                    isRefreshing = false
                    refreshTrigger = false
                }
            )
        }
        CategoryGrid(categories()) { navController.navigate(Screen.Category.createRoute(it.id)) }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Page of the home page category [id] (see [Screen.Category]): the search, filtered by it to
 * start with (but [Screen.Category.ALL]: unfiltered); typing in it right away if [search]
 * (opened by the home page's search field).
 */
@Composable
fun CategoryPage(id: String?, search: Boolean = false) {
    val context = LocalContext.current
    val categories = categories()
    if (categories.none { it.id == id }) return PageHeading(stringResource(R.string.undefined), back = true) {}

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(id.takeIf { it != Screen.Category.ALL }) }
    // Titled by the filter, as it changes
    val category = categories.first { it.id == (filter ?: Screen.Category.ALL) }

    PageHeading(
        title = category.title,
        back = true,
        // The user's own lists are emptied here, after asking
        action = if (category.features.isEmpty()) null else when (category.id) {
            Screen.Category.FAVOURITES -> {
                {
                    ClearAction(
                        stringResource(R.string.favourites_clear_confirm_title), stringResource(R.string.favourites_clear_confirm_msg),
                        Icons.Default.Star
                    ) {
                        FeatureUsage.clearFavourites(context)
                    }
                }
            }
            Screen.Category.RECENT -> {
                {
                    ClearAction(
                        stringResource(R.string.recent_clear_confirm_title), stringResource(R.string.recent_clear_confirm_msg),
                        Icons.Default.History
                    ) {
                        FeatureUsage.clearRecent(context)
                    }
                }
            }
            else -> null
        },
        header = { SearchBar(query, { query = it }, filter, autoFocus = search) { filter = it } },
        // The results are a lazy list: only the features in sight built, however many there are
        scrolls = false
    ) {
        SearchResults(query, filter, empty = category.empty.takeIf { query.isBlank() && category.features.isEmpty() })
    }
}

/**
 * The search field and the filters under it ([filter]: a [Category] id, null for none); tapping
 * the chosen filter again clears it.
 */
@Composable
private fun SearchBar(
    query: String,
    onQuery: (String) -> Unit,
    filter: String?,
    autoFocus: Boolean = false,
    onSearch: (() -> Unit)? = null,
    onFilter: (String?) -> Unit
) {
    SearchField(query, onQuery, stringResource(R.string.search_features), autoFocus = autoFocus, onClick = onSearch)
    SearchFilters(filter) { onFilter(if (filter == it) null else it) }
    Spacer(modifier = Modifier.height(16.dp))
}

/**
 * The features of [filter] (a [Category] id, null for all) matching [query], best first; with no
 * query, all of them in its order (by name, the recent ones as they were opened). [empty]: said
 * instead while the user's own list is (no favourites yet).
 */
@Composable
private fun SearchResults(query: String, filter: String?, empty: String?) {
    val context = LocalContext.current
    val resources = LocalResources.current
    // Names, categories and keywords of the features, in every app language
    val searchIndex = remember {
        all.map { it to Search.entry(context, listOf(it.titleStringResId), listOf(it.keywordsResId, it.descriptionResId, it.category.titleRes)) }
    }
    val features = categories().firstOrNull { it.id == (filter ?: Screen.Category.ALL) }?.features.orEmpty()
    val results = if (query.isBlank()) features else {
        searchIndex.asSequence().filter { it.first in features }
            .map { (feature, entry) -> feature to Search.score(query, entry) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<AppFeature, Int>> { it.second }.thenBy { resources.getString(it.first.titleStringResId) })
            .map { it.first }.toList()
    }
    // Back to the top as the query or filter changes: the best ones are there
    val list = rememberLazyListState()
    LaunchedEffect(query, filter) { list.scrollToItem(0) }
    FeatureList(results, list, empty ?: stringResource(R.string.search_no_results).takeIf { results.isEmpty() })
}

/** Chips limiting the search to the favourites, the recent ones or a category; [onSelect] gets the tapped one's id. */
@Composable
private fun SearchFilters(selected: String?, onSelect: (String) -> Unit) {
    val filters = listOf(
        Triple(Screen.Category.FAVOURITES, stringResource(R.string.favourites), Icons.Default.Star),
        Triple(Screen.Category.RECENT, stringResource(R.string.recent), Icons.Default.History)
    ) + FeatureCategory.entries.map { Triple(it.name, stringResource(it.titleRes), it.icon) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
    ) {
        filters.forEach { (id, label, icon) ->
            FilterChip(
                selected = selected == id,
                onClick = { onSelect(id) },
                label = { Text(label) },
                leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
            )
        }
    }
}

/** Tiles listing each category's features. */
@Composable
private fun CategoryGrid(categories: List<Category>, onClick: (Category) -> Unit) {
    val resources = LocalResources.current
    TileGrid(categories) { category, modifier ->
        Tile(
            title = category.title,
            subtitle = category.features.joinToString(", ") { resources.getString(it.titleStringResId) }
                .ifEmpty { category.empty.orEmpty() },
            icon = category.icon,
            modifier = modifier
        ) { onClick(category) }
    }
}

/**
 * Features to open, each with its star, filling the page and scrolling ([list]): only those in sight
 * are built, coming and going (and moving) as the search changes. [empty]: said instead of them.
 */
@Composable
private fun FeatureList(items: List<AppFeature>, list: LazyListState, empty: String?) {
    val context = LocalContext.current
    val navController = LocalAppNav.current.controller

    LazyColumn(
        state = list,
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier.fillMaxSize().edgeFade(list)
    ) {
        if (empty != null) item(key = "empty") { Box(Modifier.animateItem()) { EmptyText(empty) } }
        else items(items, key = { it.route }) { feature ->
            FeatureButton(
                modifier = Modifier.animateItem(),
                title = stringResource(feature.titleStringResId),
                subtitle = stringResource(feature.descriptionResId),
                icon = feature.icon,
                onClick = { navController.navigate(Screen.Dynamic.createRoute(feature)) },
                buttonFavouriteClicked = FeatureUsage.isFavourite(feature),
                buttonFavouriteOnClick = { FeatureUsage.toggleFavourite(context, feature) }
            )
        }
    }
}
