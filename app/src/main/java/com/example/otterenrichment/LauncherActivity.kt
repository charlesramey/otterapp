package com.example.otterenrichment

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.otterenrichment.databinding.ActivityLauncherBinding

class LauncherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLauncherBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        // First-time user button
        binding.btnFirstTimeUser.setOnClickListener {
            navigateToTutorial()
        }

        // Experienced user button
        binding.btnExperiencedUser.setOnClickListener {
            navigateToExperiencedFlow()
        }
    }

    private fun navigateToTutorial() {
        val intent = Intent(this, TutorialActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToExperiencedFlow() {
        // For now, navigate to experienced user options
        val intent = Intent(this, ExperiencedUserActivity::class.java)
        startActivity(intent)
    }
}
