@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.homeboundlabs.trace.ui.goals

import android.app.Application
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeboundlabs.trace.data.CategoryColors
import com.homeboundlabs.trace.data.CategoryIcons
import com.homeboundlabs.trace.data.Goal
import com.homeboundlabs.trace.data.Money
import com.homeboundlabs.trace.traceApp
import com.homeboundlabs.trace.ui.components.CircleButton
import com.homeboundlabs.trace.ui.components.Keypad
import com.homeboundlabs.trace.ui.components.PrimaryButton
import com.homeboundlabs.trace.ui.components.ScreenHeader
import com.homeboundlabs.trace.ui.components.SecondaryButton
import com.homeboundlabs.trace.ui.components.TCard
import com.homeboundlabs.trace.ui.components.TIcon
import com.homeboundlabs.trace.ui.components.TextInput
import com.homeboundlabs.trace.ui.components.TraceIcons
import com.homeboundlabs.trace.ui.components.tap
import com.homeboundlabs.trace.ui.home.WaveFill
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.rememberReduceMotion
import com.homeboundlabs.trace.ui.theme.ts
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class GoalsUi(val goals: List<Goal> = emptyList(), val currency: String = "₹", val loaded: Boolean = false)

class GoalsViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app.traceApp
    val ui: StateFlow<GoalsUi> = combine(a.goals.observe(), a.prefs.currency) { g, c -> GoalsUi(g, c, true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUi())

    fun add(name: String, target: Long, argb: Long, icon: String) = a.goals.add(name, target, argb, icon)
    fun edit(g: Goal, name: String, target: Long, argb: Long, icon: String) = a.goals.edit(g, name, target, argb, icon)
    fun move(g: Goal, delta: Long) = a.goals.move(g.id, delta)
    fun delete(g: Goal) = a.goals.delete(g.id)
    fun finish(g: Goal, logAs: com.homeboundlabs.trace.data.Category?, amount: Long) = a.goals.finish(g, logAs, amount)
    fun reopen(g: Goal) = a.goals.reopen(g)
}

private sealed interface GoalSheet {
    data object New : GoalSheet
    data class Edit(val id: Long) : GoalSheet
    data class Money(val id: Long, val add: Boolean) : GoalSheet
    /** Holds the goal as it is right after the deposit, so the count-up doesn't wait for the database. */
    data class Celebrate(val goal: Goal) : GoalSheet
    data class Bought(val id: Long) : GoalSheet
    data class Completed(val id: Long) : GoalSheet
}

@Composable
fun GoalsScreen(onBack: () -> Unit, vm: GoalsViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<GoalSheet?>(null) }

    var showDone by rememberSaveable { mutableStateOf(false) }
    val (done, active) = ui.goals.partition { it.done }

    Column(
        Modifier.fillMaxSize().background(TraceColors.Surface).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CircleButton(TraceIcons.Back, "Back", onBack)
            CircleButton(TraceIcons.Plus, "New goal", { sheet = GoalSheet.New })
        }
        if (ui.loaded) {
            val saved = active.sumOf { it.savedMinor }
            val left = active.sumOf { it.leftMinor }
            if (showDone) {
                TwoToneHeading(
                    "Goals you finished,",
                    if (done.isEmpty()) "nothing here yet" else Money.whole(done.sumOf { it.savedMinor }, ui.currency) + " well spent",
                    "${done.size} ${if (done.size == 1) "goal" else "goals"} completed",
                    Modifier.padding(top = 6.dp, start = 2.dp),
                )
            } else if (active.isEmpty()) {
                TwoToneHeading("Your savings,", "start your first goal", "A trip, a phone, a rainy-day fund. It never counts as spending.", Modifier.padding(top = 6.dp, start = 2.dp))
            } else {
                TwoToneHeading(
                    "Your savings,",
                    Money.whole(saved, ui.currency) + if (saved > 0) " and growing" else " so far",
                    "Across ${active.size} ${if (active.size == 1) "goal" else "goals"}" + if (left > 0) " · ${Money.whole(left, ui.currency)} still to go" else "",
                    Modifier.padding(top = 6.dp, start = 2.dp),
                )
            }
            if (!showDone) Box(Modifier.padding(horizontal = 2.dp, vertical = 4.dp)) { SavedSplit(active) }
            GoalTabs(showDone, active.size, done.size) { showDone = it }
        }
        Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!showDone) {
                if (ui.loaded && active.isEmpty()) PrimaryButton("Start a goal") { sheet = GoalSheet.New }
                active.forEach { g ->
                    androidx.compose.runtime.key(g.id) {
                        GoalCardV2(
                            g, ui.currency,
                            onAdd = { sheet = GoalSheet.Money(g.id, add = true) },
                            onTake = { sheet = GoalSheet.Money(g.id, add = false) },
                            onEdit = { sheet = GoalSheet.Edit(g.id) },
                            onBought = { sheet = GoalSheet.Bought(g.id) },
                        )
                    }
                }
            } else {
                done.forEach { g -> androidx.compose.runtime.key(g.id) { CompletedCard(g, ui.currency) { sheet = GoalSheet.Completed(g.id) } } }
                if (done.isNotEmpty()) Text("Tap a finished goal to save for it again.", style = ts(12, 500, TraceColors.Muted), modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    when (val s = sheet) {
        GoalSheet.New -> GoalEditor(null, onSave = { n, t, c, i -> vm.add(n, t, c, i); sheet = null }, onDelete = {}, onDismiss = { sheet = null })
        is GoalSheet.Edit -> ui.goals.firstOrNull { it.id == s.id }?.let { g ->
            GoalEditor(g, onSave = { n, t, c, i -> vm.edit(g, n, t, c, i); sheet = null }, onDelete = { vm.delete(g); sheet = null }, onDismiss = { sheet = null })
        }
        is GoalSheet.Money -> ui.goals.firstOrNull { it.id == s.id }?.let { g ->
            MoneySheet(g, s.add, ui.currency, onSave = { amount ->
                vm.move(g, if (s.add) amount else -amount)
                val after = g.copy(savedMinor = (g.savedMinor + if (s.add) amount else -amount).coerceAtLeast(0))
                // Crossing the target is the big moment.
                sheet = if (s.add && !g.reached && after.reached) GoalSheet.Celebrate(after) else null
            }, onDismiss = { sheet = null })
        }
        is GoalSheet.Celebrate -> GoalCelebration(
            s.goal, ui.currency,
            onBought = { sheet = GoalSheet.Bought(s.goal.id) },
            onKeepSaving = { sheet = GoalSheet.Edit(s.goal.id) },
            onDismiss = { sheet = null },
        )
        is GoalSheet.Bought -> ui.goals.firstOrNull { it.id == s.id }?.let { g ->
            BoughtSheet(g, ui.currency, onFinish = { cat, amount -> vm.finish(g, cat, amount); sheet = null }, onDismiss = { sheet = null })
        }
        is GoalSheet.Completed -> ui.goals.firstOrNull { it.id == s.id }?.let { g ->
            AlertDialog(
                onDismissRequest = { sheet = null },
                containerColor = TraceColors.Sheet,
                title = { Text(g.name, style = ts(18, 800)) },
                text = { Text("Completed with ${Money.whole(g.savedMinor, ui.currency)} saved. Bring it back to keep saving, or remove it.", style = ts(14, 500, TraceColors.Muted)) },
                confirmButton = { TextButton(onClick = { vm.reopen(g); sheet = null }) { Text("Keep saving", style = ts(14, 700)) } },
                dismissButton = { TextButton(onClick = { vm.delete(g); sheet = null }) { Text("Remove", style = ts(14, 700, TraceColors.Negative)) } },
            )
        }
        null -> {}
    }
}

private val GoalIcons = listOf("travel", "phone", "home", "gift", "heart", "book", "coffee", "shop", "ticket", "tag")

@Composable
private fun GoalEditor(existing: Goal?, onSave: (String, Long, Long, String) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var target by rememberSaveable { mutableStateOf(existing?.let { Money.keypadInput(it.targetMinor) } ?: "") }
    var argb by rememberSaveable { mutableStateOf(existing?.argb ?: CategoryColors[1]) }
    var icon by rememberSaveable { mutableStateOf(existing?.icon ?: "travel") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val minor = Money.parseMinor(target)
    val currency = androidx.compose.ui.platform.LocalContext.current.traceApp.prefs.currency.value
    val color = TraceColors.category(argb)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = TraceColors.Sheet) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (existing == null) "New goal" else "Edit goal", style = ts(18, 800))
            TextInput(name, { name = it.take(30) }, "What for? e.g. Goa trip", cursor = color)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryColors.forEach { c ->
                    val on = c == argb
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(TraceColors.category(c))
                            .border(if (on) 3.dp else 0.dp, if (on) TraceColors.Ink else Color.Transparent, CircleShape)
                            .tap(role = Role.RadioButton, label = "Colour") { argb = c },
                    )
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GoalIcons.filter { it in CategoryIcons }.forEach { k ->
                    val on = k == icon
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(if (on) color.copy(alpha = 0.16f) else TraceColors.Fill)
                            .tap(role = Role.RadioButton, label = k) { icon = k },
                        contentAlignment = Alignment.Center,
                    ) { TIcon(TraceIcons.icon(k), if (on) color else TraceColors.Muted, 20.dp) }
                }
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Target", style = ts(13, 500, TraceColors.Muted))
                Text(currency + Money.keypadText(target, currency), style = ts(40, 800, letterSpacing = -1.4f, tight = true), maxLines = 1)
            }
            Keypad(onKey = { target = Money.press(target, it) }, keyColor = TraceColors.Key, textColor = TraceColors.Ink, keyHeight = 46)
            PrimaryButton(if (existing == null) "Create goal" else "Save", enabled = name.isNotBlank() && minor > 0) { onSave(name, minor, argb, icon) }
            if (existing != null) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).tap(label = "Delete goal") { confirmDelete = true }.padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Delete goal", style = ts(15, 700, TraceColors.Negative)) }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = TraceColors.Sheet,
            title = { Text("Delete ${existing?.name}?", style = ts(18, 800)) },
            text = { Text("The goal and its saved total go. This can't be undone.", style = ts(14, 500, TraceColors.Muted)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", style = ts(14, 700, TraceColors.Negative)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", style = ts(14, 600)) } },
        )
    }
}

@Composable
private fun MoneySheet(goal: Goal, add: Boolean, currency: String, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    val minor = Money.parseMinor(amount)
    val ok = minor > 0 && (add || minor <= goal.savedMinor)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = TraceColors.Sheet) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 22.dp, end = 22.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (add) "Add to ${goal.name}" else "Take out of ${goal.name}", style = ts(18, 800))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(currency + Money.keypadText(amount, currency), style = ts(44, 800, letterSpacing = -1.5f, tight = true), maxLines = 1)
                Text(
                    if (add) "${Money.whole(goal.leftMinor, currency)} to go" else "${Money.whole(goal.savedMinor, currency)} saved",
                    style = ts(13, 500, TraceColors.Muted),
                )
            }
            Keypad(onKey = { amount = Money.press(amount, it) }, keyColor = TraceColors.Key, textColor = TraceColors.Ink, keyHeight = 50)
            PrimaryButton(if (add) "Add" else "Take out", enabled = ok, bg = if (add) TraceColors.Ink else TraceColors.Negative) { onSave(minor) }
        }
    }
}
