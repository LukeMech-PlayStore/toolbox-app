package org.lukemech.lmtoolbox

import org.lukemech.lmtoolbox.ui.helpers.InstallPromptHost
import org.lukemech.lmtoolbox.ui.components.LocalStepBarHost
import org.lukemech.lmtoolbox.ui.components.StepBarHost
import org.lukemech.lmtoolbox.ui.components.StepBarSlot
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.google.android.play.core.splitcompat.SplitCompat
import java.lang.ref.WeakReference
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.helpers.*
import org.lukemech.lmtoolbox.ui.ToolboxTheme
import org.lukemech.lmtoolbox.ui.helpers.*
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.tasks.TaskService
import org.lukemech.lmtoolbox.ui.helpers.requestSubPage
import org.lukemech.lmtoolbox.tasks.AppTasks

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        // The installed Play modules' pages and assets (see FeatureDelivery)
        SplitCompat.installActivity(this)
    }

    /** Bumped whenever the task list should be opened (notification tap). */
    private val openTasksRequests = mutableIntStateOf(0)
    /** Feature page (or its settings) to open, e.g. from a widget. */
    private val openFeatureRequest = mutableStateOf<OpenFeatureRequest?>(null)

    private fun handleOpenFeature(intent: Intent?) {
        val route = intent?.getStringExtra(EXTRA_OPEN_FEATURE) ?: return
        openFeatureRequest.value = OpenFeatureRequest(
            (openFeatureRequest.value?.count ?: 0) + 1, route, intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false),
            intent.getStringExtra(EXTRA_OPEN_PAGE)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent?.getBooleanExtra(EXTRA_OPEN_TASKS, false) == true) {
            openTasksRequests.intValue++
        }
        if (savedInstanceState == null) handleOpenFeature(intent)
        FeatureUsage.load(this)
        // The "Share to" / "Open in" entries follow what's installed: now, as each task (installing) ends and as tools are removed
        lifecycleScope.launch(Dispatchers.IO) {
            LinuxTasks.markUnlockedOnce(this@MainActivity)
            combine(AppTasks.tasks.map { tasks -> tasks.count { !it.isActive } }, LinuxTasks.extensionsRemoved) { ended, removed -> ended to removed }
                .distinctUntilChanged()
                .collect { SystemEntries.apply(this@MainActivity) }
        }

        // The app needs none of the Linux subsystem: its core comes (from Play) with the first tool on it,
        // and goes once none uses it. What's unpacked is updated with the app by LinuxSetupStarter, once the
        // app shows; the app stays usable meanwhile

        // --- Intent Parsing Logic ---
        var sharedContent: String? = null

        intent?.let {
            when (it.action) {
                Intent.ACTION_SEND -> {
                    // A shared file (its stream) whatever its type, a .txt too; text only without one
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        it.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        it.getParcelableExtra(Intent.EXTRA_STREAM)
                    }
                    sharedContent = uri?.toString() ?: it.getStringExtra(Intent.EXTRA_TEXT)
                }
                // Several files (PDFs to merge): their URIs, a line each
                Intent.ACTION_SEND_MULTIPLE -> {
                    val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        it.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        it.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                    }
                    sharedContent = uris?.joinToString("\n")
                }
                // Case 3: Opened via "Open With..." (File Handler)
                Intent.ACTION_VIEW -> {
                    sharedContent = it.data?.toString()
                }
            }
        }

        // Extract simplified class name for navigation routing
        val className = intent?.component?.className
            ?.split('.')
            ?.takeLast(3)
            ?.joinToString(".")
            ?: ""

        setContent {
            ToolboxTheme {
                // sharedContent now holds either the Text or the File URI string
                MainScreen(className, sharedContent, openTasksRequests.intValue, openFeatureRequest.value)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        FeatureDelivery.activity = WeakReference(this)
        TaskService.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        TaskService.onAppBackgrounded(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_TASKS, false)) openTasksRequests.intValue++
        // The app may already run (then this arrives here, not in onCreate)
        handleOpenFeature(intent)
    }

    companion object {
        private const val EXTRA_OPEN_TASKS = "open_tasks"
        private const val EXTRA_OPEN_FEATURE = "open_feature"
        private const val EXTRA_OPEN_SETTINGS = "open_settings"
        private const val EXTRA_OPEN_PAGE = "open_page"

        // Each with an identifier of its own: PendingIntents of intents differing only in their extras are one
        // and the same, the latest's extras for all (a server's notification opened the tasks, as the subsystem's)

        /** Brings the app to front (reusing the running activity) on the task list. */
        fun openTasksIntent(context: Context) = Intent(context, MainActivity::class.java)
            .setIdentifier(EXTRA_OPEN_TASKS)
            .putExtra(EXTRA_OPEN_TASKS, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        /**
         * Brings the app to front (reusing the running activity) on the page of the feature with
         * [route] (at its part [page], e.g. a tab: see requestSubPage), or its [settings].
         */
        fun openFeatureIntent(context: Context, route: String, settings: Boolean = false, page: String? = null) = Intent(context, MainActivity::class.java)
            .setIdentifier("$EXTRA_OPEN_FEATURE/$route" + (if (settings) "/$EXTRA_OPEN_SETTINGS" else "") + (page?.let { "/$it" } ?: ""))
            .putExtra(EXTRA_OPEN_FEATURE, route)
            .putExtra(EXTRA_OPEN_SETTINGS, settings)
            .putExtra(EXTRA_OPEN_PAGE, page)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreen(intent: String, sharedText: String?, openTasksRequests: Int = 0, openFeatureRequest: OpenFeatureRequest? = null) {

    // Fixed for the activity's life: a new start destination would reset the navigation
    val startDestination = remember {
        SystemEntries.featureOf(intent)?.let { Screen.Dynamic.createRoute(it) }
            ?: when (FeatureUsage.start) {
                FeatureUsage.StartPage.Home -> null
                FeatureUsage.StartPage.Favourites -> Screen.Category.createRoute(Screen.Category.FAVOURITES)
                FeatureUsage.StartPage.Feature -> FeatureUsage.barFeature?.let { Screen.Dynamic.createRoute(it) }
            }
            ?: Screen.Main.route
    }

    val navController = rememberNavController()
    val nav = remember { AppNav(navController, FeatureUsage.barFeature?.route) }
    val notificationsEnabled = rememberNotificationsEnabled()

    // The Linux subsystem's unpacking / update, full screen over the app (opened from the bottom bar)
    var showSetup by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // After "Not now", the notifications bar only shows in the settings
    val notificationsPostponed = rememberNotificationsPostponed(notificationsEnabled)
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    val showNotificationsBar = !notificationsEnabled &&
        (!notificationsPostponed.value || route?.startsWith(Screen.Settings.route) == true)

    @Composable
    // Above the bottom navigation: the page's steps, what runs (or failed) and the notifications' state
    fun StatusBars() = Column {
        StepBarSlot()
        TaskStatusBar(onOpenSetup = { showSetup = true })
        NotificationPermissionBar(visible = showNotificationsBar, postponed = notificationsPostponed)
    }

    LinuxSetupStarter()

    // Where pages' steps show: at the bottom, over the bars (see StepBarSlot)
    val stepBarHost = remember { StepBarHost() }
    CompositionLocalProvider(LocalAppNav provides nav, LocalStepBarHost provides stepBarHost) {
        Box {
            // A tool needed the subsystem while it's still being set up: back from its page, asking
            // (in a toast) to wait; once for a page running several commands
            LaunchedEffect(Unit) {
                var handledAt = 0L
                LinuxTasks.envNotReady.collect {
                    val now = SystemClock.uptimeMillis()
                    if (now - handledAt < NOT_READY_DEBOUNCE_MS) return@collect
                    handledAt = now
                    if (!navController.popBackStack()) navController.navigate(Screen.Main.route)
                    Toast.makeText(context, R.string.s_setup_wait, Toast.LENGTH_LONG).show()
                }
            }
            // Only once the NavHost below exists
            LaunchedEffect(openTasksRequests) {
                if (openTasksRequests > 0) {
                    navController.openSettingsPage(SettingsSubPage.Tasks)
                }
            }
            LaunchedEffect(openFeatureRequest) {
                val request = openFeatureRequest ?: return@LaunchedEffect
                val feature = FeatureRegistry.byRoute(request.route) ?: return@LaunchedEffect
                requestSubPage(feature.route, request.page)
                val screen = if (request.settings) Screen.SettingsFeatures else Screen.Dynamic
                // Not launchSingleTop: it would keep another feature's page (same pattern, other route)
                val current = navController.currentBackStackEntry
                if (current?.destination?.route == screen.route && current.arguments?.getString(Screen.Dynamic.ARG) == feature.route) return@LaunchedEffect
                navController.navigate(
                    if (request.settings) Screen.SettingsFeatures.createRoute(feature) else Screen.Dynamic.createRoute(feature)
                )
            }

            Scaffold(
                bottomBar = {
                    Column {
                        StatusBars()
                        BottomNavigationBar()
                    }
                }
            ) { paddingValues ->

                NavigatedUI(paddingValues, startDestination, sharedText)
            }

            if (showSetup) LinuxSetupScreen(onClose = { showSetup = false })
            // What's to be installed for a tool, asked first (see InstallPrompt)
            InstallPromptHost()
        }
    }
}

/** Refusals of commands closer than this are one page's (e.g. loading several things), handled once. */
private const val NOT_READY_DEBOUNCE_MS = 1500L

/** A feature page (or its [settings]) to open; [count] lets the same one be asked for again. */
data class OpenFeatureRequest(val count: Int, val route: String, val settings: Boolean, val page: String? = null)
