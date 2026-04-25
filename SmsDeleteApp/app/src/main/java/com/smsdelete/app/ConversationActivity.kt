package com.smsdelete.app

import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ConversationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversationBinding
    private lateinit var previewAdapter: SmsPreviewAdapter
    private var threadId: Long = -1L
    private var titleLabel: String = ""
    private var address: String = ""

    private var lastDeleteIndex: Int = 0
    private var pendingDeleteMs: Long = 0L

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable {
        // Smart scroll: only auto-scroll if we were already at the bottom.
        val lm = binding.rvMessages.layoutManager as? LinearLayoutManager
        val total = previewAdapter.itemCount
        val nearBottom = lm == null || total == 0 ||
            lm.findLastVisibleItemPosition() >= total - 2
        // Mark new arrivals as read since the user has the thread open.
        lifecycleScope.launch(Dispatchers.IO) {
            SmsHelper.markThreadRead(this@ConversationActivity, threadId)
        }
        refreshMessages(scrollToBottom = nearBottom)
    }

    private val smsObserver = object : ContentObserver(refreshHandler) {
        override fun onChange(selfChange: Boolean) {
            // Coalesce rapid bursts of updates into a single refresh.
            refreshHandler.removeCallbacks(refreshRunnable)
            refreshHandler.postDelayed(refreshRunnable, 250)
        }
    }

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

    private val defaultSmsForSingleDelete = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (isDefaultSmsApp()) pendingDeleteEntry?.let { executeDeleteOne(it) }
        else showToast(getString(R.string.default_app_required))
        pendingDeleteEntry = null
    }
    private var pendingDeleteEntry: SmsEntry? = null

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

        previewAdapter = SmsPreviewAdapter(
            onMessageLongClick = ::onMessageLongClick,
            onAttachmentClick = ::openImageViewer
        )
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
        registerObservers()
        // Mark thread as read off the UI thread (single UPDATE, fast).
        lifecycleScope.launch(Dispatchers.IO) {
            SmsHelper.markThreadRead(this@ConversationActivity, threadId)
        }
        refreshMessages()
    }

    override fun onPause() {
        super.onPause()
        contentResolver.unregisterContentObserver(smsObserver)
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    private fun registerObservers() {
        contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, smsObserver)
        contentResolver.registerContentObserver(Telephony.Mms.CONTENT_URI, true, smsObserver)
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
        lifecycleScope.launch {
            val messages = withContext(Dispatchers.IO) {
                SmsHelper.queryAllMessagesByThread(this@ConversationActivity, threadId)
                    .sortedBy { it.dateMs }
            }
            val items = withDayHeaders(messages)
            updateUI(items)
            if (scrollToBottom && items.isNotEmpty()) {
                binding.rvMessages.post {
                    binding.rvMessages.scrollToPosition(items.size - 1)
                }
            }
        }
    }

    private fun updateUI(items: List<MessageItem>) {
        if (items.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvMessages.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.rvMessages.visibility = View.VISIBLE
            previewAdapter.submitList(items)
        }
    }

    private fun withDayHeaders(messages: List<SmsEntry>): List<MessageItem> {
        if (messages.isEmpty()) return emptyList()
        val out = ArrayList<MessageItem>(messages.size + 8)
        var lastDayKey: String? = null
        for (m in messages) {
            val key = dayKey(m.dateMs)
            if (key != lastDayKey) {
                out += MessageItem.DayHeader(dayLabel(m.dateMs))
                lastDayKey = key
            }
            out += MessageItem.Message(m)
        }
        return out
    }

    // ── Long press / copy / delete one ─────────────────────────────────────

    private fun onMessageLongClick(entry: SmsEntry) {
        val items = arrayOf(getString(R.string.action_copy), getString(R.string.action_delete))
        AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> copyToClipboard(entry)
                    1 -> confirmDeleteOne(entry)
                }
            }
            .show()
    }

    private fun copyToClipboard(entry: SmsEntry) {
        val clip = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clip.setPrimaryClip(ClipData.newPlainText("message", entry.body))
        showToast(getString(R.string.copied))
    }

    private fun confirmDeleteOne(entry: SmsEntry) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_title)
            .setMessage(R.string.delete_one_confirm)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(R.string.delete) { _, _ ->
                if (isDefaultSmsApp()) {
                    executeDeleteOne(entry)
                } else {
                    pendingDeleteEntry = entry
                    requestDefaultRoleForSingle()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun executeDeleteOne(entry: SmsEntry) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                SmsHelper.deleteOne(this@ConversationActivity, entry.id, entry.isMms)
            }
            if (ok) {
                showToast(resources.getQuantityString(R.plurals.messages_deleted, 1, 1))
                refreshMessages(scrollToBottom = false)
            } else {
                showToast(getString(R.string.delete_failed))
            }
        }
    }

    private fun requestDefaultRoleForSingle() {
        val rm = getSystemService(Context.ROLE_SERVICE) as RoleManager
        defaultSmsForSingleDelete.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS))
    }

    // ── Delete window flow ─────────────────────────────────────────────────

    private fun showDeleteWindowDialog() {
        val labels = DURATIONS.map { getString(it.labelRes) }.toTypedArray()
        var picked = lastDeleteIndex
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_window_title)
            .setSingleChoiceItems(labels, picked) { _, which -> picked = which }
            .setPositiveButton(R.string.delete) { _, _ ->
                lastDeleteIndex = picked
                confirmDeleteWindow(DURATIONS[picked])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteWindow(option: DurationOption) {
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
        val rm = getSystemService(Context.ROLE_SERVICE) as RoleManager
        return rm.isRoleHeld(RoleManager.ROLE_SMS)
    }

    private fun requestDefaultSmsRole() {
        AlertDialog.Builder(this)
            .setTitle(R.string.default_app_title)
            .setMessage(R.string.default_app_message)
            .setPositiveButton(R.string.continue_btn) { _, _ ->
                val rm = getSystemService(Context.ROLE_SERVICE) as RoleManager
                defaultSmsRoleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS))
            }
            .setNegativeButton(R.string.cancel) { _, _ -> pendingDeleteMs = 0L }
            .show()
    }

    private fun executeDelete(durationMs: Long) {
        lifecycleScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                SmsHelper.deleteMessagesByThread(
                    this@ConversationActivity, threadId, durationMs
                )
            }
            showToast(resources.getQuantityString(R.plurals.messages_deleted, deleted, deleted))
            refreshMessages(scrollToBottom = false)
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun openImageViewer(uri: Uri) {
        startActivity(
            Intent(this, ImageViewerActivity::class.java)
                .putExtra(ImageViewerActivity.EXTRA_URI, uri.toString())
        )
    }

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun dayKey(ms: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
    }

    private fun dayLabel(ms: Long): String {
        val now = Calendar.getInstance()
        val that = Calendar.getInstance().apply { timeInMillis = ms }
        val sameYear = now.get(Calendar.YEAR) == that.get(Calendar.YEAR)
        val sameDay = sameYear && now.get(Calendar.DAY_OF_YEAR) == that.get(Calendar.DAY_OF_YEAR)
        if (sameDay) return getString(R.string.today)
        val yesterday = now.clone() as Calendar
        yesterday.add(Calendar.DAY_OF_YEAR, -1)
        if (sameYear && yesterday.get(Calendar.DAY_OF_YEAR) == that.get(Calendar.DAY_OF_YEAR)) {
            return getString(R.string.yesterday)
        }
        val pattern = if (sameYear) "EEEE, MMM d" else "MMM d, yyyy"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(ms))
    }

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
