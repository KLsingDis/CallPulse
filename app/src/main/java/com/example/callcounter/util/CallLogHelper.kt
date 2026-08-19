package com.example.callcounter.util

import android.Manifest
import android.content.Context
import android.database.Cursor
import android.provider.CallLog
import androidx.annotation.RequiresPermission
import com.example.callcounter.data.model.CallLogItem
import com.example.callcounter.data.model.CallDayStats
import com.example.callcounter.data.model.CallStats
import com.example.callcounter.data.model.PeriodStats
import java.text.SimpleDateFormat
import java.util.*

class CallLogHelper(private val context: Context) {

    private val prefs = PrefsHelper(context)

    @RequiresPermission(Manifest.permission.READ_CALL_LOG)
    fun getTodayStats(): CallStats {
        return getStatsForRange(getTodayStartMillis())
    }

    @RequiresPermission(Manifest.permission.READ_CALL_LOG)
    fun getWeekStats(): CallStats {
        return getWeekReport().summary
    }

    @RequiresPermission(Manifest.permission.READ_CALL_LOG)
    fun getMonthStats(): CallStats {
        return getMonthReport().summary
    }

    @RequiresPermission(Manifest.permission.READ_CALL_LOG)
    fun getWeekDailyStats(): List<CallDayStats> = getWeekReport().daily

    @RequiresPermission(Manifest.permission.READ_CALL_LOG)
    fun getMonthDailyStats(): List<CallDayStats> {
        return getMonthReport().daily
    }

    @RequiresPermission(Manifest.permission.READ_CALL_LOG)
    fun getWeekReport(): PeriodStats = getPeriodReport(getWeekStartMillis())

    @RequiresPermission(Manifest.permission.READ_CALL_LOG)
    fun getMonthReport(): PeriodStats {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        resetTime(calendar)
        return getPeriodReport(calendar.timeInMillis)
    }

    private fun getPeriodReport(startMillis: Long): PeriodStats {
        val deduped = applyDedup(applyFilter(queryCallLog(startMillis)))
        val summary = CallStats(
            incoming = deduped.count { it.type == CallLog.Calls.INCOMING_TYPE },
            outgoing = deduped.count { it.type == CallLog.Calls.OUTGOING_TYPE }
        )
        val grouped = deduped
            .groupBy { dayFormat.format(Date(it.date)) }
        val result = mutableListOf<CallDayStats>()
        val day = Calendar.getInstance().apply {
            timeInMillis = startMillis
            resetTime(this)
        }
        val today = Calendar.getInstance().apply { resetTime(this) }
        while (!day.after(today)) {
            val key = dayFormat.format(day.time)
            val items = grouped[key].orEmpty()
            result += CallDayStats(
                date = displayDayFormat.format(day.time),
                incoming = items.count { it.type == CallLog.Calls.INCOMING_TYPE },
                outgoing = items.count { it.type == CallLog.Calls.OUTGOING_TYPE }
            )
            day.add(Calendar.DAY_OF_MONTH, 1)
        }
        return PeriodStats(summary, result)
    }

    private fun getStatsForRange(startMillis: Long): CallStats {
        val items = queryCallLog(startMillis)
        val filtered = applyFilter(items)
        val deduped = applyDedup(filtered)

        var incoming = 0
        var outgoing = 0
        deduped.forEach {
            when (it.type) {
                CallLog.Calls.INCOMING_TYPE -> incoming++
                CallLog.Calls.OUTGOING_TYPE -> outgoing++
            }
        }
        return CallStats(incoming, outgoing)
    }

    @RequiresPermission(Manifest.permission.READ_CALL_LOG)
    private fun queryCallLog(startMillis: Long): List<CallLogItem> {
        val result = mutableListOf<CallLogItem>()
        val selection = "${CallLog.Calls.DATE} >= ?"
        val selectionArgs = arrayOf(startMillis.toString())

        val cursor: Cursor? = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            ),
            selection,
            selectionArgs,
            "${CallLog.Calls.DATE} ASC"
        )

        cursor?.use {
            val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
            val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
            val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
            val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)

            while (it.moveToNext()) {
                val number = if (numberIndex >= 0) it.getString(numberIndex) ?: "" else ""
                val type = if (typeIndex >= 0) it.getInt(typeIndex) else 0
                val date = if (dateIndex >= 0) it.getLong(dateIndex) else 0L
                val duration = if (durationIndex >= 0) it.getLong(durationIndex) else 0L
                result.add(CallLogItem(number, type, date, duration))
            }
        }
        return result
    }

    private fun applyFilter(items: List<CallLogItem>): List<CallLogItem> {
        return items.filter { item ->
            if (item.type != CallLog.Calls.INCOMING_TYPE && item.type != CallLog.Calls.OUTGOING_TYPE) {
                return@filter false
            }
            if (prefs.filterShortNumber) {
                val digits = item.number.replace(Regex("[^0-9]"), "")
                if (digits.length < 7) return@filter false
            }
            true
        }
    }

    private fun applyDedup(items: List<CallLogItem>): List<CallLogItem> {
        val windowMillis = prefs.dedupMinutes.coerceAtLeast(0) * 60 * 1000L
        val seen = mutableMapOf<String, Long>()
        return items.filter { item ->
            val key = "${item.type}_${item.number}"
            val last = seen[key]
            if (last == null || item.date - last > windowMillis) {
                seen[key] = item.date
                true
            } else {
                false
            }
        }
    }

    private fun getTodayStartMillis(): Long {
        val cal = Calendar.getInstance()
        resetTime(cal)
        return cal.timeInMillis
    }

    private fun getWeekStartMillis(): Long {
        val calendar = Calendar.getInstance()
        val daysSinceMonday = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
        calendar.add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
        resetTime(calendar)
        return calendar.timeInMillis
    }

    private fun resetTime(cal: Calendar) {
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
    }

    fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDayFormat = SimpleDateFormat("MM-dd  E", Locale.getDefault())
}
