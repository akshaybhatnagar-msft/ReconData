package com.smsdelete.app

import android.provider.Telephony

data class SmsEntry(
    val id: Long,
    val address: String,
    val body: String,
    val dateMs: Long,
    val type: Int
) {
    val isSent: Boolean
        get() = type == Telephony.Sms.MESSAGE_TYPE_SENT ||
                type == Telephony.Sms.MESSAGE_TYPE_OUTBOX ||
                type == Telephony.Sms.MESSAGE_TYPE_QUEUED
}
