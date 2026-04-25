package com.smsdelete.app

data class SmsEntry(
    val id: Long,
    val address: String,
    val body: String,
    val dateMs: Long,
    val isSent: Boolean,
    val isMms: Boolean = false
)
