package com.example.clickapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ClickAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ClickAccessibilityService"
        const val ACTION_ELEMENTS_UPDATED = "com.example.clickapp.ELEMENTS_UPDATED"
        const val EXTRA_ELEMENTS = "elements"
        const val EXTRA_PACKAGE_NAME = "package_name"

        var instance: ClickAccessibilityService? = null
            private set

        var isServiceRunning = false
            private set

        // Target app package and click configuration
        var targetPackage: String = ""
        var targetText: String = ""
        var clickX: Int = -1
        var clickY: Int = -1
        var useCoordinates: Boolean = false
        var pendingAction: Boolean = false

        // Live monitoring
        var liveMonitoringEnabled: Boolean = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastBroadcastTime: Long = 0
    private val broadcastDebounceMs: Long = 300 // Debounce to avoid flooding

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isServiceRunning = true
        Log.d(TAG, "Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // Handle live monitoring
        if (liveMonitoringEnabled) {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                    broadcastClickableElements(packageName)
                }
            }
        }

        // Handle pending click action
        if (pendingAction && packageName == targetPackage) {
            Log.d(TAG, "Target app detected: $packageName")

            // Delay to allow app UI to fully load
            handler.postDelayed({
                if (useCoordinates && clickX >= 0 && clickY >= 0) {
                    performClickAtCoordinates(clickX.toFloat(), clickY.toFloat())
                } else if (targetText.isNotEmpty()) {
                    performClickOnText(targetText)
                }
                pendingAction = false
            }, 1500)
        }
    }

    /**
     * Broadcasts the current clickable elements with debouncing
     */
    private fun broadcastClickableElements(packageName: String) {
        // Skip if this is our own app
        if (packageName == this@ClickAccessibilityService.packageName) {
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBroadcastTime < broadcastDebounceMs) {
            return // Debounce
        }
        lastBroadcastTime = currentTime

        handler.post {
            val elements = getClickableElementsDetailed()
            val intent = Intent(ACTION_ELEMENTS_UPDATED).apply {
                setPackage(this@ClickAccessibilityService.packageName)
                putParcelableArrayListExtra(EXTRA_ELEMENTS, ArrayList(elements))
                putExtra(EXTRA_PACKAGE_NAME, packageName)
            }
            sendBroadcast(intent)
            Log.d(TAG, "Broadcast ${elements.size} elements from $packageName")
        }
    }

    /**
     * Gets detailed clickable elements as ClickableElement objects
     */
    fun getClickableElementsDetailed(): List<ClickableElement> {
        val elements = mutableListOf<ClickableElement>()
        val rootNode = rootInActiveWindow ?: return elements

        collectClickableNodesDetailed(rootNode, elements)
        return elements
    }

    private fun collectClickableNodesDetailed(node: AccessibilityNodeInfo?, list: MutableList<ClickableElement>) {
        if (node == null) return

        if (node.isClickable) {
            val text = node.text?.toString() ?: ""
            val contentDesc = node.contentDescription?.toString() ?: ""
            val className = node.className?.toString() ?: ""
            val rect = Rect()
            node.getBoundsInScreen(rect)

            val displayText = when {
                text.isNotEmpty() -> text
                contentDesc.isNotEmpty() -> contentDesc
                else -> className.substringAfterLast(".")
            }

            if (displayText.isNotEmpty() && rect.width() > 0 && rect.height() > 0) {
                list.add(
                    ClickableElement(
                        text = displayText,
                        contentDescription = contentDesc,
                        className = className,
                        x = rect.centerX(),
                        y = rect.centerY(),
                        bounds = "${rect.left},${rect.top},${rect.right},${rect.bottom}"
                    )
                )
            }
        }

        for (i in 0 until node.childCount) {
            collectClickableNodesDetailed(node.getChild(i), list)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isServiceRunning = false
        Log.d(TAG, "Accessibility Service destroyed")
    }

    /**
     * Opens an app by package name
     */
    fun openApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                Log.d(TAG, "Opened app: $packageName")
                true
            } else {
                Log.e(TAG, "App not found: $packageName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app: ${e.message}")
            false
        }
    }

    /**
     * Performs a click at specific screen coordinates using gesture API
     */
    fun performClickAtCoordinates(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(TAG, "Gesture API requires Android N or higher")
            return false
        }

        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        val result = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Click completed at ($x, $y)")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.e(TAG, "Click cancelled at ($x, $y)")
            }
        }, null)

        Log.d(TAG, "Click dispatched at ($x, $y): $result")
        return result
    }

    /**
     * Finds and clicks on a UI element containing specific text
     */
    fun performClickOnText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: run {
            Log.e(TAG, "Root node is null")
            return false
        }

        val nodes = rootNode.findAccessibilityNodeInfosByText(text)
        if (nodes.isNullOrEmpty()) {
            Log.e(TAG, "No nodes found with text: $text")
            // Try finding by view ID or content description
            return findAndClickNodeRecursive(rootNode, text)
        }

        for (node in nodes) {
            if (clickNode(node)) {
                return true
            }
        }

        return false
    }

    /**
     * Recursively searches for and clicks a node matching the text
     */
    private fun findAndClickNodeRecursive(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false

        // Check if this node matches
        val nodeText = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""

        if (nodeText.contains(text, ignoreCase = true) ||
            contentDesc.contains(text, ignoreCase = true)) {
            return clickNode(node)
        }

        // Search children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (findAndClickNodeRecursive(child, text)) {
                return true
            }
        }

        return false
    }

    /**
     * Clicks on a specific accessibility node
     */
    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        // Try clicking the node directly
        if (node.isClickable) {
            val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "Clicked on node: ${node.text}, result: $result")
            return result
        }

        // If node isn't clickable, try clicking its parent
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                val result = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Clicked on parent node, result: $result")
                return result
            }
            parent = parent.parent
        }

        // Last resort: click at the node's coordinates
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val centerX = rect.centerX().toFloat()
        val centerY = rect.centerY().toFloat()

        return performClickAtCoordinates(centerX, centerY)
    }

    /**
     * Gets all clickable elements in current window
     */
    fun getClickableElements(): List<String> {
        val elements = mutableListOf<String>()
        val rootNode = rootInActiveWindow ?: return elements

        collectClickableNodes(rootNode, elements)
        return elements
    }

    private fun collectClickableNodes(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return

        if (node.isClickable) {
            val text = node.text?.toString() ?: node.contentDescription?.toString() ?: "Unknown"
            val rect = Rect()
            node.getBoundsInScreen(rect)
            list.add("$text (${rect.centerX()}, ${rect.centerY()})")
        }

        for (i in 0 until node.childCount) {
            collectClickableNodes(node.getChild(i), list)
        }
    }
}
