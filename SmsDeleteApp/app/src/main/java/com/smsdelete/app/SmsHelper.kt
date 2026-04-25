package com.smsdelete.app

import android.content.Context
import android.provider.Telephony
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SmsHelper {

    private val MESSAGE_PROJECTION = arrayOf(
        Telephony.Sms._ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY,
        Telephony.Sms.DATE,
        Telephony.Sms.TYPE
    )

    /**
     * One row per Android thread (Telephony.Sms.THREAD_ID), so messages stored under
     * different address formats for the same contact merge — matching what the system
     * Messages app shows.
     */
    fun queryThreads(context: Context): List<ThreadSummary> {
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            ),
            null, null,
            "${Telephony.Sms.DATE} DESC"
        ) ?: return emptyList()

        val byThread = LinkedHashMap<Long, MutableThread>()
        cursor.use {
            val tIdx = it.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val aIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (it.moveToNext()) {
                val threadId = it.getLong(tIdx)
                val address = it.getString(aIdx)?.takeIf { s -> s.isNotBlank() } ?: ""
                val existing = byThread[threadId]
                if (existing == null) {
                    byThread[threadId] = MutableThread(
                        lastAddress = address,
                        lastBody = it.getString(bIdx) ?: "",
                        lastDateMs = it.getLong(dIdx),
                        count = 1
                    )
                } else {
                    if (existing.lastAddress.isBlank() && address.isNotBlank()) {
                        existing.lastAddress = address
                    }
                    existing.count += 1
                }
            }
        }

        val resolver = ContactResolver(context)
        return byThread.map { (threadId, t) ->
            val name = resolver.displayNameFor(t.lastAddress)
            ThreadSummary(
                threadId = threadId,
                address = t.lastAddress,
                displayName = name ?: t.lastAddress.ifBlank { "(unknown)" },
                lastBody = t.lastBody,
                lastDateMs = t.lastDateMs,
                count = t.count
            )
        }
    }

    /** All messages on [threadId] within the past [durationMs]. */
    fun queryMessagesByThread(
        context: Context,
        threadId: Long,
        durationMs: Long
    ): List<SmsEntry> {
        val cutoff = System.currentTimeMillis() - durationMs
        val selection =
            "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.DATE} >= ?"
        val args = arrayOf(threadId.toString(), cutoff.toString())

        val results = mutableListOf<SmsEntry>()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            MESSAGE_PROJECTION,
            selection,
            args,
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val idIdx   = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addrIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val typeIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            while (cursor.moveToNext()) {
                results += SmsEntry(
                    id      = cursor.getLong(idIdx),
                    address = cursor.getString(addrIdx) ?: "",
                    body    = cursor.getString(bodyIdx) ?: "",
                    dateMs  = cursor.getLong(dateIdx),
                    type    = cursor.getInt(typeIdx)
                )
            }
        }
        return results
    }

    /**
     * Deletes all messages on [threadId] within the past [durationMs].
     * Caller MUST hold ROLE_SMS or this returns 0.
     */
    fun deleteMessagesByThread(
        context: Context,
        threadId: Long,
        durationMs: Long
    ): Int {
        val cutoff = System.currentTimeMillis() - durationMs
        val selection =
            "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.DATE} >= ?"
        val args = arrayOf(threadId.toString(), cutoff.toString())
        return try {
            context.contentResolver.delete(Telephony.Sms.CONTENT_URI, selection, args)
        } catch (e: SecurityException) {
            0
        } catch (e: Exception) {
            0
        }
    }

    fun formatDate(ms: Long): String =
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(ms))

    private data class MutableThread(
        var lastAddress: String,
        var lastBody: String,
        var lastDateMs: Long,
        var count: Int
    )
}
