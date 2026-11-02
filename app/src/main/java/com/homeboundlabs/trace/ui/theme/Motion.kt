package com.homeboundlabs.trace.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically

/**
 * The app's three animation speeds, so everything moves alike:
 * [QUICK] for colour changes and fades, [STANDARD] for most movement (screens, sheets, markers,
 * selection), and [SLOW] for data growing in (bars, rings, the chart). The few longer ones (the
 * light/dark reveal, the budget wave, onboarding's drifting glow) are deliberate exceptions.
 */
object Motion {
    const val QUICK = 150
    const val STANDARD = 300
    const val SLOW = 450
}

/** Material's emphasized easing: a quick start that settles gently. */
val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

// Material's "shared axis X", for moving sideways between peers (tabs, months, a screen and the one
// it opens): the new view slides in only a short way while fading in after the old one has faded
// out, so the change reads as calm instead of a whole screen sweeping across.
private const val SHIFT = 0.08f

/** [dir] 1 = moving forward (to the right in the bar, the next month, a screen being opened), -1 = back. */
fun axisIn(dir: Int): EnterTransition =
    slideInHorizontally(tween(Motion.STANDARD, easing = EmphasizedEasing)) { (it * SHIFT).toInt() * dir } +
        fadeIn(tween(Motion.STANDARD - 90, delayMillis = 90))

fun axisOut(dir: Int): ExitTransition =
    slideOutHorizontally(tween(Motion.STANDARD, easing = EmphasizedEasing)) { -(it * SHIFT).toInt() * dir } +
        fadeOut(tween(90))

/** A sheet-like screen (add or edit an expense) rising from the bottom, and going back down. */
fun sheetIn(): EnterTransition =
    slideInVertically(tween(Motion.STANDARD, easing = EmphasizedEasing)) { it / 3 } + fadeIn(tween(Motion.QUICK))

fun sheetOut(): ExitTransition =
    slideOutVertically(tween(Motion.STANDARD, easing = EmphasizedEasing)) { it / 3 } + fadeOut(tween(Motion.QUICK))
