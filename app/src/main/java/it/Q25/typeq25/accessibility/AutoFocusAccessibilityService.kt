package it.srik.TypeQ25.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import it.srik.TypeQ25.SettingsManager

/**
 * Accessibility service that automatically focuses input fields when apps are opened.
 * Supports browser address bars, message input fields, and other text fields.
 */
class AutoFocusAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "AutoFocusAccessibility"
        
        // Package names for specific app handling
        private val BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.sec.android.app.sbrowser",
            "com.kiwibrowser.browser",
            "com.duckduckgo.mobile.android"
        )
        
        private val MESSAGING_PACKAGES = setOf(
            "com.google.android.apps.messaging",
            "com.android.messaging",
            "com.samsung.android.messaging",
            "com.whatsapp",
            "org.telegram.messenger",
            "com.facebook.orca",
            "com.snapchat.android",
            "com.instagram.android",
            "com.discord",
            "com.twitter.android",
            "jp.naver.line.android"
        )
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or 
                         AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.notificationTimeout = 100
        
        serviceInfo = info
        
        Log.d(TAG, "AutoFocus Accessibility Service connected")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // Check if auto-focus is enabled in settings
        if (!SettingsManager.getAutoFocusInputFields(this)) {
            return
        }
        
        // Check if accessibility service has proper permissions
        if (!isAccessibilityServiceEnabled()) {
            Log.w(TAG, "Accessibility service not properly enabled")
            return
        }
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(event)
            }
        }
    }
    
    /**
     * Check if the accessibility service is properly enabled with required permissions.
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        try {
            val info = serviceInfo
            if (info == null) {
                Log.w(TAG, "Service info is null")
                return false
            }
            
            // Check if we have the required event types
            val hasWindowStateChanged = (info.eventTypes and AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) != 0
            if (!hasWindowStateChanged) {
                Log.w(TAG, "Missing TYPE_WINDOW_STATE_CHANGED event type")
                return false
            }
            
            // Check if we have the flag to retrieve interactive windows
            val canRetrieveWindows = (info.flags and AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS) != 0
            if (!canRetrieveWindows) {
                Log.w(TAG, "Missing FLAG_RETRIEVE_INTERACTIVE_WINDOWS flag")
                return false
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility service status", e)
            return false
        }
    }
    
    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        
        Log.d(TAG, "Window changed: $packageName")
        
        // Check if we have access to the active window
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.w(TAG, "Cannot access root node in active window")
            return
        }
        
        // Check if this is a browser or messaging app
        val isBrowser = BROWSER_PACKAGES.contains(packageName)
        val isMessaging = MESSAGING_PACKAGES.contains(packageName)
        
        if (!isBrowser && !isMessaging) {
            // For other apps, try to focus the first editable field
            focusFirstEditableField(rootNode)
            rootNode.recycle()
            return
        }
        
        // Wait a bit for the UI to fully load
        android.os.Handler(mainLooper).postDelayed({
            try {
                val delayedRootNode = rootInActiveWindow
                if (delayedRootNode == null) {
                    Log.w(TAG, "Root node no longer available after delay")
                    return@postDelayed
                }
                
                if (isBrowser) {
                    focusBrowserAddressBar(delayedRootNode)
                } else if (isMessaging) {
                    focusMessageInputField(delayedRootNode)
                }
                
                delayedRootNode.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "Error focusing field", e)
            }
        }, 300)
        
        rootNode.recycle()
    }
    
    private fun focusBrowserAddressBar(rootNode: AccessibilityNodeInfo) {
        // Try to find address bar by common resource IDs
        val addressBarIds = listOf(
            "url_bar",
            "search_box",
            "omnibox",
            "address_bar",
            "url_field",
            "search"
        )
        
        for (id in addressBarIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId("*:id/$id")
            if (nodes.isNotEmpty()) {
                for (node in nodes) {
                    if (node.isEditable && node.isFocusable) {
                        val focused = node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                        if (focused) {
                            Log.d(TAG, "Focused address bar: $id")
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        }
                        node.recycle()
                        return
                    }
                    node.recycle()
                }
            }
        }
        
        // Fallback: find any editable field at the top of the screen
        focusFirstEditableField(rootNode)
    }
    
    private fun focusMessageInputField(rootNode: AccessibilityNodeInfo) {
        // Try to find message input by common resource IDs
        val messageInputIds = listOf(
            "compose_message",
            "message_edit_text",
            "input",
            "compose",
            "entry",
            "chat_input"
        )
        
        for (id in messageInputIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId("*:id/$id")
            if (nodes.isNotEmpty()) {
                for (node in nodes) {
                    if (node.isEditable && node.isFocusable) {
                        val focused = node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                        if (focused) {
                            Log.d(TAG, "Focused message input: $id")
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        }
                        node.recycle()
                        return
                    }
                    node.recycle()
                }
            }
        }
        
        // Fallback: find any editable field
        focusFirstEditableField(rootNode)
    }
    
    private fun focusFirstEditableField(rootNode: AccessibilityNodeInfo) {
        val editableNode = findFirstEditableNode(rootNode)
        if (editableNode != null) {
            val focused = editableNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            if (focused) {
                Log.d(TAG, "Focused first editable field")
                editableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            editableNode.recycle()
        } else {
            Log.d(TAG, "No editable field found to focus")
        }
    }
    
    private fun findFirstEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isFocusable && node.isVisibleToUser) {
            return node
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findFirstEditableNode(child)
            if (result != null) {
                if (child != result) child.recycle()
                return result
            }
            child.recycle()
        }
        
        return null
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AutoFocus Accessibility Service destroyed")
    }
}
