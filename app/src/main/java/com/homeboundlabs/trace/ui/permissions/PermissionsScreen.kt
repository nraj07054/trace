package com.homeboundlabs.trace.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.homeboundlabs.trace.data.Prefs
import com.homeboundlabs.trace.quickadd.QuickAdd
import com.homeboundlabs.trace.quickadd.Voice
import com.homeboundlabs.trace.traceApp
import com.homeboundlabs.trace.ui.components.TIcon
import com.homeboundlabs.trace.ui.components.TraceIcons
import com.homeboundlabs.trace.ui.components.tap
import com.homeboundlabs.trace.ui.theme.SpringEasing
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.rememberReduceMotion
import com.homeboundlabs.trace.ui.theme.ts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Which permissions are worth asking about on this phone right now. */
object Permissions {
    fun notificationsNeeded(context: Context) =
        Build.VERSION.SDK_INT >= 33 && !QuickAdd.canPost(context)

    fun micGranted(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** True when the friendly permissions screen has something new to explain. */
    suspend fun shouldShow(context: Context, prefs: Prefs): Boolean {
        val notif = prefs.quickAddEnabled.value && notificationsNeeded(context) && !prefs.askedNotificationPermission
        if (notif) return true
        return !prefs.askedMicPermission && !micGranted(context) && Voice.offlineLanguage(context) != null
    }
}

private enum class Status { Ask, Allowed, Declined }

/**
 * Explains each permission in Trace's own words before Android's pop-up appears. Android's
 * dialog only opens when the person taps Allow on a card; Continue or Skip leaves without asking.
 */
@Composable
fun PermissionsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.traceApp
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()

    // Android 12 and older grant notifications at install, so there's nothing to explain there.
    val showNotif = Build.VERSION.SDK_INT >= 33 && app.prefs.quickAddEnabled.value
    var notif by remember { mutableStateOf(if (Permissions.notificationsNeeded(context)) Status.Ask else Status.Allowed) }
    var showMic by remember { mutableStateOf(false) }
    var mic by remember { mutableStateOf(if (Permissions.micGranted(context)) Status.Allowed else Status.Ask) }

    LaunchedEffect(Unit) { showMic = Voice.offlineLanguage(context) != null }
    // Coming back from Android settings: pick up any change made there.
    LifecycleResumeEffect(Unit) {
        if (!Permissions.notificationsNeeded(context)) notif = Status.Allowed
        if (Permissions.micGranted(context)) mic = Status.Allowed
        onPauseOrDispose { }
    }

    // Set once you've answered a pop-up; the screen only moves on by itself after that.
    var acted by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        app.prefs.askedNotificationPermission = true
        notif = if (granted) Status.Allowed else Status.Declined
        acted = true
        app.prefs.setQuickAddEnabled(granted)
        app.scope.launch { QuickAdd.refresh(context) }
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        app.prefs.askedMicPermission = true
        mic = if (granted) Status.Allowed else Status.Declined
        acted = true
    }

    fun finish() {
        if (finished) return
        finished = true
        // Seen once is enough: don't show this screen again for what was just explained.
        app.prefs.askedNotificationPermission = true
        if (showMic) app.prefs.askedMicPermission = true
        onDone()
    }

    // Every card answered (allowed or not)? Let the ✓ land, then move on by itself.
    val allAnswered = (!showNotif || notif != Status.Ask) && (!showMic || mic != Status.Ask)
    LaunchedEffect(acted, allAnswered) {
        if (acted && allAnswered) {
            delay(900)
            finish()
        }
    }

    val count = (if (showNotif) 1 else 0) + (if (showMic) 1 else 0)
    Column(
        Modifier.fillMaxSize().background(TraceColors.Surface).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("Skip", style = ts(14, 600, TraceColors.Muted), modifier = Modifier.clip(RoundedCornerShape(16.dp)).tap(label = "Skip") { finish() }.padding(horizontal = 12.dp, vertical = 10.dp))
        }
        Text(
            if (count <= 1) "One quick thing" else "Two quick things",
            style = ts(34, 800, letterSpacing = -1.2f),
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "Trace works without these. They just make adding a spend faster. Android will ask you to confirm each one.",
            style = ts(15, 500, TraceColors.Muted),
        )
        Spacer(Modifier.height(4.dp))

        if (showNotif) {
            PermissionCard(
                index = 0, reduceMotion = reduceMotion,
                icon = TraceIcons.Bell, tint = Color(0xFFFF7A45),
                title = "Notifications",
                why = "Shows one small, silent line on your lock screen. Tap it to add an expense without unlocking.",
                promise = "Silent · never ads or reminders",
                status = notif,
                onAllow = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                onSettings = { openAppSettings(context) },
            )
        }
        if (showMic) {
            PermissionCard(
                index = 1, reduceMotion = reduceMotion,
                icon = TraceIcons.Mic, tint = Color(0xFF2F7BFF),
                title = "Microphone",
                why = "Say “50 for vada pav” instead of typing. Trace listens only while the Listening… box is open.",
                promise = "Understood on your phone · audio never saved",
                status = mic,
                onAllow = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                onSettings = { openAppSettings(context) },
            )
        }

        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(TraceColors.Ink)
                .tap(label = "Continue") { scope.launch { finish() } },
            contentAlignment = Alignment.Center,
        ) { Text("Continue", style = ts(16, 700, Color.White)) }
        Text(
            "You can change these any time in Settings.",
            style = ts(13, 500, TraceColors.Faint),
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun PermissionCard(
    index: Int,
    reduceMotion: Boolean,
    icon: ImageVector,
    tint: Color,
    title: String,
    why: String,
    promise: String,
    status: Status,
    onAllow: () -> Unit,
    onSettings: () -> Unit,
) {
    // Cards pop in one after the other.
    val pop = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            delay(120L * index)
            pop.animateTo(1f, tween(550, easing = SpringEasing))
        }
    }
    Column(
        Modifier.fillMaxWidth()
            .graphicsLayer { alpha = pop.value.coerceIn(0f, 1f); translationY = (1f - pop.value) * 40f }
            .clip(RoundedCornerShape(24.dp)).border(1.dp, TraceColors.Border, RoundedCornerShape(24.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { TIcon(icon, tint, 22.dp) }
            Text(title, style = ts(18, 800), modifier = Modifier.weight(1f))
        }
        Text(why, style = ts(14, 500, TraceColors.Ink))
        Text(promise, style = ts(12, 600, TraceColors.Muted))
        AnimatedContent(
            targetState = status,
            transitionSpec = { (fadeIn(tween(200)) + scaleIn(tween(350, easing = SpringEasing), initialScale = 0.85f)) togetherWith fadeOut(tween(120)) },
            label = "status",
        ) { s ->
            when (s) {
                Status.Ask -> Box(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(TraceColors.Ink)
                        .tap(label = "Allow $title", onClick = onAllow),
                    contentAlignment = Alignment.Center,
                ) { Text("Allow", style = ts(15, 700, Color.White)) }
                Status.Allowed -> Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(TraceColors.PositiveBg),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(TraceColors.Positive), contentAlignment = Alignment.Center) {
                        TIcon(TraceIcons.Check, Color.White, 12.dp)
                    }
                    Text("Allowed", style = ts(15, 700, TraceColors.Positive))
                }
                Status.Declined -> Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(TraceColors.Field)
                        .tap(label = "Open settings", onClick = onSettings),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) { Text("Not now · Open settings", style = ts(14, 600, TraceColors.Muted)) }
            }
        }
    }
}

private fun openAppSettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: Exception) {
    }
}
