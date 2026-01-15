package com.example.otterenrichment

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.otterenrichment.databinding.ActivityBeginNewCollectionBinding

class BeginNewCollectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBeginNewCollectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBeginNewCollectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        // Back button
        binding.btnBack.setOnClickListener {
            val intent = Intent(this, ExperiencedUserActivity::class.java)
            startActivity(intent)
            finish()
        }

        // Power On Devices button
        binding.btnPowerOnDevices.setOnClickListener {
            navigateToPowerOnDevice()
        }

        // Start Recording Data button
        binding.btnStartRecording.setOnClickListener {
            navigateToStartRecording()
        }

        // List of Powered Devices button
        binding.btnListDevices.setOnClickListener {
            navigateToListDevices()
        }
    }

    private fun navigateToPowerOnDevice() {
        val intent = Intent(this, PowerOnDeviceActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToStartRecording() {
        val intent = Intent(this, StartRecordingActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToListDevices() {
        // TODO: Will implement later
        android.widget.Toast.makeText(this, "List Devices - Coming soon", android.widget.Toast.LENGTH_SHORT).show()
    }
}
