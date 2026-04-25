package com.smsdelete.app

data class ThreadSummary(
    val address: String,
    val lastBody: String,
    val lastDateMs: Long,
    val count: Int
)
