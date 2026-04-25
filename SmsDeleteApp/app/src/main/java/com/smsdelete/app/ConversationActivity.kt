package com.smsdelete.app

import android.app.role.RoleManager
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
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
    private var threadId: Long = -1L
    private var titleLabel: String = ""
    private var address: String = ""

    /** Most recent duration index chosen in the delete dialog. */
    private var lastDeleteIndex: Int = 0

    /** Once the user confirms a delete and we have to request ROLE_SMS, remember what to delete. */
    private var pendingDeleteMs: Long = 0L

    private val defaultSmsRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (isDefaultSmsApp() && pendingDeleteMs > 0) {
            executeDelete(pendingDeleteMs)
        } else if (pendingDeleteMs > 0) {
            showToast(getString(R.string.default_app_required))
        }
        pendingDeleteMs = 0L
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
            layoutManager = LinearLayoutManager(this@ConversationActivity).apply {
                stackFromEnd = true
            }
            adapter = previewAdapter
        }

        setupReplyBar()
    }

    override fun onResume() {
        super.onResume()
        refreshMessages()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.conversation_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        R.id.action_delete_messages -> { showDeleteWindowDialog(); true }
        else -> super.onOptionsItemSelected(item)
    }

    // ── Reply bar ───────────────────────────────────────────────────────────

    private fun setupReplyBar() {
        binding.etReply.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateSendEnabled() }
        })
        updateSendEnabled()
        binding.btnSend.setOnClickListener { sendReply() }
    }

    private fun updateSendEnabled() {
        binding.btnSend.isEnabled =
            address.isNotBlank() && binding.etReply.text?.toString()?.isNotBlank() == true
    }

    private fun sendReply() {
        val body = binding.etReply.text?.toString().orEmpty()
        if (body.isBlank() || address.isBlank()) return
        binding.btnSend.isEnabled = false
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                SmsHelper.sendSms(this@ConversationActivity, address, body)
            }
            if (ok) {
                binding.etReply.text?.clear()
                refreshMessages(scrollToBottom = true)
            } else {
                showToast(getString(R.string.send_failed))
            }
            updateSendEnabled()
        }
    }

    // ── Messages list ───────────────────────────────────────────────────────

    private fun refreshMessages(scrollToBottom: Boolean = true) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val messages = withContext(Dispatchers.IO) {
                SmsHelper.queryAllMessagesByThread(this@ConversationActivity, threadId)
                    .sortedBy { it.dateMs }  // oldest first → newest at bottom
            }
            binding.progressBar.visibility = View.GONE
            updateUI(messages)
            if (scrollToBottom && messages.isNotEmpty()) {
                binding.rvMessages.post {
                    binding.rvMessages.scrollToPosition(messages.size - 1)
                }
            }
        }
    }

    private fun updateUI(messages: List<SmsEntry>) {
        if (messages.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvMessages.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.rvMessages.visibility = View.VISIBLE
            previewAdapter.submitList(messages)
        }
    }

    // ── Delete flow ─────────────────────────────────────────────────────────

    private fun showDeleteWindowDialog() {
        val labels = DURATIONS.map { getString(it.labelRes) }.toTypedArray()
        var picked = lastDeleteIndex
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_window_title)
            .setSingleChoiceItems(labels, picked) { _, which -> picked = which }
            .setPositiveButton(R.string.delete) { _, _ ->
                lastDeleteIndex = picked
                confirmDelete(DURATIONS[picked])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(option: DurationOption) {
        val target = titleLabel.ifBlank { address }
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_title)
            .setMessage(getString(R.string.dialog_message_for_address, target, getString(option.labelRes)))
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(R.string.delete) { _, _ ->
                if (isDefaultSmsApp()) {
                    executeDelete(option.ms)
                } else {
                    pendingDeleteMs = option.ms
                    requestDefaultSmsRole()
                }
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
            .setNegativeButton(R.string.cancel) { _, _ -> pendingDeleteMs = 0L }
            .show()
    }

    private fun executeDelete(durationMs: Long) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                SmsHelper.deleteMessagesByThread(
                    this@ConversationActivity, threadId, durationMs
                )
            }
            binding.progressBar.visibility = View.GONE
            showToast(
                resources.getQuantityString(R.plurals.messages_deleted, deleted, deleted)
            )
            refreshMessages(scrollToBottom = false)
        }
    }

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    data class DurationOption(val labelRes: Int, val ms: Long)

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
