package com.example.clickapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
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

        // Double click configuration
        var doubleClickEnabled: Boolean = false
        var doubleClickDelayMs: Long = 2000

        // Anchor-based positioning
        var useAnchor: Boolean = false
        var anchorText: String = ""
        var anchorContentDescription: String = ""
        var offsetX: Int = 0
        var offsetY: Int = 0

        // Live monitoring
        var liveMonitoringEnabled: Boolean = false

        /**
         * Configure all click settings at once
         */
        fun configureClick(
            packageName: String,
            useCoordinates: Boolean,
            targetText: String = "",
            clickX: Int = -1,
            clickY: Int = -1,
            doubleClickEnabled: Boolean = false,
            doubleClickDelayMs: Long = 2000,
            useAnchor: Boolean = false,
            anchorText: String = "",
            anchorContentDescription: String = "",
            offsetX: Int = 0,
            offsetY: Int = 0
        ) {
            this.targetPackage = packageName
            this.useCoordinates = useCoordinates
            this.targetText = targetText
            this.clickX = clickX
            this.clickY = clickY
            this.doubleClickEnabled = doubleClickEnabled
            this.doubleClickDelayMs = doubleClickDelayMs
            this.useAnchor = useAnchor
            this.anchorText = anchorText
            this.anchorContentDescription = anchorContentDescription
            this.offsetX = offsetX
            this.offsetY = offsetY
            this.pendingAction = true
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastBroadcastTime: Long = 0
    private val broadcastDebounceMs: Long = 300 // Debounce to avoid flooding

    // Click indicator overlay
    private var clickIndicator: ClickIndicatorOverlay? = null
    private var windowManager: WindowManager? = null
    private var isIndicatorShowing = false
    private val indicatorDurationMs: Long = 800

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
                performConfiguredClick()

                // Schedule second click if double-click is enabled
                if (doubleClickEnabled) {
                    handler.postDelayed({
                        Log.d(TAG, "Performing second click after ${doubleClickDelayMs}ms delay")
                        performConfiguredClick()
                        pendingAction = false
                    }, doubleClickDelayMs)
                } else {
                    pendingAction = false
                }
            }, 1500)
        }
    }

    /**
     * Performs the configured click action (anchor-based, coordinates, or text)
     */
    private fun performConfiguredClick() {
        when {
            useAnchor -> performAnchorBasedClick()
            useCoordinates && clickX >= 0 && clickY >= 0 -> performClickAtCoordinates(clickX.toFloat(), clickY.toFloat())
            targetText.isNotEmpty() -> performClickOnText(targetText)
        }
    }

    /**
     * Performs a click relative to an anchor element
     * First scrolls to top, then finds anchor and clicks
     */
    private fun performAnchorBasedClick(): Boolean {
        val rootNode = rootInActiveWindow ?: run {
            Log.e(TAG, "Root node is null for anchor-based click")
            return false
        }

        // First scroll to top to ensure consistent positioning
        scrollToTop()

        // Wait for scroll to complete, then find anchor and click
        handler.postDelayed({
            performAnchorClickAfterScroll()
        }, 800)

        return true
    }

    private fun performAnchorClickAfterScroll() {
        val rootNode = rootInActiveWindow ?: run {
            Log.e(TAG, "Root node is null for anchor-based click after scroll")
            return
        }

        // Find the anchor element
        val anchorNode = findAnchorNode(rootNode)
        if (anchorNode == null) {
            Log.e(TAG, "Anchor element not found: text='$anchorText', contentDesc='$anchorContentDescription'")
            return
        }

        // Get anchor position
        val anchorRect = Rect()
        anchorNode.getBoundsInScreen(anchorRect)

        // Calculate click position relative to anchor
        val clickX = anchorRect.centerX() + offsetX
        val clickY = anchorRect.centerY() + offsetY

        Log.d(TAG, "Anchor found at (${anchorRect.centerX()}, ${anchorRect.centerY()}), clicking at ($clickX, $clickY)")

        performClickAtCoordinates(clickX.toFloat(), clickY.toFloat())
    }

    /**
     * Scrolls the current view to the top
     */
    fun scrollToTop() {
        val rootNode = rootInActiveWindow ?: return

        // Find scrollable node
        val scrollableNode = findScrollableNode(rootNode)
        if (scrollableNode != null) {
            // Perform multiple scroll-to-top actions to ensure we're at the very top
            repeat(5) {
                scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            }
            Log.d(TAG, "Scrolled to top via accessibility action")
        } else {
            // Fallback: use swipe gesture to scroll up
            performScrollGesture(isScrollUp = false) // Swipe down to scroll content up/to top
            Log.d(TAG, "Scrolled to top via gesture")
        }
    }

    /**
     * Finds a scrollable node in the view hierarchy
     */
    private fun findScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        if (node.isScrollable) {
            return node
        }

        for (i in 0 until node.childCount) {
            val result = findScrollableNode(node.getChild(i))
            if (result != null) {
                return result
            }
        }

        return null
    }

    /**
     * Performs a scroll gesture
     * @param isScrollUp true to scroll content up (swipe up), false to scroll content down (swipe down)
     */
    fun performScrollGesture(isScrollUp: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val centerX = screenWidth / 2f
        val startY: Float
        val endY: Float

        if (isScrollUp) {
            // Swipe up - start from bottom, move to top
            startY = screenHeight * 0.7f
            endY = screenHeight * 0.3f
        } else {
            // Swipe down - start from top, move to bottom (scrolls content to top)
            startY = screenHeight * 0.3f
            endY = screenHeight * 0.7f
        }

        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()

        return dispatchGesture(gesture, null, null)
    }

    /**
     * Finds an anchor node by text or content description
     */
    private fun findAnchorNode(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Try finding by text first
        if (anchorText.isNotEmpty()) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(anchorText)
            if (!nodes.isNullOrEmpty()) {
                return nodes[0]
            }
        }

        // Try finding by content description
        if (anchorContentDescription.isNotEmpty()) {
            val node = findNodeByContentDescription(rootNode, anchorContentDescription)
            if (node != null) {
                return node
            }
        }

        // Recursive search for partial match
        return findAnchorNodeRecursive(rootNode)
    }

    /**
     * Recursively searches for anchor node
     */
    private fun findAnchorNodeRecursive(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        val nodeText = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""

        // Check for match
        if (anchorText.isNotEmpty() && nodeText.contains(anchorText, ignoreCase = true)) {
            return node
        }
        if (anchorContentDescription.isNotEmpty() && contentDesc.contains(anchorContentDescription, ignoreCase = true)) {
            return node
        }

        // Search children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findAnchorNodeRecursive(child)
            if (result != null) {
                return result
            }
        }

        return null
    }

    /**
     * Finds a node by content description
     */
    private fun findNodeByContentDescription(node: AccessibilityNodeInfo?, targetDesc: String): AccessibilityNodeInfo? {
        if (node == null) return null

        val contentDesc = node.contentDescription?.toString() ?: ""
        if (contentDesc.equals(targetDesc, ignoreCase = true)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val result = findNodeByContentDescription(node.getChild(i), targetDesc)
            if (result != null) {
                return result
            }
        }

        return null
    }

    /**
     * Broadcasts the current clickable elements with debouncing
     */
    private fun broadcastClickableElements(packageName: String) {
        val ownPackage = this@ClickAccessibilityService.packageName

        // Skip if this is our own app
        if (packageName == ownPackage) {
            return
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBroadcastTime < broadcastDebounceMs) {
            return // Debounce
        }
        lastBroadcastTime = currentTime

        handler.post {
            // Double-check the root window isn't our app (might have changed since event)
            val rootNode = rootInActiveWindow
            val rootPackage = rootNode?.packageName?.toString()
            if (rootPackage == null || rootPackage == ownPackage) {
                return@post
            }

            val elements = getClickableElementsDetailed()
            val intent = Intent(ACTION_ELEMENTS_UPDATED).apply {
                setPackage(ownPackage)
                putParcelableArrayListExtra(EXTRA_ELEMENTS, ArrayList(elements))
                putExtra(EXTRA_PACKAGE_NAME, rootPackage)
            }
            sendBroadcast(intent)
            Log.d(TAG, "Broadcast ${elements.size} elements from $rootPackage")
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
        hideClickIndicator()
        instance = null
        isServiceRunning = false
        Log.d(TAG, "Accessibility Service destroyed")
    }

    /**
     * Shows a red dot indicator at the click location
     */
    private fun showClickIndicator(x: Float, y: Float) {
        handler.post {
            try {
                if (windowManager == null) {
                    windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                }

                // Remove existing indicator if showing
                hideClickIndicator()

                clickIndicator = ClickIndicatorOverlay(this)
                clickIndicator?.setClickPosition(x, y)

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                }

                windowManager?.addView(clickIndicator, params)
                isIndicatorShowing = true
                Log.d(TAG, "Click indicator shown at ($x, $y)")

                // Auto-hide after duration
                handler.postDelayed({
                    hideClickIndicator()
                }, indicatorDurationMs)

            } catch (e: Exception) {
                Log.e(TAG, "Error showing click indicator: ${e.message}")
            }
        }
    }

    /**
     * Hides the click indicator overlay
     */
    private fun hideClickIndicator() {
        try {
            if (isIndicatorShowing && clickIndicator != null) {
                windowManager?.removeView(clickIndicator)
                clickIndicator = null
                isIndicatorShowing = false
                Log.d(TAG, "Click indicator hidden")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding click indicator: ${e.message}")
            clickIndicator = null
            isIndicatorShowing = false
        }
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

        // Show click indicator
        showClickIndicator(x, y)

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
        // Get node bounds for indicator
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val centerX = rect.centerX().toFloat()
        val centerY = rect.centerY().toFloat()

        // Try clicking the node directly
        if (node.isClickable) {
            showClickIndicator(centerX, centerY)
            val result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "Clicked on node: ${node.text}, result: $result")
            return result
        }

        // If node isn't clickable, try clicking its parent
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                val parentRect = Rect()
                parent.getBoundsInScreen(parentRect)
                showClickIndicator(parentRect.centerX().toFloat(), parentRect.centerY().toFloat())
                val result = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Clicked on parent node, result: $result")
                return result
            }
            parent = parent.parent
        }

        // Last resort: click at the node's coordinates
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
