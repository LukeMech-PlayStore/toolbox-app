package org.lukemech.lmtoolbox.helpers

/** The app's pages, as the navigation graph (ui/helpers/Navigator.kt) has them. */
sealed class Screen(val route: String) {
    object Main : Screen("main")
    /** A tool's page, by its [AppFeature.route] (argument [ARG]). */
    object Dynamic : Screen("feature/{route}") {
        const val ARG = "route"
        fun createRoute(feature: AppFeature) = "feature/${feature.route}"
    }
    /** A home page category: a [FeatureCategory] name, [FAVOURITES], [RECENT] or [ALL]; typing in its search right away if searched. */
    object Category : Screen("category/{id}?search={search}") {
        const val FAVOURITES = "favourites"
        const val RECENT = "recent"
        const val ALL = "all"
        fun createRoute(id: String, search: Boolean = false) = "category/$id" + if (search) "?search=true" else ""
    }
    object Settings : Screen("settings")
    /** A tool's settings, by its route (argument [Dynamic.ARG]). */
    object SettingsFeatures : Screen("settings/features/{route}") {
        fun createRoute(feature: AppFeature) = "settings/features/${feature.route}"
    }
}

enum class SettingsSubPage {
    OpenSourceLicenses,
    Tasks,
    /** The Linux subsystem's parts on the device (see InstalledPackagesPage). */
    Packages,
    /** Settings of the features opened so far, by category. */
    ToolSettings
    ;

    val route: String get() = "${Screen.Settings.route}/${name.lowercase()}"
    companion object {
        fun fromRoute(sub: String?): SettingsSubPage? =
            entries.firstOrNull { it.name.equals(sub, ignoreCase = true) }
    }
}
