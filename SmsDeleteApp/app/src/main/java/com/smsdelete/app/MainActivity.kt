package com.smsdelete.app

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.smsdelete.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var threadAdapter: ThreadListAdapter

    private var allThreads: List<ThreadSummary> = emptyList()
    private var query: String = ""

    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable { refreshThreads() }

    private val smsObserver = object : ContentObserver(refreshHandler) {
        override fun onChange(selfChange: Boolean) {
            refreshHandler.removeCallbacks(refreshRunnable)
            refreshHandler.postDelayed(refreshRunnable, 250)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_SMS] == true) refreshThreads()
        else showToast(getString(R.string.read_perm_required))
    }

    private val pickPhone = registerForActivityResult(PickPhoneNumber) { uri ->
        if (uri != null) onPhonePicked(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        threadAdapter = ThreadListAdapter(
            onClick = { thread -> openThread(thread) },
            onLongClick = { thread -> onThreadLongClick(thread) }
        )
        binding.rvThreads.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = threadAdapter
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty().trim()
                applyFilter()
            }
        })

        binding.btnPickContact.setOnClickListener { pickPhone.launch(Unit) }
        binding.fabCompose.setOnClickListener {
            startActivity(Intent(this, ComposeActivity::class.java))
        }
        binding.swipeRefresh.setOnRefreshListener { refreshThreads() }

        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        if (hasReadSmsPermission()) refreshThreads()
        contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, smsObserver)
        contentResolver.registerContentObserver(Telephony.Mms.CONTENT_URI, true, smsObserver)
    }

    override fun onPause() {
        super.onPause()
        contentResolver.unregisterContentObserver(smsObserver)
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_settings -> {
            startActivity(Intent(this, SettingsActivity::class.java)); true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun checkAndRequestPermissions() {
        val needed = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
        else refreshThreads()
    }

    private fun hasReadSmsPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED

    private fun refreshThreads() {
        if (!hasReadSmsPermission()) {
            binding.swipeRefresh.isRefreshing = false
            return
        }
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val threads = withContext(Dispatchers.IO) {
                SmsHelper.queryThreads(this@MainActivity)
            }
            binding.progressBar.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false
            allThreads = threads
            applyFilter()
        }
    }

    private fun applyFilter() {
        val filtered = if (query.isEmpty()) {
            allThreads
        } else {
            val q = query.lowercase()
            allThreads.filter {
                it.displayName.lowercase().contains(q) ||
                        it.address.lowercase().contains(q)
            }
        }
        if (filtered.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvThreads.visibility = View.GONE
            val msg = if (allThreads.isEmpty()) getString(R.string.no_conversations)
                      else getString(R.string.no_match)
            binding.tvHeader.text = msg
            binding.tvEmpty.text = msg
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.rvThreads.visibility = View.VISIBLE
            binding.tvHeader.text = resources.getQuantityString(
                R.plurals.conversations_count, filtered.size, filtered.size
            )
            threadAdapter.submitList(filtered)
        }
    }

    private fun onThreadLongClick(thread: ThreadSummary) {
        val target = thread.displayName.ifBlank { thread.address }
        AlertDialog.Builder(this)
            .setTitle(target)
            .setItems(arrayOf(getString(R.string.action_delete_conversation))) { _, which ->
                if (which == 0) confirmDeleteThread(thread)
            }
            .show()
    }

    private fun confirmDeleteThread(thread: ThreadSummary) {
        val target = thread.displayName.ifBlank { thread.address }
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_title)
            .setMessage(getString(R.string.delete_conversation_confirm, target))
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(R.string.delete) { _, _ ->
                if (isDefaultSmsApp()) executeDeleteThread(thread)
                else {
                    pendingThreadToDelete = thread
                    val rm = getSystemService(Context.ROLE_SERVICE) as RoleManager
                    defaultSmsForThreadDelete.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun executeDeleteThread(thread: ThreadSummary) {
        lifecycleScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                SmsHelper.deleteEntireThread(this@MainActivity, thread.threadId)
            }
            showToast(resources.getQuantityString(R.plurals.messages_deleted, deleted, deleted))
            refreshThreads()
        }
    }

    private fun isDefaultSmsApp(): Boolean {
        val rm = getSystemService(Context.ROLE_SERVICE) as RoleManager
        return rm.isRoleHeld(RoleManager.ROLE_SMS)
    }

    private val defaultSmsForThreadDelete = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val pending = pendingThreadToDelete
        pendingThreadToDelete = null
        if (pending != null && isDefaultSmsApp()) executeDeleteThread(pending)
        else if (pending != null) showToast(getString(R.string.default_app_required))
    }
    private var pendingThreadToDelete: ThreadSummary? = null

    private fun openThread(thread: ThreadSummary) {
        startActivity(
            Intent(this, ConversationActivity::class.java)
                .putExtra(ConversationActivity.EXTRA_THREAD_ID, thread.threadId)
                .putExtra(ConversationActivity.EXTRA_TITLE, thread.displayName)
                .putExtra(ConversationActivity.EXTRA_ADDRESS, thread.address)
        )
    }

    private fun onPhonePicked(uri: Uri) {
        val pickedNumber = contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ),
            null, null, null
        )?.use { c ->
            if (c.moveToFirst()) c.getString(0) to (c.getString(1) ?: "") else null
        } ?: return

        val (number, name) = pickedNumber
        if (number.isNullOrBlank()) return

        val match = allThreads.firstOrNull { thread ->
            try { PhoneNumberUtils.compare(thread.address, number) }
            catch (_: Exception) { false }
        }
        if (match != null) openThread(match)
        else showToast(getString(R.string.no_messages_for_contact, name.ifBlank { number }))
    }

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private object PickPhoneNumber : ActivityResultContract<Unit, Uri?>() {
        override fun createIntent(context: Context, input: Unit): Intent =
            Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            if (resultCode == Activity.RESULT_OK) intent?.data else null
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS
        )
    }
}
