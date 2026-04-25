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

    /** One row per unique address — newest body, last activity, and total message count. */
    fun queryThreads(context: Context): List<ThreadSummary> {
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            null, null,
            "${Telephony.Sms.DATE} DESC"
        ) ?: return emptyList()

        // We rely on DATE DESC ordering: the first row we see for an address is the most recent.
        val byAddress = LinkedHashMap<String, MutableThread>()
        cursor.use {
            val addrIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (it.moveToNext()) {
                val address = it.getString(addrIdx)?.takeIf { s -> s.isNotBlank() } ?: continue
                val existing = byAddress[address]
                if (existing == null) {
                    byAddress[address] = MutableThread(
                        lastBody = it.getString(bodyIdx) ?: "",
                        lastDateMs = it.getLong(dateIdx),
                        count = 1
                    )
                } else {
                    existing.count += 1
                }
            }
        }

        return byAddress.map { (addr, t) ->
            ThreadSummary(
                address = addr,
                lastBody = t.lastBody,
                lastDateMs = t.lastDateMs,
                count = t.count
            )
        }
    }

    /** All messages (sent + received) for [address] within the past [durationMs]. */
    fun queryMessagesForAddress(
        context: Context,
        address: String,
        durationMs: Long
    ): List<SmsEntry> {
        val cutoff = System.currentTimeMillis() - durationMs
        val selection =
            "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.DATE} >= ?"
        val args = arrayOf(address, cutoff.toString())

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
     * Deletes all messages for [address] within the past [durationMs].
     * Caller MUST hold ROLE_SMS or this returns 0.
     */
    fun deleteMessagesForAddress(
        context: Context,
        address: String,
        durationMs: Long
    ): Int {
        val cutoff = System.currentTimeMillis() - durationMs
        val selection =
            "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.DATE} >= ?"
        val args = arrayOf(address, cutoff.toString())
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
        var lastBody: String,
        var lastDateMs: Long,
        var count: Int
    )
}
