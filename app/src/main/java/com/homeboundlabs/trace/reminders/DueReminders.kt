package com.homeboundlabs.trace.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.homeboundlabs.trace.MainActivity
import com.homeboundlabs.trace.R
import com.homeboundlabs.trace.data.Money
import com.homeboundlabs.trace.data.balance
import com.homeboundlabs.trace.quickadd.QuickAdd
import com.homeboundlabs.trace.traceApp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Local "it's due today" reminders for lend/borrow entries. One alarm per person, at 10 am on the
 * earliest due date that's still open. Inexact on purpose: no exact-alarm permission, and a reminder
 * a few minutes late is fine.
 */
object DueReminders {
    const val CHANNEL = "due_reminders"
    private const val EXTRA_PERSON = "person"
    private val REMIND_AT: LocalTime = LocalTime.of(10, 0)

    fun ensureChannel(context: Context) {
        val ch = NotificationChannel(CHANNEL, "Lend & borrow reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "On the day money is due back, or due to be paid back"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    /** The moment a reminder for [dueDay] (any time on that day) goes off. */
    fun remindTime(dueMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(dueMillis).atZone(zone).toLocalDate().atTime(REMIND_AT).atZone(zone).toInstant().toEpochMilli()

    /** Re-plans every reminder from the ledger. Cheap; called after each ledger change and on boot. */
    suspend fun schedule(context: Context) {
        val app = context.traceApp
        val am = context.getSystemService(AlarmManager::class.java)
        val previous = app.prefs.reminderPeople
        val now = System.currentTimeMillis()
        val next = HashSet<Long>()
        app.ledger.withDueDates().groupBy { it.personId }.forEach { (personId, dated) ->
            if (app.ledger.entries(personId).balance() == 0L) return@forEach
            val at = dated.mapNotNull { it.dueAt }.map(::remindTime).filter { it > now }.minOrNull() ?: return@forEach
            am.set(AlarmManager.RTC_WAKEUP, at, pending(context, personId))
            next += personId
        }
        (previous - next).forEach { am.cancel(pending(context, it)) }
        app.prefs.reminderPeople = next
    }

    private fun pending(context: Context, personId: Long): PendingIntent = PendingIntent.getBroadcast(
        context, personId.toInt(),
        Intent(context, DueReminderReceiver::class.java).putExtra(EXTRA_PERSON, personId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal suspend fun post(context: Context, personId: Long) {
        val app = context.traceApp
        if (!QuickAdd.canPost(context)) return
        val person = app.ledger.person(personId) ?: return
        val balance = app.ledger.entries(personId).balance()
        if (balance == 0L) return
        val amount = Money.compact(kotlin.math.abs(balance), app.prefs.currency.value)
        val (title, text) = if (balance > 0) {
            "${person.name} owes you $amount" to "It was due back today. Tap to send a friendly reminder."
        } else {
            "You owe ${person.name} $amount" to "You planned to pay it back today."
        }
        val open = PendingIntent.getActivity(
            context, 1000 + personId.toInt(),
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PERSON, personId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_trace)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(2000 + personId.toInt(), n)
        } catch (_: SecurityException) {
            // Permission was withdrawn between the check and the post.
        }
    }

    internal const val PERSON_EXTRA = EXTRA_PERSON
}

class DueReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.traceApp
        val personId = intent.getLongExtra(DueReminders.PERSON_EXTRA, -1)
        val pending = goAsync()
        app.scope.launch {
            try {
                if (personId >= 0) DueReminders.post(context, personId)
                DueReminders.schedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}
