package org.lukemech.lmtoolbox.helpers

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import com.google.android.play.core.ktx.requestInstall
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A tool's pages that come in the Play module of its Linux package, or the app's (see [AppFeature.uiClass]),
 * loaded once that's installed ([FeatureDelivery.ui]).
 */
interface FeatureUi {
    @Composable
    fun Content(arguments: Map<String, String?>?)

    /** Its options in its settings, after the app's own ones for it; only if [hasSettingsContent]. */
    @Composable
    fun SettingsContent() {}
    val hasSettingsContent: Boolean
        get() = false

    /** Its data is about to be deleted (see clearData): what runs of it stops first (e.g. a server). */
    fun onClearData() {}
}

/**
 * The on-demand Play modules (features/ in the project): one per Linux package, with its archive
 * and the pages of the tool it's for. Installed when a tool is first used, uninstalled with its
 * data. Without Play (e.g. a debug build from Gradle) installing fails; local testing by bundletool
 * stands in for Play (see app/build.gradle.kts), a universal APK has them all.
 */
object FeatureDelivery {
    private const val TAG = "FeatureDelivery"
    private const val CONFIRMATION_REQUEST = 0x5d1

    /** The app's activity, for Play's confirmation of big downloads (see MainActivity). */
    @Volatile
    var activity: WeakReference<Activity>? = null

    private val uis = ConcurrentHashMap<String, FeatureUi>()

    private fun manager(context: Context) = SplitInstallManagerFactory.create(context.applicationContext)

    /**
     * Installs [modules] from Play, [onProgress] getting how much (0..100) is downloaded (null while
     * not known). Throws, saying why, if it can't. The caller asks for those whose archives aren't
     * there: they're asked for even if Play counts them installed (local testing loses their files
     * when the app is reinstalled, yet still counts them).
     */
    suspend fun install(context: Context, modules: List<String>, onProgress: (Float?) -> Unit) {
        val manager = manager(context)
        Log.i(TAG, "Install $modules (Play counts ${manager.installedModules} installed)")
        if (modules.isEmpty()) return

        val done = CompletableDeferred<Unit>()
        // Updates may come before the session's id does
        val states = ConcurrentHashMap<Int, SplitInstallSessionState>()
        var sessionId = -1
        fun handle(state: SplitInstallSessionState) {
            when (state.status()) {
                SplitInstallSessionStatus.DOWNLOADING -> onProgress(
                    state.totalBytesToDownload().takeIf { it > 0 }?.let { state.bytesDownloaded() * 100f / it }
                )
                SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                    val activity = activity?.get()
                    if (activity == null) done.completeExceptionally(IllegalStateException("Play asks to confirm the download"))
                    else manager.startConfirmationDialogForResult(state, activity, CONFIRMATION_REQUEST)
                }
                SplitInstallSessionStatus.INSTALLED -> done.complete(Unit)
                SplitInstallSessionStatus.FAILED -> done.completeExceptionally(IllegalStateException("Google Play: error ${state.errorCode()}"))
                SplitInstallSessionStatus.CANCELED -> done.completeExceptionally(CancellationException("Canceled in Google Play"))
                else -> onProgress(null)
            }
        }
        val listener = SplitInstallStateUpdatedListener { state ->
            Log.i(TAG, "Session ${state.sessionId()}: status ${state.status()}, ${state.bytesDownloaded()}/${state.totalBytesToDownload()}, error ${state.errorCode()}")
            states[state.sessionId()] = state
            if (state.sessionId() == sessionId) handle(state)
        }
        manager.registerListener(listener)
        try {
            sessionId = try {
                // With the app's languages: bundletool's local testing counts a module with strings in
                // a language (not split off, see app/build.gradle.kts) as that language's split, and
                // left it out (installed, but its code not there) unless the device was in it. Play
                // has no language splits of the app to download
                manager.requestInstall(modules = modules, languages = Search.languageTags(context))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw IllegalStateException("Google Play: ${e.message ?: e.javaClass.simpleName}", e)
            }
            // Already installed (0), or what came meanwhile
            if (sessionId == 0) done.complete(Unit) else states[sessionId]?.let(::handle)
            done.await()
        } catch (e: CancellationException) {
            if (sessionId > 0) manager.cancelInstall(sessionId)
            throw e
        } finally {
            manager.unregisterListener(listener)
        }
        // Their code, assets, resources and native libraries, without restarting the app; SplitCompat
        // works on the main thread (off it, it failed quietly and the modules' files weren't there)
        withContext(Dispatchers.Main) {
            val app = SplitCompat.install(context.applicationContext)
            val ui = activity?.get()?.let { SplitCompat.installActivity(it) }
            Log.i(TAG, "Installed $modules; SplitCompat: app $app, activity $ui")
        }
    }

    /** Uninstalls [modules]: Play does it later, in the background, when it needs the space. */
    fun uninstall(context: Context, modules: List<String>) {
        if (modules.isEmpty()) return
        manager(context).deferredUninstall(modules).addOnFailureListener { Log.w(TAG, "Couldn't uninstall $modules", it) }
    }

    /** [feature]'s pages (from its module, or the app's), or null while that module isn't installed. */
    fun ui(feature: AppFeature): FeatureUi? {
        val name = feature.uiClass ?: return null
        uis[name]?.let { return it }
        // A Kotlin object
        val ui = runCatching { Class.forName(name).getField("INSTANCE").get(null) as FeatureUi }.getOrNull() ?: return null
        uis[name] = ui
        return ui
    }
}
