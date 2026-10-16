package com.trace.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.trace.app.data.Category
import com.trace.app.data.CategoryIcons

/** Line icons copied from the SVGs in the designs (24×24 viewBox). */
object TraceIcons {
    val Home = fill("M3 11.2a2 2 0 0 1 .7-1.5l7-6a2 2 0 0 1 2.6 0l7 6a2 2 0 0 1 .7 1.5V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")
    val Insights = stroke("M6 20V12M12 20V5M18 20v-9", 2.2f)
    val Settings = stroke(
        "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0" +
            "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z",
        2.2f,
    )
    val Plus = stroke("M12 5v14M5 12h14", 2.6f)
    val Close = stroke("M6 6l12 12M18 6L6 18", 2.4f)
    val Back = stroke("M15 5l-7 7 7 7", 2.4f)
    val Check = stroke("M5 12.5l4.5 4.5L19 7.5", 3f)
    val ArrowDown = stroke("M7 7l10 10M17 8v9H8", 3f)
    val ArrowUp = stroke("M7 17L17 7M8 7h9v9", 3f)
    val Trash = stroke("M4 7h16M10 11v6M14 11v6M5.5 7l1 12a2 2 0 0 0 2 2h7a2 2 0 0 0 2-2l1-12M9 7V4.5A1.5 1.5 0 0 1 10.5 3h3A1.5 1.5 0 0 1 15 4.5V7", 2.2f)
    val Bell = stroke("M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.94 1.94 0 0 0 3.4 0", 2f)

    private val iconCache = HashMap<String, ImageVector>()
    fun category(c: Category) = icon(c.icon)

    /** Category icon for a key in [CategoryIcons] (falls back to a tag). */
    fun icon(key: String): ImageVector = iconCache.getOrPut(key) {
        stroke(CategoryIcons[key] ?: CategoryIcons.getValue("tag"), 2f)
    }

    private fun stroke(d: String, width: Float) = ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(d),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

    private fun fill(d: String) = ImageVector.Builder(
        defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black)).build()
}

@Composable
fun TIcon(icon: ImageVector, tint: Color, size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Icon(icon, contentDescription, modifier.size(size), tint = tint)
}

/** Backspace key icon from the keypad screenshots. */
val BackspaceIcon: ImageVector = ImageVector.Builder(
    defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
).addPath(
    pathData = addPathNodes("M21 5H9l-6 7 6 7h12a1 1 0 0 0 1-1V6a1 1 0 0 0-1-1zM12.5 9.5l5 5M17.5 9.5l-5 5"),
    stroke = SolidColor(Color.Black), strokeLineWidth = 2.4f,
    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
).build()
