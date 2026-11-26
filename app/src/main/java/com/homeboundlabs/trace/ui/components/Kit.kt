package com.homeboundlabs.trace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.homeboundlabs.trace.ui.theme.ButtonPadding
import com.homeboundlabs.trace.ui.theme.CapTextScale
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.ts

/** Back button and a centred title, with an optional button on the right. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(TraceIcons.Back, "Back", onBack)
        Text(
            title, style = ts(16, 700), textAlign = TextAlign.Center, maxLines = 1,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { heading() },
        )
        if (action != null) action() else Spacer(Modifier.width(44.dp))
    }
}

/** The full-width dark main button used across the app. */
@Composable
fun PrimaryButton(
    label: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    bg: Color = TraceColors.Ink,
    textColor: Color = TraceColors.OnInk,
    onClick: () -> Unit,
) {
    Box(
        modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(27.dp))
            .background(if (enabled) bg else TraceColors.DisabledBg)
            .tap(label = label) { if (enabled) onClick() }.padding(ButtonPadding),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = ts(16, 700, if (enabled) textColor else TraceColors.DisabledText), textAlign = TextAlign.Center) }
}

/** A lighter, outlined button, optionally with an icon. */
@Composable
fun SecondaryButton(label: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = TraceColors.Ink, onClick: () -> Unit) {
    Row(
        modifier.heightIn(min = 50.dp).clip(RoundedCornerShape(25.dp)).border(1.dp, TraceColors.Border, RoundedCornerShape(25.dp))
            .tap(label = label, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        icon?.let { TIcon(it, color, 18.dp) }
        Text(label, style = ts(15, 700, color), textAlign = TextAlign.Center)
    }
}

/** Selectable pill, used for tones, due dates and similar small choices. */
@Composable
fun ChoiceChip(label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.heightIn(min = 40.dp).clip(shape)
            .background(if (on) TraceColors.Ink else TraceColors.Surface)
            .border(1.dp, if (on) TraceColors.Ink else TraceColors.Border, shape)
            .tap(role = Role.RadioButton, label = label, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = ts(13, 700, if (on) TraceColors.OnInk else TraceColors.Ink), maxLines = 1) }
}

/** The app's single-line text field look. */
@Composable
fun TextInput(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    cursor: Color = TraceColors.Accent,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    keyboardType: KeyboardType = KeyboardType.Text,
    error: Boolean = false,
) {
    val focus = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = ts(16, 600),
        cursorBrush = SolidColor(cursor),
        keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(TraceColors.Field)
                    .border(1.dp, if (error) TraceColors.Negative else TraceColors.Border, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(placeholder, style = ts(16, 600, TraceColors.Ghost))
                inner()
            }
        },
    )
}

/** Rounded bordered card. */
@Composable
fun TCard(
    modifier: Modifier = Modifier,
    bg: Color = TraceColors.Surface,
    border: Color = TraceColors.Border,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(bg).border(1.dp, border, shape).padding(padding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** Round initial for a person. */
@Composable
fun Avatar(name: String, size: Dp = 44.dp, color: Color = TraceColors.Accent) {
    Box(
        Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        CapTextScale { Text(name.trim().firstOrNull()?.uppercase() ?: "?", style = ts((size.value * 0.4f).toInt(), 800, color)) }
    }
}

/** A stable, friendly colour per person, from the category palette. */
fun personColor(id: Long): Color = TraceColors.category(com.homeboundlabs.trace.data.CategoryColors[(id % 9).toInt()])

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = ts(13, 600, TraceColors.Muted), modifier = modifier.semantics { heading() })
}
