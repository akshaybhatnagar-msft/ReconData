package com.smsdelete.app

import android.app.role.RoleManager
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.smsdelete.app.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshDefaultAppRow() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        binding.btnMakeDefault.setOnClickListener {
            val rm = getSystemService(Context.ROLE_SERVICE) as RoleManager
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS))
        }

        binding.tvVersion.text = getString(
            R.string.version_label, BuildConfig.VERSION_NAME
        )
    }

    override fun onResume() {
        super.onResume()
        refreshDefaultAppRow()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    private fun refreshDefaultAppRow() {
        val rm = getSystemService(Context.ROLE_SERVICE) as RoleManager
        val held = rm.isRoleHeld(RoleManager.ROLE_SMS)
        binding.tvDefaultStatus.text = getString(
            if (held) R.string.default_app_held else R.string.default_app_not_held
        )
        binding.btnMakeDefault.isEnabled = !held
    }
}
