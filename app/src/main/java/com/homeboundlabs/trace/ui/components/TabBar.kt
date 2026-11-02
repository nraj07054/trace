package com.homeboundlabs.trace.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.homeboundlabs.trace.ui.theme.EmphasizedEasing
import com.homeboundlabs.trace.ui.theme.Motion
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.rememberReduceMotion

/** The three screens the bar switches between, left to right. */
enum class Tab(val route: String) {
    Home("home"), Insights("insights"), Settings("settings");

    companion object {
        fun of(route: String?): Tab? = entries.firstOrNull { it.route == route }
    }
}

/**
 * The bar from the "B" design: the three icons in a raised pill and a round + button. It sits
 * above the screens and stays put while they slide underneath, and the lit circle glides to
 * the tab you pick. Both themes share this one layout and only swap colors, so the
 * light/dark reveal never changes the bar's shape halfway across.
 */
@Composable
fun TabBar(current: Tab, onTab: (Tab) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val reduceMotion = rememberReduceMotion()
    val dark = TraceColors.isDark
    val lit by animateFloatAsState(current.ordinal.toFloat(), if (reduceMotion) snap() else tween(Motion.STANDARD, easing = EmphasizedEasing), label = "lit")
    // An (empty) pointer handler makes the whole bar a touch target, so taps between the
    // buttons stop here instead of falling through to the rows scrolled behind it.
    Column(modifier.fillMaxWidth().pointerInput(Unit) {}) {
        // Short fade so list rows dissolve above the bar instead of showing through it.
        Spacer(
            Modifier.fillMaxWidth().height(28.dp)
                .background(Brush.verticalGradient(listOf(TraceColors.Surface.copy(alpha = 0f), TraceColors.Surface))),
        )
        Row(
            Modifier.fillMaxWidth().background(TraceColors.Surface).navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoxWithConstraints(
                Modifier.weight(1f).height(62.dp).clip(CircleShape).background(if (dark) PillDark else PillLight).padding(horizontal = 8.dp),
            ) {
                val slot = maxWidth / Tab.entries.size
                // The lit circle behind the current icon, gliding between slots.
                Box(
                    Modifier.offset(x = slot * lit + (slot - 46.dp) / 2).align(Alignment.CenterStart)
                        .size(46.dp).clip(CircleShape).background(if (dark) LitDark else LitLight),
                )
                Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    Tab.entries.forEach { tab ->
                        val icon: ImageVector = when (tab) {
                            Tab.Home -> TraceIcons.Home
                            Tab.Insights -> TraceIcons.Insights
                            Tab.Settings -> TraceIcons.Settings
                        }
                        val on = tab == current
                        Box(
                            // No ripple: the gliding lit circle is the feedback.
                            Modifier.width(slot).fillMaxHeight().tapNoRipple { if (!on) onTab(tab) },
                            contentAlignment = Alignment.Center,
                        ) {
                            TIcon(
                                icon, if (on) TraceColors.Ink else if (dark) DimDark else TraceColors.NavInactive,
                                if (tab == Tab.Home) 24.dp else 23.dp, contentDescription = tab.name,
                            )
                        }
                    }
                }
            }
            Box(
                Modifier.size(62.dp).clip(CircleShape).background(if (dark) TraceColors.Accent else TraceColors.Ink).tap(label = "Add expense", onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) { TIcon(TraceIcons.Plus, TraceColors.OnInk, 24.dp, contentDescription = "Add expense") }
        }
    }
}

private val PillDark = Color(0xFF1A1C21)
private val LitDark = Color(0xFF2A2D35)
private val DimDark = Color(0xFF8A909C)
private val PillLight = Color(0xFFF1F2F5)
private val LitLight = Color.White
