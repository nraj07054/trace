package com.trace.app.ui.categories

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.trace.app.data.Categories
import com.trace.app.data.Category
import com.trace.app.data.CategoryColors
import com.trace.app.data.CategoryIcons
import com.trace.app.traceApp
import com.trace.app.ui.components.TIcon
import com.trace.app.ui.components.TraceIcons
import com.trace.app.ui.components.tap
import com.trace.app.ui.theme.SpringEasing
import com.trace.app.ui.theme.TraceColors
import com.trace.app.ui.theme.ts
import kotlinx.coroutines.launch

/** "+ New" chip that sits at the end of a row of category chips. */
@Composable
fun NewCategoryChip(onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.height(40.dp).clip(shape).border(1.dp, TraceColors.Border, shape).background(TraceColors.Field)
            .tap(label = "New category", onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TIcon(TraceIcons.Plus, TraceColors.Ink, 14.dp)
        Text("New", style = ts(14, 600))
    }
}

/**
 * Create ([existing] = null) or edit a category: name, colour and icon, with a live preview.
 * [onDelete] is offered only when editing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryEditor(
    existing: Category?,
    onSaved: (Category) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val app = LocalContext.current.traceApp
    val all by Categories.all.collectAsState()
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    // New categories start on a colour no other category uses yet, where possible.
    var argb by rememberSaveable {
        mutableLongStateOf(existing?.argb ?: (CategoryColors.firstOrNull { c -> all.none { it.argb == c } } ?: CategoryColors.first()))
    }
    var icon by rememberSaveable { mutableStateOf(existing?.icon ?: "tag") }
    val color = Color(argb)

    val trimmed = name.trim()
    val duplicate = all.any { it.id != existing?.id && it.name.equals(trimmed, ignoreCase = true) }
    val canSave = trimmed.isNotEmpty() && !duplicate

    fun save() {
        if (!canSave) return
        app.scope.launch {
            val saved = if (existing == null) app.repo.addCategory(trimmed, argb, icon)
            else existing.copy(name = trimmed, argb = argb, icon = icon).also { app.repo.updateCategory(it) }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onSaved(saved) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TraceColors.Surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(if (existing == null) "New category" else "Edit category", style = ts(18, 800))

            // Preview + name
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val tint by animateColorAsState(color, label = "tint")
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(tint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) { TIcon(TraceIcons.icon(icon), tint, 24.dp) }
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    singleLine = true,
                    textStyle = ts(16, 600),
                    cursorBrush = SolidColor(color),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box(
                            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(TraceColors.Field)
                                .border(1.dp, TraceColors.Border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (name.isEmpty()) Text("Name, e.g. Fun, Health, Pets", style = ts(16, 600, TraceColors.Ghost))
                            inner()
                        }
                    },
                )
            }
            if (duplicate) Text("You already have a category called “$trimmed”.", style = ts(13, 600, TraceColors.Negative))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Colour", style = ts(13, 600, TraceColors.Muted))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CategoryColors.forEach { c ->
                        val on = c == argb
                        val scale by animateFloatAsState(if (on) 1.12f else 1f, tween(300, easing = SpringEasing), label = "sw")
                        Box(
                            Modifier.size(44.dp).scale(scale).clip(CircleShape)
                                .border(if (on) 3.dp else 0.dp, if (on) TraceColors.Ink else Color.Transparent, CircleShape)
                                .padding(if (on) 5.dp else 0.dp).clip(CircleShape).background(Color(c))
                                .tap(role = Role.RadioButton, label = "Colour") { argb = c },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Icon", style = ts(13, 600, TraceColors.Muted))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CategoryIcons.keys.forEach { key ->
                        val on = key == icon
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (on) color else TraceColors.Field)
                                .tap(role = Role.RadioButton, label = key) { icon = key },
                            contentAlignment = Alignment.Center,
                        ) { TIcon(TraceIcons.icon(key), if (on) Color.White else TraceColors.Ink, 20.dp) }
                    }
                }
            }

            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
                    .background(if (canSave) TraceColors.Ink else TraceColors.Ink.copy(alpha = 0.25f))
                    .tap(label = "Save category") { save() },
                contentAlignment = Alignment.Center,
            ) { Text(if (existing == null) "Add category" else "Save changes", style = ts(16, 700, Color.White)) }

            if (onDelete != null) {
                Box(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).tap(label = "Delete category", onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) { Text("Delete category", style = ts(15, 700, TraceColors.Negative)) }
            }
        }
    }
}
