package org.lukemech.lmtoolbox.ui.widgets.helpers

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import org.lukemech.lmtoolbox.MainActivity
import org.lukemech.lmtoolbox.helpers.FeatureRegistry

/**
 * A widget's settings in the launcher (`android:configure` in the w_*_info.xml files): opens its
 * feature's settings in the app, as a widget tap does, and closes.
 *
 * Invisible and in a task of its own (see the manifest): in the app's task, starting the app
 * would only bring that task to front and drop which page to open.
 */
class WidgetSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // The widget stays as it is either way
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))

        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.className
        FeatureRegistry.features.firstOrNull { it.widget?.javaClass?.name == provider }?.let {
            startActivity(MainActivity.openFeatureIntent(this, it.route, settings = true))
        }
        finish()
    }
}
