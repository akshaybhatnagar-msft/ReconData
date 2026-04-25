package com.smsdelete.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Required by Android to hold the ROLE_SMS role.
 * We don't process incoming messages — the default app handles that once
 * the user reverts after deletion.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
