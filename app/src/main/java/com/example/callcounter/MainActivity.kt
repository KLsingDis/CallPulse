package com.example.callcounter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.view.View
import android.view.Gravity
import androidx.lifecycle.lifecycleScope
import com.example.callcounter.data.model.PeriodStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.callcounter.util.CallLogHelper
import com.example.callcounter.util.NotificationHelper
import com.example.callcounter.util.PrefsHelper
import com.example.callcounter.data.model.CallDayStats
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var tvTotalCount: TextView
    private lateinit var tvPeriodTitle: TextView
    private lateinit var tvIncomingCount: TextView
    private lateinit var tvOutgoingCount: TextView
    private lateinit var progressTarget: ProgressBar
    private lateinit var tvTargetInfo: TextView
    private lateinit var etTarget: EditText
    private lateinit var etDedup: EditText
    private lateinit var switchFilterShort: MaterialSwitch
    private lateinit var btnSave: Button
    private lateinit var btnRefresh: Button
    private lateinit var tvStatus: TextView
    private lateinit var viewModeGroup: com.google.android.material.button.MaterialButtonToggleGroup
    private lateinit var periodDetails: LinearLayout
    private lateinit var periodDetailsRows: LinearLayout

    private var viewMode = ViewMode.DAY
    private var refreshJob: Job? = null

    private enum class ViewMode {
        DAY, WEEK, MONTH
    }

    private lateinit var prefs: PrefsHelper
    private lateinit var callLogHelper: CallLogHelper
    private lateinit var notificationHelper: NotificationHelper

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.entries.all { it.value }
        if (allGranted) {
            refreshStats()
        } else {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
            tvStatus.text = "权限未授予"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PrefsHelper(this)
        callLogHelper = CallLogHelper(this)
        notificationHelper = NotificationHelper(this)

        initViews()
        loadSettings()
        checkDateReset()
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
    }

    private fun initViews() {
        tvTotalCount = findViewById(R.id.tvTotalCount)
        tvPeriodTitle = findViewById(R.id.tvPeriodTitle)
        tvIncomingCount = findViewById(R.id.tvIncomingCount)
        tvOutgoingCount = findViewById(R.id.tvOutgoingCount)
        progressTarget = findViewById(R.id.progressTarget)
        tvTargetInfo = findViewById(R.id.tvTargetInfo)
        etTarget = findViewById(R.id.etTarget)
        etDedup = findViewById(R.id.etDedup)
        switchFilterShort = findViewById(R.id.switchFilterShort)
        btnSave = findViewById(R.id.btnSave)
        btnRefresh = findViewById(R.id.btnRefresh)
        tvStatus = findViewById(R.id.tvStatus)
        viewModeGroup = findViewById(R.id.viewModeGroup)
        periodDetails = findViewById(R.id.layoutPeriodDetails)
        periodDetailsRows = findViewById(R.id.periodDetailsRows)

        viewModeGroup.check(R.id.btnDayView)
        viewModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewMode = when (checkedId) {
                R.id.btnWeekView -> ViewMode.WEEK
                R.id.btnMonthView -> ViewMode.MONTH
                else -> ViewMode.DAY
            }
            refreshStats()
        }

        btnSave.setOnClickListener { saveSettings() }
        btnRefresh.setOnClickListener { refreshStats() }
    }

    private fun loadSettings() {
        etTarget.setText(prefs.targetCount.toString())
        etDedup.setText(prefs.dedupMinutes.toString())
        switchFilterShort.isChecked = prefs.filterShortNumber
    }

    private fun saveSettings() {
        val target = etTarget.text.toString().toIntOrNull()
            ?.takeIf { it > 0 }
            ?: PrefsHelper.DEFAULT_TARGET
        val dedup = etDedup.text.toString().toIntOrNull()
            ?.takeIf { it >= 0 }
            ?: PrefsHelper.DEFAULT_DEDUP_MINUTES

        prefs.targetCount = target
        prefs.dedupMinutes = dedup
        prefs.filterShortNumber = switchFilterShort.isChecked

        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        refreshStats()
    }

    private fun checkPermissions() {
        val callLogMissing = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED
        if (!callLogMissing) {
            refreshStats()
            return
        }

        tvStatus.text = getString(R.string.permission_required)
        permissionsLauncher.launch(arrayOf(Manifest.permission.READ_CALL_LOG))
    }

    private fun checkDateReset() {
        val today = callLogHelper.getTodayDateString()
        if (prefs.lastDate != today) {
            prefs.resetDaily(today)
        }
    }

    private fun refreshStats() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED) {
            tvStatus.text = getString(R.string.permission_required)
            return
        }

        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) {
                when (viewMode) {
                    ViewMode.DAY -> null
                    ViewMode.WEEK -> callLogHelper.getWeekReport()
                    ViewMode.MONTH -> callLogHelper.getMonthReport()
                }
            }
            val stats = report?.summary ?: withContext(Dispatchers.IO) {
                callLogHelper.getTodayStats()
            }
        tvTotalCount.text = stats.total.toString()
        tvIncomingCount.text = stats.incoming.toString()
        tvOutgoingCount.text = stats.outgoing.toString()

        val target = prefs.targetCount
        if (viewMode == ViewMode.DAY) {
            tvPeriodTitle.setText(R.string.today_total)
            val progress = if (target > 0) (stats.total * 100 / target).coerceAtMost(100) else 0
            progressTarget.visibility = View.VISIBLE
            tvTargetInfo.visibility = View.VISIBLE
            progressTarget.progress = progress
            tvTargetInfo.text = "目标: ${stats.total} / $target"
        } else {
            tvPeriodTitle.setText(if (viewMode == ViewMode.WEEK) R.string.week_total else R.string.month_total)
            progressTarget.visibility = View.GONE
            tvTargetInfo.visibility = View.GONE
        }
        tvStatus.text = when (viewMode) {
            ViewMode.DAY -> "已更新至 ${callLogHelper.getTodayDateString()}"
            ViewMode.WEEK -> "已更新本周统计（周一至今天）"
            ViewMode.MONTH -> "已更新本月统计（1日至今天）"
        }

            updatePeriodDetails(report)

            if (viewMode == ViewMode.DAY) checkTarget(stats.total)
        }
    }

    private fun updatePeriodDetails(report: PeriodStats?) {
        if (viewMode == ViewMode.DAY) {
            periodDetails.visibility = View.GONE
            return
        }

        val dailyStats = report?.daily.orEmpty()

        periodDetailsRows.removeAllViews()
        dailyStats.forEachIndexed { index, day ->
            periodDetailsRows.addView(createDetailRow(day))
            if (index < dailyStats.lastIndex) {
                val divider = View(this).apply {
                    setBackgroundColor(getColor(R.color.detail_divider))
                }
                periodDetailsRows.addView(divider, LinearLayout.LayoutParams(-1, 1))
            }
        }
        periodDetails.visibility = View.VISIBLE
    }

    private fun createDetailRow(day: CallDayStats): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(10, 12, 10, 12)
        }
        addCell(row, day.date, Gravity.START)
        addCell(row, day.total.toString(), Gravity.CENTER)
        addCell(row, day.incoming.toString(), Gravity.CENTER)
        addCell(row, day.outgoing.toString(), Gravity.CENTER)
        return row
    }

    private fun addCell(row: LinearLayout, value: String, gravity: Int) {
        val cell = TextView(this).apply {
            text = value
            this.gravity = gravity
        }
        row.addView(cell, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun checkTarget(total: Int) {
        if (total >= prefs.targetCount && !prefs.remindedToday) {
            notificationHelper.sendTargetReachedNotification(total)
            prefs.remindedToday = true
        }
    }
}
