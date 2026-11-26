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
import com.homeboundlabs.trace.quickadd.QuickAdd
import com.homeboundlabs.trace.quickadd.QuickAddActivity
import com.homeboundlabs.trace.traceApp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The evening check-in: "Anything to log today?", sent only on days with nothing logged yet.
 * "Add expense" opens the quick-add box; "Nothing today" keeps the streak for an honest empty day.
 * It's the only way to check in an empty day, so the app never shows a "spent nothing" button to chase.
 */
object CheckIn {
    const val CHANNEL = "check_in"
    private const val ID = 9
    private const val ACTION_FIRE = "com.homeboundlabs.trace.action.CHECK_IN"
    private const val ACTION_NOTHING = "com.homeboundlabs.trace.action.NOTHING_TODAY"
    private val AT: LocalTime = LocalTime.of(20, 30)

    fun ensureChannel(context: Context) {
        val ch = NotificationChannel(CHANNEL, "Evening check-in", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "At 8:30 pm, only on days with nothing logged yet"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    /**
     * Sets the alarm for the next 8:30 pm. Inexact: a few minutes late is fine. There's no in-app
     * switch: people turn it off like any notification, through Android's "Evening check-in" channel.
     */
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val fire = broadcast(context, ACTION_FIRE, 30)
        val zone = ZoneId.systemDefault()
        var next = ZonedDateTime.now(zone).with(AT).withSecond(0).withNano(0)
        if (!next.isAfter(ZonedDateTime.now(zone))) next = next.plusDays(1)
        am.set(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), fire)
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(ID)

    private fun broadcast(context: Context, action: String, code: Int) = PendingIntent.getBroadcast(
        context, code, Intent(context, CheckInReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal suspend fun onFire(context: Context) {
        val app = context.traceApp
        schedule(context) // tomorrow's
        if (!QuickAdd.canPost(context)) return
        val today = LocalDate.now()
        if (app.repo.todayTotal() > 0 || app.habits.isCheckedIn(today)) return

        val add = PendingIntent.getActivity(
            context, 31,
            Intent(context, QuickAddActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            context, 32, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_trace)
            .setContentTitle("Anything to log today?")
            .setContentText("Keep your record complete.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Add expense", add)
            .addAction(0, "Nothing today", broadcast(context, ACTION_NOTHING, 33))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID, n)
        } catch (_: SecurityException) {
            // Permission withdrawn between the check and the post.
        }
    }

    internal fun handles(action: String?) = action == ACTION_FIRE || action == ACTION_NOTHING
    internal fun isNothing(action: String?) = action == ACTION_NOTHING
}

class CheckInReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.traceApp
        val pending = goAsync()
        app.scope.launch {
            try {
                if (CheckIn.isNothing(intent.action)) {
                    app.habits.setNoSpend(LocalDate.now(), true).join()
                    CheckIn.cancel(context)
                } else if (CheckIn.handles(intent.action)) {
                    CheckIn.onFire(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
