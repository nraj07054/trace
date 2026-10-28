package com.homeboundlabs.trace.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.homeboundlabs.trace.R

object TraceColors {
    val Ink = Color(0xFF0E0F12)
    val Muted = Color(0xFF6B7080)
    val Faint = Color(0xFF8A8F9C)
    val Ghost = Color(0xFFB8BCC6)
    val Surface = Color(0xFFFFFFFF)
    val Fill = Color(0xFFF1F2F5)
    val Field = Color(0xFFF7F8FA)
    val Border = Color(0xFFECEEF2)
    val Positive = Color(0xFF12804A)
    val PositiveBg = Color(0xFFE8F7EE)
    val Negative = Color(0xFFC2410C)
    val NegativeBg = Color(0xFFFFF1EA)
    val Accent = Color(0xFFFF7A45)
    val Dash = Color(0xFFD9DCE3)
    val EmptyBar = Color(0xFFE6E8EE)
    val NavInactive = Color(0xFFA1A6B3)
    val AvatarText = Color(0xFF4A4F5C)
    val ChipSubOn = Color(0xFFC9CCD4)
}

/** cubic-bezier(.34,1.56,.64,1) from the designs — overshoots, then settles. */
val SpringEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Jakarta = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(R.font.plus_jakarta_sans, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

/** Text style shorthand matching the CSS in the designs (size in px→sp, weight, letter-spacing in px). */
fun ts(size: Int, weight: Int, color: Color = TraceColors.Ink, letterSpacing: Float = 0f, tight: Boolean = false) = TextStyle(
    fontFamily = Jakarta,
    fontSize = size.sp,
    fontWeight = FontWeight(weight),
    color = color,
    letterSpacing = letterSpacing.sp,
    lineHeight = if (tight) size.sp else TextStyle.Default.lineHeight,
    lineHeightStyle = if (tight) LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both) else null,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

@Composable
fun rememberReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@Composable
fun TraceTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = TraceColors.Ink,
            onPrimary = Color.White,
            background = TraceColors.Surface,
            surface = TraceColors.Surface,
            onSurface = TraceColors.Ink,
            onBackground = TraceColors.Ink,
            surfaceContainerHigh = TraceColors.Surface,
            surfaceContainerHighest = TraceColors.Fill,
        ),
        typography = Typography(
            bodyLarge = base.bodyLarge.copy(fontFamily = Jakarta),
            bodyMedium = base.bodyMedium.copy(fontFamily = Jakarta),
            bodySmall = base.bodySmall.copy(fontFamily = Jakarta),
            titleLarge = base.titleLarge.copy(fontFamily = Jakarta),
            titleMedium = base.titleMedium.copy(fontFamily = Jakarta),
            titleSmall = base.titleSmall.copy(fontFamily = Jakarta),
            labelLarge = base.labelLarge.copy(fontFamily = Jakarta),
            labelMedium = base.labelMedium.copy(fontFamily = Jakarta),
            labelSmall = base.labelSmall.copy(fontFamily = Jakarta),
            headlineSmall = base.headlineSmall.copy(fontFamily = Jakarta),
            headlineMedium = base.headlineMedium.copy(fontFamily = Jakarta),
        ),
        content = content,
    )
}
