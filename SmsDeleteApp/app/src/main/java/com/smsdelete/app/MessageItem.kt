package com.smsdelete.app

sealed class MessageItem {
    data class DayHeader(val label: String) : MessageItem() {
        val key: String get() = "day:$label"
    }
    data class Message(val entry: SmsEntry) : MessageItem() {
        val key: String get() = "msg:${entry.id}:${entry.isMms}"
    }
}
