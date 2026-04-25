package com.smsdelete.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
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
                    .putExtra(ConversationActivity.EXTRA_ADDRESS, thread.address)
            )
        }
        binding.rvThreads.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = threadAdapter
        }

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
            updateUI(threads)
        }
    }

    private fun updateUI(threads: List<ThreadSummary>) {
        if (threads.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvThreads.visibility = View.GONE
            binding.tvHeader.text = getString(R.string.no_conversations)
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.rvThreads.visibility = View.VISIBLE
            binding.tvHeader.text = resources.getQuantityString(
                R.plurals.conversations_count, threads.size, threads.size
            )
            threadAdapter.submitList(threads)
        }
    }

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        )
    }
}
