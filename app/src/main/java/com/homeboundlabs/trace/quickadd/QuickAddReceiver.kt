package com.homeboundlabs.trace.quickadd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.homeboundlabs.trace.data.Category
import com.homeboundlabs.trace.traceApp
import kotlinx.coroutines.launch

/** Handles the shortcut's inline reply, and re-posting after boot/update. */
class QuickAddReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.traceApp
        val pending = goAsync()
        app.scope.launch {
            try {
                when (intent.action) {
                    QuickAdd.ACTION_SAVE -> {
                        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(QuickAdd.KEY_AMOUNT)?.toString()
                        val parsed = text?.let(QuickAdd::parseReply)
                        if (parsed == null) {
                            QuickAdd.refresh(context)
                        } else {
                            val (minor, note, override) = parsed
                            val cat = override ?: app.prefs.quickCategory
                            app.repo.add(minor, cat, note, System.currentTimeMillis())
                            QuickAdd.refresh(context, justAdded = minor to cat)
                            // Show the confirmation briefly, then go back to "Add an expense".
                            app.scope.launch {
                                kotlinx.coroutines.delay(6_000)
                                QuickAdd.refresh(context)
                            }
                        }
                    }
                    else -> QuickAdd.refresh(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
