package com.homeboundlabs.trace.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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

/** One full set of app colors; [TraceColors] serves the light or the dark one. */
@Immutable
class Palette(
    val ink: Color,
    /** Text and icons drawn on an [ink] background (buttons, selected chips). */
    val onInk: Color,
    val muted: Color,
    val faint: Color,
    val ghost: Color,
    val surface: Color,
    val fill: Color,
    val field: Color,
    val border: Color,
    val positive: Color,
    val positiveBg: Color,
    val negative: Color,
    val negativeBg: Color,
    val accent: Color,
    /** Soft tint of [accent] behind accent icons (the Ko-fi cup). */
    val accentBg: Color,
    val warn: Color,
    val warnBg: Color,
    val info: Color,
    val infoBg: Color,
    val dash: Color,
    val emptyBar: Color,
    val navInactive: Color,
    val avatarText: Color,
    val chipSubOn: Color,
    /** Keypad keys: one step up from the page, so they read as buttons. */
    val key: Color,
    /** Small round buttons such as the month and date arrows. */
    val roundButton: Color,
    /** A main button that can't be pressed yet, and its label. */
    val disabledBg: Color,
    val disabledText: Color,
    /** Bottom sheets and dialogs: on dark, a step lighter than the page so their edge shows. */
    val sheet: Color,
    /** A switch that's on. */
    val switchOn: Color,
    val switchThumb: Color,
)

val LightPalette = Palette(
    ink = Color(0xFF0E0F12),
    onInk = Color(0xFFFFFFFF),
    muted = Color(0xFF6B7080),
    faint = Color(0xFF8A8F9C),
    ghost = Color(0xFFB8BCC6),
    surface = Color(0xFFFFFFFF),
    fill = Color(0xFFF1F2F5),
    field = Color(0xFFF7F8FA),
    border = Color(0xFFECEEF2),
    positive = Color(0xFF12804A),
    positiveBg = Color(0xFFE8F7EE),
    negative = Color(0xFFC2410C),
    negativeBg = Color(0xFFFFF1EA),
    accent = Color(0xFFFF7A45),
    accentBg = Color(0xFFFFE9DF),
    warn = Color(0xFFB45309),
    warnBg = Color(0xFFFFF4E0),
    info = Color(0xFF3949AB),
    infoBg = Color(0xFFEEF1FF),
    dash = Color(0xFFD9DCE3),
    emptyBar = Color(0xFFE6E8EE),
    navInactive = Color(0xFFA1A6B3),
    avatarText = Color(0xFF4A4F5C),
    chipSubOn = Color(0xFFC9CCD4),
    key = Color(0xFFF7F8FA),
    roundButton = Color(0xFFF1F2F5),
    disabledBg = Color(0x400E0F12),
    disabledText = Color(0xFFFFFFFF),
    sheet = Color(0xFFFFFFFF),
    switchOn = Color(0xFF0E0F12),
    switchThumb = Color(0xFFFFFFFF),
)

/** Near-black surfaces and greys from the dark "B" designs; the orange accent stays. */
val DarkPalette = Palette(
    ink = Color(0xFFF4F5F7),
    onInk = Color(0xFF0C0D10),
    muted = Color(0xFF9298A4),
    faint = Color(0xFF7A808C),
    ghost = Color(0xFF5C616D),
    surface = Color(0xFF0C0D10),
    fill = Color(0xFF17191E),
    field = Color(0xFF15171B),
    border = Color(0xFF262930),
    positive = Color(0xFF5BD68C),
    positiveBg = Color(0xFF10261A),
    negative = Color(0xFFFF8A5B),
    negativeBg = Color(0xFF2B1810),
    accent = Color(0xFFFF7A45),
    accentBg = Color(0xFF2A1A12),
    warn = Color(0xFFFBBF24),
    warnBg = Color(0xFF2A2110),
    info = Color(0xFF9AA8FF),
    infoBg = Color(0xFF1A1E36),
    dash = Color(0xFF2A2D35),
    emptyBar = Color(0xFF23262D),
    navInactive = Color(0xFF6E7480),
    avatarText = Color(0xFFC9CDD6),
    chipSubOn = Color(0xFF4A4F5C),
    key = Color(0xFF1D2026),
    roundButton = Color(0xFF1D2026),
    disabledBg = Color(0xFF1D2026),
    disabledText = Color(0xFF6E7480),
    sheet = Color(0xFF17191E),
    switchOn = Color(0xFFFF7A45),
    switchThumb = Color(0xFFFFFFFF),
)

/**
 * The colors in use. [isDark] is snapshot state, so everything that reads a color here
 * redraws when it flips; [ThemeHost] flips it behind the sunset/sunrise animation.
 */
object TraceColors {
    var isDark by mutableStateOf(false)
        internal set

    private val p: Palette get() = if (isDark) DarkPalette else LightPalette

    val Ink: Color get() = p.ink
    val OnInk: Color get() = p.onInk
    val Muted: Color get() = p.muted
    val Faint: Color get() = p.faint
    val Ghost: Color get() = p.ghost
    val Surface: Color get() = p.surface
    val Fill: Color get() = p.fill
    val Field: Color get() = p.field
    val Border: Color get() = p.border
    val Positive: Color get() = p.positive
    val PositiveBg: Color get() = p.positiveBg
    val Negative: Color get() = p.negative
    val NegativeBg: Color get() = p.negativeBg
    val Accent: Color get() = p.accent
    val AccentBg: Color get() = p.accentBg
    val Warn: Color get() = p.warn
    val WarnBg: Color get() = p.warnBg
    val Info: Color get() = p.info
    val InfoBg: Color get() = p.infoBg
    val Dash: Color get() = p.dash
    val EmptyBar: Color get() = p.emptyBar
    val NavInactive: Color get() = p.navInactive
    val AvatarText: Color get() = p.avatarText
    val ChipSubOn: Color get() = p.chipSubOn
    val Key: Color get() = p.key
    val RoundButton: Color get() = p.roundButton
    val DisabledBg: Color get() = p.disabledBg
    val DisabledText: Color get() = p.disabledText
    val Sheet: Color get() = p.sheet
    val SwitchOn: Color get() = p.switchOn
    val SwitchThumb: Color get() = p.switchThumb

    /** Category colors are stored once; on dark surfaces they're lifted a little so they stay bright. */
    fun category(argb: Long): Color = if (isDark) lerp(Color(argb), Color.White, 0.12f) else Color(argb)
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
        colorScheme = if (TraceColors.isDark) {
            darkColorScheme(
                primary = TraceColors.Ink,
                onPrimary = TraceColors.OnInk,
                background = TraceColors.Surface,
                surface = TraceColors.Surface,
                onSurface = TraceColors.Ink,
                onBackground = TraceColors.Ink,
                onSurfaceVariant = TraceColors.Muted,
                surfaceContainerHigh = TraceColors.Surface,
                surfaceContainerHighest = TraceColors.Fill,
            )
        } else {
            lightColorScheme(
                primary = TraceColors.Ink,
                onPrimary = TraceColors.OnInk,
                background = TraceColors.Surface,
                surface = TraceColors.Surface,
                onSurface = TraceColors.Ink,
                onBackground = TraceColors.Ink,
                surfaceContainerHigh = TraceColors.Surface,
                surfaceContainerHighest = TraceColors.Fill,
            )
        },
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
