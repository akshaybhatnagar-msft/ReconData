package com.smsdelete.app

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Telephony
import android.view.View
import android.widget.Toast
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
    private lateinit var previewAdapter: SmsPreviewAdapter

    // Duration stored in milliseconds; default = 1 hour
    private var selectedDurationMs: Long = ONE_HOUR_MS

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            refreshPreview()
        } else {
            showToast("SMS read permission is required to scan messages.")
        }
    }

    // Launched when we ask the user to make this the default SMS app
    private val defaultSmsRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (isDefaultSmsApp()) {
            executeDelete()
        } else {
            showToast("Default SMS app permission is needed to delete messages.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupDurationToggle()
        setupDeleteButton()
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        refreshPreview()
    }

    // ── UI setup ──────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        previewAdapter = SmsPreviewAdapter()
        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = previewAdapter
        }
    }

    private fun setupDurationToggle() {
        // Pre-select the 1-hour button
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
    }

    private fun setupDeleteButton() {
        binding.btnDelete.setOnClickListener { confirmAndDelete() }
    }

    // ── Permission handling ───────────────────────────────────────────────────

    private fun checkAndRequestPermissions() {
        val needed = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            refreshPreview()
        }
    }

    private fun hasReadSmsPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED

    // ── Preview ───────────────────────────────────────────────────────────────

    private fun refreshPreview() {
        if (!hasReadSmsPermission()) return

        binding.progressBar.visibility = View.VISIBLE
        binding.btnDelete.isEnabled = false

        lifecycleScope.launch {
            val messages = withContext(Dispatchers.IO) {
                SmsHelper.queryMessagesInWindow(this@MainActivity, selectedDurationMs)
            }
            binding.progressBar.visibility = View.GONE
            updateUI(messages)
        }
    }

    private fun updateUI(messages: List<SmsEntry>) {
        val count = messages.size
        if (count == 0) {
            binding.tvMessageCount.text = getString(R.string.no_messages_found)
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

    // ── Delete flow ───────────────────────────────────────────────────────────

    private fun confirmAndDelete() {
        val label = durationLabel(selectedDurationMs)
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_title)
            .setMessage(getString(R.string.dialog_message, label))
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
                SmsHelper.deleteMessagesInWindow(this@MainActivity, selectedDurationMs)
            }
            binding.progressBar.visibility = View.GONE
            showToast(
                resources.getQuantityString(R.plurals.messages_deleted, deleted, deleted)
            )
            refreshPreview()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun durationLabel(ms: Long) = when (ms) {
        30 * 60 * 1000L -> getString(R.string.duration_30min)
        ONE_HOUR_MS     -> getString(R.string.duration_1hour)
        2 * ONE_HOUR_MS -> getString(R.string.duration_2hours)
        else            -> getString(R.string.duration_2days)
    }

    companion object {
        private const val ONE_HOUR_MS = 60 * 60 * 1000L
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        )
    }
}
