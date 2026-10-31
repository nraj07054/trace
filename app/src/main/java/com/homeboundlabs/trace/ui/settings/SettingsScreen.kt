package com.homeboundlabs.trace.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homeboundlabs.trace.data.CURRENCIES
import com.homeboundlabs.trace.data.ThemeMode
import com.homeboundlabs.trace.quickadd.QuickAdd
import com.homeboundlabs.trace.traceApp
import com.homeboundlabs.trace.ui.components.CircleButton
import com.homeboundlabs.trace.ui.components.TIcon
import com.homeboundlabs.trace.ui.components.TraceIcons
import com.homeboundlabs.trace.ui.components.tap
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.ts
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, onCategories: () -> Unit) {
    val context = LocalContext.current
    val app = context.traceApp
    val scope = rememberCoroutineScope()
    val currency by app.prefs.currency.collectAsStateWithLifecycle()
    val quickAdd by app.prefs.quickAddEnabled.collectAsStateWithLifecycle()
    val name by app.prefs.name.collectAsStateWithLifecycle()
    var denied by remember { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        app.prefs.setQuickAddEnabled(granted)
        scope.launch { QuickAdd.refresh(context) }
    }

    fun setQuickAdd(on: Boolean) {
        if (on && !QuickAdd.canPost(context) && Build.VERSION.SDK_INT >= 33) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        app.prefs.setQuickAddEnabled(on)
        scope.launch { QuickAdd.refresh(context) }
    }

    Column(
        Modifier.fillMaxSize().background(TraceColors.Surface).statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CircleButton(TraceIcons.Back, "Back", onBack)
            Text("Settings", style = ts(16, 700), modifier = Modifier.weight(1f).padding(end = 44.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }

        Section("Your name") {
            BasicTextField(
                value = name,
                onValueChange = { app.prefs.setName(it.take(30)) },
                singleLine = true,
                textStyle = ts(16, 600),
                cursorBrush = SolidColor(TraceColors.Accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(TraceColors.Field)
                            .border(1.dp, TraceColors.Border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (name.isEmpty()) Text("Shown as your avatar initial", style = ts(16, 600, TraceColors.Ghost))
                        inner()
                    }
                },
            )
        }

        Section("Currency") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CURRENCIES.forEach { c ->
                    val on = c == currency
                    val shape = RoundedCornerShape(18.dp)
                    Box(
                        Modifier.size(width = 64.dp, height = 48.dp).clip(shape)
                            .background(if (on) TraceColors.Ink else TraceColors.Surface)
                            .border(1.dp, if (on) TraceColors.Ink else TraceColors.Border, shape)
                            .tap(role = Role.RadioButton, label = c) {
                                app.prefs.setCurrency(c)
                                scope.launch { QuickAdd.refresh(context) }
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(c, style = ts(18, 800, if (on) TraceColors.OnInk else TraceColors.Ink)) }
                }
            }
        }

        Section("Appearance") {
            val mode by app.prefs.themeMode.collectAsStateWithLifecycle()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(ThemeMode.Light, "Light", TraceIcons.Sun),
                    Triple(ThemeMode.Dark, "Dark", TraceIcons.Moon),
                    Triple(ThemeMode.System, "Match phone", TraceIcons.icon("phone")),
                ).forEach { (m, label, icon) ->
                    val on = m == mode
                    val shape = RoundedCornerShape(18.dp)
                    val fg = if (on) TraceColors.OnInk else TraceColors.Ink
                    Column(
                        Modifier.weight(1f).height(64.dp).clip(shape)
                            .background(if (on) TraceColors.Ink else TraceColors.Surface)
                            .border(1.dp, if (on) TraceColors.Ink else TraceColors.Border, shape)
                            .tap(role = Role.RadioButton, label = label) { app.prefs.setThemeMode(m) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                    ) {
                        TIcon(icon, fg, 18.dp)
                        Text(label, style = ts(13, 700, fg), maxLines = 1)
                    }
                }
            }
        }

        Section("Categories") {
            val cats by com.homeboundlabs.trace.data.Categories.all.collectAsStateWithLifecycle()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, TraceColors.Border, RoundedCornerShape(18.dp))
                    .tap(label = "Manage categories", onClick = onCategories).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Manage categories", style = ts(15, 700))
                    Text(cats.joinToString(" · ") { it.name }, style = ts(13, 400, TraceColors.Muted), maxLines = 2)
                }
                Text("›", style = ts(20, 700, TraceColors.Muted))
            }
        }

        Section("Lock screen") {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, TraceColors.Border, RoundedCornerShape(18.dp))
                    .tap(role = Role.Switch) { setQuickAdd(!quickAdd) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Lock-screen notification", style = ts(15, 700))
                    Text(
                        "One small silent line on your lock screen. Tap it to add an expense without unlocking.",
                        style = ts(13, 400, TraceColors.Muted),
                    )
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
            if (denied) {
                Text(
                    "Notifications are blocked. Open system settings to allow them.",
                    style = ts(13, 600, TraceColors.Negative),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).tap {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    }.padding(vertical = 8.dp),
                )
            }
            LockScreenExtras()
        }

        Section("About") { AboutSection() }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = ts(13, 600, TraceColors.Muted))
        content()
    }
}
