package com.homeboundlabs.trace.ui.add

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.homeboundlabs.trace.data.Categories
import com.homeboundlabs.trace.data.Category
import com.homeboundlabs.trace.data.Expense
import com.homeboundlabs.trace.data.Money
import com.homeboundlabs.trace.traceApp
import com.homeboundlabs.trace.ui.categories.CategoryEditor
import com.homeboundlabs.trace.ui.categories.NewCategoryChip
import com.homeboundlabs.trace.ui.components.CircleButton
import com.homeboundlabs.trace.ui.components.DatePickerCard
import com.homeboundlabs.trace.ui.components.Keypad
import com.homeboundlabs.trace.ui.components.TIcon
import com.homeboundlabs.trace.ui.components.TraceIcons
import com.homeboundlabs.trace.ui.components.dayLabel
import com.homeboundlabs.trace.ui.components.tap
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.ts
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddScreen(onClose: () -> Unit, editId: Long? = null) {
    val app = LocalContext.current.traceApp
    val currency = app.prefs.currency.value
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var catId by rememberSaveable { mutableStateOf(Category.Food.id) }
    val categories by Categories.all.collectAsState()
    val cat = categories.firstOrNull { it.id == catId } ?: Category.of(catId)
    var newCategory by rememberSaveable { mutableStateOf(false) }
    var dateEpochDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var showDate by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var original by remember { mutableStateOf<Expense?>(null) }
    var loaded by rememberSaveable { mutableStateOf(editId == null) }

    // Edit mode: load the expense once and prefill the form.
    LaunchedEffect(editId) {
        if (editId != null && !loaded) {
            val e = app.repo.get(editId)
            if (e == null) { onClose(); return@LaunchedEffect }
            original = e
            amount = Money.keypadInput(e.amountMinor)
            note = e.note
            catId = e.categoryId
            dateEpochDay = Instant.ofEpochMilli(e.createdAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
            loaded = true
        } else if (editId != null && original == null) {
            original = app.repo.get(editId)
        }
    }
    val focus = LocalFocusManager.current
    val minor = Money.parseMinor(amount)
    val date = LocalDate.ofEpochDay(dateEpochDay)

    fun save() {
        if (minor <= 0) return
        val zone = ZoneId.systemDefault()
        val orig = original
        if (orig != null) {
            // Keep the original time of day; only move it if the date changed.
            val origAt = Instant.ofEpochMilli(orig.createdAt).atZone(zone)
            val at = if (origAt.toLocalDate() == date) orig.createdAt
            else date.atTime(origAt.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
            app.repo.updateInBackground(orig.copy(amountMinor = minor, categoryId = cat.id, note = note.trim(), createdAt = at))
        } else {
            val at = if (date == LocalDate.now()) System.currentTimeMillis()
            else date.atTime(LocalTime.now()).atZone(zone).toInstant().toEpochMilli()
            app.repo.addInBackground(minor, cat, note, at)
        }
        onClose()
    }

    Column(
        Modifier.fillMaxSize().background(TraceColors.Surface).statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CircleButton(TraceIcons.Close, "Close", onClose)
            Text(if (editId == null) "New expense" else "Edit expense", style = ts(16, 700))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(TraceColors.Fill)
                        .tap(label = "Change date") { showDate = true }.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(dayLabel(date), style = ts(13, 600)) }
                if (editId != null) {
                    // Kept in the header so it's reachable on short screens too.
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(TraceColors.NegativeBg)
                            .tap(label = "Delete expense") { confirmDelete = true },
                        contentAlignment = Alignment.Center,
                    ) { TIcon(TraceIcons.Trash, TraceColors.Negative, 20.dp, contentDescription = "Delete expense") }
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Amount", style = ts(13, 400, TraceColors.Muted))
            val text = currency + Money.keypadText(amount, currency)
            val size = when { text.length <= 8 -> 58; text.length <= 10 -> 48; else -> 40 }
            val caret by animateColorAsState(cat.color, label = "caret")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics { contentDescription = "Amount $text" },
            ) {
                Text(text, style = ts(size, 800, letterSpacing = -2f, tight = true), maxLines = 1)
                Spacer(Modifier.width(4.dp))
                Box(Modifier.width(3.dp).height((size - 8).dp).clip(RoundedCornerShape(2.dp)).background(caret))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("What was it for?", style = ts(13, 600, TraceColors.Muted))
            BasicTextField(
                value = note,
                onValueChange = { note = it.take(80) },
                singleLine = true,
                textStyle = ts(16, 600),
                cursorBrush = SolidColor(cat.color),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(TraceColors.Field)
                            .border(1.dp, TraceColors.Border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (note.isEmpty()) Text("e.g. Lunch at Café Mocha", style = ts(16, 600, TraceColors.Ghost))
                        inner()
                    }
                },
            )
        }

        // One sideways-scrolling row, so any number of categories keeps the keypad on screen.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.forEach { c ->
                val on = c.id == cat.id
                val bg by animateColorAsState(if (on) c.color else Color.White, label = "chip")
                val shape = RoundedCornerShape(20.dp)
                Row(
                    Modifier.height(40.dp).clip(shape).background(bg).border(1.dp, if (on) c.color else TraceColors.Border, shape)
                        .tap(role = Role.RadioButton) { catId = c.id }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) Color.White else c.color))
                    Text(c.label, style = ts(14, 600, if (on) Color.White else TraceColors.Ink))
                }
            }
            NewCategoryChip { newCategory = true }
        }

        Spacer(Modifier.weight(1f))

        Keypad(
            onKey = { amount = Money.press(amount, it) },
            keyColor = TraceColors.Field,
            textColor = TraceColors.Ink,
        )

        val enabled = minor > 0
        Box(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
                .background(if (enabled) TraceColors.Ink else TraceColors.Ink.copy(alpha = 0.25f))
                .tap(label = "Save expense") { save() },
            contentAlignment = Alignment.Center,
        ) { Text(if (editId == null) "Save expense" else "Save changes", style = ts(16, 700, Color.White)) }
    }

    if (confirmDelete && editId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = TraceColors.Surface,
            title = { Text("Delete expense?", style = ts(18, 800)) },
            text = {
                Text(
                    (note.ifBlank { cat.label }) + " · " + currency + Money.keypadText(amount, currency) + "\nThis can't be undone.",
                    style = ts(14, 500, TraceColors.Muted),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    app.repo.delete(editId)
                    confirmDelete = false
                    onClose()
                }) { Text("Delete", style = ts(14, 700, TraceColors.Negative)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", style = ts(14, 600)) } },
        )
    }

    if (newCategory) {
        CategoryEditor(
            existing = null,
            onSaved = { created -> catId = created.id; newCategory = false },
            onDismiss = { newCategory = false },
        )
    }

    if (showDate) {
        DatePickerCard(
            selected = date,
            onPick = { dateEpochDay = it.toEpochDay(); showDate = false },
            onDismiss = { showDate = false },
        )
    }
}
