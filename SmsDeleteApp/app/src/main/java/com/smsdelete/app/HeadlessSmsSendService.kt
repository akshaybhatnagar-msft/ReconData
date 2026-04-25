package com.smsdelete.app

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Required stub for ROLE_SMS (respond-via-message). */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent): IBinder? = null
}
