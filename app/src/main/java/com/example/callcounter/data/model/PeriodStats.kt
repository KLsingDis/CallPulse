package com.example.callcounter.data.model

data class PeriodStats(
    val summary: CallStats,
    val daily: List<CallDayStats>
)
