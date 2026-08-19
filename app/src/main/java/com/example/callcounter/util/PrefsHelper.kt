package com.example.callcounter.util

import android.content.Context
import android.content.SharedPreferences

class PrefsHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "call_counter_prefs"
        private const val KEY_TARGET = "target_count"
        private const val KEY_DEDUP_MINUTES = "dedup_minutes"
        private const val KEY_FILTER_SHORT = "filter_short"
        private const val KEY_REMINDED_TODAY = "reminded_today"
        private const val KEY_LAST_DATE = "last_date"

        const val DEFAULT_TARGET = 50
        const val DEFAULT_DEDUP_MINUTES = 5
    }

    var targetCount: Int
        get() = prefs.getInt(KEY_TARGET, DEFAULT_TARGET)
        set(value) = prefs.edit().putInt(KEY_TARGET, value).apply()

    var dedupMinutes: Int
        get() = prefs.getInt(KEY_DEDUP_MINUTES, DEFAULT_DEDUP_MINUTES)
        set(value) = prefs.edit().putInt(KEY_DEDUP_MINUTES, value).apply()

    var filterShortNumber: Boolean
        get() = prefs.getBoolean(KEY_FILTER_SHORT, true)
        set(value) = prefs.edit().putBoolean(KEY_FILTER_SHORT, value).apply()

    var remindedToday: Boolean
        get() = prefs.getBoolean(KEY_REMINDED_TODAY, false)
        set(value) = prefs.edit().putBoolean(KEY_REMINDED_TODAY, value).apply()

    var lastDate: String
        get() = prefs.getString(KEY_LAST_DATE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_DATE, value).apply()

    fun resetDaily(date: String) {
        prefs.edit()
            .putBoolean(KEY_REMINDED_TODAY, false)
            .putString(KEY_LAST_DATE, date)
            .apply()
    }
}
