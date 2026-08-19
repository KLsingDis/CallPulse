package com.example.callcounter.data.model

data class CallLogItem(
    val number: String,
    val type: Int,
    val date: Long,
    val duration: Long
)
