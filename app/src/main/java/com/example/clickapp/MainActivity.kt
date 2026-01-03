package com.example.clickapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.clickapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        updateServiceStatus()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun setupUI() {
        // Enable accessibility service button
        binding.btnEnableService.setOnClickListener {
            openAccessibilitySettings()
        }

        // Open app and click button
        binding.btnOpenAndClick.setOnClickListener {
            performOpenAndClick()
        }

        // Click at coordinates button
        binding.btnClickCoordinates.setOnClickListener {
            performClickAtCoordinates()
        }

        // Show clickable elements button
        binding.btnShowElements.setOnClickListener {
            showClickableElements()
        }
    }

    private fun updateServiceStatus() {
        val isEnabled = ClickAccessibilityService.isServiceRunning
        binding.tvServiceStatus.text = if (isEnabled) {
            "Service Status: ENABLED"
        } else {
            "Service Status: DISABLED"
        }
        binding.tvServiceStatus.setTextColor(
            if (isEnabled) getColor(android.R.color.holo_green_dark)
            else getColor(android.R.color.holo_red_dark)
        )
    }

    private fun openAccessibilitySettings() {
        AlertDialog.Builder(this)
            .setTitle("Enable Accessibility Service")
            .setMessage("To use this app, you need to enable the ClickApp accessibility service.\n\n" +
                    "1. Find 'ClickApp' in the list\n" +
                    "2. Toggle it ON\n" +
                    "3. Confirm the permissions")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performOpenAndClick() {
        if (!ClickAccessibilityService.isServiceRunning) {
            Toast.makeText(this, "Please enable the accessibility service first", Toast.LENGTH_LONG).show()
            return
        }

        val packageName = binding.etPackageName.text.toString().trim()
        val targetText = binding.etTargetText.text.toString().trim()

        if (packageName.isEmpty()) {
            Toast.makeText(this, "Please enter a package name", Toast.LENGTH_SHORT).show()
            return
        }

        if (targetText.isEmpty()) {
            Toast.makeText(this, "Please enter text to click on", Toast.LENGTH_SHORT).show()
            return
        }

        // Configure the accessibility service
        ClickAccessibilityService.targetPackage = packageName
        ClickAccessibilityService.targetText = targetText
        ClickAccessibilityService.useCoordinates = false
        ClickAccessibilityService.pendingAction = true

        // Open the target app
        val service = ClickAccessibilityService.instance
        if (service != null) {
            val opened = service.openApp(packageName)
            if (opened) {
                Toast.makeText(this, "Opening app and will click on '$targetText'", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Could not open app. Check package name.", Toast.LENGTH_LONG).show()
                ClickAccessibilityService.pendingAction = false
            }
        }
    }

    private fun performClickAtCoordinates() {
        if (!ClickAccessibilityService.isServiceRunning) {
            Toast.makeText(this, "Please enable the accessibility service first", Toast.LENGTH_LONG).show()
            return
        }

        val packageName = binding.etPackageName.text.toString().trim()
        val xStr = binding.etClickX.text.toString().trim()
        val yStr = binding.etClickY.text.toString().trim()

        if (packageName.isEmpty()) {
            Toast.makeText(this, "Please enter a package name", Toast.LENGTH_SHORT).show()
            return
        }

        val x = xStr.toIntOrNull()
        val y = yStr.toIntOrNull()

        if (x == null || y == null) {
            Toast.makeText(this, "Please enter valid X and Y coordinates", Toast.LENGTH_SHORT).show()
            return
        }

        // Configure the accessibility service
        ClickAccessibilityService.targetPackage = packageName
        ClickAccessibilityService.clickX = x
        ClickAccessibilityService.clickY = y
        ClickAccessibilityService.useCoordinates = true
        ClickAccessibilityService.pendingAction = true

        // Open the target app
        val service = ClickAccessibilityService.instance
        if (service != null) {
            val opened = service.openApp(packageName)
            if (opened) {
                Toast.makeText(this, "Opening app and will click at ($x, $y)", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Could not open app. Check package name.", Toast.LENGTH_LONG).show()
                ClickAccessibilityService.pendingAction = false
            }
        }
    }

    private fun showClickableElements() {
        if (!ClickAccessibilityService.isServiceRunning) {
            Toast.makeText(this, "Please enable the accessibility service first", Toast.LENGTH_LONG).show()
            return
        }

        val service = ClickAccessibilityService.instance
        if (service != null) {
            val elements = service.getClickableElements()
            if (elements.isEmpty()) {
                Toast.makeText(this, "No clickable elements found in current window", Toast.LENGTH_SHORT).show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Clickable Elements")
                    .setItems(elements.toTypedArray()) { _, which ->
                        Toast.makeText(this, "Selected: ${elements[which]}", Toast.LENGTH_SHORT).show()
                    }
                    .setPositiveButton("Close", null)
                    .show()
            }
        }
    }
}
