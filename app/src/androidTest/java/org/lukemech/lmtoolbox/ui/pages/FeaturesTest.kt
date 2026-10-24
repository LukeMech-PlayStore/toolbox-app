package org.lukemech.lmtoolbox.ui.pages

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import org.junit.Assert.assertEquals
import org.junit.Test
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.e2e.Device
import org.lukemech.lmtoolbox.e2e.E2eTest
import org.lukemech.lmtoolbox.e2e.Explorer
import org.lukemech.lmtoolbox.features.measure.compass.CompassFeature
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.helpers.FeatureRegistry
import org.lukemech.lmtoolbox.helpers.FeatureUsage
import org.lukemech.lmtoolbox.helpers.Screen

/** The home page (FeaturesPage) and its categories (CategoryPage): every tool from them, the search. */
class FeaturesTest : E2eTest() {

    @Test
    fun everyToolOpensFromTheHomePage() {
        FeatureRegistry.features.forEach { feature ->
            ui.openTool(feature)
            ui.waitShown(hasContentDescription(ui.str(feature.titleStringResId)), what = "${feature.route} in the bottom bar")
            assertEquals(feature, FeatureUsage.recentFeatures.firstOrNull())
            Device.screenshot("tool-${feature.route}")
        }
    }

    @Test
    fun everyCategoryOpens() {
        val categories = FeatureCategory.entries.map { it.name to ui.str(it.titleRes) } +
            listOf(Screen.Category.FAVOURITES to ui.str(R.string.favourites), Screen.Category.RECENT to ui.str(R.string.recent))
        categories.forEach { (id, title) ->
            ui.home()
            ui.waitPage(Screen.Main.route)
            ui.clickText(title)
            ui.waitPage("category/$id")
            Device.screenshot("category-$id")
        }
    }

    @Test
    fun searchFindsToolsByWhatTheyDo() {
        ui.home()
        ui.clickText(R.string.all_features)
        ui.waitPage("category/${Screen.Category.ALL}")
        // A keyword, not its name: what it's for
        val keyword = ui.str(CompassFeature.keywordsResId).split(',').first().trim()
        ui.type(ui.str(R.string.search_features), keyword)
        ui.waitShown(hasText(ui.str(CompassFeature.titleStringResId)) and hasClickAction(), what = "the compass found by \"$keyword\"")
        ui.type(ui.str(R.string.search_features), "zzzqqqxxx")
        ui.waitShown(hasText(ui.str(R.string.search_no_results)))
    }

    /** Everything on the home page and the pages it leads to (categories, favourites, stars). */
    @Test
    fun homePageThroughAndThrough() {
        ui.home()
        ui.waitPage(Screen.Main.route)
        Explorer(ui, "home", depth = 1).explore()
    }
}
