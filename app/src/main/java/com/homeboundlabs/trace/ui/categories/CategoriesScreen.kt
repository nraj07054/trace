package com.homeboundlabs.trace.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.homeboundlabs.trace.data.Categories
import com.homeboundlabs.trace.data.Category
import com.homeboundlabs.trace.traceApp
import com.homeboundlabs.trace.ui.components.CircleButton
import com.homeboundlabs.trace.ui.components.TIcon
import com.homeboundlabs.trace.ui.components.TraceIcons
import com.homeboundlabs.trace.ui.components.tap
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.ts
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val app = LocalContext.current.traceApp
    val categories by Categories.all.collectAsState()
    val counts = remember { mutableStateMapOf<String, Int>() }
    LaunchedEffect(categories) { categories.forEach { counts[it.id] = app.repo.expenseCount(it.id) } }

    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(TraceColors.Surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleButton(TraceIcons.Back, "Back", onBack)
            Text("Categories", style = ts(16, 700), modifier = Modifier.weight(1f).padding(end = 44.dp), textAlign = TextAlign.Center)
        }
        LazyColumn(
            Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(categories, key = { it.id }) { c ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, TraceColors.Border, RoundedCornerShape(18.dp))
                        .tap(label = "Edit ${c.name}") { editingId = c.id }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.color.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center,
                    ) { TIcon(TraceIcons.category(c), c.color, 20.dp) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(c.name, style = ts(15, 700))
                        val n = counts[c.id]
                        if (n != null) Text(if (n == 1) "1 expense" else "$n expenses", style = ts(12, 400, TraceColors.Muted))
                    }
                    Text("Edit", style = ts(13, 600, TraceColors.Muted))
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(TraceColors.Ink)
                        .tap(label = "New category") { creating = true },
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TIcon(TraceIcons.Plus, Color.White, 18.dp)
                    Text("New category", style = ts(16, 700, Color.White))
                }
            }
        }
    }

    if (creating) {
        CategoryEditor(existing = null, onSaved = { creating = false }, onDismiss = { creating = false })
    }

    editingId?.let { id ->
        val c = categories.firstOrNull { it.id == id }
        if (c == null) {
            editingId = null
        } else {
            CategoryEditor(
                existing = c,
                onSaved = { editingId = null },
                onDismiss = { editingId = null },
                // At least one category must remain to put expenses in.
                onDelete = if (categories.size > 1) ({ editingId = null; deletingId = id }) else null,
            )
        }
    }

    deletingId?.let { id ->
        val c = categories.firstOrNull { it.id == id }
        val others = categories.filter { it.id != id }
        if (c == null || others.isEmpty()) {
            deletingId = null
        } else {
            DeleteDialog(c, others, counts[c.id] ?: 0, onDismiss = { deletingId = null }) { target ->
                app.scope.launch { app.repo.deleteCategory(c.id, target.id) }
                deletingId = null
            }
        }
    }
}

/** Asks where this category's expenses should go, then deletes it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeleteDialog(c: Category, others: List<Category>, count: Int, onDismiss: () -> Unit, onConfirm: (Category) -> Unit) {
    var targetIdx by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TraceColors.Surface,
        title = { Text("Delete “${c.name}”?", style = ts(18, 800)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (count == 0) "It has no expenses. Anything added to it later would go to:"
                    else "Its ${if (count == 1) "1 expense moves" else "$count expenses move"} to:",
                    style = ts(14, 500, TraceColors.Muted),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    others.forEachIndexed { i, o ->
                        val on = i == targetIdx
                        val shape = RoundedCornerShape(18.dp)
                        Row(
                            Modifier.height(36.dp).clip(shape).background(if (on) o.color else Color.White)
                                .border(1.dp, if (on) o.color else TraceColors.Border, shape)
                                .tap(role = Role.RadioButton, label = o.name) { targetIdx = i }.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) Color.White else o.color))
                            Text(o.name, style = ts(13, 600, if (on) Color.White else TraceColors.Ink))
                        }
                    }
                }
                Text("Your totals stay the same.", style = ts(13, 500, TraceColors.Faint))
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(others[targetIdx]) }) {
                Text(if (count == 0) "Delete" else "Move & delete", style = ts(14, 700, TraceColors.Negative))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = ts(14, 600)) } },
    )
}
