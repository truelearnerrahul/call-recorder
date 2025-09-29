package org.fossify.phone.models

data class CallStats(
    val callType: Int,
    val callTypeLabel: String,
    val callCount: Int,
    val totalDuration: Int, // in seconds
    val color: Int
)
