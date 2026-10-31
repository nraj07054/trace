package com.homeboundlabs.trace.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.layout
import com.homeboundlabs.trace.data.CURRENCIES
import com.homeboundlabs.trace.data.Category
import com.homeboundlabs.trace.data.Money
import com.homeboundlabs.trace.quickadd.QuickAdd
import com.homeboundlabs.trace.traceApp
import com.homeboundlabs.trace.ui.components.TIcon
import com.homeboundlabs.trace.ui.components.TraceIcons
import com.homeboundlabs.trace.ui.components.tap
import com.homeboundlabs.trace.ui.theme.SpringEasing
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.rememberReduceMotion
import com.homeboundlabs.trace.ui.theme.ts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private const val PAGES = 4

private data class Intro(val title: String, val body: String, val factLabel: String, val fact: String, val glow: Color)

private val intros = listOf(
    Intro(
        "Every rupee\nleaves a trace.",
        "A calm, quick way to see where your money goes. No bank logins, no clutter.",
        "Small things add up",
        "₹150 a day is ₹54,750 a year. That's what a daily coffee and snack can quietly cost.",
        Category.Food.color,
    ),
    Intro(
        "See where\nit goes.",
        "Every spend lands in a category, so one glance tells you what's growing.",
        "The 50/30/20 rule",
        "A popular budgeting guideline: about 50% on needs, 30% on wants, and 20% saved.",
        Category.Shopping.color,
    ),
    Intro(
        "Add it without\nunlocking.",
        "A small card on your lock screen. Type the amount, tap a category, done.",
        "Why it works",
        "Logging a spend right when it happens is easier than trying to remember it later.",
        Category.Travel.color,
    ),
    Intro(
        "Make it\nyours.",
        "Two quick choices and you're set.",
        "Rule of 72",
        "Divide 72 by a yearly interest rate to estimate how many years money takes to double. At 8%, about 9 years.",
        Category.Bills.color,
    ),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.traceApp
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    val pager = rememberPagerState { PAGES }
    // Voice is only mentioned on phones that can understand speech offline (same rule as the mic).
    var voice by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { voice = com.homeboundlabs.trace.quickadd.Voice.offlineLanguage(context) != null }

    var name by rememberSaveable { mutableStateOf(app.prefs.name.value) }
    var currency by rememberSaveable { mutableStateOf(app.prefs.currency.value) }
    var quickAdd by rememberSaveable { mutableStateOf(true) }

    fun finish(granted: Boolean? = null) {
        app.prefs.setName(name.trim())
        app.prefs.setCurrency(currency)
        app.prefs.setQuickAddEnabled(quickAdd && granted != false)
        app.prefs.onboarded = true
        app.scope.launch { QuickAdd.refresh(context) }
        onDone()
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        app.prefs.askedNotificationPermission = true
        finish(granted)
    }

    fun next() {
        if (pager.currentPage < PAGES - 1) {
            scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
        } else {
            // Permissions are explained on the next screen before Android asks.
            finish()
        }
    }

    // Background glow takes the color of the current page.
    val glow by animateColorAsState(intros[pager.currentPage].glow, tween(600), label = "glow")

    Box(Modifier.fillMaxSize().background(TraceColors.Surface)) {
        AmbientGlow(glow, reduceMotion)

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            TopBar(pager, onSkip = { scope.launch { pager.animateScrollToPage(PAGES - 1) } })

            HorizontalPager(state = pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 1) { page ->
                // Read lazily inside graphicsLayer so swiping only redraws, never recomposes.
                val offset = { (pager.currentPage - page) + pager.currentPageOffsetFraction }
                // Restart page animations only when a swipe settles on a new page.
                val active = pager.settledPage == page
                if (page == PAGES - 1) {
                    SetupPage(
                        intro = intros[page], offset = offset,
                        name = name, onName = { name = it.take(30) },
                        currency = currency, onCurrency = { currency = it },
                        quickAdd = quickAdd, onQuickAdd = { quickAdd = it },
                    )
                } else {
                    val intro = if (page == 2 && voice) intros[page].copy(
                        body = "A small card on your lock screen. Type the amount, or just say it: “50 for vada pav”.",
                    ) else intros[page]
                    IntroPage(intro, offset) {
                        when (page) {
                            0 -> BarsArt(active, reduceMotion)
                            1 -> CategoriesArt(active, reduceMotion)
                            else -> LockCardArt(active, reduceMotion, voice)
                        }
                    }
                }
            }

            BottomBar(pager, onNext = ::next)
        }
    }
}

/* ---------- chrome ---------- */

@Composable
private fun TopBar(pager: PagerState, onSkip: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            buildAnnotatedString {
                append("Trace")
                withStyle(SpanStyle(color = TraceColors.Accent)) { append(".") }
            },
            style = ts(20, 800, letterSpacing = -0.6f),
        )
        val showSkip = pager.currentPage < PAGES - 1
        val skipAlpha by animateFloatAsState(if (showSkip) 1f else 0f, label = "skip")
        Box(
            Modifier.graphicsLayer { alpha = skipAlpha }.clip(RoundedCornerShape(18.dp))
                .then(if (showSkip) Modifier.tap(label = "Skip intro", onClick = onSkip) else Modifier)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) { Text("Skip", style = ts(14, 600, TraceColors.Muted)) }
    }
}

@Composable
private fun BottomBar(pager: PagerState, onNext: () -> Unit) {
    val last = pager.currentPage == PAGES - 1
    Row(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Dots(pager)
        val width by animateDpAsState(if (last) 176.dp else 120.dp, tween(350, easing = SpringEasing), label = "btn")
        Box(
            Modifier.width(width).height(56.dp).clip(RoundedCornerShape(28.dp)).background(TraceColors.Ink)
                .tap(label = if (last) "Start tracing" else "Next", onClick = onNext),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = last,
                transitionSpec = {
                    (fadeIn(tween(200)) + slideInVertically { it / 2 }) togetherWith (fadeOut(tween(120)) + slideOutVertically { -it / 2 })
                },
                label = "btnText",
            ) { isLast ->
                Text(if (isLast) "Start tracing" else "Next", style = ts(16, 700, TraceColors.OnInk))
            }
        }
    }
}

@Composable
private fun Dots(pager: PagerState) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(PAGES) { i ->
            // Distance from the current scroll position, so dots stretch smoothly while swiping.
            val d = ((pager.currentPage + pager.currentPageOffsetFraction) - i).absoluteValue.coerceAtMost(1f)
            Box(
                Modifier.width(8.dp + 18.dp * (1f - d)).height(8.dp).clip(CircleShape)
                    .background(lerp(TraceColors.Border, TraceColors.Ink, 1f - d)),
            )
        }
    }
}

private fun lerp(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

/** Two soft, slowly drifting blobs of the page color behind everything. */
@Composable
private fun AmbientGlow(color: Color, reduceMotion: Boolean) {
    val t = rememberInfiniteTransition(label = "drift")
    val drift = t.animateFloat(
        0f, if (reduceMotion) 0f else 1f,
        infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Reverse), label = "drift",
    )
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.offset(x = (-140).dp).size(420.dp)
                .graphicsLayer { translationX = 60.dp.toPx() * drift.value; translationY = 30.dp.toPx() * drift.value }
                .background(Brush.radialGradient(listOf(color.copy(alpha = 0.20f), color.copy(alpha = 0f)))),
        )
        Box(
            Modifier.align(Alignment.BottomEnd).offset(x = 140.dp, y = (-90).dp).size(380.dp)
                .graphicsLayer { translationX = -50.dp.toPx() * drift.value; translationY = -40.dp.toPx() * drift.value }
                .background(Brush.radialGradient(listOf(color.copy(alpha = 0.14f), color.copy(alpha = 0f)))),
        )
    }
}

/* ---------- pages ---------- */

@Composable
private fun IntroPage(intro: Intro, offset: () -> Float, art: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().clipToBounds().padding(horizontal = 22.dp)) {
        // Art moves slower than the page (parallax) and fades as it leaves.
        Box(
            Modifier.fillMaxWidth().weight(1f).graphicsLayer {
                val o = offset()
                translationX = o * size.width * 0.45f
                alpha = 1f - o.absoluteValue.coerceAtMost(1f)
                val s = 1f - o.absoluteValue.coerceAtMost(1f) * 0.12f
                scaleX = s; scaleY = s
            },
            contentAlignment = Alignment.Center,
        ) { art() }
        PageText(intro, offset)
    }
}

@Composable
private fun PageText(intro: Intro, offset: () -> Float) {
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp).graphicsLayer {
            val o = offset()
            translationX = o * size.width * 0.15f
            alpha = 1f - o.absoluteValue.coerceAtMost(1f)
        },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(intro.title, style = ts(34, 800, letterSpacing = -1.2f), modifier = Modifier.semantics { heading() })
        Text(intro.body, style = ts(15, 500, TraceColors.Muted))
        FactCard(intro)
    }
}

@Composable
private fun FactCard(intro: Intro) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TraceColors.Field)
            .border(1.dp, TraceColors.Border, RoundedCornerShape(18.dp)).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(intro.glow.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Text("i", style = ts(14, 800, intro.glow)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(intro.factLabel.uppercase(), style = ts(11, 700, TraceColors.Faint, letterSpacing = 0.6f))
            Text(intro.fact, style = ts(13, 500, TraceColors.Ink))
        }
    }
}

@Composable
private fun SetupPage(
    intro: Intro,
    offset: () -> Float,
    name: String,
    onName: (String) -> Unit,
    currency: String,
    onCurrency: (String) -> Unit,
    quickAdd: Boolean,
    onQuickAdd: (Boolean) -> Unit,
) {
    val focus = LocalFocusManager.current
    Column(
        Modifier.fillMaxSize().clipToBounds().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).graphicsLayer {
            val o = offset()
            translationX = o * size.width * 0.2f
            alpha = 1f - o.absoluteValue.coerceAtMost(1f)
        },
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(intro.title, style = ts(34, 800, letterSpacing = -1.2f), modifier = Modifier.semantics { heading() })
        Text(intro.body, style = ts(15, 500, TraceColors.Muted))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("What should we call you?", style = ts(13, 600, TraceColors.Muted))
            BasicTextField(
                value = name, onValueChange = onName, singleLine = true,
                textStyle = ts(16, 600), cursorBrush = SolidColor(TraceColors.Accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(TraceColors.Field)
                            .border(1.dp, TraceColors.Border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (name.isEmpty()) Text("Your name (optional)", style = ts(16, 600, TraceColors.Ghost))
                        inner()
                    }
                },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Currency", style = ts(13, 600, TraceColors.Muted))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CURRENCIES.forEach { c ->
                    val on = c == currency
                    val bg by animateColorAsState(if (on) TraceColors.Ink else TraceColors.Surface, label = "cur")
                    val scale by animateFloatAsState(if (on) 1.06f else 1f, tween(300, easing = SpringEasing), label = "curScale")
                    Box(
                        Modifier.scale(scale).size(width = 64.dp, height = 48.dp).clip(RoundedCornerShape(18.dp)).background(bg)
                            .border(1.dp, if (on) TraceColors.Ink else TraceColors.Border, RoundedCornerShape(18.dp))
                            .tap(role = Role.RadioButton, label = c) { onCurrency(c) },
                        contentAlignment = Alignment.Center,
                    ) { Text(c, style = ts(18, 800, if (on) TraceColors.OnInk else TraceColors.Ink)) }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, TraceColors.Border, RoundedCornerShape(18.dp))
                .tap(role = Role.Switch) { onQuickAdd(!quickAdd) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Lock-screen quick-add", style = ts(15, 700))
                Text("We'll ask to show one silent notification for it.", style = ts(13, 400, TraceColors.Muted))
            }
            Switch(
                checked = quickAdd, onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = TraceColors.Ink, checkedThumbColor = TraceColors.OnInk,
                    uncheckedTrackColor = TraceColors.Fill, uncheckedBorderColor = TraceColors.Border,
                    uncheckedThumbColor = TraceColors.Faint,
                ),
            )
        }
        FactCard(intro)
        Spacer(Modifier.height(8.dp))
    }
}

/* ---------- illustrations ---------- */

// Fractions of max height, and [food, travel, shop, bills] shares per bar.
private val demoBars = listOf(
    0.34f to listOf(3, 1, 0, 0), 0.62f to listOf(2, 1, 5, 0), 0.42f to listOf(4, 2, 0, 0), 0.78f to listOf(1, 0, 0, 6),
    0.52f to listOf(4, 2, 2, 0), 1f to listOf(4, 1, 5, 0), 0.4f to listOf(3, 2, 0, 0),
)

/** Page 1: the Home chart, bars springing up one after another. */
@Composable
private fun BarsArt(active: Boolean, reduceMotion: Boolean) {
    val anims = remember { demoBars.map { Animatable(0f) } }
    var showTip by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        showTip = false
        anims.forEach { it.snapTo(0f) }
        anims.forEachIndexed { i, a ->
            launch {
                if (!reduceMotion) delay(70L * i)
                if (reduceMotion) a.snapTo(1f) else a.animateTo(1f, tween(650, easing = SpringEasing))
            }
        }
        delay(if (reduceMotion) 0 else 750)
        showTip = true
    }
    val maxH = 200.dp
    Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.BottomCenter) {
        Row(Modifier.fillMaxWidth().height(maxH + 50.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            demoBars.forEachIndexed { i, (h, mix) ->
                val selected = i == 5
                Box(Modifier.width(34.dp).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    val grow = { maxH * h * anims[i].value.coerceAtLeast(0f) }
                    if (selected) {
                        val tipAlpha by animateFloatAsState(if (showTip) 1f else 0f, label = "tip")
                        val tipScale by animateFloatAsState(if (showTip) 1f else 0.6f, tween(400, easing = SpringEasing), label = "tipS")
                        Text(
                            Money.whole(375000L, "₹"),
                            style = ts(12, 700, TraceColors.OnInk), maxLines = 1,
                            modifier = Modifier
                                .graphicsLayer { translationY = -(grow() + 10.dp).toPx(); alpha = tipAlpha; scaleX = tipScale; scaleY = tipScale; transformOrigin = TransformOrigin(0.5f, 1f) }
                                .wrapContentWidth(unbounded = true)
                                .clip(RoundedCornerShape(12.dp)).background(TraceColors.Ink).padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    Column(
                        Modifier.animatedHeight(grow).width(34.dp)
                            .graphicsLayer { scaleX = if (selected) 1.12f else 1f; transformOrigin = TransformOrigin(0.5f, 1f) }
                            .clip(RoundedCornerShape(50)).background(TraceColors.Fill),
                    ) {
                        Category.defaults.take(4).forEachIndexed { ci, c ->
                            if (mix[ci] > 0) {
                                Box(
                                    Modifier.fillMaxWidth().weight(mix[ci].toFloat())
                                        .background(c.color.copy(alpha = if (selected) 1f else 0.35f)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Page 2: categories slide in with counting amounts and filling bars. */
@Composable
private fun CategoriesArt(active: Boolean, reduceMotion: Boolean) {
    val amounts = listOf(4050, 1512, 3939, 2450)
    val max = amounts.max().toFloat()
    val progress = remember { Category.defaults.take(4).map { Animatable(0f) } }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        progress.forEach { it.snapTo(0f) }
        progress.forEachIndexed { i, a ->
            launch {
                if (!reduceMotion) delay(110L * i)
                if (reduceMotion) a.snapTo(1f) else a.animateTo(1f, tween(800, easing = SpringEasing))
            }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Category.defaults.take(4).forEachIndexed { i, c ->
            val p = progress[i].value
            Row(
                Modifier.fillMaxWidth()
                    .graphicsLayer { alpha = p.coerceIn(0f, 1f); translationX = (1f - p) * 120f }
                    .clip(RoundedCornerShape(18.dp)).background(TraceColors.Surface)
                    .border(1.dp, TraceColors.Border, RoundedCornerShape(18.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.color.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center,
                ) { TIcon(TraceIcons.category(c), c.color, 20.dp) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(c.label, style = ts(14, 700))
                        val shown = (amounts[i] * p.coerceIn(0f, 1f)).toLong()
                        Text(Money.whole(shown * 100, "₹"), style = ts(14, 800))
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(TraceColors.Fill)) {
                        Box(
                            Modifier.fillMaxWidth(amounts[i] / max * p.coerceIn(0f, 1.05f)).fillMaxHeight()
                                .clip(CircleShape).background(c.color),
                        )
                    }
                }
            }
        }
    }
}

/** Page 3: a mini lock-screen card that types ₹450, saves, and confirms, on a loop. */
@Composable
private fun LockCardArt(active: Boolean, reduceMotion: Boolean, voice: Boolean) {
    // Stages: 0 typing · 1 saved · 2 listening · 3 filled from voice · 4 saved from voice
    var stage by remember { mutableIntStateOf(0) }
    var typed by remember { mutableStateOf("") }
    var words by remember { mutableStateOf("") }
    LaunchedEffect(active, voice) {
        if (!active) return@LaunchedEffect
        if (reduceMotion) { stage = 0; typed = "450"; return@LaunchedEffect }
        val sentence = "for vada pav I paid rupees 50".split(" ")
        while (true) {
            stage = 0; typed = ""
            delay(600)
            for (t in listOf("4", "45", "450")) { typed = t; delay(320) }
            delay(450); stage = 1; delay(1700)
            if (!voice) continue
            stage = 2; words = ""
            delay(500)
            sentence.forEachIndexed { i, w -> words = sentence.take(i + 1).joinToString(" "); delay(260) }
            delay(700); stage = 3; delay(1500); stage = 4; delay(1700)
        }
    }
    // Phone silhouette with a dark lock screen.
    Box(
        Modifier.width(260.dp).height(300.dp).clip(RoundedCornerShape(36.dp)).background(Color(0xFF14161B))
            .border(6.dp, Color(0xFF0E0F12), RoundedCornerShape(36.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xFF232836), radius = size.width * 0.75f, center = androidx.compose.ui.geometry.Offset(0f, size.height))
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("9:41", style = ts(44, 700, Color.White, -2f, tight = true))
        }
        AnimatedContent(
            targetState = stage,
            transitionSpec = { (fadeIn(tween(220)) + slideInVertically { -it / 4 }) togetherWith fadeOut(tween(150)) },
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            label = "lockCard",
        ) { st ->
            when (st) {
                1 -> MiniSaved("−₹450")
                4 -> MiniSaved("−₹50")
                2 -> MiniListening(words)
                3 -> MiniEdit(amount = "50", ready = true, note = "Vada pav", showMic = voice)
                else -> MiniEdit(amount = typed, ready = typed == "450", note = null, showMic = voice)
            }
        }
    }
}

@Composable
private fun MiniSaved(amount: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.Black).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Category.Food.color), contentAlignment = Alignment.Center) {
            TIcon(TraceIcons.Check, Color.White, 14.dp)
        }
        Text("Added to Food", style = ts(12, 700, Color.White), modifier = Modifier.weight(1f))
        Text(amount, style = ts(14, 800, Color.White))
    }
}

@Composable
private fun MiniEdit(amount: String, ready: Boolean, note: String?, showMic: Boolean) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.Black).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("₹" + amount.ifEmpty { "0" }, style = ts(26, 800, if (amount.isNotEmpty()) Color.White else Color(0xFF4A4F5C), -1f, tight = true))
                Spacer(Modifier.width(2.dp))
                Box(Modifier.width(2.dp).height(22.dp).background(Category.Food.color))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (showMic) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(Color(0xFF1E2128)), contentAlignment = Alignment.Center) {
                        TIcon(TraceIcons.Mic, Color(0xFFA1A6B3), 12.dp)
                    }
                }
                val saveBg by animateColorAsState(if (ready) Category.Food.color else Color(0xFF2A2D35), label = "save")
                Box(Modifier.size(34.dp).clip(CircleShape).background(saveBg), contentAlignment = Alignment.Center) {
                    TIcon(TraceIcons.Check, Color.White, 16.dp)
                }
            }
        }
        if (note != null) Text("“$note”", style = ts(11, 600, Color(0xFFA1A6B3)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Category.defaults.take(4).forEachIndexed { i, c ->
                val on = i == 0
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(if (on) c.color else c.color.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { TIcon(TraceIcons.category(c), if (on) Color.White else c.color, 14.dp) }
            }
        }
    }
}

@Composable
private fun MiniListening(words: String) {
    val orange = Color(0xFFFF7A45)
    val t = rememberInfiniteTransition(label = "miniBars")
    val phase by t.animateFloat(0f, 6.283f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "phase")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.Black).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White)) { append("Trace") }
                append(" · ")
                withStyle(SpanStyle(color = orange)) { append("LISTENING…") }
            },
            style = ts(10, 700, Color(0xFFA1A6B3), 0.3f),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(4) { i ->
                    val h = 6f + 14f * ((kotlin.math.sin(phase + i * 1.2f) + 1f) / 2f)
                    Box(Modifier.width(3.dp).height(h.dp).clip(RoundedCornerShape(2.dp)).background(orange))
                }
            }
            Text(if (words.isBlank()) "…" else "“$words”", style = ts(12, 700, Color.White), maxLines = 2, modifier = Modifier.weight(1f))
            Box(Modifier.size(30.dp).clip(CircleShape).background(orange), contentAlignment = Alignment.Center) {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
            }
        }
    }
}

/** Sets height from [height] during layout, so animating it re-measures without recomposing. */
internal fun Modifier.animatedHeight(height: () -> Dp): Modifier = layout { measurable, constraints ->
    val h = height().roundToPx().coerceAtLeast(0)
    val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
    layout(placeable.width, h) { placeable.place(0, 0) }
}
