package com.example.callcounter.data.model

data class CallStats(
    val incoming: Int = 0,
    val outgoing: Int = 0
) {
    val total: Int get() = incoming + outgoing
}
