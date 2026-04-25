package com.smsdelete.app

data class ThreadSummary(
    val threadId: Long,
    /** A representative address from this thread (most recent sender/recipient). */
    val address: String,
    /** Contact name if resolvable, otherwise the address. */
    val displayName: String,
    val lastBody: String,
    val lastDateMs: Long,
    val count: Int
)
