package com.smsdelete.app

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smsdelete.app.databinding.ActivityComposeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ComposeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityComposeBinding

    private val pickPhone = registerForActivityResult(PickPhoneNumber) { uri ->
        if (uri != null) onPhonePicked(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComposeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.compose_title)

        binding.btnPickContact.setOnClickListener { pickPhone.launch(Unit) }
        binding.btnSend.setOnClickListener { onSendClicked() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish(); return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun onPhonePicked(uri: Uri) {
        val number = contentResolver.query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        if (!number.isNullOrBlank()) {
            binding.etRecipient.setText(number)
            binding.etRecipient.setSelection(binding.etRecipient.text?.length ?: 0)
        }
    }

    private fun onSendClicked() {
        val recipient = binding.etRecipient.text?.toString()?.trim().orEmpty()
        val body = binding.etBody.text?.toString().orEmpty()

        if (recipient.isEmpty()) {
            binding.recipientLayout.error = getString(R.string.recipient_required)
            return
        }
        binding.recipientLayout.error = null

        binding.btnSend.isEnabled = false
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                trySend(recipient, body)
            }
            if (ok) {
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                binding.btnSend.isEnabled = true
                Toast.makeText(
                    this@ComposeActivity,
                    getString(R.string.send_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun trySend(address: String, body: String): Boolean {
        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(body)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(address, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(address, null, body, null, null)
            }
            insertIntoSentBox(this, address, body)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun insertIntoSentBox(context: Context, address: String, body: String) {
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
    }

    private object PickPhoneNumber : ActivityResultContract<Unit, Uri?>() {
        override fun createIntent(context: Context, input: Unit): Intent =
            Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            if (resultCode == Activity.RESULT_OK) intent?.data else null
    }
}
