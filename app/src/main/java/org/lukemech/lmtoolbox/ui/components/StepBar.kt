package org.lukemech.lmtoolbox.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Where a page with steps is: [current] of [count], the next one being made while [loadingNext]. */
class Steps(
    val count: Int,
    val current: Int,
    val loadingNext: Boolean,
    /** The current step is under way (e.g. a download): its bar fills with [progress] (0..1), or pulses without. */
    val working: Boolean = false,
    val progress: Float? = null,
    /**
     * What the current step's work is at, in a line right under the bars (e.g. "Downloading…"); how far
     * ([progress]) at its end. Its own words only: which step it is, the bars tell.
     */
    val caption: String? = null,
    /** A short word after it, quieter (e.g. the time left). */
    val detail: String? = null,
    /** Stops that work: a button at the caption's end. */
    val onCancel: (() -> Unit)? = null,
)

/**
 * Where in a page's [count] steps (e.g. the QR generator's kind, content, code) we are: a bar per
 * step, filled up to the [current] one; the next one runs while it's being made ([loadingNext]).
 * Under the page's title (see [PageHeading]'s steps). [caption]: what its work is at, right under
 * the bars (how far first: [progress] as a percentage; [detail] after it), [onCancel]'s button at
 * its end. [failed]: what it tells failed (the bottom bar's error): the caption in red, a ✕ where the percentage goes.
 */
@Composable
fun StepBar(
    count: Int, current: Int, loadingNext: Boolean, working: Boolean = false, progress: Float? = null,
    caption: String? = null, detail: String? = null, onCancel: (() -> Unit)? = null, failed: Boolean = false,
) {
    // The last ones, while they fade or fold away
    val last = remember { Last() }
    caption?.let { last.caption = it }
    progress?.let { last.progress = it }
    detail?.let { last.detail = it }
    onCancel?.let { last.onCancel = it }
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
        // What it's at over the bars
        Reveal(visible = caption != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).heightIn(min = 32.dp)) {
                // How far first, as a pill in the bars' color; failed, a ✕ in the error's
                AnimatedVisibility(visible = failed || working && progress != null, enter = sideEnter, exit = sideExit) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        if (failed) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp).size(20.dp))
                        } else {
                            Text(
                                "${(last.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                // Its words take what's left: the buttons stay at the end
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    // Whole first (measured before the detail, which gets what's left): the detail coming and
                    // going (e.g. the time left) never cuts it short
                    Text(
                        last.caption,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // At the end, by its buttons (e.g. the time left by cancelling): what's left of the line
                    Fade(visible = detail != null, modifier = Modifier.weight(1f)) {
                        Text(
                            last.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                AnimatedVisibility(visible = onCancel != null, enter = sideEnter, exit = sideExit) {
                    IconButton(onClick = { last.onCancel() }, enabled = onCancel != null, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(android.R.string.cancel), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        StepBars(count, current, loadingNext, working, progress)
    }
}

/** What a [StepBar] last showed, kept while it goes. */
private class Last {
    var caption = ""
    var progress = 0f
    var detail = ""
    var onCancel: () -> Unit = {}
}

/** Coming and going at a row's side: fading as it opens up or closes its room. */
private val sideEnter = fadeEnter + expandHorizontally(tween(REVEAL_MS, easing = FastOutSlowInEasing))

private val sideExit = fadeExit + shrinkHorizontally(tween(REVEAL_MS, easing = FastOutSlowInEasing))

/** [steps] as their bar. */
@Composable
fun StepBar(steps: Steps) =
    StepBar(steps.count, steps.current, steps.loadingNext, steps.working, steps.progress, steps.caption, steps.detail, steps.onCancel)

/**
 * Where a page's steps show (its [PageHeading]'s): at the bottom of the app, over its bars
 * ([StepBarSlot]); one page's at a time, the one that set them last.
 */
class StepBarHost {
    var steps by mutableStateOf<Steps?>(null)
        internal set
    internal var owner: Any? = null
}

/** The app's [StepBarHost] (MainActivity's); none: a page shows its steps under its title. */
val LocalStepBarHost = staticCompositionLocalOf<StepBarHost?> { null }

/** Hands a page's [steps] to [host] as they change, and takes them back once the page goes. */
@Composable
internal fun ShowSteps(host: StepBarHost, steps: Steps?) {
    val owner = remember { Any() }
    SideEffect {
        if (steps != null) {
            host.owner = owner
            host.steps = steps
        } else if (host.owner === owner) {
            host.steps = null
        }
    }
    DisposableEffect(host) {
        onDispose {
            if (host.owner === owner) {
                host.owner = null
                host.steps = null
            }
        }
    }
}

/** The page's steps ([LocalStepBarHost]), coming and going as anything does (see Reveal); over the app's bars at its bottom. */
@Composable
fun StepBarSlot() {
    val host = LocalStepBarHost.current ?: return
    val steps = host.steps
    // The last ones, while they fold away
    val last = remember { arrayOfNulls<Steps>(1) }
    if (steps != null) last[0] = steps
    Reveal(visible = steps != null) {
        last[0]?.let { s -> Surface(color = MaterialTheme.colorScheme.background) { StepBar(s) } }
    }
}

/** [StepBar]'s bars. */
@Composable
private fun StepBars(count: Int, current: Int, loadingNext: Boolean, working: Boolean, progress: Float?) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        repeat(count) { i ->
            // The current step's own work, where it stands
            if (working && i == current) {
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier.weight(1f).height(4.dp)
                    )
                } else {
                    LinearProgressIndicator(
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier.weight(1f).height(4.dp)
                    )
                }
                return@repeat
            }
            if (loadingNext && i == current + 1) {
                LinearProgressIndicator(
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier.weight(1f).height(4.dp)
                )
                return@repeat
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(
                        if (i <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

/**
 * A page's (or step's) [content] (scrolling), with its [buttons] kept at the bottom under it: how
 * every tool's main button (create, next, convert...) sits.
 */
@Composable
fun StepWithButtons(content: @Composable () -> Unit, buttons: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) { content() }
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            content = buttons
        )
    }
}
