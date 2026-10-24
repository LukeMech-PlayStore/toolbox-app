package org.lukemech.lmtoolbox.ui.helpers

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dehaze
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.*
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.*
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.SettingsSubPage
import org.lukemech.lmtoolbox.helpers.FeatureUsage
import org.lukemech.lmtoolbox.helpers.FeatureRegistry
import org.lukemech.lmtoolbox.helpers.Screen
import org.lukemech.lmtoolbox.ui.pages.settings.SettingsSubPageContent
import org.lukemech.lmtoolbox.ui.pages.settings.FeatureSettingsPage
import androidx.compose.runtime.mutableStateOf
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.ui.pages.SettingsPage
import org.lukemech.lmtoolbox.ui.pages.FeaturesPage
import org.lukemech.lmtoolbox.ui.pages.CategoryPage

// ----------------------------
// Navigation Host
// ----------------------------

/** [lastFeatureRoute]: feature of the bottom bar's middle tab, the default (or last used) one at start. */
class AppNav(val controller: NavHostController, initialFeatureRoute: String? = null) {
    var lastFeatureRoute by mutableStateOf(initialFeatureRoute)

    /** The bottom bar's middle tab's feature ([lastFeatureRoute]). */
    val lastFeature: AppFeature? get() = FeatureRegistry.byRoute(lastFeatureRoute)
}
val LocalAppNav = compositionLocalOf<AppNav> {
    error("No AppNav provided")
}

/** The page shown: its route with its arguments (e.g. "feature/ohm", "settings/tasks"), for the end-to-end tests (src/androidTest). */
object CurrentPage {
    @Volatile
    var route: String? = null
        private set

    internal fun set(entry: NavBackStackEntry?) {
        route = entry?.destination?.route?.let { pattern ->
            Regex("""\{(\w+)\}""").replace(pattern.substringBefore('?')) { entry.arguments?.getString(it.groupValues[1]).orEmpty() }
        }
    }
}

@Composable
fun NavigatedUI(paddingValues: PaddingValues, startDestination: String = Screen.Main.route, sharedText: String? = null) {
    var alreadyForwardedArgs by remember { mutableStateOf(sharedText?.isNotBlank()) }
    val entry by LocalAppNav.current.controller.currentBackStackEntryAsState()
    SideEffect { CurrentPage.set(entry) }

    NavHost(
        navController = LocalAppNav.current.controller,
        startDestination = startDestination,
        enterTransition = { fadeIn(tween(300)) },
        exitTransition = { fadeOut(tween(300)) },
        popEnterTransition = { fadeIn(tween(300)) },
        popExitTransition = { fadeOut(tween(300)) },
        modifier = Modifier.padding(paddingValues)
    ) {
        composable(Screen.Main.route) {
            FeaturesPage()
        }

        composable(
            route = Screen.Category.route,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("search") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            CategoryPage(backStackEntry.arguments?.getString("id"), backStackEntry.arguments?.getBoolean("search") == true)
        }

        composable(
            route = Screen.Dynamic.route,
            arguments = listOf(navArgument(Screen.Dynamic.ARG) { type = NavType.StringType })
        ) { backStackEntry ->
            val feature = FeatureRegistry.byRoute(backStackEntry.arguments?.getString(Screen.Dynamic.ARG))
            val localAppNav = LocalAppNav.current
            feature?.let { localAppNav.lastFeatureRoute = it.route }

            val context = LocalContext.current
            // However it was opened (home, bottom bar, widget, share...)
            LaunchedEffect(feature) { feature?.let { FeatureUsage.opened(context, it) } }
            if (feature != null) {
                val args = alreadyForwardedArgs?.let { if (it) mapOf("sharedText" to sharedText) else emptyMap() }
                alreadyForwardedArgs = false
                feature.Content(args)
            } else {
                Text("Feature not found")
            }
        }

        composable(Screen.Settings.route) {
            SettingsPage()
        }

        composable(
            route = Screen.SettingsFeatures.route,
            arguments = listOf(navArgument(Screen.Dynamic.ARG) { type = NavType.StringType })
        ) { backStackEntry ->
            FeatureSettingsPage(FeatureRegistry.byRoute(backStackEntry.arguments?.getString(Screen.Dynamic.ARG)))
        }

        composable(
            route = "settings/{subpage}",
            arguments = listOf(navArgument("subpage") { type = NavType.StringType })
        ) { backStackEntry ->
            val sub = backStackEntry.arguments?.getString("subpage")
            SettingsSubPageContent(sub)
        }
    }
}

// ----------------------------
// Bottom Navigation Bar
// ----------------------------

@Composable
fun BottomNavigationBar() {
    val navController = LocalAppNav.current.controller
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val appNav = LocalAppNav.current
    val lastFeature = appNav.lastFeature
    val currentDestination = navBackStackEntry?.destination
    val isDynamicPage = currentDestination?.route == Screen.Dynamic.route
    val currentFeature = if (isDynamicPage) {
        FeatureRegistry.byRoute(navBackStackEntry?.arguments?.getString(Screen.Dynamic.ARG)) ?: lastFeature
    } else {
        lastFeature
    }

    // The page's background, as one surface with it
    NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
        val screens: List<Screen> = listOf(Screen.Main, Screen.Dynamic, Screen.Settings)
        screens.forEach { screen ->
            val selected = when (screen) {
                Screen.Main, Screen.Category -> currentDestination?.hierarchy?.any {
                    it.route == Screen.Main.route || it.route == Screen.Category.route
                } == true
                Screen.Dynamic -> isDynamicPage
                Screen.Settings, Screen.SettingsFeatures -> currentDestination?.route?.startsWith(Screen.Settings.route) == true
            }

            val (icon, label) = when (screen) {
                Screen.Main, Screen.Category -> Pair(Icons.Default.Home, stringResource(R.string.nav_main_page))
                Screen.Dynamic -> {
                    if (currentFeature != null) {
                        Pair(currentFeature.icon, stringResource(currentFeature.titleStringResId))
                    } else {
                        Pair(Icons.Default.Dehaze, stringResource(R.string.undefined))
                    }
                }
                Screen.Settings, Screen.SettingsFeatures -> Pair(Icons.Default.Settings, stringResource(R.string.nav_settings))
            }

            NavigationBarItem(
                selected = selected,
                onClick = {
                    val route = when (screen) {
                        Screen.Main, Screen.Category -> Screen.Main.route
                        Screen.Dynamic -> lastFeature?.let { Screen.Dynamic.createRoute(it) } ?: Screen.Main.route
                        Screen.Settings, Screen.SettingsFeatures -> Screen.Settings.route
                    }

                    navController.navigate(route) {
                        popUpTo(navController.graph.startDestinationId)
                        launchSingleTop = true
                    }
                },
                // Icons only: the name is for screen readers
                icon = { Icon(icon, contentDescription = label) }
            )
        }
    }
}

/**
 * Opens [feature]'s page (e.g. a task's tool, see `LinuxTask.source`), unless it's open already; false
 * without one (e.g. the subsystem's own task).
 */
fun NavController.openFeature(feature: AppFeature?): Boolean {
    if (feature == null) return false
    val entry = currentBackStackEntry
    if (entry?.destination?.route == Screen.Dynamic.route && entry.arguments?.getString(Screen.Dynamic.ARG) == feature.route) return true
    navigate(Screen.Dynamic.createRoute(feature))
    return true
}

/**
 * Opens settings page [page] on top of the current one, so back comes here again; nothing if it's
 * the page already. Not single-top: the settings' pages share a route, it'd take this one's place.
 */
fun NavController.openSettingsPage(page: SettingsSubPage) {
    if (currentBackStackEntry?.arguments?.getString("subpage") == page.name.lowercase()) return
    navigate(page.route)
}
