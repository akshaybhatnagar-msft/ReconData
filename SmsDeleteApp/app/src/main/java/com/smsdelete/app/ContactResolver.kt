package com.smsdelete.app

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

/**
 * Resolves SMS addresses to contact display names via [ContactsContract.PhoneLookup].
 * Cached per-process; instances are cheap to discard.
 */
class ContactResolver(private val context: Context) {

    private val cache = HashMap<String, String?>()

    fun displayNameFor(address: String): String? {
        if (address.isBlank()) return null
        cache[address]?.let { return it }
        if (cache.containsKey(address)) return null  // cached negative

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(address)
        )
        val name = try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
        cache[address] = name
        return name
    }
}
