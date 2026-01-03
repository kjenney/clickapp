package com.example.clickapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.clickapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private var installedApps: List<AppInfo> = emptyList()
    private var selectedPackageName: String = ""

    private lateinit var elementsAdapter: ClickableElementAdapter
    private val elementsList = mutableListOf<ClickableElement>()

    private val elementsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ClickAccessibilityService.ACTION_ELEMENTS_UPDATED) {
                val elements = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(
                        ClickAccessibilityService.EXTRA_ELEMENTS,
                        ClickableElement::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(ClickAccessibilityService.EXTRA_ELEMENTS)
                }

                val packageName = intent.getStringExtra(ClickAccessibilityService.EXTRA_PACKAGE_NAME) ?: ""

                handler.post {
                    elements?.let {
                        elementsAdapter.updateElements(it)
                        binding.tvCurrentApp.text = "Current App: $packageName"
                        binding.tvMonitoringStatus.text = "Monitoring: ${it.size} elements found"
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadInstalledApps()
        setupElementsList()
        setupUI()
        updateServiceStatus()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        registerElementsReceiver()
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(elementsReceiver)
    }

    private fun registerElementsReceiver() {
        val filter = IntentFilter(ClickAccessibilityService.ACTION_ELEMENTS_UPDATED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(elementsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(elementsReceiver, filter)
        }
    }

    private fun setupElementsList() {
        elementsAdapter = ClickableElementAdapter(this, elementsList) { element ->
            onElementSelected(element)
        }
        binding.listElements.adapter = elementsAdapter
    }

    private fun onElementSelected(element: ClickableElement) {
        AlertDialog.Builder(this)
            .setTitle(element.text)
            .setMessage("Type: ${element.className.substringAfterLast(".")}\nCoordinates: (${element.x}, ${element.y})\nBounds: ${element.bounds}")
            .setPositiveButton("Use Text") { _, _ ->
                binding.etTargetText.setText(element.text)
                Toast.makeText(this, "Text set: ${element.text}", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Use Coordinates") { _, _ ->
                binding.etClickX.setText(element.x.toString())
                binding.etClickY.setText(element.y.toString())
                Toast.makeText(this, "Coordinates set: (${element.x}, ${element.y})", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadInstalledApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfoList = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)

        installedApps = resolveInfoList
            .map { resolveInfo ->
                AppInfo(
                    appName = resolveInfo.loadLabel(pm).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    icon = resolveInfo.loadIcon(pm)
                )
            }
            .filter { it.packageName != packageName } // Exclude this app
            .sortedBy { it.appName.lowercase() }
            .distinctBy { it.packageName }

        // Set up spinner adapter
        val adapter = AppSpinnerAdapter(this, installedApps)
        binding.spinnerPackage.adapter = adapter

        binding.spinnerPackage.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                selectedPackageName = installedApps[position].packageName
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedPackageName = ""
            }
        }

        // Select first item by default
        if (installedApps.isNotEmpty()) {
            selectedPackageName = installedApps[0].packageName
        }
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

        // Live monitoring toggle
        binding.switchLiveMonitoring.setOnCheckedChangeListener { _, isChecked ->
            if (!ClickAccessibilityService.isServiceRunning && isChecked) {
                binding.switchLiveMonitoring.isChecked = false
                Toast.makeText(this, "Please enable the accessibility service first", Toast.LENGTH_LONG).show()
                return@setOnCheckedChangeListener
            }

            ClickAccessibilityService.liveMonitoringEnabled = isChecked
            binding.tvMonitoringStatus.text = if (isChecked) {
                "Monitoring: ON - Switch to another app to see elements"
            } else {
                "Monitoring: OFF"
            }

            if (!isChecked) {
                elementsList.clear()
                elementsAdapter.notifyDataSetChanged()
                binding.tvCurrentApp.text = "Current App: -"
            }
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

    private fun configureDoubleClick() {
        val isDoubleClick = binding.cbDoubleClick.isChecked
        val delayStr = binding.etClickDelay.text.toString().trim()
        val delaySeconds = delayStr.toFloatOrNull() ?: 2f

        ClickAccessibilityService.doubleClickEnabled = isDoubleClick
        ClickAccessibilityService.doubleClickDelayMs = (delaySeconds * 1000).toLong()
    }

    private fun performOpenAndClick() {
        if (!ClickAccessibilityService.isServiceRunning) {
            Toast.makeText(this, "Please enable the accessibility service first", Toast.LENGTH_LONG).show()
            return
        }

        val targetText = binding.etTargetText.text.toString().trim()

        if (selectedPackageName.isEmpty()) {
            Toast.makeText(this, "Please select an app", Toast.LENGTH_SHORT).show()
            return
        }

        if (targetText.isEmpty()) {
            Toast.makeText(this, "Please enter text to click on", Toast.LENGTH_SHORT).show()
            return
        }

        // Configure double-click settings
        configureDoubleClick()

        // Configure the accessibility service
        ClickAccessibilityService.targetPackage = selectedPackageName
        ClickAccessibilityService.targetText = targetText
        ClickAccessibilityService.useCoordinates = false
        ClickAccessibilityService.pendingAction = true

        // Open the target app
        val service = ClickAccessibilityService.instance
        if (service != null) {
            val opened = service.openApp(selectedPackageName)
            if (opened) {
                val clickMsg = if (ClickAccessibilityService.doubleClickEnabled) {
                    "Opening app and will click twice on '$targetText'"
                } else {
                    "Opening app and will click on '$targetText'"
                }
                Toast.makeText(this, clickMsg, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Could not open app.", Toast.LENGTH_LONG).show()
                ClickAccessibilityService.pendingAction = false
            }
        }
    }

    private fun performClickAtCoordinates() {
        if (!ClickAccessibilityService.isServiceRunning) {
            Toast.makeText(this, "Please enable the accessibility service first", Toast.LENGTH_LONG).show()
            return
        }

        val xStr = binding.etClickX.text.toString().trim()
        val yStr = binding.etClickY.text.toString().trim()

        if (selectedPackageName.isEmpty()) {
            Toast.makeText(this, "Please select an app", Toast.LENGTH_SHORT).show()
            return
        }

        val x = xStr.toIntOrNull()
        val y = yStr.toIntOrNull()

        if (x == null || y == null) {
            Toast.makeText(this, "Please enter valid X and Y coordinates", Toast.LENGTH_SHORT).show()
            return
        }

        // Configure double-click settings
        configureDoubleClick()

        // Configure the accessibility service
        ClickAccessibilityService.targetPackage = selectedPackageName
        ClickAccessibilityService.clickX = x
        ClickAccessibilityService.clickY = y
        ClickAccessibilityService.useCoordinates = true
        ClickAccessibilityService.pendingAction = true

        // Open the target app
        val service = ClickAccessibilityService.instance
        if (service != null) {
            val opened = service.openApp(selectedPackageName)
            if (opened) {
                val clickMsg = if (ClickAccessibilityService.doubleClickEnabled) {
                    "Opening app and will click twice at ($x, $y)"
                } else {
                    "Opening app and will click at ($x, $y)"
                }
                Toast.makeText(this, clickMsg, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Could not open app.", Toast.LENGTH_LONG).show()
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
