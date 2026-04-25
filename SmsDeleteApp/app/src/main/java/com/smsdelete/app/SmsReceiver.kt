package com.smsdelete.app

import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * Handles incoming SMS while the app holds ROLE_SMS. Persists the message to
 * the system inbox and posts a notification. When the app is not the default,
 * the framework routes SMS to the actual default app and this receiver is a
 * no-op.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
        if (!roleManager.isRoleHeld(RoleManager.ROLE_SMS)) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        data class Group(val bodies: StringBuilder, var firstTimestamp: Long)
        val grouped = LinkedHashMap<String, Group>()
        for (msg in messages) {
            val sender = msg.originatingAddress ?: continue
            val group = grouped.getOrPut(sender) {
                Group(StringBuilder(), msg.timestampMillis)
            }
            group.bodies.append(msg.messageBody.orEmpty())
            if (msg.timestampMillis < group.firstTimestamp) {
                group.firstTimestamp = msg.timestampMillis
            }
        }

        NotificationHelper.ensureChannel(context)

        for ((sender, group) in grouped) {
            val body = group.bodies.toString()
            val threadId = Telephony.Threads.getOrCreateThreadId(context, sender)
            try {
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, sender)
                    put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.DATE_SENT, group.firstTimestamp)
                    put(Telephony.Sms.READ, 0)
                    put(Telephony.Sms.SEEN, 0)
                    put(Telephony.Sms.THREAD_ID, threadId)
                }
                context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            } catch (_: Exception) { }

            NotificationHelper.notifyIncoming(context, sender, body, threadId)
        }
    }
}
