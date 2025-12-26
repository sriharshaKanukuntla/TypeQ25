package it.srik.TypeQ25.inputmethod

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.content.ContextCompat
import it.srik.TypeQ25.DeviceManager
import it.srik.TypeQ25.R
import it.srik.TypeQ25.SettingsManager
import it.srik.TypeQ25.core.NavModeController
import it.srik.TypeQ25.data.mappings.KeyMappingLoader
import android.os.Handler
import android.os.Looper
import it.srik.TypeQ25.inputmethod.AltSymManager
import it.srik.TypeQ25.core.SymLayoutController
import it.srik.TypeQ25.core.SymLayoutController.SymKeyResult
import it.srik.TypeQ25.core.TextInputController
import it.srik.TypeQ25.core.AutoCorrectionManager
import it.srik.TypeQ25.core.ModifierStateController
import it.srik.TypeQ25.inputmethod.KeyboardEventTracker
import it.srik.TypeQ25.inputmethod.TextSelectionHelper
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import it.srik.TypeQ25.data.layout.LayoutMapping
import it.srik.TypeQ25.data.layout.LayoutMappingRepository
import it.srik.TypeQ25.data.layout.isRealMultiTap

/**
 * Routes IME key events to the appropriate handlers so that the service can
 * focus on lifecycle wiring.
 */
class InputEventRouter(
    private val context: Context,
    private val navModeController: NavModeController
) {
    
    companion object {
        private const val TAG = "InputEventRouter"
    }
    
    // Reusable handler to avoid creating new instances
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Cache device type to avoid repeated calls
    private val deviceType = DeviceManager.getDevice(context)
    private val isQ25Device = deviceType == "Q25"
    
    /**
     * Checks if a keycode represents a CTRL key based on device type.
     */
    private fun isCtrlKey(keyCode: Int): Boolean {
        return if (isQ25Device) {
            keyCode == 60
        } else {
            keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT
        }
    }

    /**
     * Checks if a keycode represents a SYM key based on device type.
     */
    private fun isSymKey(keyCode: Int): Boolean {
        return if (isQ25Device) {
            keyCode == 58
        } else {
            keyCode == KeyEvent.KEYCODE_SYM
        }
    }

    sealed class EditableFieldRoutingResult {
        object Continue : EditableFieldRoutingResult()
        object Consume : EditableFieldRoutingResult()
        object CallSuper : EditableFieldRoutingResult()
    }

    data class NoEditableFieldCallbacks(
        val isAlphabeticKey: (Int) -> Boolean,
        val isLauncherPackage: (String?) -> Boolean,
        val handleLauncherShortcut: (Int) -> Boolean,
        val handlePowerShortcut: (Int) -> Boolean,
        val togglePowerShortcutMode: (String, Boolean) -> Unit,
        val callSuper: () -> Boolean,
        val currentInputConnection: () -> InputConnection?
    )

    fun handleKeyDownWithNoEditableField(
        keyCode: Int,
        event: KeyEvent?,
        ctrlKeyMap: Map<Int, KeyMappingLoader.CtrlMapping>,
        callbacks: NoEditableFieldCallbacks,
        ctrlLatchActive: Boolean,
        editorInfo: EditorInfo?,
        currentPackageName: String?,
        powerShortcutsEnabled: Boolean = false,
        launcherShortcutsEnabled: Boolean = false
    ): Boolean {
        // Handle keycode 0 for Q25: Answer call or open dialer
        if (isQ25Device && keyCode == 0) {
            try {
                // Check if we have permission to answer calls
                if (ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.ANSWER_PHONE_CALLS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
                    if (telecomManager != null) {
                        telecomManager.acceptRingingCall()
                        android.util.Log.d(TAG, "Attempted to answer call with keycode 0")
                        // Don't open dialer after attempting to answer
                        return true
                    } else {
                        android.util.Log.w(TAG, "TelecomManager is null")
                    }
                } else {
                    android.util.Log.w(TAG, "ANSWER_PHONE_CALLS permission not granted")
                }
            } catch (e: SecurityException) {
                // Permission denied
                android.util.Log.e(TAG, "SecurityException for acceptRingingCall", e)
            } catch (e: Exception) {
                // Other error, fall through to open dialer
                android.util.Log.e(TAG, "Exception in acceptRingingCall", e)
            }
            
            // If API < P or no permission or exception, open phone dialer
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL)
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // Failed to launch dialer, fall through
            }
        }
        
        // Handle Ctrl+H (Hang up) or Ctrl+E (End call) as alternative to HOME key
        // This is a workaround since Android system intercepts HOME key before IME sees it
        if (isQ25Device && (keyCode == KeyEvent.KEYCODE_H || keyCode == KeyEvent.KEYCODE_E) && 
            (event?.isCtrlPressed == true)) {
            android.util.Log.d(TAG, "Ctrl+${if (keyCode == KeyEvent.KEYCODE_H) "H" else "E"} pressed - triggering call end/silence")
            // Use the same logic as HOME key
            return handleCallEndOrSilence(callbacks)
        }
        
        // Handle KEYCODE_HOME for Q25: Mute ringtone when ringing, end call when active
        // Note: Android system often intercepts HOME key before IME can see it
        if (isQ25Device && keyCode == KeyEvent.KEYCODE_HOME) {
            android.util.Log.d(TAG, "HOME key detected in handleKeyDownWithNoEditableField")
            return handleCallEndOrSilence(callbacks)
        }
        
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (navModeController.isNavModeActive()) {
                navModeController.exitNavMode()
                return false
            }
            return callbacks.callSuper()
        }
        
        // Handle SYM key for Power Shortcuts (toggle: activate/deactivate)
        // Q25 uses keyCode 58 for SYM, other devices use KEYCODE_SYM (63)
        val isSymKey = (isQ25Device && keyCode == 58) || keyCode == KeyEvent.KEYCODE_SYM
        if (isSymKey && powerShortcutsEnabled) {
            android.util.Log.d(TAG, "SYM key pressed - toggling power shortcut mode")
            val message = context.getString(R.string.power_shortcuts_press_key)
            val isNavModeActive = navModeController.isNavModeActive()
            callbacks.togglePowerShortcutMode(message, isNavModeActive)
            return true // Consume the event
        }
        
        // Handle Power Shortcuts (SYM pressed + alphabetic key)
        if (!ctrlLatchActive && powerShortcutsEnabled) {
            if (callbacks.isAlphabeticKey(keyCode)) {
                android.util.Log.d(TAG, "Alphabetic key $keyCode pressed with power shortcuts enabled")
                if (callbacks.handlePowerShortcut(keyCode)) {
                    android.util.Log.d(TAG, "Power shortcut handled for keyCode=$keyCode")
                    return true
                }
                android.util.Log.d(TAG, "Power shortcut NOT handled for keyCode=$keyCode (mode not active?)")
            }
        }
        
        // Check if we're in the launcher and launcher shortcuts are enabled
        val isInLauncher = currentPackageName?.let { callbacks.isLauncherPackage(it) } == true
        if (isInLauncher && launcherShortcutsEnabled) {
            // Ignore certain keycodes that should always pass through to system
            // 24=O, 25=P, 164=VOLUME_MUTE, 66=ENTER
            if (keyCode != 24 && keyCode != 25 && keyCode != 164 && keyCode != 66) {
                // Try to handle as launcher shortcut
                val handled = callbacks.handleLauncherShortcut(keyCode)
                if (handled) {
                    android.util.Log.d(TAG, "Launcher shortcut handled for keyCode=$keyCode")
                    return true
                }
            }
        }
        
        return false
    }
    
    /**
     * Handle ending active call or silencing ringing call
     */
    private fun handleCallEndOrSilence(callbacks: NoEditableFieldCallbacks): Boolean {
        try {
            // Check READ_PHONE_STATE permission for isInCall
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_PHONE_STATE
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                android.util.Log.w(TAG, "READ_PHONE_STATE permission not granted")
                // No permission, let system handle normally
                return callbacks.callSuper()
            }
            
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
            if (telecomManager == null) {
                android.util.Log.w(TAG, "TelecomManager is null")
                return callbacks.callSuper()
            }
            
            // Check if in active call first
            val inCall = try {
                telecomManager.isInCall
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error checking isInCall", e)
                false
            }
            android.util.Log.d(TAG, "Call end/silence triggered, isInCall=$inCall")
            
            if (inCall) {
                // Try multiple methods to end the call
                android.util.Log.d(TAG, "Attempting to end call")
                
                // Method 1: Inject KEYCODE_ENDCALL (most reliable for ending calls)
                try {
                    val eventTime = android.os.SystemClock.uptimeMillis()
                    val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENDCALL, 0)
                    val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENDCALL, 0)
                    
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    audioManager?.dispatchMediaKeyEvent(downEvent)
                    audioManager?.dispatchMediaKeyEvent(upEvent)
                    android.util.Log.d(TAG, "KEYCODE_ENDCALL injected via AudioManager")
                    
                    // Also try via input manager
                    try {
                        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? android.hardware.input.InputManager
                        inputManager?.let { im ->
                            val injectInputEventMethod = im.javaClass.getMethod(
                                "injectInputEvent",
                                android.view.InputEvent::class.java,
                                Int::class.java
                            )
                            injectInputEventMethod.invoke(im, downEvent, 0)
                            injectInputEventMethod.invoke(im, upEvent, 0)
                            android.util.Log.d(TAG, "KEYCODE_ENDCALL also injected via InputManager")
                        }
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "InputManager injection failed (expected): ${e.message}")
                    }
                    
                    return true
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to inject KEYCODE_ENDCALL", e)
                }
                
                // Method 2: Try TelecomManager.endCall() as fallback
                if (ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.ANSWER_PHONE_CALLS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    try {
                        val success = telecomManager.endCall()
                        android.util.Log.d(TAG, "TelecomManager.endCall() returned: $success")
                        if (success) return true
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "TelecomManager.endCall() threw exception", e)
                    }
                }
                
                // If we tried to end call, consume the event even if uncertain of success
                return true
            } else {
                // Not in active call, likely ringing - silence it
                android.util.Log.d(TAG, "Not in call, attempting to silence ringtone")
                
                // Method 1: Send KEYCODE_ENDCALL to reject/silence the call
                try {
                    val eventTime = android.os.SystemClock.uptimeMillis()
                    val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENDCALL, 0)
                    val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENDCALL, 0)
                    
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    audioManager?.dispatchMediaKeyEvent(downEvent)
                    audioManager?.dispatchMediaKeyEvent(upEvent)
                    android.util.Log.d(TAG, "KEYCODE_ENDCALL sent to reject/silence ringing call")
                    return true
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed to send KEYCODE_ENDCALL for ringing", e)
                }
                
                // Method 2: Try to mute ringer volume
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                if (audioManager != null) {
                    try {
                        val currentRingerMode = audioManager.ringerMode
                        android.util.Log.d(TAG, "Current ringer mode: $currentRingerMode")
                        
                        // Check if we have Do Not Disturb permission
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                        val hasDndPermission = notificationManager?.isNotificationPolicyAccessGranted == true
                        android.util.Log.d(TAG, "Has DND permission: $hasDndPermission")
                        
                        if (hasDndPermission && currentRingerMode != android.media.AudioManager.RINGER_MODE_SILENT) {
                            // Mute the ringer
                            audioManager.ringerMode = android.media.AudioManager.RINGER_MODE_SILENT
                            android.util.Log.d(TAG, "Set ringer mode to SILENT")
                            return true
                        } else {
                            // Try alternative method: adjust volume to 0
                            android.util.Log.d(TAG, "Trying volume adjustment")
                            val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_RING)
                            android.util.Log.d(TAG, "Current ring volume: $currentVolume")
                        if (currentVolume > 0) {
                            audioManager.setStreamVolume(
                                android.media.AudioManager.STREAM_RING,
                                0,
                                0
                            )
                            android.util.Log.d(TAG, "Set ring volume to 0")
                            return true
                        }
                    }
                } catch (e: SecurityException) {
                    android.util.Log.e(TAG, "SecurityException silencing ringer", e)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error silencing ringer", e)
                }
            }
        }
        } catch (e: SecurityException) {
            // Permission denied
            android.util.Log.e(TAG, "SecurityException for call control", e)
        } catch (e: Exception) {
            // Other error, fall through
            android.util.Log.e(TAG, "Exception in call control", e)
        }
        // If no call or action failed, return false
        return false
    }
    
    // Original handleKeyDownWithNoEditableField continues here...
    // (This helper function is called from handleKeyDownWithNoEditableField for HOME key handling)
    
    fun handleKeyUpWithNoEditableField(
        keyCode: Int,
        event: KeyEvent?,
        ctrlKeyMap: Map<Int, KeyMappingLoader.CtrlMapping>,
        callbacks: NoEditableFieldCallbacks,
        powerShortcutsEnabled: Boolean = false
    ): Boolean {
        if (navModeController.isNavModeKey(keyCode)) {
            return navModeController.handleNavModeKey(
                keyCode,
                event,
                isKeyDown = false,
                ctrlKeyMap = ctrlKeyMap,
                inputConnectionProvider = callbacks.currentInputConnection
            )
        }
        return callbacks.callSuper()
    }

    data class EditableFieldKeyDownParams(
        val ctrlLatchFromNavMode: Boolean,
        val ctrlLatchActive: Boolean,
        val isInputViewActive: Boolean,
        val isInputViewShown: Boolean,
        val hasInputConnection: Boolean
    )

    data class EditableFieldKeyDownCallbacks(
        val exitNavMode: () -> Unit,
        val ensureInputViewCreated: () -> Unit,
        val callSuper: () -> Boolean
    )

    fun handleEditableFieldKeyDownPrelude(
        keyCode: Int,
        params: EditableFieldKeyDownParams,
        callbacks: EditableFieldKeyDownCallbacks
    ): EditableFieldRoutingResult {
        if (params.ctrlLatchFromNavMode && params.ctrlLatchActive) {
            callbacks.exitNavMode()
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return EditableFieldRoutingResult.CallSuper
        }

        if (params.hasInputConnection && params.isInputViewActive && !params.isInputViewShown) {
            callbacks.ensureInputViewCreated()
        }

        return EditableFieldRoutingResult.Continue
    }

    data class EditableFieldKeyDownHandlingParams(
        val inputConnection: InputConnection?,
        val isNumericField: Boolean,
        val isPasswordField: Boolean,
        val isInputViewActive: Boolean,
        val shiftPressed: Boolean,
        val ctrlPressed: Boolean,
        val altPressed: Boolean,
        val ctrlLatchActive: Boolean,
        val altLatchActive: Boolean,
        val ctrlLatchFromNavMode: Boolean,
        val ctrlKeyMap: Map<Int, KeyMappingLoader.CtrlMapping>,
        val ctrlOneShot: Boolean,
        val altOneShot: Boolean,
        val clearAltOnSpaceEnabled: Boolean,
        val shiftOneShot: Boolean,
        val capsLockEnabled: Boolean,
        val cursorUpdateDelayMs: Long
    )

    data class EditableFieldKeyDownControllers(
        val modifierStateController: ModifierStateController,
        val symLayoutController: SymLayoutController,
        val altSymManager: AltSymManager,
        val variationStateController: VariationStateController
    )

    data class EditableFieldKeyDownHandlingCallbacks(
        val updateStatusBar: () -> Unit,
        val refreshStatusBar: () -> Unit,
        val disableShiftOneShot: () -> Unit,
        val clearAltOneShot: () -> Unit,
        val clearCtrlOneShot: () -> Unit,
        val getCharacterFromLayout: (Int, KeyEvent?, Boolean) -> Char?,
        val isAlphabeticKey: (Int) -> Boolean,
        val callSuper: () -> Boolean,
        val callSuperWithKey: (Int, KeyEvent?) -> Boolean,
        val startSpeechRecognition: () -> Unit,
        val getMapping: (Int) -> LayoutMapping?,
        val handleMultiTapCommit: (Int, LayoutMapping, Boolean, InputConnection?, Boolean) -> Boolean,
        val isLongPressSuppressed: (Int) -> Boolean,
        val showSymbolPicker: () -> Unit
    )
    
    /**
     * Helper function to commit text, automatically converting Arabic-Indic numerals
     * to Western numerals in password fields for compatibility.
     */
    private fun commitTextSafe(
        ic: InputConnection?,
        text: String,
        newCursorPosition: Int,
        isPasswordField: Boolean
    ) {
        val finalText = if (isPasswordField) {
            LayoutMappingRepository.convertArabicNumeralsToWestern(text)
        } else {
            text
        }
        ic?.commitText(finalText, newCursorPosition)
    }

    fun routeEditableFieldKeyDown(
        keyCode: Int,
        event: KeyEvent?,
        params: EditableFieldKeyDownHandlingParams,
        controllers: EditableFieldKeyDownControllers,
        callbacks: EditableFieldKeyDownHandlingCallbacks
    ): EditableFieldRoutingResult {
        var shiftOneShotActive = params.shiftOneShot
        var altLatchActive = params.altLatchActive
        var altOneShotActive = params.altOneShot
        val ic = params.inputConnection

        // Handle KEYCODE_HOME for Q25: Mute ringtone when ringing, end call when active
        if (isQ25Device && keyCode == KeyEvent.KEYCODE_HOME) {
            try {
                // Check READ_PHONE_STATE permission for isInCall
                if (ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.READ_PHONE_STATE
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    android.util.Log.w(TAG, "READ_PHONE_STATE permission not granted (editable field)")
                    // No permission, allow default HOME behavior
                    return EditableFieldRoutingResult.CallSuper
                }
                
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
                if (telecomManager == null) {
                    android.util.Log.w(TAG, "TelecomManager is null (editable field)")
                    return EditableFieldRoutingResult.CallSuper
                }
                
                // Check if in active call first
                val inCall = try {
                    telecomManager.isInCall
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error checking isInCall (editable field)", e)
                    false
                }
                android.util.Log.d(TAG, "HOME key pressed (editable field), isInCall=$inCall")
                
                if (inCall) {
                    // Try multiple methods to end the call
                    android.util.Log.d(TAG, "Attempting to end call (editable field)")
                    
                    // Method 1: Inject KEYCODE_ENDCALL (most reliable for ending calls)
                    try {
                        val eventTime = android.os.SystemClock.uptimeMillis()
                        val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENDCALL, 0)
                        val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENDCALL, 0)
                        
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                        audioManager?.dispatchMediaKeyEvent(downEvent)
                        audioManager?.dispatchMediaKeyEvent(upEvent)
                        android.util.Log.d(TAG, "KEYCODE_ENDCALL injected via AudioManager (editable field)")
                        
                        // Also try via input manager
                        try {
                            val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? android.hardware.input.InputManager
                            inputManager?.let { im ->
                                val injectInputEventMethod = im.javaClass.getMethod(
                                    "injectInputEvent",
                                    android.view.InputEvent::class.java,
                                    Int::class.java
                                )
                                injectInputEventMethod.invoke(im, downEvent, 0)
                                injectInputEventMethod.invoke(im, upEvent, 0)
                                android.util.Log.d(TAG, "KEYCODE_ENDCALL also injected via InputManager (editable field)")
                            }
                        } catch (e: Exception) {
                            android.util.Log.w(TAG, "InputManager injection failed (expected, editable field): ${e.message}")
                        }
                        
                        return EditableFieldRoutingResult.Consume
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Failed to inject KEYCODE_ENDCALL (editable field)", e)
                    }
                    
                    // Method 2: Try TelecomManager.endCall() as fallback
                    if (ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.ANSWER_PHONE_CALLS
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        try {
                            val success = telecomManager.endCall()
                            android.util.Log.d(TAG, "TelecomManager.endCall() returned: $success (editable field)")
                            if (success) return EditableFieldRoutingResult.Consume
                        } catch (e: Exception) {
                            android.util.Log.e(TAG, "TelecomManager.endCall() threw exception (editable field)", e)
                        }
                    }
                    
                    // If we tried to end call, consume the event even if uncertain of success
                    return EditableFieldRoutingResult.Consume
                } else {
                    // Not in active call, likely ringing - silence it
                    android.util.Log.d(TAG, "Not in call, attempting to silence ringtone (editable field)")
                    
                    // Method 1: Send KEYCODE_ENDCALL to reject/silence the call
                    try {
                        val eventTime = android.os.SystemClock.uptimeMillis()
                        val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENDCALL, 0)
                        val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENDCALL, 0)
                        
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                        audioManager?.dispatchMediaKeyEvent(downEvent)
                        audioManager?.dispatchMediaKeyEvent(upEvent)
                        android.util.Log.d(TAG, "KEYCODE_ENDCALL sent to reject/silence ringing call (editable field)")
                        return EditableFieldRoutingResult.Consume
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Failed to send KEYCODE_ENDCALL for ringing (editable field)", e)
                    }
                    
                    // Method 2: Try to mute ringer volume
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    if (audioManager != null) {
                        try {
                            val currentRingerMode = audioManager.ringerMode
                            android.util.Log.d(TAG, "Current ringer mode: $currentRingerMode (editable field)")
                            
                            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                            val hasDndPermission = notificationManager?.isNotificationPolicyAccessGranted == true
                            android.util.Log.d(TAG, "Has DND permission: $hasDndPermission (editable field)")
                            
                            if (hasDndPermission && currentRingerMode != android.media.AudioManager.RINGER_MODE_SILENT) {
                                audioManager.ringerMode = android.media.AudioManager.RINGER_MODE_SILENT
                                android.util.Log.d(TAG, "Set ringer mode to SILENT (editable field)")
                                return EditableFieldRoutingResult.Consume
                            } else {
                                android.util.Log.d(TAG, "Trying volume adjustment (editable field)")
                                val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_RING)
                                android.util.Log.d(TAG, "Current ring volume: $currentVolume (editable field)")
                                if (currentVolume > 0) {
                                    audioManager.setStreamVolume(
                                        android.media.AudioManager.STREAM_RING,
                                        0,
                                        0
                                    )
                                    android.util.Log.d(TAG, "Set ring volume to 0 (editable field)")
                                    return EditableFieldRoutingResult.Consume
                                }
                            }
                        } catch (e: SecurityException) {
                            android.util.Log.e(TAG, "SecurityException silencing ringer (editable field)", e)
                        } catch (e: Exception) {
                            android.util.Log.e(TAG, "Error silencing ringer (editable field)", e)
                        }
                    }
                }
            } catch (e: SecurityException) {
                // Permission denied
                android.util.Log.e(TAG, "SecurityException for call control (editable field)", e)
            } catch (e: Exception) {
                // Other error, fall through
                android.util.Log.e(TAG, "Exception in call control (editable field)", e)
            }
            // If no call or action failed, allow default HOME behavior
            return EditableFieldRoutingResult.CallSuper
        }
        
        // Launch Phone app or answer call with keycode 0 for Q25 device
        if (isQ25Device && keyCode == 0) {
            try {
                // Check if we have permission to answer calls
                if (ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.ANSWER_PHONE_CALLS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
                    if (telecomManager != null) {
                        telecomManager.acceptRingingCall()
                        android.util.Log.d(TAG, "Attempted to answer call with keycode 0 (editable field)")
                        // Don't open dialer after attempting to answer
                        return EditableFieldRoutingResult.Consume
                    } else {
                        android.util.Log.w(TAG, "TelecomManager is null (editable field)")
                    }
                } else {
                    android.util.Log.w(TAG, "ANSWER_PHONE_CALLS permission not granted (editable field)")
                }
            } catch (e: SecurityException) {
                // Permission denied
                android.util.Log.e(TAG, "SecurityException for acceptRingingCall (editable field)", e)
            } catch (e: Exception) {
                // Other error, fall through to open dialer
                android.util.Log.e(TAG, "Exception in acceptRingingCall (editable field)", e)
            }
            
            // If no permission or exception, launch Phone app
            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            return EditableFieldRoutingResult.Consume
        }

        // Check CTRL key BEFORE SHIFT because Q25 uses keycode 60 which is KEYCODE_SHIFT_RIGHT
        if (isCtrlKey(keyCode)) {
            android.util.Log.d("InputEventRouter", "routeEditableFieldKeyDown: CTRL key detected, keyCode=$keyCode")
            // Check if Alt is physically pressed (not latch) - if so, trigger speech recognition (if enabled)
            // Only trigger if both keys are physically pressed simultaneously, not if one is in latch
            if (event?.isAltPressed == true && 
                !params.ctrlPressed &&
                SettingsManager.getAltCtrlSpeechShortcutEnabled(context)) {
                android.util.Log.d("InputEventRouter", "routeEditableFieldKeyDown: Alt+Ctrl speech shortcut triggered")
                callbacks.startSpeechRecognition()
                return EditableFieldRoutingResult.Consume
            }
            
            android.util.Log.d("InputEventRouter", "routeEditableFieldKeyDown: Calling modifierStateController.handleCtrlKeyDown")
            // Always handle Ctrl key to support double-tap and triple-tap
            val result = controllers.modifierStateController.handleCtrlKeyDown(
                keyCode,
                params.isInputViewActive,
                onNavModeDeactivated = {
                    navModeController.cancelNotification()
                }
            )
            
            if (result.shouldConsume) {
                if (result.shouldUpdateStatusBar) {
                    callbacks.updateStatusBar()
                }
                return EditableFieldRoutingResult.Consume
            } else if (result.shouldUpdateStatusBar) {
                callbacks.updateStatusBar()
            }
            // Return CallSuper instead of Consume to allow onKeyUp to be called
            return EditableFieldRoutingResult.CallSuper
        }

        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            if (!params.shiftPressed) {
                val result = controllers.modifierStateController.handleShiftKeyDown(keyCode)
                if (result.shouldUpdateStatusBar) {
                    callbacks.updateStatusBar()
                } else if (result.shouldRefreshStatusBar) {
                    callbacks.refreshStatusBar()
                }
            }
            return EditableFieldRoutingResult.CallSuper
        }

        // Check SYM key BEFORE ALT because Q25 uses keycode 58 which is KEYCODE_ALT_RIGHT
        if (isSymKey(keyCode)) {
            callbacks.showSymbolPicker()
            return EditableFieldRoutingResult.Consume
        }

        if (keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            // Check if Ctrl is physically pressed (not latch) - if so, trigger speech recognition (if enabled)
            // Only trigger if both keys are physically pressed simultaneously, not if one is in latch
            if (event?.isCtrlPressed == true && 
                !params.altPressed &&
                SettingsManager.getAltCtrlSpeechShortcutEnabled(context)) {
                callbacks.startSpeechRecognition()
                return EditableFieldRoutingResult.Consume
            }
            
            if (controllers.symLayoutController.isSymActive()) {
                if (controllers.symLayoutController.closeSymPage()) {
                    callbacks.updateStatusBar()
                }
            }
            if (!params.altPressed) {
                val result = controllers.modifierStateController.handleAltKeyDown(keyCode)
                if (result.shouldUpdateStatusBar) {
                    callbacks.updateStatusBar()
                }
            }
            return EditableFieldRoutingResult.Consume
        }

        if (keyCode == 322) {
            // Disable swipe-to-delete for Q25 device
            if (isQ25Device) {
                return EditableFieldRoutingResult.Consume
            }
            
            val swipeToDeleteEnabled = SettingsManager.getSwipeToDelete(context)
            if (swipeToDeleteEnabled) {
                if (ic != null && TextSelectionHelper.deleteLastWord(ic)) {
                    return EditableFieldRoutingResult.Consume
                }
            } else {
                return EditableFieldRoutingResult.Consume
            }
        }

        if (controllers.altSymManager.hasPendingPress(keyCode)) {
            return EditableFieldRoutingResult.Consume
        }

        if (
            params.clearAltOnSpaceEnabled &&
            (keyCode == KeyEvent.KEYCODE_SPACE || keyCode == KeyEvent.KEYCODE_ENTER) &&
            (altLatchActive || altOneShotActive)
        ) {
            controllers.modifierStateController.clearAltState()
            altLatchActive = false
            altOneShotActive = false
            callbacks.updateStatusBar()
        }

        if (
            handleNumericAndSym(
                keyCode = keyCode,
                event = event,
                inputConnection = ic,
                isNumericField = params.isNumericField,
                altSymManager = controllers.altSymManager,
                symLayoutController = controllers.symLayoutController,
                ctrlLatchActive = params.ctrlLatchActive,
                ctrlOneShot = params.ctrlOneShot,
                altLatchActive = altLatchActive,
                altOneShot = altOneShotActive,
                cursorUpdateDelayMs = params.cursorUpdateDelayMs,
                updateStatusBar = callbacks.updateStatusBar,
                callSuper = callbacks.callSuper
            )
        ) {
            return EditableFieldRoutingResult.Consume
        }

        if (event?.isAltPressed == true || altLatchActive || altOneShotActive) {
            controllers.altSymManager.cancelPendingLongPress(keyCode)
            if (altOneShotActive) {
                callbacks.clearAltOneShot()
                callbacks.refreshStatusBar()
                altOneShotActive = false
            }

            if (keyCode == KeyEvent.KEYCODE_BACK) {
                return EditableFieldRoutingResult.CallSuper
            }

            if (
                handleAltModifiedKey(
                    keyCode = keyCode,
                    event = event,
                    inputConnection = ic,
                    altSymManager = controllers.altSymManager,
                    updateStatusBar = callbacks.updateStatusBar,
                    callSuperWithKey = callbacks.callSuperWithKey,
                    isPasswordField = params.isPasswordField
                )
            ) {
                return EditableFieldRoutingResult.Consume
            }
        }

        // Check if CTRL is active: either physically pressed (event.isCtrlPressed OR params.ctrlPressed), or in latch/one-shot mode
        val isCtrlActive = event?.isCtrlPressed == true || params.ctrlPressed || params.ctrlLatchActive || params.ctrlOneShot
        
        var skipLongPressAndVariations = false
        
        if (isCtrlActive) {
            val handled = handleCtrlModifiedKey(
                keyCode = keyCode,
                event = event,
                inputConnection = ic,
                ctrlKeyMap = params.ctrlKeyMap,
                ctrlLatchFromNavMode = params.ctrlLatchFromNavMode,
                ctrlOneShot = params.ctrlOneShot,
                clearCtrlOneShot = {
                    callbacks.clearCtrlOneShot()
                },
                updateStatusBar = callbacks.updateStatusBar,
                callSuper = callbacks.callSuper
            )
            
            // If handled is true, the mapping was executed
            if (handled) {
                return EditableFieldRoutingResult.Consume
            }
            // If handled is false, one-shot was already cleared in handleCtrlModifiedKey
            // Skip long-press and variations for this key to avoid blocking subsequent input
            skipLongPressAndVariations = true
        }

        val mapping = callbacks.getMapping(keyCode)
        val resolvedUppercase = mapping?.let {
            when {
                shiftOneShotActive -> true
                params.capsLockEnabled && event?.isShiftPressed != true -> true
                event?.isShiftPressed == true -> true
                else -> false
            }
        } ?: false

        // Compute long-press eligibility up front so multi-tap can still schedule it.
        val longPressSuppressed = callbacks.isLongPressSuppressed(keyCode)
        val useShiftForLongPress = SettingsManager.isLongPressShift(context)
        val hasLongPressSupport = if (useShiftForLongPress) {
            !longPressSuppressed && event != null && event.unicodeChar != 0 && event.unicodeChar.toChar().isLetter()
        } else {
            !longPressSuppressed && controllers.altSymManager.hasAltMapping(keyCode)
        }

        // Ignore system-generated repeats on multi-tap keys so holding the key
        // won't churn through tap levels. Legacy keys keep their normal repeat.
        if (mapping?.isRealMultiTap == true && (event?.repeatCount ?: 0) > 0) {
            return EditableFieldRoutingResult.Consume
        }

        // Multi-tap: commit immediately and replace within the timeout window.
        if (mapping?.isRealMultiTap == true && ic != null) {
            if (callbacks.handleMultiTapCommit(keyCode, mapping, resolvedUppercase, ic, hasLongPressSupport)) {
                if (shiftOneShotActive) {
                    callbacks.disableShiftOneShot()
                    shiftOneShotActive = false
                }
                mainHandler.postDelayed({
                    callbacks.updateStatusBar()
                }, params.cursorUpdateDelayMs)
                return EditableFieldRoutingResult.Consume
            }
        }

        if (hasLongPressSupport && !skipLongPressAndVariations) {
            val wasShiftOneShot = shiftOneShotActive
            val layoutChar = callbacks.getCharacterFromLayout(
                keyCode,
                event,
                event?.isShiftPressed == true
            )
            if (ic != null) {
                controllers.altSymManager.handleKeyWithAltMapping(
                    keyCode,
                    event,
                    params.capsLockEnabled,
                    ic,
                    shiftOneShotActive,
                    layoutChar
                )
            }
            if (wasShiftOneShot) {
                callbacks.disableShiftOneShot()
                callbacks.updateStatusBar()
                shiftOneShotActive = false
            }
            return EditableFieldRoutingResult.Consume
        }

        if (shiftOneShotActive) {
            val char = LayoutMappingRepository.getCharacterStringWithModifiers(
                keyCode,
                event?.isShiftPressed == true,
                params.capsLockEnabled,
                true
            )
            if (char.isNotEmpty() && char[0].isLetter()) {
                callbacks.disableShiftOneShot()
                commitTextSafe(ic, char, 1, params.isPasswordField)
                mainHandler.postDelayed({
                    callbacks.updateStatusBar()
                }, params.cursorUpdateDelayMs)
                return EditableFieldRoutingResult.Consume
            }
        }

        if (params.capsLockEnabled && LayoutMappingRepository.isMapped(keyCode)) {
            val char = LayoutMappingRepository.getCharacterStringWithModifiers(
                keyCode,
                event?.isShiftPressed == true,
                params.capsLockEnabled,
                false
            )
            if (char.isNotEmpty() && char[0].isLetter()) {
                commitTextSafe(ic, char, 1, params.isPasswordField)
                mainHandler.postDelayed({
                    callbacks.updateStatusBar()
                }, params.cursorUpdateDelayMs)
                return EditableFieldRoutingResult.Consume
            }
        }

        val charForVariations = if (LayoutMappingRepository.isMapped(keyCode)) {
            LayoutMappingRepository.getCharacterWithModifiers(
                keyCode,
                event?.isShiftPressed == true,
                params.capsLockEnabled,
                shiftOneShotActive
            )
        } else {
            callbacks.getCharacterFromLayout(keyCode, event, event?.isShiftPressed == true)
        }
        if (charForVariations != null && !skipLongPressAndVariations) {
            if (controllers.variationStateController.hasVariationsFor(charForVariations)) {
                commitTextSafe(ic, charForVariations.toString(), 1, params.isPasswordField)
                mainHandler.postDelayed({
                    callbacks.updateStatusBar()
                }, params.cursorUpdateDelayMs)
                return EditableFieldRoutingResult.Consume
            }
        }

        val isAlphabeticKey = callbacks.isAlphabeticKey(keyCode)
        if (isAlphabeticKey && LayoutMappingRepository.isMapped(keyCode)) {
            val char = LayoutMappingRepository.getCharacterStringWithModifiers(
                keyCode,
                event?.isShiftPressed == true,
                params.capsLockEnabled,
                shiftOneShotActive
            )
            if (char.isNotEmpty() && char[0].isLetter()) {
                commitTextSafe(ic, char, 1, params.isPasswordField)
                mainHandler.postDelayed({
                    callbacks.updateStatusBar()
                }, params.cursorUpdateDelayMs)
                return EditableFieldRoutingResult.Consume
            }
        }

        return EditableFieldRoutingResult.CallSuper
    }

    fun handleTextInputPipeline(
        keyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        isAutoCorrectEnabled: Boolean,
        textInputController: TextInputController,
        autoCorrectionManager: AutoCorrectionManager,
        updateStatusBar: () -> Unit
    ): Boolean {
        if (
            autoCorrectionManager.handleBackspaceUndo(
                keyCode,
                inputConnection,
                isAutoCorrectEnabled,
                onStatusBarUpdate = updateStatusBar
            )
        ) {
            return true
        }

        if (
            textInputController.handleDoubleSpaceToPeriod(
                keyCode,
                inputConnection,
                shouldDisableSmartFeatures,
                onStatusBarUpdate = updateStatusBar
            )
        ) {
            return true
        }

        textInputController.handleAutoCapAfterPeriod(
            keyCode,
            inputConnection,
            shouldDisableSmartFeatures,
            onStatusBarUpdate = updateStatusBar
        )

        textInputController.handleAutoCapAfterEnter(
            keyCode,
            inputConnection,
            shouldDisableSmartFeatures,
            onStatusBarUpdate = updateStatusBar
        )

        if (
            autoCorrectionManager.handleSpaceOrPunctuation(
                keyCode,
                event,
                inputConnection,
                isAutoCorrectEnabled,
                onStatusBarUpdate = updateStatusBar
            )
        ) {
            return true
        }

        autoCorrectionManager.handleAcceptOrResetOnOtherKeys(
            keyCode,
            event,
            isAutoCorrectEnabled
        )
        return false
    }

    fun handleNumericAndSym(
        keyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection?,
        isNumericField: Boolean,
        altSymManager: AltSymManager,
        symLayoutController: SymLayoutController,
        ctrlLatchActive: Boolean,
        ctrlOneShot: Boolean,
        altLatchActive: Boolean,
        altOneShot: Boolean,
        cursorUpdateDelayMs: Long,
        updateStatusBar: () -> Unit,
        callSuper: () -> Boolean
    ): Boolean {
        val ic = inputConnection ?: return false

        // Numeric fields always use the Alt mapping for every key press (short press included).
        if (isNumericField) {
            val altChar = altSymManager.getAltMappings()[keyCode]
            if (altChar != null) {
                ic.commitText(altChar, 1)
                mainHandler.postDelayed({
                    updateStatusBar()
                }, cursorUpdateDelayMs)
                return true
            }
        }

        // Handle keycode 7 for Q25 device based on user preference
        if (isQ25Device && keyCode == 7) {
            val behavior = SettingsManager.getKeycode7Behavior(context)
            val isAltActive = altLatchActive || altOneShot || event?.isAltPressed == true
            
            when (behavior) {
                "zero" -> {
                    // Insert 0 on keycode 7, Alt+7 triggers speech
                    if (!isAltActive) {
                        ic?.commitText("0", 1)
                        updateStatusBar()
                        return true
                    }
                    // Alt+7 will be handled in handleAltModifiedKey
                }
                "alt_zero" -> {
                    // Trigger speech on keycode 7, Alt+7 inserts 0
                    if (!isAltActive) {
                        (context as? it.srik.TypeQ25.inputmethod.PhysicalKeyboardInputMethodService)?.let { service ->
                            service.startSpeechRecognitionFromRouter()
                        }
                        return true
                    }
                    // Alt+7 will be handled in handleAltModifiedKey
                }
            }
        }

        // If SYM is active, check SYM mappings first (they take precedence over Alt and Ctrl)
        // When SYM is active, all other modifiers are bypassed
        val shouldBypassSymForCtrl = event?.isCtrlPressed == true || ctrlLatchActive || ctrlOneShot
        if (!shouldBypassSymForCtrl && symLayoutController.isSymActive()) {
            return when (
                symLayoutController.handleKeyWhenActive(
                    keyCode,
                    event,
                    ic,
                    ctrlLatchActive = ctrlLatchActive,
                    altLatchActive = altLatchActive,
                    updateStatusBar = updateStatusBar
                )
            ) {
                SymKeyResult.CONSUME -> true
                SymKeyResult.CALL_SUPER -> callSuper()
                SymKeyResult.NOT_HANDLED -> false
            }
        }

        return false
    }

    /**
     * Handles Alt-modified key presses once Alt is considered active
     * (physical Alt, latch or one-shot). The caller is responsible for
     * managing Alt latch/one-shot state.
     */
    fun handleAltModifiedKey(
        keyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection?,
        altSymManager: AltSymManager,
        updateStatusBar: () -> Unit,
        callSuperWithKey: (Int, KeyEvent?) -> Boolean,
        isPasswordField: Boolean = false
    ): Boolean {
        val ic = inputConnection ?: return false

        // Handle Alt+keycode 7 for Q25 device based on user preference
        if (isQ25Device && keyCode == 7) {
            val behavior = SettingsManager.getKeycode7Behavior(context)
            
            when (behavior) {
                "zero" -> {
                    // Alt+7 triggers speech-to-text (plain 7 inserts 0)
                    (context as? it.srik.TypeQ25.inputmethod.PhysicalKeyboardInputMethodService)?.let { service ->
                        service.startSpeechRecognitionFromRouter()
                    }
                    return true
                }
                "alt_zero" -> {
                    // Alt+7 inserts 0 (or ٠ for Arabic with numerals enabled, but always 0 in passwords)
                    val zero = if (!isPasswordField) {
                        val currentLayout = SettingsManager.getKeyboardLayout(context)
                        if (currentLayout.startsWith("arabic", ignoreCase = true) && 
                            SettingsManager.getUseArabicNumerals(context)) {
                            "٠"
                        } else {
                            "0"
                        }
                    } else {
                        "0"
                    }
                    ic.commitText(zero, 1)
                    updateStatusBar()
                    return true
                }
            }
        }

        // Consume Alt+Space to avoid Android's symbol picker and just insert a space.
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            // Record the word before space for suggestions
            recordWordBeforeSpace(ic)
            
            ic.commitText(" ", 1)
            updateStatusBar()
            return true
        }

        val result = altSymManager.handleAltCombination(
            keyCode,
            ic,
            event,
            { defaultKeyCode, defaultEvent ->
                // Fallback: delegate to caller (typically super.onKeyDown)
                callSuperWithKey(defaultKeyCode, defaultEvent)
            },
            isPasswordField
        )

        if (result) {
            updateStatusBar()
        }
        return result
    }
    
    private fun recordWordBeforeSpace(ic: android.view.inputmethod.InputConnection) {
        try {
            val textBefore = ic.getTextBeforeCursor(100, 0) ?: return
            
            // Find the last word
            var startIndex = textBefore.length - 1
            while (startIndex >= 0) {
                val char = textBefore[startIndex]
                if (char.isWhitespace() || char in ".,;:!?\"'()[]{}/<>@#\$%^&*+=|\\") {
                    break
                }
                startIndex--
            }
            
            val lastWord = textBefore.substring(startIndex + 1)
            if (lastWord.isNotEmpty() && lastWord.length >= 2) {
                (context as? it.srik.TypeQ25.inputmethod.PhysicalKeyboardInputMethodService)?.let { svc ->
                    try {
                        svc.suggestionEngine.recordWord(lastWord)
                    } catch (e: Exception) {
                        // Silently ignore errors
                    }
                }
            }
        } catch (e: Exception) {
            // Silently ignore errors
        }
    }

    /**
     * Handles Ctrl-modified shortcuts in editable fields (copy/paste/cut/undo/select_all,
     * expand selection, DPAD/TAB/PAGE/ESC mappings and Ctrl+Backspace behaviour).
     * The caller is responsible for setting/clearing Ctrl latch and one-shot flags.
     */
    fun handleCtrlModifiedKey(
        keyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection?,
        ctrlKeyMap: Map<Int, KeyMappingLoader.CtrlMapping>,
        ctrlLatchFromNavMode: Boolean,
        ctrlOneShot: Boolean,
        clearCtrlOneShot: () -> Unit,
        updateStatusBar: () -> Unit,
        callSuper: () -> Boolean
    ): Boolean {
        val ic = inputConnection ?: return false

        val ctrlMapping = ctrlKeyMap[keyCode]
        
        // If no mapping exists and one-shot is active, clear one-shot and return false
        // to allow normal key processing
        if (ctrlMapping == null && ctrlOneShot && !ctrlLatchFromNavMode) {
            clearCtrlOneShot()
            updateStatusBar()
            return false
        }
        
        // If mapping exists and one-shot is active, clear one-shot before executing the action
        if (ctrlOneShot && !ctrlLatchFromNavMode) {
            clearCtrlOneShot()
            updateStatusBar()
        }

        if (ctrlMapping != null) {
            when (ctrlMapping.type) {
                "action" -> {
                    when (ctrlMapping.value) {
                        "expand_selection_left" -> {
                            KeyboardEventTracker.notifyKeyEvent(
                                keyCode,
                                event,
                                "KEY_DOWN",
                                outputKeyCode = null,
                                outputKeyCodeName = "expand_selection_left"
                            )
                            TextSelectionHelper.expandSelectionLeft(ic)
                            return true
                        }
                        "expand_selection_right" -> {
                            KeyboardEventTracker.notifyKeyEvent(
                                keyCode,
                                event,
                                "KEY_DOWN",
                                outputKeyCode = null,
                                outputKeyCodeName = "expand_selection_right"
                            )
                            TextSelectionHelper.expandSelectionRight(ic)
                            return true
                        }
                        else -> {
                            val actionId = when (ctrlMapping.value) {
                                "copy" -> android.R.id.copy
                                "paste" -> android.R.id.paste
                                "cut" -> android.R.id.cut
                                "undo" -> android.R.id.undo
                                "select_all" -> android.R.id.selectAll
                                else -> null
                            }
                            if (actionId != null) {
                                KeyboardEventTracker.notifyKeyEvent(
                                    keyCode,
                                    event,
                                    "KEY_DOWN",
                                    outputKeyCode = null,
                                    outputKeyCodeName = ctrlMapping.value
                                )
                                ic.performContextMenuAction(actionId)
                                return true
                            }
                            return true
                        }
                    }
                }
                "keycode" -> {
                    val mappedKeyCode = when (ctrlMapping.value) {
                        "DPAD_UP" -> KeyEvent.KEYCODE_DPAD_UP
                        "DPAD_DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
                        "DPAD_LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
                        "DPAD_RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
                        "DPAD_CENTER" -> KeyEvent.KEYCODE_DPAD_CENTER
                        "TAB" -> KeyEvent.KEYCODE_TAB
                        "PAGE_UP" -> KeyEvent.KEYCODE_PAGE_UP
                        "PAGE_DOWN" -> KeyEvent.KEYCODE_PAGE_DOWN
                        "ESCAPE" -> KeyEvent.KEYCODE_ESCAPE
                        else -> null
                    }
                    if (mappedKeyCode != null) {
                        KeyboardEventTracker.notifyKeyEvent(
                            keyCode,
                            event,
                            "KEY_DOWN",
                            outputKeyCode = mappedKeyCode,
                            outputKeyCodeName = KeyboardEventTracker.getOutputKeyCodeName(mappedKeyCode)
                        )
                        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, mappedKeyCode))
                        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, mappedKeyCode))

                        if (mappedKeyCode in listOf(
                                KeyEvent.KEYCODE_DPAD_UP,
                                KeyEvent.KEYCODE_DPAD_DOWN,
                                KeyEvent.KEYCODE_DPAD_LEFT,
                                KeyEvent.KEYCODE_DPAD_RIGHT,
                                KeyEvent.KEYCODE_PAGE_UP,
                                KeyEvent.KEYCODE_PAGE_DOWN
                            )
                        ) {
                            mainHandler.postDelayed({
                                updateStatusBar()
                            }, 50)
                        }

                        return true
                    }
                    return true
                }
            }
        } else {
            if (keyCode == KeyEvent.KEYCODE_DEL) {
                val extractedText: ExtractedText? = ic.getExtractedText(
                    ExtractedTextRequest().apply {
                        flags = ExtractedText.FLAG_SELECTING
                    },
                    0
                )

                val hasSelection = extractedText?.let {
                    it.selectionStart >= 0 && it.selectionEnd >= 0 && it.selectionStart != it.selectionEnd
                } ?: false

                if (hasSelection) {
                    KeyboardEventTracker.notifyKeyEvent(
                        keyCode,
                        event,
                        "KEY_DOWN",
                        outputKeyCode = null,
                        outputKeyCodeName = "delete_selection"
                    )
                    ic.commitText("", 0)
                    return true
                } else {
                    KeyboardEventTracker.notifyKeyEvent(
                        keyCode,
                        event,
                        "KEY_DOWN",
                        outputKeyCode = null,
                        outputKeyCodeName = "delete_last_word"
                    )
                    TextSelectionHelper.deleteLastWord(ic)
                    return true
                }
            }

            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_BACK) {
                return callSuper()
            }

            return true
        }

        return false
    }
}
