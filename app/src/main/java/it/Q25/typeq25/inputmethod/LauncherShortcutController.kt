package it.srik.TypeQ25.inputmethod

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log
import it.srik.TypeQ25.R
import it.srik.TypeQ25.SettingsManager

/**
 * Controller for handling launcher shortcuts functionality.
 * Manages app launching, launcher detection, and shortcut assignment dialogs.
 */
class LauncherShortcutController(
    private val context: Context
) {
    companion object {
        private const val TAG = "TypeQ25InputMethod"
        private const val POWER_SHORTCUT_TIMEOUT_MS = 5000L // 5 seconds timeout
    }

    // Cache for launcher packages
    private var cachedLauncherPackages: Set<String>? = null
    
    // State for Power Shortcuts: SYM pressed to activate shortcuts
    private var powerShortcutSymPressed: Boolean = false
    private var powerShortcutTimeoutHandler: android.os.Handler? = null
    private var powerShortcutTimeoutRunnable: Runnable? = null
    
    // State for managing nav mode during power shortcuts
    private var navModeWasActive: Boolean = false
    private var exitNavModeCallback: (() -> Unit)? = null
    private var enterNavModeCallback: (() -> Unit)? = null

    /**
     * Check if the current package is a launcher.
     */
    fun isLauncher(packageName: String?): Boolean {
        if (packageName == null) return false
        
        // Cache the list of launchers to avoid repeated queries
        if (cachedLauncherPackages == null) {
            try {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                }
                
                val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
                cachedLauncherPackages = resolveInfos.map { it.activityInfo.packageName }.toSet()
                Log.d(TAG, "Launcher packages found: $cachedLauncherPackages")
            } catch (e: Exception) {
                Log.e(TAG, "Error detecting launchers", e)
                cachedLauncherPackages = emptySet()
            }
        }
        
        val isLauncher = cachedLauncherPackages?.contains(packageName) ?: false
        Log.d(TAG, "isLauncher($packageName) = $isLauncher")
        return isLauncher
    }
    
    /**
     * Open an app by package name.
     */
    private fun launchApp(packageName: String): Boolean {
        try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "App opened: $packageName")
                return true
            } else {
                Log.w(TAG, "No launch intent found for: $packageName")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app $packageName", e)
            return false
        }
    }
    
    /**
     * Handles launcher shortcuts when not in a text field.
     */
    fun handleLauncherShortcut(keyCode: Int): Boolean {
        val shortcut = SettingsManager.getLauncherShortcut(context, keyCode)
        if (shortcut != null) {
            // Gestisci diversi tipi di azioni
            when (shortcut.type) {
                SettingsManager.LauncherShortcut.TYPE_APP -> {
                    if (shortcut.packageName != null) {
                        val success = launchApp(shortcut.packageName)
                        if (success) {
                            Log.d(TAG, "Launcher shortcut executed: key $keyCode -> ${shortcut.packageName}")
                            return true // Consume the event
                        }
                    }
                }
                SettingsManager.LauncherShortcut.TYPE_SHORTCUT -> {
                    // TODO: Handle shortcuts in the future
                    Log.d(TAG, "Shortcut type not yet implemented: ${shortcut.type}")
                }
                else -> {
                    Log.d(TAG, "Unknown action type: ${shortcut.type}")
                }
            }
        } else {
            // Key not assigned: show dialog to assign an app
            showLauncherShortcutAssignmentDialog(keyCode)
            return true // Consume the event to prevent it from being handled elsewhere
        }
        return false // Don't consume the event
    }
    
    /**
     * Show the dialog to assign an app to a key.
     */
    private fun showLauncherShortcutAssignmentDialog(keyCode: Int) {
        try {
            val intent = Intent(context, LauncherShortcutAssignmentActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(LauncherShortcutAssignmentActivity.EXTRA_KEY_CODE, keyCode)
            }
            context.startActivity(intent)
            Log.d(TAG, "Assignment dialog shown for key $keyCode")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing assignment dialog", e)
        }
    }
    
    /**
     * Set callbacks to manage nav mode during power shortcuts.
     */
    fun setNavModeCallbacks(
        exitNavMode: () -> Unit,
        enterNavMode: () -> Unit
    ) {
        exitNavModeCallback = exitNavMode
        enterNavModeCallback = enterNavMode
    }
    
    /**
     * Toggle Power Shortcut mode (SYM pressed).
     * If already active, deactivates it (edge case).
     * Returns true if mode was activated, false if deactivated.
     * @param isNavModeActive indicates if nav mode is active when SYM is pressed
     */
    fun togglePowerShortcutMode(
        showToast: (String) -> Unit,
        isNavModeActive: Boolean = false
    ): Boolean {
        if (powerShortcutSymPressed) {
            // Edge case: if already active, deactivate
            resetPowerShortcutMode()
            Log.d(TAG, "Power Shortcut mode deactivated by SYM")
            return false
        }
        
        // Save if nav mode was active and disable it if necessary
        navModeWasActive = isNavModeActive
        if (isNavModeActive) {
            exitNavModeCallback?.invoke()
            Log.d(TAG, "Nav mode disabled to activate Power Shortcut")
        }
        
        // Activate mode
        powerShortcutSymPressed = true
        Log.d(TAG, "Power Shortcut mode activated")
        
        // Show toast
        val message = context.getString(R.string.power_shortcuts_press_key)
        showToast(message)
        
        // Cancel previous timeout if exists
        cancelPowerShortcutTimeout()
        
        // Set timeout to reset automatically
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        powerShortcutTimeoutRunnable = Runnable {
            resetPowerShortcutMode()
        }
        powerShortcutTimeoutHandler = handler
        handler.postDelayed(powerShortcutTimeoutRunnable!!, POWER_SHORTCUT_TIMEOUT_MS)
        
        return true
    }
    
    /**
     * Reset Power Shortcut mode.
     * If nav mode was active before, re-enable it.
     */
    fun resetPowerShortcutMode() {
        if (powerShortcutSymPressed) {
            powerShortcutSymPressed = false
            cancelPowerShortcutTimeout()
            Log.d(TAG, "Power Shortcut mode reset")
            
            // If nav mode was active before, re-enable it
            if (navModeWasActive) {
                enterNavModeCallback?.invoke()
                navModeWasActive = false
                Log.d(TAG, "Nav mode re-enabled after Power Shortcut")
            }
        }
    }
    
    /**
     * Check if Power Shortcut mode is active.
     */
    fun isPowerShortcutModeActive(): Boolean {
        return powerShortcutSymPressed
    }
    
    /**
     * Cancel Power Shortcut mode timeout.
     */
    private fun cancelPowerShortcutTimeout() {
        powerShortcutTimeoutRunnable?.let { runnable ->
            powerShortcutTimeoutHandler?.removeCallbacks(runnable)
        }
        powerShortcutTimeoutRunnable = null
        powerShortcutTimeoutHandler = null
    }

    /**
     * Handle power shortcuts when SYM was pressed first.
     * Reuses existing handleLauncherShortcut logic.
     * Returns true if shortcut was handled, false otherwise.
     */
    fun handlePowerShortcut(keyCode: Int): Boolean {
        if (!isPowerShortcutModeActive()) {
            return false
        }
        
        // Reset mode after use
        resetPowerShortcutMode()
        
        // Reuse existing logic - same function, same assignments
        return handleLauncherShortcut(keyCode)
    }
}

