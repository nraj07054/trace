package com.homeboundlabs.trace.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.homeboundlabs.trace.BuildConfig
import com.homeboundlabs.trace.ui.components.TIcon
import com.homeboundlabs.trace.ui.components.TraceIcons
import com.homeboundlabs.trace.ui.components.tap
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.ts

const val KOFI_URL = "https://ko-fi.com/hey_nikhilraj"
private const val SOURCE_URL = "https://github.com/nraj07054/trace-app"

/**
 * Quiet "About" block at the end of Settings: an optional Ko-fi tip, the source link and the version.
 * Links open in the browser, so the app itself still needs no internet permission.
 */
@Composable
fun AboutSection() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(TraceColors.Field).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).rotate(-6f).clip(RoundedCornerShape(15.dp)).background(TraceColors.AccentBg),
                    contentAlignment = Alignment.Center,
                ) { TIcon(TraceIcons.Cup, TraceColors.Accent, 24.dp) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Enjoying Trace?", style = ts(16, 800))
                    Text(
                        "It's free, with no ads and no sign-up. If it helps you, you can buy me a coffee.",
                        style = ts(13, 500, TraceColors.Muted),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(TraceColors.Accent)
                    .tap(role = Role.Button, label = "Support Trace on Ko-fi") { openLink(context, KOFI_URL) },
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TIcon(TraceIcons.Heart, Color.White, 16.dp)
                Text("Support on Ko-fi", style = ts(15, 700, Color.White))
            }
            Text("Totally optional. Trace stays free either way.", style = ts(12, 500, TraceColors.Faint))
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, TraceColors.Border, RoundedCornerShape(18.dp))
                .tap(role = Role.Button, label = "Open the source code on GitHub") { openLink(context, SOURCE_URL) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TIcon(TraceIcons.Code, TraceColors.Ink, 18.dp)
            Column(Modifier.weight(1f)) {
                Text("Source code", style = ts(15, 700))
                Text("Open source on GitHub", style = ts(12, 500, TraceColors.Muted))
            }
            Text("›", style = ts(20, 700, TraceColors.Muted))
        }

        Text(
            "Trace ${BuildConfig.VERSION_NAME} · GPL-3.0",
            style = ts(12, 500, TraceColors.Faint),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Opens a web link in the browser; silently does nothing if no browser is installed. */
fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}
