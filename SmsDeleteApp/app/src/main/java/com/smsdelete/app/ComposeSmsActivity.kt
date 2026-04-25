package com.smsdelete.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Required stub for ROLE_SMS (compose/send intents). Immediately finishes. */
class ComposeSmsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
