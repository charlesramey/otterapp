package com.example.otterenrichment

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.otterenrichment.databinding.ActivityExperiencedUserBinding

class ExperiencedUserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExperiencedUserBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExperiencedUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        // Back button
        binding.btnBack.setOnClickListener {
            goBackToLauncher()
        }

        // Begin New Collection button
        binding.btnBeginNewCollection.setOnClickListener {
            navigateToNewCollection()
        }

        // Maintain Existing Collection button
        binding.btnMaintainCollection.setOnClickListener {
            navigateToMaintainCollection()
        }
    }

    private fun goBackToLauncher() {
        val intent = Intent(this, LauncherActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToNewCollection() {
        val intent = Intent(this, StartRecordingActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun navigateToMaintainCollection() {
        val intent = Intent(this, MaintenanceModeActivity::class.java)
        startActivity(intent)
        finish()
    }
}
