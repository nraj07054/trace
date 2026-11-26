package com.homeboundlabs.trace.ui.people

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homeboundlabs.trace.data.LedgerEntry
import com.homeboundlabs.trace.data.Money
import com.homeboundlabs.trace.data.Person
import com.homeboundlabs.trace.data.balance
import com.homeboundlabs.trace.traceApp
import com.homeboundlabs.trace.ui.components.Avatar
import com.homeboundlabs.trace.ui.components.CircleButton
import com.homeboundlabs.trace.ui.components.ScreenHeader
import com.homeboundlabs.trace.ui.components.SecondaryButton
import com.homeboundlabs.trace.ui.components.TIcon
import com.homeboundlabs.trace.ui.components.TraceIcons
import com.homeboundlabs.trace.ui.components.personColor
import com.homeboundlabs.trace.ui.components.tap
import com.homeboundlabs.trace.ui.components.whenLabel
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.ts
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PersonUi(
    val person: Person? = null,
    val entries: List<LedgerEntry> = emptyList(),
    val currency: String = "₹",
    val upiId: String = "",
    val myName: String = "",
    val loaded: Boolean = false,
) {
    val balance: Long get() = entries.balance()
    /** What the user handed over and what came back, for the card's "₹500 of ₹2,000 back". */
    val lent: Long get() = entries.filter { it.gave }.sumOf { it.amountMinor }
    val returned: Long get() = entries.filter { !it.gave }.sumOf { it.amountMinor }
    val nextDue: Long? get() = if (balance == 0L) null else entries.mapNotNull { it.dueAt }.minOrNull()
}

class PersonViewModel(app: Application, saved: SavedStateHandle) : AndroidViewModel(app) {
    private val a = app.traceApp
    val id: Long = saved.get<Long>("id") ?: -1
    private val profile = combine(a.prefs.currency, a.prefs.upiId, a.prefs.name) { c, u, n -> Triple(c, u, n) }
    val ui: StateFlow<PersonUi> = combine(a.ledger.observePerson(id), a.ledger.observeEntries(id), profile) { p, e, (c, u, n) ->
        PersonUi(p, e, c, u, n, true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonUi())

    fun add(amount: Long, gave: Boolean, note: String, dueAt: Long?) = a.ledger.add(id, amount, gave, note, System.currentTimeMillis(), dueAt)
    fun delete(entry: LedgerEntry) = a.ledger.deleteEntry(entry.id)
    fun settle(note: String) = a.ledger.settle(id, note)
    fun rename(p: Person, name: String) = a.ledger.rename(p, name)
    fun deletePerson() = a.ledger.deletePerson(id)
    fun setUpiId(v: String) = a.prefs.setUpiId(v)
    var tone: String
        get() = a.prefs.requestTone
        set(v) { a.prefs.requestTone = v }
}

private sealed interface Sheet {
    data class Entry(val gave: Boolean) : Sheet
    data object Request : Sheet
    data object Qr : Sheet
    data class PaidBack(val amount: Long, val note: String) : Sheet
    data object Rename : Sheet
}

@Composable
fun PersonScreen(onBack: () -> Unit, vm: PersonViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDeletePerson by rememberSaveable { mutableStateOf(false) }
    val person = ui.person
    if (ui.loaded && person == null) {
        // Deleted (or a stale link from a reminder).
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        return
    }
    val bal = ui.balance

    Column(
        Modifier.fillMaxSize().background(TraceColors.Surface).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        ScreenHeader(person?.name ?: "", onBack) { CircleButton(TraceIcons.Trash, "Delete person", { confirmDeletePerson = true }) }

        // Balance
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.clip(RoundedCornerShape(30.dp)).tap(label = "Rename") { sheet = Sheet.Rename }) {
                Avatar(person?.name ?: "", size = 64.dp, color = personColor(vm.id))
            }
            Text(
                when {
                    bal > 0 -> "${person?.name} owes you"
                    bal < 0 -> "You owe ${person?.name}"
                    ui.entries.isEmpty() -> "Nothing yet"
                    else -> "All settled 🙌"
                },
                style = ts(14, 600, TraceColors.Muted),
            )
            Text(
                Money.compact(kotlin.math.abs(bal), ui.currency),
                style = ts(46, 800, if (bal > 0) TraceColors.Positive else if (bal < 0) TraceColors.Negative else TraceColors.Ink, letterSpacing = -1.5f, tight = true),
                maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 46.sp),
            )
            ui.nextDue?.let { Text("Due ${dueFmt.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))}", style = ts(13, 600, TraceColors.Warn)) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("I gave", Modifier.weight(1f), TraceIcons.ArrowUp, TraceColors.Negative) { sheet = Sheet.Entry(gave = true) }
            SecondaryButton("I got", Modifier.weight(1f), TraceIcons.ArrowDown, TraceColors.Positive) { sheet = Sheet.Entry(gave = false) }
        }

        if (bal > 0) {
            com.homeboundlabs.trace.ui.components.PrimaryButton("Request ${Money.compact(bal, ui.currency)}") { sheet = Sheet.Request }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (ui.currency == "₹") SecondaryButton("Show QR", Modifier.weight(1f), QrIcon) { sheet = Sheet.Qr }
                SecondaryButton("Got it back", Modifier.weight(1f), TraceIcons.Check, TraceColors.Positive) { vm.settle("Paid back to you") }
            }
        } else if (bal < 0) {
            com.homeboundlabs.trace.ui.components.PrimaryButton("I paid it back") {
                val owed = -bal
                vm.settle("Paid back")
                sheet = Sheet.PaidBack(owed, "")
            }
        }

        if (ui.entries.isNotEmpty()) {
            Text("History", style = ts(16, 700))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ui.entries.forEach { e -> EntryRow(e, ui.currency) { deleting = e.id } }
            }
        }
        Text(
            "Only your side of the record, kept on this phone. Lending isn't spending, so none of this counts in your totals or budgets.",
            style = ts(12, 400, TraceColors.Faint),
        )
        Spacer(Modifier.height(16.dp))
    }

    when (val s = sheet) {
        is Sheet.Entry -> EntrySheet(
            gave = s.gave, name = person?.name ?: "", balance = bal, currency = ui.currency,
            onSave = { amount, gave, note, due -> vm.add(amount, gave, note, due); sheet = null },
            onDismiss = { sheet = null },
        )
        Sheet.Request -> person?.let {
            RequestSheet(ui, it, initialTone = vm.tone, onTone = { t -> vm.tone = t }, onSaveUpi = vm::setUpiId, onDismiss = { sheet = null })
        }
        Sheet.Qr -> person?.let { QrDialog(ui, it, onSaveUpi = vm::setUpiId, onDismiss = { sheet = null }) }
        is Sheet.PaidBack -> person?.let { PaidBackSheet(it.name, s.amount, ui.currency, onDismiss = { sheet = null }) }
        Sheet.Rename -> person?.let { p ->
            NameSheet("Rename", p.name, "Save", onSave = { vm.rename(p, it); sheet = null }, onDismiss = { sheet = null })
        }
        null -> {}
    }

    deleting?.let { id ->
        val e = ui.entries.firstOrNull { it.id == id }
        if (e == null) deleting = null
        else AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = TraceColors.Sheet,
            title = { Text("Delete this entry?", style = ts(18, 800)) },
            text = { Text(entryTitle(e) + " · " + Money.compact(e.amountMinor, ui.currency) + "\nThe balance updates straight away.", style = ts(14, 500, TraceColors.Muted)) },
            confirmButton = { TextButton(onClick = { vm.delete(e); deleting = null }) { Text("Delete", style = ts(14, 700, TraceColors.Negative)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel", style = ts(14, 600)) } },
        )
    }

    if (confirmDeletePerson) {
        AlertDialog(
            onDismissRequest = { confirmDeletePerson = false },
            containerColor = TraceColors.Sheet,
            title = { Text("Delete ${person?.name}?", style = ts(18, 800)) },
            text = { Text("Their whole history goes too. This can't be undone.", style = ts(14, 500, TraceColors.Muted)) },
            confirmButton = {
                TextButton(onClick = { confirmDeletePerson = false; vm.deletePerson(); onBack() }) { Text("Delete", style = ts(14, 700, TraceColors.Negative)) }
            },
            dismissButton = { TextButton(onClick = { confirmDeletePerson = false }) { Text("Cancel", style = ts(14, 600)) } },
        )
    }
}

private val dueFmt = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.US)

private fun entryTitle(e: LedgerEntry) = e.note.ifBlank { if (e.gave) "You gave" else "You got" }

@Composable
private fun EntryRow(e: LedgerEntry, currency: String, onClick: () -> Unit) {
    val fg = if (e.gave) TraceColors.Negative else TraceColors.Positive
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).tap(label = "Entry options", onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(if (e.gave) TraceColors.NegativeBg else TraceColors.PositiveBg),
            contentAlignment = Alignment.Center,
        ) { TIcon(if (e.gave) TraceIcons.ArrowUp else TraceIcons.ArrowDown, fg, 16.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entryTitle(e), style = ts(15, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val due = e.dueAt?.let { " · due " + dueFmt.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) } ?: ""
            Text((if (e.gave) "You gave" else "You got") + " · " + whenLabel(e.createdAt) + due, style = ts(12, 400, TraceColors.Muted), maxLines = 2)
        }
        Text(Money.compact(e.amountMinor, currency), style = ts(15, 700, fg), maxLines = 1)
    }
}
