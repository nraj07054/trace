@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.homeboundlabs.trace.ui.goals

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homeboundlabs.trace.data.Categories
import com.homeboundlabs.trace.data.Category
import com.homeboundlabs.trace.data.Goal
import com.homeboundlabs.trace.data.Money
import com.homeboundlabs.trace.ui.components.Keypad
import com.homeboundlabs.trace.ui.components.PrimaryButton
import com.homeboundlabs.trace.ui.components.TIcon
import com.homeboundlabs.trace.ui.components.TraceIcons
import com.homeboundlabs.trace.ui.components.dayLabel
import com.homeboundlabs.trace.ui.components.tap
import com.homeboundlabs.trace.ui.theme.ButtonPadding
import com.homeboundlabs.trace.ui.theme.CapTextScale
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.ts
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/* The goals screen's own look: two-tone headings, bordered cards with a soft colour wash, glossy goal badges. */

/** A card edge that's clearly visible in both themes (the app's hairline is too faint for these cards). */
internal val CardBorder: Color get() = if (TraceColors.isDark) Color(0xFF2B2F37) else Color(0xFFE1E4EA)

/** The soft grey first line of a two-tone heading. */
private val SoftText: Color get() = if (TraceColors.isDark) Color(0xFF6E7480) else Color(0xFF8A8F9C)

/** Light tint of a goal colour for pills, tracks and the Add button; deep enough to read in dark mode. */
internal fun Goal.tint(): Color = if (TraceColors.isDark) color.copy(alpha = 0.16f) else color.copy(alpha = 0.12f)

/** Text on [tint]: the goal colour itself in dark mode, a deeper shade in light mode for contrast. */
internal fun Goal.onTint(): Color = if (TraceColors.isDark) color else androidx.compose.ui.graphics.lerp(color, Color.Black, 0.25f)

/**
 * A soft wash of [color] spreading from a corner, like light falling on the card: an elliptical
 * radial fade, not a hard-edged disc.
 */
internal fun Modifier.colorWash(color: Color, strength: Float = 0.22f, center: (Size) -> Offset = { Offset(it.width * 0.12f, it.height * 0.25f) }) =
    drawBehind {
        val c = center(size)
        val r = size.width * 0.55f
        // Squash vertically so the light spreads along the card.
        scale(scaleX = 1f, scaleY = 0.7f, pivot = c) {
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = strength), color.copy(alpha = strength * 0.35f), Color.Transparent), center = c, radius = r), radius = r, center = c)
        }
    }

/** "Your savings," in grey, then the main line in ink: the heading style the screen opens with. */
@Composable
internal fun TwoToneHeading(soft: String, strong: String, sub: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = SoftText)) { append(soft) }
                append("\n")
                append(strong)
            },
            style = ts(24, 700, letterSpacing = -0.5f).copy(lineHeight = 30.sp),
        )
        sub?.let { Text(it, style = ts(13, 500, TraceColors.Muted)) }
    }
}

/** One thin bar split into each active goal's share of what's saved. */
@Composable
internal fun SavedSplit(goals: List<Goal>) {
    val total = goals.sumOf { it.savedMinor }
    if (total <= 0) return
    Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        goals.filter { it.savedMinor > 0 }.forEach { g ->
            Box(Modifier.weight(g.savedMinor.toFloat()).height(8.dp).clip(RoundedCornerShape(4.dp)).background(g.color))
        }
    }
}

/** Active / Completed, styled like Home's period pills. */
@Composable
internal fun GoalTabs(showDone: Boolean, active: Int, done: Int, onPick: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(false to "Active · $active", true to "Completed · $done").forEach { (isDone, label) ->
            val on = isDone == showDone
            Box(
                Modifier.heightIn(min = 38.dp).clip(RoundedCornerShape(19.dp)).background(if (on) TraceColors.Ink else Color.Transparent)
                    .tap(role = Role.Tab, label = label) { onPick(isDone) }.padding(horizontal = 15.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = ts(13, 700, if (on) TraceColors.OnInk else TraceColors.Faint)) }
        }
    }
}

/** The goal's icon on a glossy disc of its colour, with a white rim and a highlight. */
@Composable
internal fun GoalBadge(goal: Goal, size: Dp = 28.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2
            drawCircle(Brush.verticalGradient(listOf(goal.color.copy(alpha = 0.78f), goal.color)), radius = r)
            drawCircle(Color.White.copy(alpha = 0.35f), radius = r * 0.42f, center = Offset(r * 0.72f, r * 0.55f))
            drawCircle(if (TraceColors.isDark) Color(0xFF121418) else Color.White, radius = r - 0.75.dp.toPx(), style = Stroke(1.5.dp.toPx()))
        }
        TIcon(TraceIcons.icon(goal.icon), Color.White, size * 0.5f)
    }
}

/** The pot with its badge tucked on the lower right. */
@Composable
internal fun PotWithBadge(goal: Goal, size: Dp = 56.dp, badge: Boolean = true) {
    Box(Modifier.size(size)) {
        Gullak(goal, size = size)
        if (badge) Box(Modifier.align(Alignment.BottomEnd).offset(x = 7.dp, y = 5.dp)) { GoalBadge(goal, size * 0.5f) }
    }
}

@Composable
private fun Pill(text: String, fg: Color, bg: Color, icon: Boolean = false) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon) TIcon(TraceIcons.Check, fg, 11.dp)
        Text(text, style = ts(12, 700, fg), maxLines = 1)
    }
}

/** "today", "yesterday", "Mon", "26 Nov": for "Reached …" and "Bought …". */
private fun whenWord(millis: Long): String {
    val label = dayLabel(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate())
    return if (label == "Today" || label == "Yesterday") label.lowercase() else label
}

private fun daysSince(millis: Long): Long =
    ChronoUnit.DAYS.between(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate(), LocalDate.now()).coerceAtLeast(0)

private fun savingFor(goal: Goal): String {
    val d = daysSince(goal.createdAt)
    return when {
        d < 1 -> "Started today"
        d < 60 -> "$d ${if (d == 1L) "day" else "days"} of saving"
        else -> "${d / 30} months of saving"
    }
}

/**
 * One goal, as in the design: pot with badge, name and a status pill; the saved amount with its
 * target and percent; a thin bar; then what's left and the actions. A reached goal skips the
 * numbers and offers "Keep saving" and "I bought it".
 */
@Composable
internal fun GoalCardV2(g: Goal, currency: String, onAdd: () -> Unit, onTake: () -> Unit, onEdit: () -> Unit, onBought: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(TraceColors.Surface).colorWash(g.color, if (g.reached) 0.30f else 0.22f)
            .border(1.5.dp, CardBorder, shape).tap(label = "Edit ${g.name}", onClick = onEdit).padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PotWithBadge(g)
            Column(Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(g.name, style = ts(15, 700), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (g.reached) "Ready to buy" else savingFor(g), style = ts(12, 500, TraceColors.Muted), maxLines = 1)
            }
            CapTextScale {
                if (g.reached) Pill("Reached", TraceColors.Positive, TraceColors.PositiveBg, icon = true)
                else Pill("${(g.fraction * 100).toInt()}%", g.onTint(), g.tint())
            }
        }
        if (!g.reached) {
            Row(Modifier.padding(top = 14.dp, bottom = 7.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    buildAnnotatedString {
                        append(Money.whole(g.savedMinor, currency))
                        withStyle(SpanStyle(color = TraceColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)) { append("  of " + Money.whole(g.targetMinor, currency)) }
                    },
                    style = ts(18, 800, letterSpacing = -0.3f), maxLines = 1, modifier = Modifier.weight(1f),
                )
            }
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(g.tint())) {
                Box(Modifier.fillMaxWidth(g.fraction).height(6.dp).clip(RoundedCornerShape(3.dp)).background(g.color))
            }
        }
        Row(Modifier.padding(top = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!g.reached) {
                Text("${Money.whole(g.leftMinor, currency)} to go", style = ts(12, 500, TraceColors.Muted), modifier = Modifier.weight(1f))
                if (g.savedMinor > 0) {
                    Box(Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(18.dp)).tap(label = "Take money out of ${g.name}", onClick = onTake).padding(horizontal = 10.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text("Take out", style = ts(13, 700, TraceColors.Muted))
                    }
                }
                Row(
                    Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(18.dp)).background(g.tint())
                        .tap(label = "Add money to ${g.name}", onClick = onAdd).padding(horizontal = 15.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    TIcon(TraceIcons.Plus, g.onTint(), 13.dp)
                    Text("Add", style = ts(13, 700, g.onTint()))
                }
            } else {
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.heightIn(min = 38.dp).clip(RoundedCornerShape(19.dp)).border(1.5.dp, CardBorder, RoundedCornerShape(19.dp))
                        .tap(label = "Keep saving", onClick = onEdit).padding(horizontal = 15.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Keep saving", style = ts(13, 700)) }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.heightIn(min = 38.dp).clip(RoundedCornerShape(19.dp)).background(g.color)
                        .tap(label = "I bought it", onClick = onBought).padding(horizontal = 16.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("I bought it", style = ts(13, 700, Color.White)) }
            }
        }
    }
}

/** A finished goal on the Completed tab. */
@Composable
internal fun CompletedCard(g: Goal, currency: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(TraceColors.Surface).colorWash(g.color, 0.20f)
            .border(1.5.dp, CardBorder, shape).tap(label = "${g.name}, completed", onClick = onClick).padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PotWithBadge(g, size = 44.dp, badge = false)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(g.name, style = ts(15, 700), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Bought" + (g.doneAt?.let { " " + whenWord(it) } ?: ""), style = ts(12, 500, TraceColors.Muted), maxLines = 1)
        }
        Text(Money.whole(g.savedMinor, currency), style = ts(14, 800, TraceColors.Positive))
    }
}

/**
 * "I bought it": a soft-washed header with the full pot and a two-tone line, then the price (tap Edit
 * if it differed from the target) and a category, so the purchase is logged as real spending.
 */
@Composable
internal fun BoughtSheet(goal: Goal, currency: String, onFinish: (Category?, Long) -> Unit, onDismiss: () -> Unit) {
    val cats by Categories.all.collectAsStateWithLifecycle()
    var catId by rememberSaveable { mutableStateOf(cats.firstOrNull { it.id == "shop" }?.id ?: cats.firstOrNull()?.id ?: "shop") }
    var amount by rememberSaveable { mutableStateOf(Money.keypadInput(goal.savedMinor.coerceAtMost(goal.targetMinor))) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val minor = Money.parseMinor(amount)
    val days = daysSince(goal.createdAt).coerceAtLeast(1)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = TraceColors.Sheet) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Column(
                Modifier.fillMaxWidth()
                    .colorWash(goal.color, 0.30f) { Offset(it.width * 0.15f, 0f) }
                    .colorWash(Color(0xFFEC4899), 0.12f) { Offset(it.width * 0.95f, 0f) }
                    .padding(horizontal = 22.dp).padding(top = 4.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PotWithBadge(goal)
                    TwoToneHeading("Goal complete,", "enjoy your ${goal.name.lowercase()}", null)
                }
                Text("${Money.whole(goal.savedMinor, currency)} saved in $days ${if (days == 1L) "day" else "days"}", style = ts(13, 500, TraceColors.Muted))
            }
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Log the purchase", style = ts(15, 700))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TraceColors.Field)
                        .border(1.5.dp, if (editing) goal.color else CardBorder, RoundedCornerShape(16.dp))
                        .tap(label = "Change amount") { editing = !editing }.padding(horizontal = 15.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("It cost", style = ts(12, 500, TraceColors.Muted))
                        Text(currency + Money.keypadText(amount, currency), style = ts(20, 800, letterSpacing = -0.4f), maxLines = 1)
                    }
                    Pill(if (editing) "Done" else "Edit", goal.onTint(), goal.tint())
                }
                if (editing) Keypad(onKey = { amount = Money.press(amount, it) }, keyColor = TraceColors.Key, textColor = TraceColors.Ink, keyHeight = 44)
                Text("Category", style = ts(12, 500, TraceColors.Muted))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cats.forEach { c ->
                        val on = c.id == catId
                        Row(
                            Modifier.heightIn(min = 38.dp).clip(RoundedCornerShape(19.dp))
                                .background(if (on) c.color.copy(alpha = 0.12f) else Color.Transparent)
                                .border(1.5.dp, if (on) c.color else CardBorder, RoundedCornerShape(19.dp))
                                .tap(role = Role.RadioButton, label = c.name) { catId = c.id }
                                .padding(horizontal = 13.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            TIcon(TraceIcons.category(c), c.color, 15.dp)
                            Text(c.name, style = ts(13, 700, if (on) TraceColors.Ink else TraceColors.Muted))
                        }
                    }
                }
                Text("Buying it is real spending, so it goes in your record.", style = ts(12, 500, TraceColors.Faint))
                Spacer(Modifier.height(2.dp))
                PrimaryButton("Log it and finish", enabled = minor > 0, bg = goal.color, textColor = Color.White) { onFinish(cats.firstOrNull { it.id == catId }, minor) }
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).tap(label = "Finish without logging") { onFinish(null, 0) }.padding(ButtonPadding),
                    contentAlignment = Alignment.Center,
                ) { Text("Finish without logging", style = ts(14, 700, TraceColors.Muted)) }
            }
        }
    }
}
