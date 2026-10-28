package org.lukemech.lmtoolbox.helpers

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import java.io.File
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.ui.widgets.helpers.FeatureWidget
import org.lukemech.lmtoolbox.linux.LinuxTasks

/** Groups of features on the home page and in the feature settings, in this order. */
enum class FeatureCategory(@param:StringRes val titleRes: Int, val icon: ImageVector) {
    Measure(R.string.cat_measure, Icons.Default.Straighten),
    Electronics(R.string.cat_electronics, Icons.Default.DeveloperBoard),
    Reference(R.string.cat_reference, Icons.AutoMirrored.Default.MenuBook),
    Finance(R.string.cat_finance, Icons.Default.AccountBalance),
    Media(R.string.cat_media, Icons.Default.VideoLibrary),
    Servers(R.string.cat_servers, Icons.Default.Dns),
}

/**
 * Interface for a feature in the application.
 * Each feature must provide a unique route, a display name (string resource ID),
 * an icon, and a Composable for its main content. Optionally, it can provide a settings Composable.
 */
interface AppFeature {
    /**
     * Unique string identifying the route for this feature (e.g., "compass", "ruler").
     */
    val route: String

    /**
     * String resource ID for the display name of the feature (for navigation, titles, etc.).
     */
    val titleStringResId: Int

    /**
     * Comma-separated search words (translated: search covers every app language, see [Search]),
     * for what the feature does rather than its name, e.g. "currency, exchange, money".
     */
    @get:StringRes
    val keywordsResId: Int

    /** A line of what it's for, catchy, under its name in the lists (e.g. "Find north, wherever you are"). */
    @get:StringRes
    val descriptionResId: Int

    /** Home page category the feature is listed under. */
    val category: FeatureCategory

    /**
     * Icon to represent this feature in navigation. Composable, so it can come from a drawable
     * resource too (ImageVector.vectorResource) when that one is needed elsewhere anyway.
     */
    val icon: ImageVector
        @Composable get

    /**
     * Composable function that renders the main screen for this feature.
     * @param arguments Optional arguments passed to the feature.
     */
    @Composable
    fun Content(arguments: Map<String, String?>?)

    /**
     * The feature's own options in its settings page, between the common ones (favourite, delete)
     * and its widget's (see [widget]); only if [hasSettingsContent].
     */
    @Composable
    fun SettingsContent() {}

    /** Its data is about to be deleted ([clearData]): what runs of it stops first (e.g. a server). */
    fun onClearData() {}

    /**
     * What its work's tiles show of what it takes (ui.components.ResourceTiles), in order: e.g. the
     * network for a download, storage for an encoder; none: it shows none.
     */
    val resourceTiles: List<ResourceTile>
        get() = emptyList()

    val hasSettingsContent: Boolean
        get() = false

    /** Preference keys the feature stores (those of its widget are known already), cleared by [clearData]. */
    val preferenceKeys: List<String>
        get() = emptyList()

    /** Files and folders the feature stores, deleted by [clearData]. */
    fun dataFiles(context: Context): List<File> = emptyList()

    /**
     * The Linux subsystem packages its tools come in (e.g. "ytdlp", which builds on "python" and
     * "ffmpeg"; the Minecraft server's "jdk" and "cloudflared"), with what they build on, each a Play
     * module: installed and unpacked on its first use, deleted (and uninstalled) by [clearData]; none
     * if it needs none.
     */
    val linuxExtensions: List<String>
        get() = emptyList()

    /**
     * Packages it may use besides, which it installs itself when it needs one (e.g. the Minecraft
     * server's Java versions, for a server's version: see [LinuxTasks.installParts]): used while they're
     * unpacked (for a tool using its [linuxExtensions]), deleted by [clearData].
     */
    val extraExtensions: List<String>
        get() = emptyList()

    /**
     * Whether it uses [name], one of its [extraExtensions], now (e.g. a server's tunnel turned on): kept while
     * any tool does (see [org.lukemech.lmtoolbox.features.servers.common.ServerTunnels.prune]).
     */
    fun usesExtraExtension(context: Context, name: String): Boolean = false

    /**
     * Its pages' [FeatureUi] (a Kotlin object), when it's on the Linux subsystem: in its part's Play module
     * ([linuxExtensions], features/<name>) or the app's; see [FeatureDelivery.ui].
     */
    @Suppress("SameReturnValue") // The default, for features without one
    val uiClass: String?
        get() = null

    /** The feature's home screen widget (see ui/widgets/features), if it has one; its settings come with it. */
    @Suppress("SameReturnValue") // The default, for features without one
    val widget: FeatureWidget?
        get() = null
}
