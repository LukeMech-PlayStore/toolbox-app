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
import org.lukemech.lmtoolbox.tasks.AppTask

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

    /** Told by the navigation itself as it moves: no composition or coroutine in between. */
    internal val listener = NavController.OnDestinationChangedListener { _, destination, arguments ->
        route = destination.route?.let { pattern ->
            Regex("""\{(\w+)\}""").replace(pattern.substringBefore('?')) { arguments?.getString(it.groupValues[1]).orEmpty() }
        }
    }
}

@Composable
fun NavigatedUI(paddingValues: PaddingValues, startDestination: String = Screen.Main.route, sharedText: String? = null) {
    var alreadyForwardedArgs by remember { mutableStateOf(sharedText?.isNotBlank()) }
    val controller = LocalAppNav.current.controller
    DisposableEffect(controller) {
        controller.addOnDestinationChangedListener(CurrentPage.listener)
        onDispose { controller.removeOnDestinationChangedListener(CurrentPage.listener) }
    }

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
 * Opens [feature]'s page (e.g. a task's tool, see `AppTask.source`), unless it's open already, at its part [page]
 * (e.g. a tab, see [SubPageRequest]); false without one (e.g. the subsystem's own task).
 */
fun NavController.openFeature(feature: AppFeature?, page: String? = null): Boolean {
    if (feature == null) return false
    requestSubPage(feature.route, page)
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

/** The part of a tool's page to show (its route, the part's name), asked for as it's opened: its page takes it ([SubPageRequest]). */
private val subPageRequest = kotlinx.coroutines.flow.MutableStateFlow<Pair<String, String>?>(null)

/** Asks the page of the tool with [route] to show its part [page] (e.g. a tab; null: as it is), now or as it's opened. */
fun requestSubPage(route: String, page: String?) {
    if (page != null) subPageRequest.value = route to page
}

/**
 * A page with parts (tabs) taking what [requestSubPage] asked of [feature]'s: [open] gets the part's name (from the
 * bottom bar, a notification), once.
 */
@Composable
fun SubPageRequest(feature: AppFeature, open: (String) -> Unit) {
    val request by subPageRequest.collectAsState()
    LaunchedEffect(request) {
        val (route, page) = request?.takeIf { it.first == feature.route } ?: return@LaunchedEffect
        subPageRequest.value = null
        open(page)
    }
}
