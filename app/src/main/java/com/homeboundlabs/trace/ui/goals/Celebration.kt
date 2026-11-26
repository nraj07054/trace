package com.homeboundlabs.trace.ui.goals

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.homeboundlabs.trace.data.CategoryColors
import com.homeboundlabs.trace.data.Goal
import com.homeboundlabs.trace.data.Money
import com.homeboundlabs.trace.ui.components.TIcon
import com.homeboundlabs.trace.ui.components.TraceIcons
import com.homeboundlabs.trace.ui.components.tap
import com.homeboundlabs.trace.ui.theme.ButtonPadding
import com.homeboundlabs.trace.ui.theme.rememberReduceMotion
import com.homeboundlabs.trace.ui.theme.ts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val CoinGold = Color(0xFFF5B82E)
private val CoinRim = Color(0xFFC98A0B)

/** One coin flung out of the pot: launch angle (radians, up is -π/2), speed and spin. */
private data class Flung(val angle: Float, val speed: Float, val spin: Float, val delay: Float)

/** One confetti piece falling from the top: strip or dot. */
private data class Bit(val x: Float, val drift: Float, val speed: Float, val spin: Float, val color: Color, val delay: Float, val round: Boolean)

/**
 * The moment a goal fills up, as a full-screen scene in the goal's colour: light rays turn slowly
 * behind a glowing pot, coins burst out of the slot, confetti falls, the amount counts up, and a
 * haptic tap marks it. Actions sit at the bottom. Reduce-motion shows it all at rest.
 */
@Composable
fun GoalCelebration(goal: Goal, currency: String, onBought: () -> Unit, onKeepSaving: () -> Unit, onDismiss: () -> Unit) {
    val reduceMotion = rememberReduceMotion()
    val haptics = LocalHapticFeedback.current
    val pop = remember { Animatable(if (reduceMotion) 1f else 0.2f) }
    val burst = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val rain = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val textIn = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val buttonsIn = remember { Animatable(if (reduceMotion) 1f else 0f) }
    var shown by remember { mutableLongStateOf(if (reduceMotion) goal.savedMinor else 0L) }

    val spin = if (reduceMotion) 0f else rememberInfiniteTransition(label = "rays")
        .animateFloat(0f, 360f, infiniteRepeatable(tween<Float>(24_000, easing = LinearEasing)), label = "spin").value

    val coins = remember {
        val r = Random(goal.id)
        List(18) { Flung((-PI / 2 + (r.nextFloat() - 0.5f) * 2.4f).toFloat(), 0.55f + r.nextFloat() * 0.55f, r.nextFloat() * 720f, r.nextFloat() * 0.25f) }
    }
    val bits = remember {
        val r = Random(goal.id + 7)
        List(56) {
            Bit(
                x = r.nextFloat(), drift = (r.nextFloat() - 0.5f) * 0.3f, speed = 0.75f + r.nextFloat() * 0.6f,
                spin = r.nextFloat() * 900f, color = Color(CategoryColors[r.nextInt(CategoryColors.size)]),
                delay = r.nextFloat() * 0.4f, round = r.nextInt(4) == 0,
            )
        }
    }
    val days = remember(goal) {
        val start = Instant.ofEpochMilli(goal.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
        ChronoUnit.DAYS.between(start, java.time.LocalDate.now()).coerceAtLeast(1)
    }

    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (reduceMotion) return@LaunchedEffect
        launch { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
        launch { delay(220); burst.animateTo(1f, tween(1500, easing = LinearEasing)) }
        launch { rain.animateTo(1f, tween(3200, easing = LinearEasing)) }
        launch { delay(380); textIn.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
        launch { delay(900); buttonsIn.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
        delay(380)
        val steps = 45
        for (i in 1..steps) {
            shown = (goal.savedMinor * FastOutSlowInEasing.transform(i / steps.toFloat())).toLong()
            delay(22)
        }
        shown = goal.savedMinor
    }

    // Deep version of the goal colour for the backdrop, so white text always reads well.
    val deep = lerp(goal.color, Color.Black, 0.62f)
    val night = lerp(goal.color, Color.Black, 0.86f)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(deep, night)))) {
            // Slowly turning rays and a glow, centred where the pot sits.
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height * 0.36f)
                val len = size.maxDimension
                val g = pop.value.coerceIn(0f, 1f)
                rotate(spin, c) {
                    repeat(12) { i ->
                        val a0 = (i * 30f) * (PI / 180).toFloat()
                        val a1 = (i * 30f + 11f) * (PI / 180).toFloat()
                        val ray = Path().apply {
                            moveTo(c.x, c.y)
                            lineTo(c.x + cos(a0) * len, c.y + sin(a0) * len)
                            lineTo(c.x + cos(a1) * len, c.y + sin(a1) * len)
                            close()
                        }
                        drawPath(ray, Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f * g), Color.Transparent), center = c, radius = len * 0.55f))
                    }
                }
                drawCircle(
                    Brush.radialGradient(listOf(goal.color.copy(alpha = 0.55f * g), Color.Transparent), center = c, radius = 170.dp.toPx()),
                    radius = 170.dp.toPx(), center = c,
                )
            }

            // Confetti over everything.
            Canvas(Modifier.fillMaxSize()) {
                val t = rain.value
                if (t <= 0f || t >= 1f) return@Canvas
                bits.forEach { b ->
                    val p = ((t - b.delay) / (1f - b.delay)).coerceIn(0f, 1f) * b.speed
                    if (p <= 0f) return@forEach
                    val x = (b.x + b.drift * p + 0.02f * sin(p * 12f + b.spin)) * size.width
                    val y = -20f + p * (size.height + 40f)
                    val a = (1.3f - p).coerceIn(0f, 1f)
                    if (b.round) {
                        drawCircle(b.color.copy(alpha = a), radius = 5.dp.toPx(), center = Offset(x, y))
                    } else {
                        rotate(b.spin * p, Offset(x, y)) {
                            // Flutter: the strip's width swings as it tumbles.
                            val w = 12.dp.toPx() * (0.3f + 0.7f * abs(cos(p * 9f + b.spin)))
                            drawRect(b.color.copy(alpha = a), topLeft = Offset(x - w / 2, y - 3.dp.toPx()), size = Size(w, 6.dp.toPx()))
                        }
                    }
                }
            }

            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
                // Close, top right.
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).tap(label = "Close", onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) { TIcon(TraceIcons.Close, Color.White, 15.dp, contentDescription = "Close") }
                }
                Spacer(Modifier.weight(0.15f))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }) { Gullak(goal, size = 168.dp) }
                        Canvas(Modifier.fillMaxSize()) {
                            val t = burst.value
                            if (t <= 0f || t >= 1f) return@Canvas
                            val origin = Offset(size.width / 2, size.height / 2 - 168.dp.toPx() * 0.14f)
                            val reach = size.minDimension * 0.8f
                            val r = 11.dp.toPx()
                            coins.forEach { c ->
                                val p = ((t - c.delay) / (1f - c.delay)).coerceIn(0f, 1f)
                                if (p <= 0f) return@forEach
                                val center = origin + Offset(cos(c.angle) * c.speed * reach * p, sin(c.angle) * c.speed * reach * p + 1.15f * reach * p * p)
                                val a = (1f - p * p).coerceIn(0f, 1f)
                                val squash = 0.3f + 0.7f * abs(cos(Math.toRadians((c.spin * p).toDouble()))).toFloat()
                                val tl = center - Offset(r * squash, r)
                                val sz = Size(2 * r * squash, 2 * r)
                                drawOval(CoinGold.copy(alpha = a), topLeft = tl, size = sz)
                                drawOval(CoinRim.copy(alpha = a), topLeft = tl, size = sz, style = Stroke(1.5.dp.toPx()))
                                drawOval(Color.White.copy(alpha = 0.5f * a), topLeft = tl + Offset(sz.width * 0.25f, sz.height * 0.2f), size = Size(sz.width * 0.25f, sz.height * 0.25f))
                            }
                        }
                    }
                }
                Column(
                    Modifier.fillMaxWidth().graphicsLayer {
                        alpha = textIn.value.coerceIn(0f, 1f)
                        translationY = (1f - textIn.value) * 40f
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("GOAL REACHED", style = ts(12, 800, Color.White.copy(alpha = 0.7f), letterSpacing = 2.5f))
                    Text("You did it! 🎉", style = ts(32, 800, Color.White, letterSpacing = -0.8f), textAlign = TextAlign.Center)
                    Text(Money.whole(shown, currency), style = ts(48, 800, Color.White, letterSpacing = -1.8f), maxLines = 1)
                    Text("saved for ${goal.name}", style = ts(16, 600, Color.White.copy(alpha = 0.75f)), textAlign = TextAlign.Center)
                    Row(
                        Modifier.padding(top = 4.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Text("in $days ${if (days == 1L) "day" else "days"} of saving", style = ts(13, 700, Color.White)) }
                }
                Spacer(Modifier.weight(0.25f))
                Column(
                    Modifier.fillMaxWidth().widthIn(max = 420.dp).graphicsLayer {
                        alpha = buttonsIn.value
                        translationY = (1f - buttonsIn.value) * 60f
                    },
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp)).background(Color.White)
                            .tap(label = "I bought it", onClick = onBought).padding(ButtonPadding),
                        contentAlignment = Alignment.Center,
                    ) { Text("I bought it 🎉", style = ts(17, 800, deep)) }
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = 0.10f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(28.dp))
                            .tap(label = "Keep saving", onClick = onKeepSaving).padding(ButtonPadding),
                        contentAlignment = Alignment.Center,
                    ) { Text("Keep saving", style = ts(16, 700, Color.White)) }
                }
            }
        }
    }
}
