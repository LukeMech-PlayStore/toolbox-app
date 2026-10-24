package org.lukemech.lmtoolbox.helpers

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import java.io.File
import org.lukemech.lmtoolbox.ui.components.ModulePage
import org.lukemech.lmtoolbox.linux.LinuxScripts

/**
 * A tool on the Linux subsystem's programs ([linuxExtensions], each part a Play module, features/<name>).
 * Here only what the app needs before they're installed (to list, search, open and clear it). Its page,
 * settings are its [FeatureUi] ([uiClass]): in its part's module, shown once that's there, or the app's
 * (a tool on several parts, e.g. the Minecraft server's).
 */
abstract class ModuleFeature(
    override val route: String,
    override val titleStringResId: Int,
    override val keywordsResId: Int,
    override val descriptionResId: Int,
    override val category: FeatureCategory,
    /** Its icon: a material one, or a vector drawable of the app's ([moduleIconRes]). */
    private val moduleIcon: ImageVector? = null,
    @param:DrawableRes private val moduleIconRes: Int? = null,
    override val linuxExtensions: List<String>,
    override val extraExtensions: List<String> = emptyList(),
    override val uiClass: String,
    /** Its folder of scripts in the Linux subsystem (see [LinuxScripts.scriptDir]), deleted by [clearData]. */
    val scriptDir: String? = null,
    /** Its folder in the app's cache ([cacheDir]), deleted by [clearData]. */
    private val cacheName: String? = null,
    override val resourceTiles: List<ResourceTile> = emptyList(),
) : AppFeature {
    override val icon: ImageVector
        @Composable get() = moduleIconRes?.let { ImageVector.vectorResource(it) } ?: checkNotNull(moduleIcon) { "$route has no icon" }

    override fun dataFiles(context: Context) = listOfNotNull(
        scriptDir?.let { LinuxScripts.scriptDir(context, it) },
        cacheName?.let { cacheDir(context) }
    )

    /** Its folder in the app's cache, e.g. for the files it makes (and shares from there). */
    fun cacheDir(context: Context) = File(context.cacheDir, checkNotNull(cacheName) { "$route keeps nothing in the cache" })

    private val ui get() = FeatureDelivery.ui(this)

    @Composable
    override fun Content(arguments: Map<String, String?>?) = ModulePage(this, arguments)

    override val hasSettingsContent get() = ui?.hasSettingsContent == true

    @Composable
    override fun SettingsContent() {
        ui?.SettingsContent()
    }

    override fun onClearData() {
        ui?.onClearData()
    }
}
