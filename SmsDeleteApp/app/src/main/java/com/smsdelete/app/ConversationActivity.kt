package com.smsdelete.app

import android.app.role.RoleManager
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
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
    private var threadId: Long = -1L
    private var titleLabel: String = ""
    private var address: String = ""
    private var selectedIndex: Int = 0  // index into DURATIONS

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

        threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
        titleLabel = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        address = intent.getStringExtra(EXTRA_ADDRESS).orEmpty()
        if (threadId < 0) { finish(); return }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = titleLabel.ifBlank { address }
        if (titleLabel.isNotBlank() && titleLabel != address && address.isNotBlank()) {
            supportActionBar?.subtitle = address
        }

        previewAdapter = SmsPreviewAdapter()
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@ConversationActivity)
            adapter = previewAdapter
        }

        setupDurationDropdown()
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

    private fun setupDurationDropdown() {
        val labels = DURATIONS.map { getString(it.labelRes) }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        binding.durationDropdown.setAdapter(adapter)
        // Default selection
        binding.durationDropdown.setText(labels[selectedIndex], false)
        binding.durationDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedIndex = position
            refreshPreview()
        }
    }

    private fun selectedDurationMs(): Long = DURATIONS[selectedIndex].ms
    private fun selectedDurationLabel(): String = getString(DURATIONS[selectedIndex].labelRes)

    private fun refreshPreview() {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnDelete.isEnabled = false
        lifecycleScope.launch {
            val messages = withContext(Dispatchers.IO) {
                SmsHelper.queryMessagesByThread(this@ConversationActivity, threadId, selectedDurationMs())
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
        val target = titleLabel.ifBlank { address }
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_title)
            .setMessage(getString(R.string.dialog_message_for_address, target, selectedDurationLabel()))
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
                SmsHelper.deleteMessagesByThread(
                    this@ConversationActivity, threadId, selectedDurationMs()
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

    private data class DurationOption(val labelRes: Int, val ms: Long)

    companion object {
        const val EXTRA_THREAD_ID = "extra_thread_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ADDRESS = "extra_address"

        private const val H = 60L * 60L * 1000L
        private const val D = 24L * H

        private val DURATIONS = listOf(
            DurationOption(R.string.duration_1hour,   1L * H),
            DurationOption(R.string.duration_2hours,  2L * H),
            DurationOption(R.string.duration_4hours,  4L * H),
            DurationOption(R.string.duration_6hours,  6L * H),
            DurationOption(R.string.duration_12hours, 12L * H),
            DurationOption(R.string.duration_1day,    1L * D),
            DurationOption(R.string.duration_2days,   2L * D),
            DurationOption(R.string.duration_3days,   3L * D),
            DurationOption(R.string.duration_5days,   5L * D),
            DurationOption(R.string.duration_1week,   7L * D),
            DurationOption(R.string.duration_2weeks,  14L * D),
            DurationOption(R.string.duration_1month,  30L * D),
        )
    }
}
