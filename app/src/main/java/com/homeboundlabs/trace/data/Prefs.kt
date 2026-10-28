package com.homeboundlabs.trace.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("trace", Context.MODE_PRIVATE)

    private val _currency = MutableStateFlow(sp.getString(KEY_CURRENCY, "₹") ?: "₹")
    val currency: StateFlow<String> = _currency.asStateFlow()

    private val _quickAdd = MutableStateFlow(sp.getBoolean(KEY_QUICK_ADD, true))
    val quickAddEnabled: StateFlow<Boolean> = _quickAdd.asStateFlow()

    private val _name = MutableStateFlow(sp.getString(KEY_NAME, "") ?: "")
    val name: StateFlow<String> = _name.asStateFlow()

    fun setCurrency(value: String) {
        sp.edit().putString(KEY_CURRENCY, value).apply(); _currency.value = value
    }

    fun setQuickAddEnabled(value: Boolean) {
        sp.edit().putBoolean(KEY_QUICK_ADD, value).apply(); _quickAdd.value = value
    }

    fun setName(value: String) {
        sp.edit().putString(KEY_NAME, value).apply(); _name.value = value
    }

    /** Category currently selected in the lock-screen quick-add notification. */
    var quickCategory: Category
        get() = Category.of(sp.getString(KEY_QUICK_CAT, Category.Food.id)!!)
        set(value) = sp.edit().putString(KEY_QUICK_CAT, value.id).apply()

    var askedNotificationPermission: Boolean
        get() = sp.getBoolean(KEY_ASKED, false)
        set(value) = sp.edit().putBoolean(KEY_ASKED, value).apply()

    /** Mic is asked for once at start (unlocked), so voice works later on the lock screen without a prompt. */
    var askedMicPermission: Boolean
        get() = sp.getBoolean(KEY_ASKED_MIC, false)
        set(value) = sp.edit().putBoolean(KEY_ASKED_MIC, value).apply()

    /** Set the first time the add box opens while the phone is locked: proof it works on this device. */
    var lockScreenVerified: Boolean
        get() = sp.getBoolean(KEY_LOCK_VERIFIED, false)
        set(value) = sp.edit().putBoolean(KEY_LOCK_VERIFIED, value).apply()

    private val _budgets = MutableStateFlow(decodeBudgets(sp.getString(KEY_BUDGETS, "") ?: ""))
    /** Monthly spending limits: category id → limit in minor units. */
    val budgets: StateFlow<Map<String, Long>> = _budgets.asStateFlow()

    fun setBudget(categoryId: String, limitMinor: Long?) {
        val next = _budgets.value.toMutableMap().apply { if (limitMinor == null || limitMinor <= 0) remove(categoryId) else put(categoryId, limitMinor) }
        sp.edit().putString(KEY_BUDGETS, next.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()
        _budgets.value = next
    }

    private fun decodeBudgets(raw: String): Map<String, Long> = raw.split(';').mapNotNull { part ->
        val i = part.lastIndexOf('=')
        if (i <= 0) null else part.substring(i + 1).toLongOrNull()?.let { part.substring(0, i) to it }
    }.toMap()

    var onboarded: Boolean
        get() = sp.getBoolean(KEY_ONBOARDED, false)
        set(value) = sp.edit().putBoolean(KEY_ONBOARDED, value).apply()

    /** True on installs that received the old debug sample data. */
    val hadSampleData: Boolean get() = sp.getBoolean(KEY_SEEDED, false)

    var sampleDataRemoved: Boolean
        get() = sp.getBoolean(KEY_SAMPLE_REMOVED, false)
        set(value) = sp.edit().putBoolean(KEY_SAMPLE_REMOVED, value).apply()

    private companion object {
        const val KEY_CURRENCY = "currency"
        const val KEY_QUICK_ADD = "quick_add"
        const val KEY_NAME = "name"
        const val KEY_QUICK_CAT = "quick_cat"
        const val KEY_ASKED = "asked_notif"
        const val KEY_ASKED_MIC = "asked_mic"
        const val KEY_SEEDED = "seeded"
        const val KEY_SAMPLE_REMOVED = "sample_removed"
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_BUDGETS = "budgets"
        const val KEY_LOCK_VERIFIED = "lock_verified"
    }
}
