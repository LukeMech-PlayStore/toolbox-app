package org.lukemech.lmtoolbox.helpers

import android.content.Context
import org.lukemech.lmtoolbox.linux.PartUsers
import org.lukemech.lmtoolbox.linux.LinuxParts
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.ui.widgets.helpers.FeatureWidget

/**
 * Whether [this] tool is there to use: its packages are installed, and it's their user (it got them or ran
 * on them) or unlocked by another's installation (see [PartUsers.isUnlocked]). Uninstalled, it's neither,
 * though its packages may stay for another tool (e.g. pypkgs). One without any always is.
 */
fun AppFeature.isInstalled(ctx: Context) = linuxExtensions.isEmpty() ||
    (LinuxParts.missingExtensions(ctx, linuxExtensions).isEmpty() && (LinuxTasks.hasExtension(ctx, this) || PartUsers.isUnlocked(ctx, route)))

/** Its preference keys, its widget's too. */
private fun AppFeature.allPreferenceKeys() = preferenceKeys + if (widget != null) FeatureWidget.preferenceKeys(this) else emptyList()

/** Whether [clearData] would remove anything. */
fun AppFeature.hasData(context: Context): Boolean =
    FeatureUsage.hasHistory(this) || allPreferenceKeys().any { PreferenceHelper.contains(context, it) } ||
        dataFiles(context).any { it.exists() } || LinuxTasks.hasExtension(context, this) || PartUsers.isUnlocked(context, route)

/**
 * Deletes everything the feature keeps: its preferences, its widget's settings, its files and its
 * unpacked tools, and forgets it was ever opened (recent too). Favourites and pinning are the app's settings: they
 * stay (see [AppReset.general]).
 */
fun AppFeature.clearData(context: Context, programs: Boolean = true) {
    // What runs of it first, its files under it
    onClearData()
    allPreferenceKeys().forEach { PreferenceHelper.remove(context, it) }
    // Whatever still writes there as it's stopped (onClearData) and anything deleting it too: deleteTree copes
    dataFiles(context).forEach { it.deleteTree() }
    if (programs) LinuxTasks.removeExtension(context, this)
    FeatureUsage.forget(context, this)

    // Back to the defaults: share / open-in entries shown, widgets redrawn
    SystemEntries.apply(context, this)
    widget?.updateAll(context)
}
