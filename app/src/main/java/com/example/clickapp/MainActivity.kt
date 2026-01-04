package com.example.clickapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.AdapterView
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.clickapp.databinding.ActivityMainBinding
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())
    private val OVERLAY_PERMISSION_REQUEST_CODE = 1001
    private var installedApps: List<AppInfo> = emptyList()
    private var selectedPackageName: String = ""

    private lateinit var elementsAdapter: ClickableElementAdapter
    private val elementsList = mutableListOf<ClickableElement>()

    private lateinit var schedulerManager: SchedulerManager
    private var selectedScheduleInterval = ScheduleInterval.NONE

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

    private val coordinatesReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == CoordinatePickerService.ACTION_COORDINATES_PICKED) {
                val x = intent.getIntExtra(CoordinatePickerService.EXTRA_X, 0)
                val y = intent.getIntExtra(CoordinatePickerService.EXTRA_Y, 0)

                handler.post {
                    binding.etClickX.setText(x.toString())
                    binding.etClickY.setText(y.toString())
                    Toast.makeText(
                        this@MainActivity,
                        "Coordinates picked: ($x, $y)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        schedulerManager = SchedulerManager(this)
        schedulerManager.rescheduleAllShortcuts()

        registerCoordinatesReceiver()
        loadInstalledApps()
        setupElementsList()
        setupUI()
        setupScheduleIntervalSpinner()
        updateServiceStatus()
        setupVersionFooter()
    }

    private fun setupVersionFooter() {
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        binding.tvVersion.text = "Version $versionName"
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

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(coordinatesReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered, ignore
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_shortcuts -> {
                startActivity(Intent(this, ShortcutsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun registerElementsReceiver() {
        val elementsFilter = IntentFilter(ClickAccessibilityService.ACTION_ELEMENTS_UPDATED)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(elementsReceiver, elementsFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(elementsReceiver, elementsFilter)
        }
    }

    private fun registerCoordinatesReceiver() {
        val coordinatesFilter = IntentFilter(CoordinatePickerService.ACTION_COORDINATES_PICKED)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(coordinatesReceiver, coordinatesFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(coordinatesReceiver, coordinatesFilter)
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

    private fun setupScheduleIntervalSpinner() {
        val intervals = ScheduleInterval.values()
        val intervalNames = intervals.map { it.displayName }
        val adapter = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            intervalNames
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerScheduleInterval.adapter = adapter

        binding.spinnerScheduleInterval.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                selectedScheduleInterval = intervals[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedScheduleInterval = ScheduleInterval.NONE
            }
        }

        // Enable/disable spinner based on checkbox
        binding.cbEnableScheduling.setOnCheckedChangeListener { _, isChecked ->
            binding.spinnerScheduleInterval.isEnabled = isChecked
            if (!isChecked) {
                binding.spinnerScheduleInterval.setSelection(0) // Reset to NONE
            }
        }

        // Initially disable the spinner
        binding.spinnerScheduleInterval.isEnabled = false
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

        // Pick coordinates button
        binding.btnPickCoordinates.setOnClickListener {
            startCoordinatePicker()
        }

        // Click at coordinates button
        binding.btnClickCoordinates.setOnClickListener {
            performClickAtCoordinates()
        }

        // Show clickable elements button
        binding.btnShowElements.setOnClickListener {
            showClickableElements()
        }

        // Save shortcut button
        binding.btnSaveShortcut.setOnClickListener {
            saveAsShortcut()
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

    private fun startCoordinatePicker() {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("To pick coordinates from screen, this app needs permission to draw over other apps.\n\nYou will be taken to settings to enable this permission.")
                .setPositiveButton("Open Settings") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            launchCoordinatePicker()
        }
    }

    private fun launchCoordinatePicker() {
        if (selectedPackageName.isEmpty()) {
            Toast.makeText(this, "Please select an app first", Toast.LENGTH_SHORT).show()
            return
        }

        val service = ClickAccessibilityService.instance
        if (service != null) {
            val opened = service.openApp(selectedPackageName)
            if (opened) {
                Toast.makeText(this, "Tap anywhere on screen to pick coordinates", Toast.LENGTH_LONG).show()

                // Delay to let the target app open, then show overlay
                handler.postDelayed({
                    val intent = Intent(this, CoordinatePickerService::class.java)
                    startService(intent)
                }, 1500)
            } else {
                Toast.makeText(this, "Could not open app", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Please enable the accessibility service first", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                launchCoordinatePicker()
            } else {
                Toast.makeText(
                    this,
                    "Overlay permission is required to pick coordinates",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
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

        if (selectedPackageName.isEmpty()) {
            Toast.makeText(this, "Please select an app first", Toast.LENGTH_SHORT).show()
            return
        }

        val service = ClickAccessibilityService.instance
        if (service != null) {
            // First open the target app
            val opened = service.openApp(selectedPackageName)
            if (opened) {
                Toast.makeText(this, "Opening app to scan elements...", Toast.LENGTH_SHORT).show()

                // Wait for the app to fully load, then get elements
                handler.postDelayed({
                    val elements = service.getClickableElements()
                    if (elements.isEmpty()) {
                        Toast.makeText(this, "No clickable elements found in target app", Toast.LENGTH_SHORT).show()
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle("Clickable Elements")
                            .setItems(elements.toTypedArray()) { _, which ->
                                Toast.makeText(this, "Selected: ${elements[which]}", Toast.LENGTH_SHORT).show()
                            }
                            .setPositiveButton("Close", null)
                            .show()
                    }
                }, 1500) // Same delay as other operations
            } else {
                Toast.makeText(this, "Could not open app", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveAsShortcut() {
        if (selectedPackageName.isEmpty()) {
            Toast.makeText(this, "Please select an app first", Toast.LENGTH_SHORT).show()
            return
        }

        val targetText = binding.etTargetText.text.toString().trim()
        val xStr = binding.etClickX.text.toString().trim()
        val yStr = binding.etClickY.text.toString().trim()
        val x = xStr.toIntOrNull() ?: -1
        val y = yStr.toIntOrNull() ?: -1

        val useCoordinates: Boolean
        if (targetText.isEmpty() && (x == -1 || y == -1)) {
            Toast.makeText(this, "Please configure either text or coordinates", Toast.LENGTH_SHORT).show()
            return
        } else if (targetText.isNotEmpty()) {
            useCoordinates = false
        } else {
            useCoordinates = true
        }

        val input = EditText(this)
        input.hint = "Shortcut name"

        val selectedApp = installedApps.find { it.packageName == selectedPackageName }
        val defaultName = if (useCoordinates) {
            "Click at ($x, $y) in ${selectedApp?.appName ?: selectedPackageName}"
        } else {
            "Click \"$targetText\" in ${selectedApp?.appName ?: selectedPackageName}"
        }
        input.setText(defaultName)

        AlertDialog.Builder(this)
            .setTitle("Save Shortcut")
            .setMessage("Enter a name for this shortcut:")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val delayStr = binding.etClickDelay.text.toString().trim()
                val delaySeconds = delayStr.toFloatOrNull() ?: 2f

                val schedulingEnabled = binding.cbEnableScheduling.isChecked
                val scheduleInterval = if (schedulingEnabled) selectedScheduleInterval else ScheduleInterval.NONE

                val shortcut = ClickShortcut(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    appName = selectedApp?.appName ?: selectedPackageName,
                    packageName = selectedPackageName,
                    useCoordinates = useCoordinates,
                    targetText = if (useCoordinates) "" else targetText,
                    clickX = if (useCoordinates) x else -1,
                    clickY = if (useCoordinates) y else -1,
                    doubleClickEnabled = binding.cbDoubleClick.isChecked,
                    doubleClickDelayMs = (delaySeconds * 1000).toLong(),
                    schedulingEnabled = schedulingEnabled,
                    scheduleInterval = scheduleInterval
                )

                val storage = ShortcutStorage(this)
                storage.saveShortcut(shortcut)

                // Schedule the shortcut if scheduling is enabled
                if (schedulingEnabled && scheduleInterval != ScheduleInterval.NONE) {
                    schedulerManager.scheduleShortcut(shortcut)
                    Toast.makeText(this, "Shortcut saved and scheduled: $name", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Shortcut saved: $name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
