package com.example.callcounter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.callcounter.data.model.CallDayStats
import com.example.callcounter.data.model.PeriodStats
import com.example.callcounter.databinding.ActivityMainBinding
import com.example.callcounter.util.CallLogHelper
import com.example.callcounter.util.NotificationHelper
import com.example.callcounter.util.PrefsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsHelper
    private lateinit var callLogHelper: CallLogHelper
    private lateinit var notificationHelper: NotificationHelper

    private var viewMode = ViewMode.DAY
    private var refreshJob: Job? = null

    private enum class ViewMode {
        DAY, WEEK, MONTH
    }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.entries.all { it.value }
        if (allGranted) {
            refreshStats()
        } else {
            Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
            binding.tvStatus.setText(R.string.permission_denied)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsHelper(this)
        callLogHelper = CallLogHelper(this)
        notificationHelper = NotificationHelper(this)

        initViews()
        loadSettings()
        checkDateReset()
        if (prefs.onboardingSeen) {
            checkPermissions()
        } else {
            binding.root.post { showOnboarding(true) }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
    }

    private fun initViews() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            binding.scrollView.smoothScrollTo(0, 0)
        }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    binding.scrollView.smoothScrollTo(0, binding.cardSettings.top)
                    true
                }
                R.id.action_guide -> {
                    showOnboarding(false)
                    true
                }
                else -> false
            }
        }

        binding.viewModeGroup.check(binding.btnDayView.id)
        binding.viewModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewMode = when (checkedId) {
                binding.btnWeekView.id -> ViewMode.WEEK
                binding.btnMonthView.id -> ViewMode.MONTH
                else -> ViewMode.DAY
            }
            refreshStats()
        }

        binding.btnSave.setOnClickListener { saveSettings() }
        binding.btnRefresh.setOnClickListener { refreshStats() }
    }

    private fun loadSettings() {
        binding.etTarget.setText(prefs.targetCount.toString())
        binding.etDedup.setText(prefs.dedupMinutes.toString())
        binding.switchFilterShort.isChecked = prefs.filterShortNumber
    }

    private fun saveSettings() {
        val target = binding.etTarget.text.toString().toIntOrNull()
            ?.takeIf { it > 0 }
            ?: PrefsHelper.DEFAULT_TARGET
        val dedup = binding.etDedup.text.toString().toIntOrNull()
            ?.takeIf { it >= 0 }
            ?: PrefsHelper.DEFAULT_DEDUP_MINUTES

        prefs.targetCount = target
        prefs.dedupMinutes = dedup
        prefs.filterShortNumber = binding.switchFilterShort.isChecked

        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        refreshStats()
    }

    private fun checkPermissions() {
        val callLogMissing = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CALL_LOG
        ) != PackageManager.PERMISSION_GRANTED

        if (!callLogMissing) {
            refreshStats()
            return
        }

        binding.tvStatus.setText(R.string.permission_required)
        permissionsLauncher.launch(arrayOf(Manifest.permission.READ_CALL_LOG))
    }

    private fun showOnboarding(firstLaunch: Boolean) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.onboarding_title)
            .setMessage(R.string.onboarding_message)
            .setPositiveButton(R.string.onboarding_start) { _, _ ->
                if (firstLaunch) prefs.onboardingSeen = true
                checkPermissions()
            }
            .setNegativeButton(if (firstLaunch) R.string.onboarding_later else R.string.close) { _, _ ->
                if (firstLaunch) {
                    prefs.onboardingSeen = true
                    checkPermissions()
                }
            }
            .show()
    }

    private fun checkDateReset() {
        val today = callLogHelper.getTodayDateString()
        if (prefs.lastDate != today) {
            prefs.resetDaily(today)
        }
    }

    private fun refreshStats() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            binding.tvStatus.setText(R.string.permission_required)
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

            binding.tvTotalCount.text = stats.total.toString()
            binding.tvIncomingCount.text = stats.incoming.toString()
            binding.tvOutgoingCount.text = stats.outgoing.toString()

            val target = prefs.targetCount
            if (viewMode == ViewMode.DAY) {
                binding.tvPeriodTitle.setText(R.string.today_total)
                val progress = if (target > 0) (stats.total * 100 / target).coerceAtMost(100) else 0
                binding.progressTarget.isVisible = true
                binding.tvTargetInfo.isVisible = true
                binding.progressTarget.progress = progress
                binding.tvTargetInfo.text = getString(R.string.target_progress, stats.total, target)
            } else {
                binding.tvPeriodTitle.setText(
                    if (viewMode == ViewMode.WEEK) R.string.week_total else R.string.month_total
                )
                binding.progressTarget.isVisible = false
                binding.tvTargetInfo.isVisible = false
            }

            binding.tvStatus.text = when (viewMode) {
                ViewMode.DAY -> getString(R.string.updated_today, callLogHelper.getTodayDateString())
                ViewMode.WEEK -> getString(R.string.updated_week)
                ViewMode.MONTH -> getString(R.string.updated_month)
            }

            updatePeriodDetails(report)

            if (viewMode == ViewMode.DAY) checkTarget(stats.total)
        }
    }

    private fun updatePeriodDetails(report: PeriodStats?) {
        if (viewMode == ViewMode.DAY) {
            binding.layoutPeriodDetails.isVisible = false
            return
        }

        val dailyStats = report?.daily.orEmpty()
        binding.periodDetailsRows.removeAllViews()

        dailyStats.forEachIndexed { index, day ->
            binding.periodDetailsRows.addView(createDetailRow(day))
            if (index < dailyStats.lastIndex) {
                val divider = View(this).apply {
                    setBackgroundColor(getColor(R.color.detail_divider))
                }
                binding.periodDetailsRows.addView(
                    divider,
                    LinearLayout.LayoutParams(-1, 1)
                )
            }
        }
        binding.layoutPeriodDetails.isVisible = true
    }

    private fun createDetailRow(day: CallDayStats): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 14, 12, 14)
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
            setTextAppearance(R.style.TextAppearance_CallCounter_BodyMedium)
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
