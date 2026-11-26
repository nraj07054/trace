@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.homeboundlabs.trace.ui.people

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeboundlabs.trace.data.Money
import com.homeboundlabs.trace.data.PersonBalance
import com.homeboundlabs.trace.traceApp
import com.homeboundlabs.trace.ui.components.Avatar
import com.homeboundlabs.trace.ui.components.CircleButton
import com.homeboundlabs.trace.ui.components.PrimaryButton
import com.homeboundlabs.trace.ui.components.ScreenHeader
import com.homeboundlabs.trace.ui.components.TCard
import com.homeboundlabs.trace.ui.components.TextInput
import com.homeboundlabs.trace.ui.components.TraceIcons
import com.homeboundlabs.trace.ui.components.dayLabel
import com.homeboundlabs.trace.ui.components.personColor
import com.homeboundlabs.trace.ui.components.tap
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.ts
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

data class PeopleUi(val people: List<PersonBalance> = emptyList(), val currency: String = "₹", val loaded: Boolean = false) {
    val owedToYou: Long get() = people.filter { it.balanceMinor > 0 }.sumOf { it.balanceMinor }
    val youOwe: Long get() = people.filter { it.balanceMinor < 0 }.sumOf { -it.balanceMinor }
}

class PeopleViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app.traceApp
    val ui: StateFlow<PeopleUi> = combine(a.ledger.observeBalances(), a.prefs.currency) { list, cur -> PeopleUi(list, cur, true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeopleUi())

    suspend fun addPerson(name: String) = a.ledger.addPerson(name)
}

@Composable
fun PeopleScreen(onBack: () -> Unit, onPerson: (Long) -> Unit, vm: PeopleViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().background(TraceColors.Surface).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScreenHeader("Lend & borrow", onBack) { CircleButton(TraceIcons.Plus, "Add person", { adding = true }) }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Summary("Owed to you", ui.owedToYou, ui.currency, TraceColors.Positive, TraceColors.PositiveBg, Modifier.weight(1f))
            Summary("You owe", ui.youOwe, ui.currency, TraceColors.Negative, TraceColors.NegativeBg, Modifier.weight(1f))
        }

        if (ui.loaded && ui.people.isEmpty()) {
            TCard {
                Text("Keep track of money you lend or borrow", style = ts(15, 700))
                Text(
                    "Add a friend, then note what you gave or got. Trace keeps the running balance, reminds you on the due day, " +
                        "and can send a friendly reminder with a UPI QR. None of this counts as spending.",
                    style = ts(13, 400, TraceColors.Muted),
                )
                PrimaryButton("Add a person") { adding = true }
            }
        }

        val (open, settled) = ui.people.partition { it.balanceMinor != 0L }
        if (open.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { open.forEach { PersonRow(it, ui.currency) { onPerson(it.id) } } }
        }
        if (settled.isNotEmpty()) {
            Text("Settled", style = ts(13, 600, TraceColors.Muted))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { settled.forEach { PersonRow(it, ui.currency) { onPerson(it.id) } } }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (adding) {
        NameSheet(
            title = "Add a person", initial = "", button = "Add",
            onSave = { name -> scope.launch { val id = vm.addPerson(name); adding = false; onPerson(id) } },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun Summary(label: String, amount: Long, currency: String, fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = ts(13, 600, fg))
        Text(Money.whole(amount, currency), style = ts(22, 800, TraceColors.Ink, letterSpacing = -0.5f), maxLines = 1)
    }
}

@Composable
internal fun PersonRow(p: PersonBalance, currency: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).tap(label = "Open ${p.name}", onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(p.name, color = personColor(p.id))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(p.name, style = ts(15, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = when {
                p.balanceMinor > 0 -> "Owes you"
                p.balanceMinor < 0 -> "You owe"
                else -> "All settled"
            } + (p.lastAt?.let { " · " + dayLabel(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()) } ?: "")
            Text(sub, style = ts(12, 400, TraceColors.Muted), maxLines = 1)
        }
        val color = when {
            p.balanceMinor > 0 -> TraceColors.Positive
            p.balanceMinor < 0 -> TraceColors.Negative
            else -> TraceColors.Muted
        }
        Text(Money.compact(kotlin.math.abs(p.balanceMinor), currency), style = ts(15, 700, color), maxLines = 1)
    }
}

/** Sheet with one name field, for adding or renaming a person. */
@Composable
internal fun NameSheet(title: String, initial: String, button: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = TraceColors.Sheet) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 22.dp, end = 22.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = ts(18, 800))
            TextInput(name, { name = it.take(30) }, "Name, e.g. Rahul", capitalization = KeyboardCapitalization.Words)
            PrimaryButton(button, enabled = name.isNotBlank()) { onSave(name) }
        }
    }
}

