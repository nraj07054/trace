package com.homeboundlabs.trace.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.homeboundlabs.trace.ui.theme.TraceColors
import com.homeboundlabs.trace.ui.theme.ts

// The app icon's dot is 10 units across beside a T 35 units tall, and its bottom sits 1 unit below
// the T's base. Plus Jakarta Sans at weight 800 has a cap height of 0.745 em, so the same
// proportions give a dot 0.213 em across, 0.021 em below the baseline.
private const val DOT_EM = 0.745f * 10f / 35f
private const val DROP_EM = 0.745f * 1f / 35f

/**
 * "Trace." with a round accent dot drawn to the app icon's proportions. The font's own full stop
 * is squarish at this weight, so it's laid out invisibly (keeping the font's spacing after the
 * "e" and the line's height) and a circle is painted centred on it.
 */
@Composable
fun Wordmark(fontSize: Int, modifier: Modifier = Modifier) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val accent = TraceColors.Accent
    Text(
        buildAnnotatedString {
            append("Trace")
            withStyle(SpanStyle(color = Color.Transparent)) { append(".") }
        },
        style = ts(fontSize, 800, letterSpacing = -0.6f),
        onTextLayout = { layout = it },
        modifier = modifier
            .semantics { contentDescription = "Trace" }
            .drawWithContent {
                drawContent()
                val l = layout ?: return@drawWithContent
                val em = fontSize.toFloat() * fontScale * density
                val r = DOT_EM * em / 2
                val x = l.getBoundingBox(5).center.x
                val bottom = l.getLineBaseline(0) + DROP_EM * em
                drawCircle(accent, radius = r, center = Offset(x, bottom - r))
            },
    )
}
