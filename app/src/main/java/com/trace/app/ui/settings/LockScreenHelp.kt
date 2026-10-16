package com.trace.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.trace.app.traceApp
import com.trace.app.ui.components.tap
import com.trace.app.ui.theme.TraceColors
import com.trace.app.ui.theme.ts

/* ---------- Lock screen ---------- */

/** Health check, plus help specific to this phone's brand until it works. */
@Composable
fun LockScreenExtras() {
    val context = LocalContext.current
    val app = context.traceApp
    var verified by remember { mutableStateOf(app.prefs.lockScreenVerified) }
    LifecycleResumeEffect(Unit) {
        verified = app.prefs.lockScreenVerified
        onPauseOrDispose { }
    }

    StatusRow(
        ok = verified,
        title = if (verified) "Works on this phone" else "Not tested yet",
        body = if (verified) "You've added an expense from the lock screen here before."
        else "Lock your phone, then tap Trace's “Add an expense” notification. Once it opens without unlocking, this turns green.",
    )

    // Only needed while it isn't working yet.
    if (verified) return
    val help = brandHelp()
    Card {
        Text("If it asks you to unlock", style = ts(15, 700))
        Text(help.text, style = ts(13, 400, TraceColors.Muted))
        PillButton(help.button) { help.open(context) }
    }
}

private class BrandHelp(val text: String, val button: String, val open: (Context) -> Unit)

private fun brandHelp(): BrandHelp {
    val brand = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
    return when {
        listOf("xiaomi", "redmi", "poco").any { it in brand } -> BrandHelp(
            "Xiaomi phones hide apps on the lock screen until you allow it: App info → Other permissions → “Show on Lock screen”.",
            "Open permissions",
        ) { ctx ->
            val miui = Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                .putExtra("extra_pkgname", ctx.packageName)
            if (!tryStart(ctx, miui)) openAppDetails(ctx)
        }
        listOf("oppo", "realme", "oneplus").any { it in brand } -> BrandHelp(
            "Settings → Notifications & status bar → Trace → turn on lock-screen notifications.",
            "Open notification settings",
        ) { openNotificationSettings(it) }
        listOf("vivo", "iqoo").any { it in brand } -> BrandHelp(
            "Settings → Apps → Trace → Permissions → allow “Display on lock screen”, and lock-screen notifications.",
            "Open app settings",
        ) { openAppDetails(it) }
        "samsung" in brand -> BrandHelp(
            "Settings → Notifications → Lock screen notifications → make sure Trace is allowed and content is shown.",
            "Open notification settings",
        ) { openNotificationSettings(it) }
        else -> BrandHelp(
            "Make sure Trace's notifications are allowed on the lock screen, with content shown.",
            "Open notification settings",
        ) { openNotificationSettings(it) }
    }
}

/* ---------- small pieces ---------- */

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, TraceColors.Border, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun StatusRow(ok: Boolean, title: String, body: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (ok) TraceColors.PositiveBg else TraceColors.Field).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(if (ok) TraceColors.Positive else TraceColors.Ghost),
            contentAlignment = Alignment.Center,
        ) { Text(if (ok) "✓" else "?", style = ts(12, 800, Color.White)) }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = ts(14, 700, if (ok) TraceColors.Positive else TraceColors.Ink))
            Text(body, style = ts(13, 400, TraceColors.Muted))
        }
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(TraceColors.Ink).tap(label = label, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = ts(13, 700, Color.White)) }
}

@Composable
fun TraceSwitch(checked: Boolean) {
    Switch(
        checked = checked, onCheckedChange = null,
        colors = SwitchDefaults.colors(
            checkedTrackColor = TraceColors.Ink, checkedThumbColor = Color.White,
            uncheckedTrackColor = TraceColors.Fill, uncheckedBorderColor = TraceColors.Border,
            uncheckedThumbColor = TraceColors.Faint,
        ),
    )
}

private fun tryStart(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

private fun openAppDetails(context: Context) {
    tryStart(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
}

private fun openNotificationSettings(context: Context) {
    val ok = tryStart(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    if (!ok) openAppDetails(context)
}
