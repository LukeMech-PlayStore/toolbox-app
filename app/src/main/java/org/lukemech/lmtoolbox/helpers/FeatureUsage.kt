package org.lukemech.lmtoolbox.helpers

import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.compose.runtime.mutableStateOf
import java.io.File
import org.lukemech.lmtoolbox.MainActivity
import org.lukemech.lmtoolbox.linux.LinuxTasks

/**
 * What the user did with the features: favourites, recently / ever opened, the default tool and
 * start page. Backed by preferences; reads are observable by Compose, so every
 * page shows the same state. [load] runs once at app start (MainActivity).
 */
object FeatureUsage {
    private const val PREF_FAVOURITES = "favourites"
    private const val PREF_RECENT = "recent"
    private const val PREF_USED = "features_used"
    private const val PREF_LAST = "last_feature"
    private const val PREF_DEFAULT = "default_feature"
    private const val PREF_START_PAGE = "start_page"

    /** Page the app opens on: home, the favourites, or [barFeature]. */
    enum class StartPage { Home, Favourites, Feature }

    private const val RECENT_LIMIT = 15

    private val favourites = mutableStateOf(emptyList<String>())
    private val recent = mutableStateOf(emptyList<String>())
    private val used = mutableStateOf(emptySet<String>())
    private val last = mutableStateOf("")
    private val default = mutableStateOf("")
    private val startPage = mutableStateOf(StartPage.Home)

    fun load(context: Context) {
        favourites.value = PreferenceHelper.getStringList(context, PREF_FAVOURITES)
        recent.value = PreferenceHelper.getStringList(context, PREF_RECENT)
        last.value = PreferenceHelper.get(context, PREF_LAST, "")
        default.value = PreferenceHelper.get(context, PREF_DEFAULT, "")
        startPage.value = StartPage.entries.firstOrNull { it.name == PreferenceHelper.get(context, PREF_START_PAGE, "") }
            ?: StartPage.Home
        used.value = PreferenceHelper.getStringList(context, PREF_USED).toSet()
    }

    private fun features(routes: Collection<String>) =
        routes.mapNotNull { route -> FeatureRegistry.features.find { it.route == route } }

    // --- Favourites ---

    /** Starred features, the latest first. */
    val favouriteFeatures: List<AppFeature> get() = features(favourites.value.asReversed())

    fun isFavourite(feature: AppFeature) = feature.route in favourites.value

    fun toggleFavourite(context: Context, feature: AppFeature) {
        favourites.value = if (isFavourite(feature)) favourites.value - feature.route else favourites.value + feature.route
        PreferenceHelper.putStringList(context, PREF_FAVOURITES, favourites.value)
    }

    // --- Opened ---

    /** Opened features, the latest first. */
    val recentFeatures: List<AppFeature> get() = features(recent.value)

    fun clearRecent(context: Context) {
        recent.value = emptyList()
        PreferenceHelper.putStringList(context, PREF_RECENT, recent.value)
    }

    /** Ever opened (their settings are offered only then). */
    fun isUsed(feature: AppFeature) = feature.route in used.value

    val lastFeature: AppFeature? get() = features(listOf(last.value)).firstOrNull()

    /** Call whenever a feature's page is shown. */
    fun opened(context: Context, feature: AppFeature) {
        val route = feature.route
        if (recent.value.firstOrNull() != route) {
            recent.value = (listOf(route) + (recent.value - route)).take(RECENT_LIMIT)
            PreferenceHelper.putStringList(context, PREF_RECENT, recent.value)
        }
        if (route !in used.value) {
            used.value += route
            PreferenceHelper.putStringList(context, PREF_USED, used.value.toList())
        }
        if (last.value != route) {
            last.value = route
            PreferenceHelper.put(context, PREF_LAST, route)
        }
    }

    /**
     * Drops [feature]'s history here (recent, ever opened, last), see [AppFeature.clearData].
     * Favourites and pinning are the app's settings, not the feature's: they stay.
     */
    fun forget(context: Context, feature: AppFeature) {
        val route = feature.route
        recent.value -= route
        used.value -= route
        PreferenceHelper.putStringList(context, PREF_RECENT, recent.value)
        PreferenceHelper.putStringList(context, PREF_USED, used.value.toList())
        if (last.value == route) {
            last.value = ""
            PreferenceHelper.remove(context, PREF_LAST)
        }
    }

    /** Whether [feature] was opened (so [forget] would change something). */
    fun hasHistory(feature: AppFeature) = feature.route in used.value || feature.route in recent.value || feature.route == last.value

    fun clearFavourites(context: Context) {
        favourites.value = emptyList()
        PreferenceHelper.putStringList(context, PREF_FAVOURITES, favourites.value)
    }

    // --- Defaults ---

    /** Feature in the bottom bar when the app starts (else the last opened one). */
    val defaultFeature: AppFeature? get() = features(listOf(default.value)).firstOrNull()

    fun setDefaultFeature(context: Context, feature: AppFeature?) {
        default.value = feature?.route.orEmpty()
        PreferenceHelper.put(context, PREF_DEFAULT, default.value)
    }

    val start: StartPage get() = startPage.value

    fun setStart(context: Context, page: StartPage) {
        startPage.value = page
        PreferenceHelper.put(context, PREF_START_PAGE, page.name)
    }

    /** Feature shown in the bottom bar when the app starts. */
    val barFeature: AppFeature? get() = defaultFeature ?: lastFeature ?: FeatureRegistry.features.firstOrNull()
}

/** Resetting the app, in steps from defaults to everything. */
object AppReset {
    /** Whether a general setting differs from its default (so [general] would change something). */
    val generalChanged: Boolean
        get() = FeatureUsage.start != FeatureUsage.StartPage.Home || FeatureUsage.defaultFeature != null

    /** Back to the general settings' defaults: home page on start, no feature pinned. The favourites stay. */
    fun general(context: Context) {
        FeatureUsage.setStart(context, FeatureUsage.StartPage.Home)
        FeatureUsage.setDefaultFeature(context, null)
    }

    /** Whether any feature has something [features] would clear. */
    fun featuresHaveData(context: Context) = FeatureRegistry.features.any { it.hasData(context) }

    /** Every feature's data, as [clearData] of each, and every package of the Linux subsystem, the core too. */
    fun features(context: Context) {
        FeatureRegistry.features.forEach { it.clearData(context, programs = false) }
        LinuxTasks.removeAllExtensions(context)
    }

    /**
     * Deletes everything the app keeps (preferences, files, cache, the Linux subsystem) and starts
     * it afresh. Unlike the system's "clear storage", runtime permissions stay: they aren't app data.
     */
    fun everything(context: Context) {
        val app = context.applicationContext
        val dataDir = app.dataDir
        File(dataDir, "shared_prefs").list()?.forEach { app.deleteSharedPreferences(it.removeSuffix(".xml")) }
        // "lib" is the system's link to the native libraries, not app data
        dataDir.listFiles()?.filter { it.name != "lib" }?.forEach { it.deleteRecursively() }
        app.externalCacheDir?.deleteRecursively()
        restart(app)
    }

    /** Starts the app in a new process, so nothing in memory outlives what was deleted. */
    private fun restart(context: Context) {
        val app = context.applicationContext
        app.startActivity(
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        Process.killProcess(Process.myPid())
    }
}
