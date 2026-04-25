package com.smsdelete.app

import android.net.Uri

data class SmsEntry(
    val id: Long,
    val address: String,
    val body: String,
    val dateMs: Long,
    val isSent: Boolean,
    val isMms: Boolean = false,
    val attachmentUri: Uri? = null,
    val attachmentMime: String? = null
)
