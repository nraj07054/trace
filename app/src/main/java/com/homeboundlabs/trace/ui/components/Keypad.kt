package com.homeboundlabs.trace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.homeboundlabs.trace.ui.theme.ts

private val KEYS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "del")

/** 3×4 number pad. Light variant for Add expense, dark variant for the lock-screen card. */
@Composable
fun Keypad(
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyColor: Color,
    textColor: Color,
    keyHeight: Int = 56,
    fontSize: Int = 22,
    fontWeight: Int = 700,
) {
    val haptics = LocalHapticFeedback.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KEYS.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.weight(1f).height(keyHeight.dp).clip(RoundedCornerShape(18.dp)).background(keyColor)
                            .tap(label = if (k == "del") "Delete" else k) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onKey(k)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (k == "del") {
                            TIcon(BackspaceIcon, textColor, 24.dp, contentDescription = "Delete")
                        } else {
                            Text(k, style = ts(fontSize, fontWeight, textColor))
                        }
                    }
                }
            }
        }
    }
}

