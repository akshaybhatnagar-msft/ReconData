package com.smsdelete.app

data class ThreadSummary(
    val threadId: Long,
    val address: String,
    val displayName: String,
    val lastBody: String,
    val lastDateMs: Long,
    val count: Int,
    val unreadCount: Int = 0
)
