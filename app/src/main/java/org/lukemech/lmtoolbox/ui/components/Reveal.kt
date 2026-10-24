package org.lukemech.lmtoolbox.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** How long anything takes to come into sight or go out of it, across the app. */
const val REVEAL_MS = 300

/** Coming into sight: unfolding as it fades in (over [ms]). */
fun revealEnter(ms: Int = REVEAL_MS): EnterTransition =
    fadeIn(tween(ms, easing = FastOutSlowInEasing)) + expandVertically(tween(ms, easing = FastOutSlowInEasing))

/** Going out of sight: folding away as it fades out (over [ms]). */
fun revealExit(ms: Int = REVEAL_MS): ExitTransition =
    fadeOut(tween(ms, easing = FastOutSlowInEasing)) + shrinkVertically(tween(ms, easing = FastOutSlowInEasing))

/** Fading alone, for what keeps its place as it comes and goes (e.g. a spinner in a row). */
val fadeEnter: EnterTransition = fadeIn(tween(REVEAL_MS, easing = FastOutSlowInEasing))
val fadeExit: ExitTransition = fadeOut(tween(REVEAL_MS, easing = FastOutSlowInEasing))

/**
 * [content] while [visible], unfolding into sight and folding away as everything in the app does
 * ([revealEnter], [revealExit]): settings that show only sometimes, a tool's options, bars.
 */
@Composable
fun Reveal(visible: Boolean, modifier: Modifier = Modifier, content: @Composable AnimatedVisibilityScope.() -> Unit) =
    AnimatedVisibility(visible = visible, modifier = modifier, enter = revealEnter(), exit = revealExit(), content = content)

/**
 * [content] while [visible], fading in and out where it is ([fadeEnter], [fadeExit]): a spinner in a
 * row, a button by a title. Anywhere, also where AnimatedVisibility would take an outer Column's scope.
 */
@Composable
fun Fade(visible: Boolean, modifier: Modifier = Modifier, content: @Composable AnimatedVisibilityScope.() -> Unit) =
    AnimatedVisibility(visible = visible, modifier = modifier, enter = fadeEnter, exit = fadeExit, content = content)
