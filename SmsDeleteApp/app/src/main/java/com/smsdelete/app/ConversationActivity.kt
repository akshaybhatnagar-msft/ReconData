package com.smsdelete.app

import android.app.role.RoleManager
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.smsdelete.app.databinding.ActivityConversationBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConversationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversationBinding
    private lateinit var previewAdapter: SmsPreviewAdapter
    private lateinit var address: String
    private var selectedDurationMs: Long = ONE_HOUR_MS

    private val defaultSmsRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (isDefaultSmsApp()) executeDelete()
        else showToast(getString(R.string.default_app_required))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConversationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        address = intent.getStringExtra(EXTRA_ADDRESS).orEmpty().also {
            if (it.isBlank()) { finish(); return }
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = address

        previewAdapter = SmsPreviewAdapter()
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@ConversationActivity)
            adapter = previewAdapter
        }

        binding.durationToggleGroup.check(R.id.btn1hour)
        binding.durationToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            selectedDurationMs = when (checkedId) {
                R.id.btn30min  -> 30 * 60 * 1000L
                R.id.btn1hour  -> ONE_HOUR_MS
                R.id.btn2hours -> 2 * ONE_HOUR_MS
                R.id.btn2days  -> 48 * ONE_HOUR_MS
                else           -> ONE_HOUR_MS
            }
            refreshPreview()
        }

        binding.btnDelete.setOnClickListener { confirmAndDelete() }
    }

    override fun onResume() {
        super.onResume()
        refreshPreview()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish(); return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun refreshPreview() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnDelete.isEnabled = false
        lifecycleScope.launch {
            val messages = withContext(Dispatchers.IO) {
                SmsHelper.queryMessagesForAddress(this@ConversationActivity, address, selectedDurationMs)
            }
            binding.progressBar.visibility = View.GONE
            updateUI(messages)
        }
    }

    private fun updateUI(messages: List<SmsEntry>) {
        val count = messages.size
        if (count == 0) {
            binding.tvMessageCount.text = getString(R.string.no_messages_in_window)
            binding.rvMessages.visibility = View.GONE
        } else {
            binding.tvMessageCount.text = resources.getQuantityString(
                R.plurals.messages_found, count, count
            )
            binding.rvMessages.visibility = View.VISIBLE
            previewAdapter.submitList(messages)
        }
        binding.btnDelete.isEnabled = count > 0
    }

    private fun confirmAndDelete() {
        val label = durationLabel(selectedDurationMs)
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_title)
            .setMessage(getString(R.string.dialog_message_for_address, address, label))
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(R.string.delete) { _, _ ->
                if (isDefaultSmsApp()) executeDelete() else requestDefaultSmsRole()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun isDefaultSmsApp(): Boolean {
        val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        return roleManager.isRoleHeld(RoleManager.ROLE_SMS)
    }

    private fun requestDefaultSmsRole() {
        AlertDialog.Builder(this)
            .setTitle(R.string.default_app_title)
            .setMessage(R.string.default_app_message)
            .setPositiveButton(R.string.continue_btn) { _, _ ->
                val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                defaultSmsRoleLauncher.launch(intent)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun executeDelete() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnDelete.isEnabled = false
        lifecycleScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                SmsHelper.deleteMessagesForAddress(
                    this@ConversationActivity, address, selectedDurationMs
                )
            }
            binding.progressBar.visibility = View.GONE
            showToast(
                resources.getQuantityString(R.plurals.messages_deleted, deleted, deleted)
            )
            refreshPreview()
        }
    }

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun durationLabel(ms: Long) = when (ms) {
        30 * 60 * 1000L -> getString(R.string.duration_30min)
        ONE_HOUR_MS     -> getString(R.string.duration_1hour)
        2 * ONE_HOUR_MS -> getString(R.string.duration_2hours)
        else            -> getString(R.string.duration_2days)
    }

    companion object {
        const val EXTRA_ADDRESS = "extra_address"
        private const val ONE_HOUR_MS = 60 * 60 * 1000L
    }
}
