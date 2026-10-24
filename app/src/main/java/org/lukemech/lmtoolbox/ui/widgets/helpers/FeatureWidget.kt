package org.lukemech.lmtoolbox.ui.widgets.helpers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import androidx.compose.material3.ColorScheme
import org.lukemech.lmtoolbox.MainActivity
import org.lukemech.lmtoolbox.helpers.AppFeature
import org.lukemech.lmtoolbox.helpers.PreferenceHelper
import org.lukemech.lmtoolbox.ui.toolboxColorScheme

/**
 * Home screen widget of a feature, built from [RemoteViews]. Anything that changes often should
 * be sent with [partiallyUpdateAll] as a few view properties (image level, tint, text), never as
 * bitmaps: launchers can't apply those smoothly.
 *
 * Adding a widget (X = the feature):
 * 1. ui/widgets/features/XWidget.kt: `class XWidget : FeatureWidget(XFeature)` implementing
 *    [render], usually with res/layout/w_x.xml as its layout.
 * 2. res/xml/w_x_info.xml: its size and description (copy w_compass_info.xml).
 * 3. AndroidManifest.xml: a `<receiver>` for XWidget using that file (copy the compass one).
 * 4. features/<category>/XFun.kt: `override val widget by lazy { XWidget() }`, which puts its settings in
 *    the feature's ones (also opened by the launcher's widget settings).
 */
abstract class FeatureWidget(val feature: AppFeature) : AppWidgetProvider() {

    /** Builds the views of one widget instance of the given size. */
    abstract fun render(context: Context, size: WidgetSize): RemoteViews

    /** Redraws every placed instance of this widget. */
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        manager.getAppWidgetIds(ComponentName(context, this::class.java)).forEach { update(context, manager, it) }
    }

    /**
     * Like [partiallyUpdateAll], but with views per instance (e.g. positions depending on its size);
     * [views] returns null when nothing changed for that one.
     */
    fun partiallyUpdateEach(context: Context, views: (id: Int, size: WidgetSize) -> RemoteViews?) {
        val manager = AppWidgetManager.getInstance(context)
        for (id in manager.getAppWidgetIds(ComponentName(context, this::class.java))) {
            views(id, WidgetSize.of(manager.getAppWidgetOptions(id)))?.let { manager.partiallyUpdateAppWidget(id, it) }
        }
    }

    /** Applies [views] (only the changed properties, same layout as [render]) to every instance. */
    fun partiallyUpdateAll(context: Context, views: RemoteViews) {
        val manager = AppWidgetManager.getInstance(context)
        manager.partiallyUpdateAppWidget(manager.getAppWidgetIds(ComponentName(context, this::class.java)), views)
    }

    private fun update(context: Context, manager: AppWidgetManager, id: Int) {
        manager.updateAppWidget(id, render(context, WidgetSize.of(manager.getAppWidgetOptions(id))))
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, manager, it) }
    }

    // Resized
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        update(context, manager, appWidgetId)
    }

    private var colors: Pair<Int, ColorScheme>? = null

    /** The app's colors, kept while the light / dark mode stays the same (live widgets need them every frame). */
    protected fun colorScheme(context: Context): ColorScheme {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        colors?.let { (cachedMode, scheme) -> if (cachedMode == mode) return scheme }
        return toolboxColorScheme(context).also { colors = mode to it }
    }

    /** Preference of [WidgetBackgroundSetting] for this widget: an ARGB color or [TRANSPARENT]. */
    val backgroundKey get() = backgroundKey(feature)

    /**
     * Sets the background from [WidgetBackgroundSetting] on the layout's root (which has the id
     * android:id/background), with the launcher's corner radius on Android 12+.
     */
    protected fun applyBackground(context: Context, views: RemoteViews) {
        val color = PreferenceHelper.get(context, backgroundKey, TRANSPARENT)
        views.setInt(android.R.id.background, "setBackgroundColor", color)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setViewOutlinePreferredRadiusDimen(android.R.id.background, android.R.dimen.system_app_widget_background_radius)
        }
    }

    companion object {
        /** [backgroundKey] value of no background. */
        const val TRANSPARENT = 0

        private fun backgroundKey(feature: AppFeature) = "${feature.route}_widget_background"

        /** Every preference of [feature]'s widget. */
        fun preferenceKeys(feature: AppFeature) = listOf(backgroundKey(feature))

    }

    /** Opens the feature's page in the app. */
    fun openFeatureIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, feature.route.hashCode(),
        MainActivity.openFeatureIntent(context, feature.route),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

/** Size of a widget instance in dp (the portrait one: launchers report a range). */
data class WidgetSize(val widthDp: Int, val heightDp: Int) {
    companion object {
        fun of(options: Bundle) = WidgetSize(
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110),
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
        )
    }
}
