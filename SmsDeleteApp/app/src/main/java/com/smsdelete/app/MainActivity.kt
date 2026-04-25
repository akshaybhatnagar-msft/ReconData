package com.smsdelete.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_SMS] == true) {
            refreshThreads()
        } else {
            showToast(getString(R.string.read_perm_required))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        threadAdapter = ThreadListAdapter { thread ->
            startActivity(
                Intent(this, ConversationActivity::class.java)
                    .putExtra(ConversationActivity.EXTRA_THREAD_ID, thread.threadId)
                    .putExtra(ConversationActivity.EXTRA_TITLE, thread.displayName)
                    .putExtra(ConversationActivity.EXTRA_ADDRESS, thread.address)
            )
        }
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

        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        if (hasReadSmsPermission()) refreshThreads()
    }

    private fun checkAndRequestPermissions() {
        val needed = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            refreshThreads()
        }
    }

    private fun hasReadSmsPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED

    private fun refreshThreads() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val threads = withContext(Dispatchers.IO) {
                SmsHelper.queryThreads(this@MainActivity)
            }
            binding.progressBar.visibility = View.GONE
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
            binding.tvHeader.text = if (allThreads.isEmpty()) {
                getString(R.string.no_conversations)
            } else {
                getString(R.string.no_match)
            }
            binding.tvEmpty.text = if (allThreads.isEmpty()) {
                getString(R.string.no_conversations)
            } else {
                getString(R.string.no_match)
            }
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.rvThreads.visibility = View.VISIBLE
            binding.tvHeader.text = resources.getQuantityString(
                R.plurals.conversations_count, filtered.size, filtered.size
            )
            threadAdapter.submitList(filtered)
        }
    }

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        // READ_CONTACTS isn't strictly required, but resolving names needs it on most devices.
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS
        )
    }
}
