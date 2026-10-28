package com.homeboundlabs.trace.quickadd

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.homeboundlabs.trace.R
import com.homeboundlabs.trace.data.Category
import com.homeboundlabs.trace.data.Money
import com.homeboundlabs.trace.traceApp

/**
 * Lock-screen shortcut, kept deliberately small: one standard, silent notification line.
 * Standard templates render the same on every brand, unlike custom layouts.
 * Tapping it opens [QuickAddActivity] over the lock screen; the inline reply is a fallback
 * for phones that insist on unlocking before opening anything.
 */
object QuickAdd {
    const val CHANNEL = "quick_add_v3"
    // "payments_detected" belonged to the removed UPI auto-log.
    private val OLD_CHANNELS = listOf("quick_add", "quick_add_v2", "payments_detected")
    private const val ID = 7
    const val EXTRA_CATEGORY = "category"
    const val KEY_AMOUNT = "amount"
    const val ACTION_SAVE = "com.homeboundlabs.trace.action.QUICK_SAVE"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        // Channel importance can't change once created, so older channels are replaced.
        OLD_CHANNELS.forEach(nm::deleteNotificationChannel)
        // DEFAULT, not LOW: stock Android hides "silent" (LOW) notifications from the lock screen,
        // which is the one place this must show. No sound or vibration, and no heads-up (that needs HIGH).
        val ch = NotificationChannel(CHANNEL, "Lock-screen shortcut", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A small silent shortcut to add an expense from the lock screen"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(ch)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(ID)

    /** Posts (or removes) the shortcut. [justAdded] briefly shows a confirmation after an inline reply. */
    suspend fun refresh(context: Context, justAdded: Pair<Long, Category>? = null) {
        val app = context.traceApp
        if (!app.prefs.quickAddEnabled.value || !canPost(context)) {
            cancel(context); return
        }
        val currency = app.prefs.currency.value

        val reply = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_trace, "Type amount",
            PendingIntent.getBroadcast(
                context, 10,
                Intent(context, QuickAddReceiver::class.java).setAction(ACTION_SAVE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            ),
        ).addRemoteInput(
            RemoteInput.Builder(KEY_AMOUNT).setLabel("e.g. 450 lunch, 120 cab").build(),
        ).setAuthenticationRequired(false).setAllowGeneratedReplies(false).build()

        val (title, text) = if (justAdded != null) {
            "Added to ${justAdded.second.label}" to "−" + Money.compact(justAdded.first, currency) + " · tap to add another"
        } else {
            "Add an expense" to "Tap here, no unlock needed"
        }

        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_trace)
            .setColor(0xFFFF7A45.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(quickAddIntent(context, null))
            .addAction(reply)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(ID, n)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; nothing to show.
        }
    }

    /** Opens the add box; it shows over the lock screen where the phone allows it. */
    fun quickAddIntent(context: Context, c: Category?): PendingIntent {
        val i = Intent(context, QuickAddActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (c != null) i.putExtra(EXTRA_CATEGORY, c.id)
        return PendingIntent.getActivity(context, 20 + (c?.id?.hashCode()?.and(0xFFFF) ?: 0), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    // Words that pick a category from an inline reply like "120 cab".
    private val keywords: Map<String, List<String>> = mapOf(
        Category.Food.id to listOf("food", "lunch", "dinner", "breakfast", "coffee", "tea", "chai", "snack", "swiggy", "zomato", "groceries", "grocery"),
        Category.Travel.id to listOf("travel", "cab", "uber", "ola", "auto", "metro", "bus", "train", "fuel", "petrol", "diesel", "parking", "rapido"),
        Category.Shopping.id to listOf("shop", "shopping", "amazon", "flipkart", "myntra", "clothes", "shirt", "shoes", "gift"),
        Category.Bills.id to listOf("bill", "bills", "electricity", "recharge", "rent", "wifi", "internet", "water", "gas"),
        Category.Fun.id to listOf("fun", "movie", "movies", "cinema", "ride", "rides", "park", "game", "games", "concert", "party", "outing"),
    )

    /** Parses replies like "450", "450 lunch" or "120 cab" → (minor, note, category if a word implies one). */
    fun parseReply(text: String): Triple<Long, String, Category?>? {
        val match = Regex("""\d+(?:\.\d{1,2})?""").find(text.replace(",", "")) ?: return null
        val minor = Money.parseMinor(match.value)
        if (minor <= 0) return null
        val rest = text.replace(",", "").removeRange(match.range).replace(Regex("[₹$€£]"), "").trim()
        val words = rest.split(Regex("\\s+")).filter { it.isNotBlank() }
        val cats = Category.entries
        val override = words.firstNotNullOfOrNull { w ->
            val lw = w.lowercase()
            // A category's own name wins (works for ones you created), then the built-in keywords.
            cats.firstOrNull { it.name.equals(lw, true) || it.id.equals(lw, true) }
                ?: keywords.entries.firstOrNull { (_, list) -> lw in list }?.let { (id, _) -> cats.firstOrNull { it.id == id } }
        }
        // Bare category names ("food", "bills") are dropped from the note; descriptive words stay.
        val note = words.filterNot { w -> Category.entries.any { it.id.equals(w, true) || it.label.equals(w, true) } }
            .joinToString(" ").replaceFirstChar { it.uppercase() }
        return Triple(minor, note, override)
    }
}
