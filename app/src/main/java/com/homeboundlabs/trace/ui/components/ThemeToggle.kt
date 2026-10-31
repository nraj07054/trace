package com.homeboundlabs.trace.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.homeboundlabs.trace.data.ThemeMode
import com.homeboundlabs.trace.traceApp
import com.homeboundlabs.trace.ui.theme.ThemeOrigin
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.themeOrigin
import com.homeboundlabs.trace.ui.theme.rememberReduceMotion

/**
 * Small round light/dark switch. The night spreads out of this button and shrinks back into it.
 * Going dark, the sun sinks out of the bottom and the moon comes down from the top; going light,
 * the moon lifts away and the sun rises back up.
 */
@Composable
fun ThemeToggle(modifier: Modifier = Modifier) {
    val app = LocalContext.current.traceApp
    val dark = TraceColors.isDark
    val reduceMotion = rememberReduceMotion()
    // 0 = day, 1 = night.
    val night by animateFloatAsState(
        if (dark) 1f else 0f,
        if (reduceMotion) snap() else tween(900, easing = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)),
        label = "night",
    )
    val center = remember { arrayOf(Offset.Unspecified) }
    Box(
        modifier.size(36.dp).themeOrigin { center[0] = it }.clip(CircleShape).background(TraceColors.Fill)
            .semantics { stateDescription = if (dark) "Dark mode" else "Light mode" }
            .tap(role = Role.Switch, label = if (dark) "Switch to light mode" else "Switch to dark mode") {
                // The night grows out of (and shrinks back into) this button.
                ThemeOrigin.set(center[0])
                app.prefs.setThemeMode(if (dark) ThemeMode.Light else ThemeMode.Dark)
            },
        contentAlignment = Alignment.Center,
    ) {
        TIcon(
            TraceIcons.Sun, TraceColors.Accent, 17.dp,
            Modifier.graphicsLayer {
                translationY = night * 28.dp.toPx()
                rotationZ = night * 90f
                alpha = 1f - night
            },
        )
        TIcon(
            TraceIcons.Moon, TraceColors.Ink, 16.dp,
            Modifier.graphicsLayer {
                translationY = (night - 1f) * 28.dp.toPx()
                rotationZ = (night - 1f) * 40f
                alpha = night
            },
        )
    }
}
