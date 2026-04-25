package com.smsdelete.app

import android.content.Context
import android.provider.Telephony
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SmsHelper {

    private val PROJECTION = arrayOf(
        Telephony.Sms._ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY,
        Telephony.Sms.DATE
    )

    /** Returns all received SMS messages that arrived within [durationMs] milliseconds. */
    fun queryMessagesInWindow(context: Context, durationMs: Long): List<SmsEntry> {
        val cutoff = System.currentTimeMillis() - durationMs
        val selection = "${Telephony.Sms.DATE} >= ?"
        val selectionArgs = arrayOf(cutoff.toString())
        val sortOrder = "${Telephony.Sms.DATE} DESC"

        val results = mutableListOf<SmsEntry>()

        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            PROJECTION,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idIdx      = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addrIdx    = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx    = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx    = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (cursor.moveToNext()) {
                results += SmsEntry(
                    id      = cursor.getLong(idIdx),
                    address = cursor.getString(addrIdx) ?: "",
                    body    = cursor.getString(bodyIdx) ?: "",
                    dateMs  = cursor.getLong(dateIdx)
                )
            }
        }

        return results
    }

    /**
     * Deletes all received SMS messages within [durationMs] milliseconds.
     * The calling app MUST hold the ROLE_SMS role or this will return 0.
     */
    fun deleteMessagesInWindow(context: Context, durationMs: Long): Int {
        val cutoff = System.currentTimeMillis() - durationMs
        val selection = "${Telephony.Sms.DATE} >= ?"
        val selectionArgs = arrayOf(cutoff.toString())

        return try {
            context.contentResolver.delete(
                Telephony.Sms.Inbox.CONTENT_URI,
                selection,
                selectionArgs
            )
        } catch (e: SecurityException) {
            0
        } catch (e: Exception) {
            0
        }
    }

    fun formatDate(ms: Long): String =
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(ms))
}
