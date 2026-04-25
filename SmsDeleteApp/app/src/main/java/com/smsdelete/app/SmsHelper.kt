package com.smsdelete.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Combined SMS + MMS access. Threads are listed via [Telephony.Threads.CONTENT_URI]
 * (one row per conversation, regardless of whether messages are SMS or MMS), and
 * per-thread queries union both providers.
 *
 * Note: MMS DATE is stored in seconds; SMS DATE is in milliseconds. We normalise to
 * milliseconds in [SmsEntry.dateMs].
 */
object SmsHelper {

    private val THREADS_URI: Uri =
        Telephony.Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build()
    private val CANONICAL_ADDRESSES: Uri =
        Uri.parse("content://mms-sms/canonical-addresses")
    private val MMS_PART: Uri = Uri.parse("content://mms/part")

    /** One row per Telephony thread (SMS + MMS), with unread counts. */
    fun queryThreads(context: Context): List<ThreadSummary> {
        val canonicalById = readCanonicalAddresses(context)
        val unreadByThread = readUnreadCounts(context)
        val resolver = ContactResolver(context)

        val results = mutableListOf<ThreadSummary>()
        context.contentResolver.query(
            THREADS_URI,
            arrayOf(
                Telephony.Threads._ID,
                Telephony.Threads.DATE,
                Telephony.Threads.MESSAGE_COUNT,
                Telephony.Threads.SNIPPET,
                Telephony.Threads.RECIPIENT_IDS
            ),
            null, null,
            "${Telephony.Threads.DATE} DESC"
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(Telephony.Threads._ID)
            val dateIdx = c.getColumnIndexOrThrow(Telephony.Threads.DATE)
            val countIdx = c.getColumnIndexOrThrow(Telephony.Threads.MESSAGE_COUNT)
            val snippetIdx = c.getColumnIndexOrThrow(Telephony.Threads.SNIPPET)
            val recipsIdx = c.getColumnIndexOrThrow(Telephony.Threads.RECIPIENT_IDS)
            while (c.moveToNext()) {
                val threadId = c.getLong(idIdx)
                val dateMs = c.getLong(dateIdx)
                val count = c.getInt(countIdx)
                val snippet = c.getString(snippetIdx)?.takeIf { it.isNotBlank() }
                    ?: "[no preview]"
                val recipientIds = c.getString(recipsIdx).orEmpty()

                val firstAddr = recipientIds
                    .split(' ', '\t', ',')
                    .firstOrNull { it.isNotBlank() }
                    ?.toLongOrNull()
                    ?.let { canonicalById[it] }
                    .orEmpty()

                val name = if (firstAddr.isNotBlank()) resolver.displayNameFor(firstAddr) else null
                results += ThreadSummary(
                    threadId = threadId,
                    address = firstAddr,
                    displayName = name ?: firstAddr.ifBlank { "(unknown)" },
                    lastBody = snippet,
                    lastDateMs = dateMs,
                    count = count,
                    unreadCount = unreadByThread[threadId] ?: 0
                )
            }
        }
        return results
    }

    /** Marks all unread SMS + MMS rows in [threadId] as read+seen. */
    fun markThreadRead(context: Context, threadId: Long) {
        try {
            val v = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
            }
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI, v,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString())
            )
        } catch (_: Exception) { }
        try {
            val v = ContentValues().apply {
                put(Telephony.Mms.READ, 1)
                put(Telephony.Mms.SEEN, 1)
            }
            context.contentResolver.update(
                Telephony.Mms.CONTENT_URI, v,
                "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.READ} = 0",
                arrayOf(threadId.toString())
            )
        } catch (_: Exception) { }
    }

    /** Deletes a single SMS (or MMS) row by id. Caller must hold ROLE_SMS. */
    fun deleteOne(context: Context, id: Long, isMms: Boolean): Boolean {
        return try {
            val uri = if (isMms) Telephony.Mms.CONTENT_URI else Telephony.Sms.CONTENT_URI
            val deleted = context.contentResolver.delete(uri, "_id = ?", arrayOf(id.toString()))
            deleted > 0
        } catch (_: Exception) { false }
    }

    /** Deletes every SMS + MMS row in [threadId]. Caller must hold ROLE_SMS. */
    fun deleteEntireThread(context: Context, threadId: Long): Int {
        var deleted = 0
        try {
            deleted += context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString())
            )
        } catch (_: Exception) { }
        try {
            deleted += context.contentResolver.delete(
                Telephony.Mms.CONTENT_URI,
                "${Telephony.Mms.THREAD_ID} = ?",
                arrayOf(threadId.toString())
            )
        } catch (_: Exception) { }
        return deleted
    }

    /** Combined SMS + MMS rows for [threadId] within [durationMs]. */
    fun queryMessagesByThread(
        context: Context,
        threadId: Long,
        durationMs: Long
    ): List<SmsEntry> {
        val cutoffMs = System.currentTimeMillis() - durationMs
        val cutoffSec = cutoffMs / 1000L
        val merged = mutableListOf<SmsEntry>()

        // ── SMS ──
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
            ),
            "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.DATE} >= ?",
            arrayOf(threadId.toString(), cutoffMs.toString()),
            null
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addrIdx = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val typeIdx = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            while (c.moveToNext()) {
                val type = c.getInt(typeIdx)
                merged += SmsEntry(
                    id      = c.getLong(idIdx),
                    address = c.getString(addrIdx) ?: "",
                    body    = c.getString(bodyIdx) ?: "",
                    dateMs  = c.getLong(dateIdx),
                    isSent  = type == Telephony.Sms.MESSAGE_TYPE_SENT ||
                              type == Telephony.Sms.MESSAGE_TYPE_OUTBOX ||
                              type == Telephony.Sms.MESSAGE_TYPE_QUEUED,
                    isMms   = false
                )
            }
        }

        // ── MMS ── (collect rows, then batch-fetch text bodies)
        data class MmsRow(val id: Long, val dateMs: Long, val isSent: Boolean)
        val mmsRows = mutableListOf<MmsRow>()
        context.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(
                Telephony.Mms._ID,
                Telephony.Mms.DATE,
                Telephony.Mms.MESSAGE_BOX
            ),
            "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.DATE} >= ?",
            arrayOf(threadId.toString(), cutoffSec.toString()),
            null
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(Telephony.Mms._ID)
            val dateIdx = c.getColumnIndexOrThrow(Telephony.Mms.DATE)
            val boxIdx = c.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX)
            while (c.moveToNext()) {
                val box = c.getInt(boxIdx)
                mmsRows += MmsRow(
                    id = c.getLong(idIdx),
                    dateMs = c.getLong(dateIdx) * 1000L,
                    isSent = box == Telephony.Mms.MESSAGE_BOX_SENT ||
                             box == Telephony.Mms.MESSAGE_BOX_OUTBOX
                )
            }
        }

        if (mmsRows.isNotEmpty()) {
            val ids = mmsRows.map { it.id }
            val bodies = readMmsTextBodies(context, ids)
            val attachments = readMmsImageAttachments(context, ids)
            for (row in mmsRows) {
                val text = bodies[row.id].orEmpty()
                val att = attachments[row.id]
                val body = when {
                    text.isNotBlank() -> text
                    att != null       -> ""
                    else              -> "[MMS]"
                }
                merged += SmsEntry(
                    id = row.id,
                    address = "",
                    body = body,
                    dateMs = row.dateMs,
                    isSent = row.isSent,
                    isMms = true,
                    attachmentUri = att?.first,
                    attachmentMime = att?.second
                )
            }
        }

        return merged.sortedByDescending { it.dateMs }
    }

    /** Same as [queryMessagesByThread] but with no time filter — returns the entire thread. */
    fun queryAllMessagesByThread(context: Context, threadId: Long): List<SmsEntry> =
        queryMessagesByThread(context, threadId, durationMs = System.currentTimeMillis())

    /**
     * Deletes SMS + MMS for [threadId] within [durationMs]. Returns total rows deleted.
     * Caller MUST hold ROLE_SMS or this returns 0.
     */
    fun deleteMessagesByThread(
        context: Context,
        threadId: Long,
        durationMs: Long
    ): Int {
        val cutoffMs = System.currentTimeMillis() - durationMs
        val cutoffSec = cutoffMs / 1000L
        var deleted = 0
        try {
            deleted += context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.DATE} >= ?",
                arrayOf(threadId.toString(), cutoffMs.toString())
            )
        } catch (_: Exception) { }
        try {
            deleted += context.contentResolver.delete(
                Telephony.Mms.CONTENT_URI,
                "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.DATE} >= ?",
                arrayOf(threadId.toString(), cutoffSec.toString())
            )
        } catch (_: Exception) { }
        return deleted
    }

    fun formatDate(ms: Long): String =
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(ms))

    /**
     * Sends [body] to [address] via [SmsManager] and writes the message to
     * [Telephony.Sms.Sent] so it appears in the conversation immediately.
     * Returns true on success, false if any step throws.
     */
    fun sendSms(context: Context, address: String, body: String): Boolean {
        if (address.isBlank() || body.isBlank()) return false
        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(body)
            if (parts != null && parts.size > 1) {
                smsManager.sendMultipartTextMessage(address, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(address, null, body, null, null)
            }

            val threadId = Telephony.Threads.getOrCreateThreadId(context, address)
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
                put(Telephony.Sms.THREAD_ID, threadId)
            }
            context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            true
        } catch (_: Exception) {
            false
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** id → canonical phone-style address (one row per recipient ID). */
    private fun readCanonicalAddresses(context: Context): Map<Long, String> {
        val map = HashMap<Long, String>()
        try {
            context.contentResolver.query(
                CANONICAL_ADDRESSES,
                arrayOf("_id", "address"),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    map[c.getLong(0)] = c.getString(1) ?: ""
                }
            }
        } catch (_: Exception) { }
        return map
    }

    /** mms _id → concatenated text of all text/plain parts. */
    private fun readMmsTextBodies(context: Context, ids: List<Long>): Map<Long, String> {
        if (ids.isEmpty()) return emptyMap()
        val placeholders = ids.joinToString(",") { "?" }
        val args = ids.map { it.toString() }.toTypedArray() + "text/plain"
        val map = HashMap<Long, StringBuilder>()
        try {
            context.contentResolver.query(
                MMS_PART,
                arrayOf("mid", "ct", "text"),
                "mid IN ($placeholders) AND ct = ?",
                args,
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val mid = c.getLong(0)
                    val text = c.getString(2) ?: continue
                    val sb = map.getOrPut(mid) { StringBuilder() }
                    if (sb.isNotEmpty()) sb.append(' ')
                    sb.append(text)
                }
            }
        } catch (_: Exception) { }
        return map.mapValues { it.value.toString() }
    }

    /** thread_id → unread count, summed over SMS + MMS. */
    private fun readUnreadCounts(context: Context): Map<Long, Int> {
        val map = HashMap<Long, Int>()
        fun tally(uri: Uri, threadCol: String, readCol: String) {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(threadCol),
                    "$readCol = 0",
                    null, null
                )?.use { c ->
                    val tIdx = c.getColumnIndexOrThrow(threadCol)
                    while (c.moveToNext()) {
                        val tid = c.getLong(tIdx)
                        map.merge(tid, 1) { a, b -> a + b }
                    }
                }
            } catch (_: Exception) { }
        }
        tally(Telephony.Sms.CONTENT_URI, Telephony.Sms.THREAD_ID, Telephony.Sms.READ)
        tally(Telephony.Mms.CONTENT_URI, Telephony.Mms.THREAD_ID, Telephony.Mms.READ)
        return map
    }

    /** mms _id → (image part Uri, content-type) for the first image part of each message. */
    private fun readMmsImageAttachments(
        context: Context,
        ids: List<Long>
    ): Map<Long, Pair<Uri, String>> {
        if (ids.isEmpty()) return emptyMap()
        val placeholders = ids.joinToString(",") { "?" }
        val args = ids.map { it.toString() }.toTypedArray()
        val map = HashMap<Long, Pair<Uri, String>>()
        try {
            context.contentResolver.query(
                MMS_PART,
                arrayOf("_id", "mid", "ct"),
                "mid IN ($placeholders) AND ct LIKE 'image/%'",
                args,
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val partId = c.getLong(0)
                    val mid = c.getLong(1)
                    if (map.containsKey(mid)) continue
                    val ct = c.getString(2) ?: "image/*"
                    map[mid] = Uri.parse("content://mms/part/$partId") to ct
                }
            }
        } catch (_: Exception) { }
        return map
    }
}
