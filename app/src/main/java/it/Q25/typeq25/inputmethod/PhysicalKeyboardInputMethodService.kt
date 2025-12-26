package it.srik.TypeQ25.inputmethod

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import it.srik.TypeQ25.SettingsManager
import android.inputmethodservice.InputMethodService
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import it.srik.TypeQ25.inputmethod.KeyboardEventTracker
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import android.view.MotionEvent
import android.view.View
import it.srik.TypeQ25.CurrencyManager
import it.srik.TypeQ25.DeviceManager
import android.media.AudioManager
import it.srik.TypeQ25.core.AutoCorrectionManager
import it.srik.TypeQ25.core.InputContextState
import it.srik.TypeQ25.core.ModifierStateController
import it.srik.TypeQ25.core.NavModeController
import it.srik.TypeQ25.core.SymLayoutController
import it.srik.TypeQ25.core.TextInputController
import it.srik.TypeQ25.data.layout.LayoutMappingRepository
import it.srik.TypeQ25.data.layout.LayoutFileStore
import it.srik.TypeQ25.data.layout.LayoutMapping
import it.srik.TypeQ25.data.mappings.KeyMappingLoader
import it.srik.TypeQ25.data.variation.VariationRepository
import it.srik.TypeQ25.inputmethod.SpeechRecognitionActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import android.text.SpannableString
import android.text.Spanned
import android.text.style.UnderlineSpan
import android.text.style.ForegroundColorSpan
import android.graphics.Color

/**
 * Input method service specialized for physical keyboards.
 * Handles advanced features such as long press that simulates Alt+key.
 */
class PhysicalKeyboardInputMethodService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    companion object {
        private const val TAG = "TypeQ25InputMethod"
        
        // Instance reference for direct emoji commit access
        @Volatile
        private var instance: PhysicalKeyboardInputMethodService? = null
        
        fun getInstance(): PhysicalKeyboardInputMethodService? = instance
    }

    // Lifecycle components for Compose support
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    // Reusable handler to avoid creating new instances
    private val mainHandler = Handler(Looper.getMainLooper())

    // SharedPreferences for settings
    private lateinit var prefs: SharedPreferences
    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    private lateinit var altSymManager: AltSymManager
    
    // Broadcast receiver for speech recognition
    private var speechResultReceiver: BroadcastReceiver? = null
    private lateinit var candidatesBarController: CandidatesBarController

    // Symbol picker popup
    private var symbolPickerPopup: it.srik.TypeQ25.inputmethod.ui.SymbolPickerPopup? = null
    
    // Clipboard history
    private lateinit var clipboardHistoryManager: it.srik.TypeQ25.clipboard.ClipboardHistoryManager
    private var clipboardHistoryPopup: it.srik.TypeQ25.inputmethod.ui.ClipboardHistoryPopup? = null
    
    // Emoji shortcode support
    private lateinit var emojiShortcodeManager: it.srik.TypeQ25.emoji.EmojiShortcodeManager
    private var emojiShortcodePopup: it.srik.TypeQ25.inputmethod.ui.EmojiShortcodePopup? = null

    // Keycode for the SYM key
    private val KEYCODE_SYM = 63
    
    // Cached device type to avoid repeated DeviceManager.getDevice() calls
    private val deviceType: String by lazy { DeviceManager.getDevice(this) }

    // Flashlight toggle via double-tap SYM
    private var lastSymPressTime: Long = 0
    private val symDoubleTapThreshold: Long = 300 // ms
    private var isFlashlightOn = false
    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null

    // Single instance to show layout switch toasts without overlapping
    private var layoutSwitchToast: android.widget.Toast? = null
    private var lastLayoutToastText: String? = null
    private var lastLayoutToastTime: Long = 0
    private var suppressNextLayoutReload: Boolean = false
    
    // Power Shortcuts toast
    private var powerShortcutToast: android.widget.Toast? = null
    
    // Mapping Ctrl+key -> action or keycode (loaded from JSON)
    private val ctrlKeyMap = mutableMapOf<Int, KeyMappingLoader.CtrlMapping>()
    
    // Accessor properties for backwards compatibility with existing code
    private var capsLockEnabled: Boolean
        get() = modifierStateController.capsLockEnabled
        set(value) { modifierStateController.capsLockEnabled = value }
    
    private var shiftPressed: Boolean
        get() = modifierStateController.shiftPressed
        set(value) { modifierStateController.shiftPressed = value }
    
    private var ctrlLatchActive: Boolean
        get() = modifierStateController.ctrlLatchActive
        set(value) { modifierStateController.ctrlLatchActive = value }
    
    private var altLatchActive: Boolean
        get() = modifierStateController.altLatchActive
        set(value) { modifierStateController.altLatchActive = value }
    
    private var ctrlPressed: Boolean
        get() = modifierStateController.ctrlPressed
        set(value) { modifierStateController.ctrlPressed = value }
    
    private var altPressed: Boolean
        get() = modifierStateController.altPressed
        set(value) { modifierStateController.altPressed = value }
    
    private var shiftPhysicallyPressed: Boolean
        get() = modifierStateController.shiftPhysicallyPressed
        set(value) { modifierStateController.shiftPhysicallyPressed = value }
    
    private var ctrlPhysicallyPressed: Boolean
        get() = modifierStateController.ctrlPhysicallyPressed
        set(value) { modifierStateController.ctrlPhysicallyPressed = value }
    
    private var altPhysicallyPressed: Boolean
        get() = modifierStateController.altPhysicallyPressed
        set(value) { modifierStateController.altPhysicallyPressed = value }
    
    private var shiftOneShot: Boolean
        get() = modifierStateController.shiftOneShot
        set(value) { modifierStateController.shiftOneShot = value }

    private var ctrlOneShot: Boolean
        get() = modifierStateController.ctrlOneShot
        set(value) { modifierStateController.ctrlOneShot = value }
    
    private var altOneShot: Boolean
        get() = modifierStateController.altOneShot
        set(value) { modifierStateController.altOneShot = value }
    
    private var ctrlLatchFromNavMode: Boolean
        get() = modifierStateController.ctrlLatchFromNavMode
        set(value) { modifierStateController.ctrlLatchFromNavMode = value }
    
    // Flag to track whether we are in a valid input context
    private var isInputViewActive = false
    
    // Snapshot of the current input context (numeric/password/restricted fields, etc.)
    private var inputContextState: InputContextState = InputContextState.EMPTY
    
    private val isNumericField: Boolean
        get() = inputContextState.isNumericField
    
    private val shouldDisableSmartFeatures: Boolean
        get() = inputContextState.shouldDisableSmartFeatures
    
    // Current package name
    private var currentPackageName: String? = null
    // Last external app package (not our keyboard)

    
    // Constants
    private val DOUBLE_TAP_THRESHOLD = 500L
    private val CURSOR_UPDATE_DELAY = 50L
    private val MULTI_TAP_TIMEOUT_MS = 400L

    // Modifier/nav/SYM controllers
    private lateinit var modifierStateController: ModifierStateController
    private lateinit var navModeController: NavModeController
    private lateinit var symLayoutController: SymLayoutController
    private lateinit var textInputController: TextInputController
    private lateinit var autoCorrectionManager: AutoCorrectionManager
    private lateinit var variationStateController: VariationStateController
    private lateinit var inputEventRouter: InputEventRouter
    private lateinit var keyboardVisibilityController: KeyboardVisibilityController
    private lateinit var launcherShortcutController: LauncherShortcutController
    private lateinit var modifierOverlay: it.srik.TypeQ25.inputmethod.ui.ModifierOverlay
    internal lateinit var suggestionEngine: SuggestionEngine
    private var clearAltOnSpaceEnabled: Boolean = false
    
    // Cached terminal app check to avoid repeated package name lookups
    private val isTerminalApp: Boolean
        get() = currentPackageName?.let { pkg ->
            pkg.contains("termux", ignoreCase = true) ||
            pkg.contains("terminal", ignoreCase = true) ||
            pkg.contains("console", ignoreCase = true)
        } ?: false

    // Space long-press for layout cycling
    private val spaceLongPressHandler = Handler(Looper.getMainLooper())
    private var spaceLongPressRunnable: Runnable? = null
    private var spaceLongPressTriggered: Boolean = false
    
    // Cursor update callback for memory leak prevention
    private var cursorUpdateJob: Runnable? = null

    private val multiTapHandler = Handler(Looper.getMainLooper())
    private val multiTapController = MultiTapController(
        handler = multiTapHandler,
        timeoutMs = MULTI_TAP_TIMEOUT_MS
    )
    private val uiHandler = Handler(Looper.getMainLooper())

    private val motionEventController = MotionEventController(logTag = TAG)

    private val symPage: Int
        get() = if (::symLayoutController.isInitialized) symLayoutController.currentSymPage() else 0

    private fun updateInputContextState(info: EditorInfo?) {
        inputContextState = InputContextState.fromEditorInfo(info)
    }

    private fun refreshStatusBar() {
        updateStatusBarText()
    }
    
    private fun startSpeechRecognition() {
        try {
            val intent = Intent(this, SpeechRecognitionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_HISTORY or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }
            startActivity(intent)
            Log.d(TAG, "Speech recognition started via Alt+Ctrl shortcut")
        } catch (e: Exception) {
            Log.e(TAG, "Unable to launch speech recognition", e)
        }
    }
    
    /**
     * Public method to start speech recognition from InputEventRouter
     */
    fun startSpeechRecognitionFromRouter() {
        startSpeechRecognition()
    }


    /**
     * Initializes the input context for a field.
     * This method contains all common initialization logic that must run
     * regardless of whether input view or candidates view is shown.
     */
    private fun initializeInputContext(restarting: Boolean) {
        if (restarting) {
            return
        }
        
        val state = inputContextState
        val isEditable = state.isEditable
        val isReallyEditable = state.isReallyEditable
        val canCheckAutoCapitalize = isEditable && !state.shouldDisableSmartFeatures
        
        if (!isReallyEditable) {
            isInputViewActive = false
            
            if (canCheckAutoCapitalize) {
                AutoCapitalizeHelper.checkAndEnableAutoCapitalize(
                    this,
                    currentInputConnection,
                    shouldDisableSmartFeatures,
                    enableShift = { modifierStateController.requestShiftOneShotFromAutoCap() },
                    disableShift = { modifierStateController.consumeShiftOneShot() },
                    onUpdateStatusBar = { updateStatusBarText() }
                )
            }
            return
        }
        
        isInputViewActive = true
        
        enforceSmartFeatureDisabledState()
        
        if (ctrlLatchFromNavMode && ctrlLatchActive) {
            val inputConnection = currentInputConnection
            if (inputConnection != null) {
                navModeController.exitNavMode()
            }
        }
        
        AutoCapitalizeHelper.checkAndEnableAutoCapitalize(
            this,
            currentInputConnection,
            shouldDisableSmartFeatures,
            enableShift = { modifierStateController.requestShiftOneShotFromAutoCap() },
            disableShift = { modifierStateController.consumeShiftOneShot() },
            onUpdateStatusBar = { updateStatusBarText() }
        )
        
        symLayoutController.restoreSymPageIfNeeded { updateStatusBarText() }
        
        altSymManager.reloadLongPressThreshold()
        altSymManager.resetTransientState()
    }
    
    private fun enforceSmartFeatureDisabledState() {
        if (!shouldDisableSmartFeatures) {
            return
        }
        setCandidatesViewShown(false)
        deactivateVariations()
    }
    
    /**
     * Reloads nav mode key mappings from the file.
     */
    private fun loadKeyboardLayout() {
        val layoutName = SettingsManager.getKeyboardLayout(this)
        val layout = LayoutMappingRepository.loadLayout(assets, layoutName, this)
        Log.d(TAG, "Keyboard layout loaded: $layoutName")
    }
    
    /**
     * Public method to reload the keyboard layout.
     * Used when settings change (e.g., Arabic numerals toggle).
     */
    fun reloadKeyboardLayout() {
        loadKeyboardLayout()
        // Also reload Alt key mappings as they may contain numbers
        altSymManager.reloadAltMappings()
    }
    
    /**
     * Gets the character from the selected keyboard layout for a given keyCode and shift state.
     * If the keyCode is mapped in the layout, returns that character.
     * Otherwise, returns the character from the event (if available).
     * This ensures that keyboard layouts work correctly regardless of Android's system layout settings.
     */
    private fun getCharacterFromLayout(keyCode: Int, event: KeyEvent?, isShift: Boolean): Char? {
        // First, try to get the character from the selected layout
        val layoutChar = LayoutMappingRepository.getCharacter(keyCode, isShift)
        if (layoutChar != null) {
            return layoutChar
        }
        // If not mapped in layout, fall back to event's unicode character
        if (event != null && event.unicodeChar != 0) {
            return event.unicodeChar.toChar()
        }
        return null
    }
    
    /**
     * Gets the character string from the selected keyboard layout.
     * Returns the original event character if not mapped in layout.
     */
    private fun getCharacterStringFromLayout(keyCode: Int, event: KeyEvent?, isShift: Boolean): String {
        val char = getCharacterFromLayout(keyCode, event, isShift)
        return char?.toString() ?: ""
    }

    private fun switchToLayout(layoutName: String, showToast: Boolean) {
        LayoutMappingRepository.loadLayout(assets, layoutName, this)
        if (showToast) {
            val metadata = try {
                LayoutFileStore.getLayoutMetadataFromAssets(assets, layoutName)
                    ?: LayoutFileStore.getLayoutMetadata(this, layoutName)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting layout metadata for toast", e)
                null
            }
            val displayName = metadata?.name ?: layoutName
            showLayoutSwitchToast(displayName)
        }
        updateStatusBarText()
    }

    private fun cycleLayoutFromShortcut() {
        suppressNextLayoutReload = true
        val nextLayout = SettingsManager.cycleKeyboardLayout(this)
        if (nextLayout != null) {
            switchToLayout(nextLayout, showToast = true)
        }
    }

    private fun showLayoutSwitchToast(displayName: String) {
        uiHandler.post {
            val now = System.currentTimeMillis()
            // Avoid spamming identical toasts and keep a minimum gap to satisfy system quota.
            val sameText = lastLayoutToastText == displayName
            val sinceLast = now - lastLayoutToastTime
            if (sinceLast < 1000 || (sameText && sinceLast < 4000)) {
                return@post
            }

            lastLayoutToastText = displayName
            lastLayoutToastTime = now
            layoutSwitchToast?.cancel()
            layoutSwitchToast = android.widget.Toast.makeText(
                applicationContext,
                displayName,
                android.widget.Toast.LENGTH_SHORT
            )
            layoutSwitchToast?.show()
        }
    }
    
    private fun showPowerShortcutToast(message: String) {
        uiHandler.post {
            val now = System.currentTimeMillis()
            val sameText = lastLayoutToastText == message
            val sinceLast = now - lastLayoutToastTime
            
            if (!sameText || sinceLast > 1000) {
                lastLayoutToastText = message
                lastLayoutToastTime = now
                powerShortcutToast?.cancel()
                powerShortcutToast = android.widget.Toast.makeText(
                    applicationContext,
                    message,
                    android.widget.Toast.LENGTH_SHORT
                )
                powerShortcutToast?.show()
            }
        }
    }

    private fun cancelSpaceLongPress() {
        spaceLongPressRunnable?.let { spaceLongPressHandler.removeCallbacks(it) }
        spaceLongPressRunnable = null
        spaceLongPressTriggered = false
    }

    private fun scheduleSpaceLongPress() {
        if (spaceLongPressRunnable != null) {
            return
        }
        spaceLongPressTriggered = false
        val threshold = SettingsManager.getLongPressThreshold(this)
        val runnable = Runnable {
            spaceLongPressRunnable = null

            // Clear Alt if active so layout switching does not leave Alt latched.
            val hadAlt = altLatchActive || altOneShot || altPressed
            if (hadAlt) {
                modifierStateController.clearAltState()
                altLatchActive = false
                altOneShot = false
                altPressed = false
                updateStatusBarText()
            }

            cycleLayoutFromShortcut()
            spaceLongPressTriggered = true
        }
        spaceLongPressRunnable = runnable
        spaceLongPressHandler.postDelayed(runnable, threshold)
    }

    private fun handleMultiTapCommit(
        keyCode: Int,
        mapping: LayoutMapping,
        useUppercase: Boolean,
        inputConnection: InputConnection?,
        allowLongPress: Boolean
    ): Boolean {
        val ic = inputConnection ?: return false
        val handled = multiTapController.handleTap(
            keyCode, 
            mapping, 
            useUppercase, 
            ic,
            inputContextState.isPasswordField
        )
        if (handled && allowLongPress) {
            val committedText = LayoutMappingRepository.resolveText(
                mapping,
                multiTapController.state.useUppercase,
                multiTapController.state.tapIndex
            )
            if (!committedText.isNullOrEmpty()) {
                altSymManager.scheduleLongPressOnly(keyCode, ic, committedText)
            }
        }
        return handled
    }
    
    private fun reloadNavModeMappings() {
        try {
            ctrlKeyMap.clear()
            val assets = assets
            ctrlKeyMap.putAll(KeyMappingLoader.loadCtrlKeyMappings(assets, this))
            Log.d(TAG, "Nav mode mappings reloaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error reloading nav mode mappings", e)
        }
    }
    
    /**
     * Checks if a keycode corresponds to an alphabetic key (A-Z).
     * Returns true only for alphabetic keys, false for all others (modifiers, volume, etc.).
     */
    private fun isAlphabeticKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_B,
            KeyEvent.KEYCODE_C,
            KeyEvent.KEYCODE_D,
            KeyEvent.KEYCODE_E,
            KeyEvent.KEYCODE_F,
            KeyEvent.KEYCODE_G,
            KeyEvent.KEYCODE_H,
            KeyEvent.KEYCODE_I,
            KeyEvent.KEYCODE_J,
            KeyEvent.KEYCODE_K,
            KeyEvent.KEYCODE_L,
            KeyEvent.KEYCODE_M,
            KeyEvent.KEYCODE_N,
            KeyEvent.KEYCODE_O,
            KeyEvent.KEYCODE_P,
            KeyEvent.KEYCODE_Q,
            KeyEvent.KEYCODE_R,
            KeyEvent.KEYCODE_S,
            KeyEvent.KEYCODE_T,
            KeyEvent.KEYCODE_U,
            KeyEvent.KEYCODE_V,
            KeyEvent.KEYCODE_W,
            KeyEvent.KEYCODE_X,
            KeyEvent.KEYCODE_Y,
            KeyEvent.KEYCODE_Z -> true
            else -> false
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Set instance for direct access from other components
        instance = this
        
        // Initialize lifecycle components for Compose support
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        
        // Use device-protected storage for Direct Boot support (Android N+)
        // This allows the IME to remain active even before device unlock
        prefs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val deviceContext = createDeviceProtectedStorageContext()
                deviceContext.getSharedPreferences("TypeQ25_prefs", Context.MODE_PRIVATE)
            } catch (e: Exception) {
                Log.e("PhysicalKeyboardIME", "Failed to create device protected storage, using default", e)
                getSharedPreferences("TypeQ25_prefs", Context.MODE_PRIVATE)
            }
        } else {
            getSharedPreferences("TypeQ25_prefs", Context.MODE_PRIVATE)
        }
        clearAltOnSpaceEnabled = SettingsManager.getClearAltOnSpace(this)
        
        NotificationHelper.createNotificationChannel(this)
        
        modifierStateController = ModifierStateController(DOUBLE_TAP_THRESHOLD, ::isCtrlKey)
        navModeController = NavModeController(this, modifierStateController)
        inputEventRouter = InputEventRouter(this, navModeController)
        textInputController = TextInputController(
            context = this,
            modifierStateController = modifierStateController,
            doubleTapThreshold = DOUBLE_TAP_THRESHOLD
        )
        autoCorrectionManager = AutoCorrectionManager(this)
        suggestionEngine = SuggestionEngine(this)
        
        modifierOverlay = it.srik.TypeQ25.inputmethod.ui.ModifierOverlay(this)
        
        candidatesBarController = CandidatesBarController(this)

        // Initialize camera manager for flashlight
        try {
            cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            cameraId = cameraManager?.cameraIdList?.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize camera for flashlight", e)
        }

        // Register listener for variation selection (both controllers)
        val variationListener = object : VariationButtonHandler.OnVariationSelectedListener {
            override fun onVariationSelected(variation: String) {
                // Update variations after one has been selected (refresh view if needed)
                updateStatusBarText()
            }
        }
        candidatesBarController.onVariationSelectedListener = variationListener

        // Register listener for cursor movement (both controllers)
        val cursorListener = {
            updateStatusBarText()
        }
        candidatesBarController.onCursorMovedListener = cursorListener
        altSymManager = AltSymManager(assets, prefs, this)
        altSymManager.reloadSymMappings() // Load custom mappings for page 1 if present
        altSymManager.reloadSymMappings2() // Load custom mappings for page 2 if present
        
        // Initialize clipboard history manager
        clipboardHistoryManager = it.srik.TypeQ25.clipboard.ClipboardHistoryManager(this)
        clipboardHistoryManager.startMonitoring()
        
        // Initialize emoji shortcode manager
        emojiShortcodeManager = it.srik.TypeQ25.emoji.EmojiShortcodeManager(this)
        Log.d(TAG, "Emoji shortcode manager initialized with ${emojiShortcodeManager.getShortcodeCount()} shortcodes")
        // Register callback to be notified when an Alt character is inserted after long press.
        // Variations are updated automatically by updateStatusBarText().
        altSymManager.onAltCharInserted = { char ->
            updateStatusBarText()
        }
        symLayoutController = SymLayoutController(this, prefs, altSymManager)
        keyboardVisibilityController = KeyboardVisibilityController(
            candidatesBarController = candidatesBarController,
            symLayoutController = symLayoutController,
            isInputViewActive = { isInputViewActive },
            isNavModeLatched = { ctrlLatchFromNavMode },
            currentInputConnection = { currentInputConnection },
            isInputViewShown = { isInputViewShown },
            attachInputView = { view -> setInputView(view) },
            setCandidatesViewShown = { shown -> setCandidatesViewShown(shown) },
            requestShowInputView = { requestShowSelf(0) },
            refreshStatusBar = { refreshStatusBar() }
        )
        launcherShortcutController = LauncherShortcutController(this)
        // Configure callbacks to manage nav mode during power shortcuts
        launcherShortcutController.setNavModeCallbacks(
            exitNavMode = { navModeController.exitNavMode() },
            enterNavMode = { navModeController.enterNavMode() }
        )
        
        // Initialize keyboard layout
        loadKeyboardLayout()
        
        // Initialize nav mode mappings file if needed
        it.srik.TypeQ25.SettingsManager.initializeNavModeMappingsFile(this)
        ctrlKeyMap.putAll(KeyMappingLoader.loadCtrlKeyMappings(assets, this))
        variationStateController = VariationStateController(VariationRepository.loadVariations(assets))
        
        // Load default special characters for variations bar
        it.srik.TypeQ25.data.DefaultSpecialChars.load(this, assets)
        
        // Load auto-correction rules
        AutoCorrector.loadCorrections(assets, this)
        
        // Load dictionary words into suggestion engine
        suggestionEngine.loadDictionaryWords(AutoCorrector.corrections)
        
        // Register listener for SharedPreferences changes
        prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
            if (key == "sym_mappings_custom") {
                Log.d(TAG, "SYM mappings page 1 changed, reloading...")
                // Reload SYM mappings for page 1
                altSymManager.reloadSymMappings()
                // Update status bar to reflect new mappings
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (key == "sym_mappings_page2_custom") {
                Log.d(TAG, "SYM mappings page 2 changed, reloading...")
                // Reload SYM mappings for page 2
                altSymManager.reloadSymMappings2()
                // Update status bar to reflect new mappings
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (key == "sym_pages_config") {
                Log.d(TAG, "SYM pages configuration changed, refreshing status bar...")
                Handler(Looper.getMainLooper()).post {
                    updateStatusBarText()
                }
            } else if (key == "clear_alt_on_space") {
                clearAltOnSpaceEnabled = SettingsManager.getClearAltOnSpace(this)
            } else if (key != null && (key.startsWith("auto_correct_custom_") || key == "auto_correct_enabled_languages")) {
                Log.d(TAG, "Auto-correction rules changed, reloading...")
                // Reload auto-corrections (including new custom languages)
                AutoCorrector.loadCorrections(assets, this)
                // Reload suggestion dictionaries
                suggestionEngine.loadDictionaryWords(AutoCorrector.corrections)
            } else if (key == "nav_mode_mappings_updated") {
                Log.d(TAG, "Nav mode mappings changed, reloading...")
                // Reload nav mode key mappings
                reloadNavModeMappings()
            } else if (key == "keyboard_layout") {
                if (suppressNextLayoutReload) {
                    Log.d(TAG, "Keyboard layout change observed, reload suppressed")
                    suppressNextLayoutReload = false
                } else {
                    Log.d(TAG, "Keyboard layout changed, reloading...")
                    val layoutName = SettingsManager.getKeyboardLayout(this)
                    switchToLayout(layoutName, showToast = true)
                }
            } else if (key == "use_minimal_ui") {
                Log.d(TAG, "Minimal UI setting changed, recreating input view...")
                // Recreate the input view to apply the new minimal UI setting
                Handler(Looper.getMainLooper()).post {
                    try {
                        // Clear cached layouts so they get recreated with the new setting
                        candidatesBarController.clearLayouts()
                        // Request the system to recreate the input view
                        setInputView(onCreateInputView())
                        updateStatusBarText()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error recreating input view after minimal UI change", e)
                    }
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        
        // Register broadcast receiver for speech recognition
        speechResultReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "Broadcast receiver called - action: ${intent?.action}")
                if (intent?.action == SpeechRecognitionActivity.ACTION_SPEECH_RESULT) {
                    val text = intent.getStringExtra(SpeechRecognitionActivity.EXTRA_TEXT)
                    Log.d(TAG, "Broadcast received with text: $text")
                    if (text != null && text.isNotEmpty()) {
                        Log.d(TAG, "Received speech recognition result: $text")
                        
                        // Delay text insertion to give the system time to restore InputConnection
                        // after the speech recognition activity has closed.
                        mainHandler.postDelayed({
                            // Try multiple times if InputConnection is not immediately available
                            var attempts = 0
                            val maxAttempts = 10
                            
                            fun tryInsertText() {
                                val inputConnection = currentInputConnection
                                if (inputConnection != null) {
                                    inputConnection.commitText(text, 1)
                                    Log.d(TAG, "Speech text inserted successfully: $text")
                                } else {
                                    attempts++
                                    if (attempts < maxAttempts) {
                                        Log.d(TAG, "InputConnection not available, attempt $attempts/$maxAttempts, retrying in 100ms...")
                                        mainHandler.postDelayed({ tryInsertText() }, 100)
                                    } else {
                                        Log.w(TAG, "InputConnection not available after $maxAttempts attempts, text not inserted: $text")
                                    }
                                }
                            }
                            
                            tryInsertText()
                        }, 300) // Wait 300ms before trying to insert text
                    }
                }
            }
        }
        
        val filter = IntentFilter(SpeechRecognitionActivity.ACTION_SPEECH_RESULT)
        
        // On Android 13+ (API 33+) we must specify whether the receiver is exported
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(speechResultReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(speechResultReceiver, filter)
        }
        
        Log.d(TAG, "Broadcast receiver registered for: ${SpeechRecognitionActivity.ACTION_SPEECH_RESULT}")
    }
    
    /**
     * Commits an emoji to the current input connection.
     * Called from EmojiPickerActivity after emoji selection.
     */
    fun commitEmoji(emoji: String) {
        val ic = currentInputConnection
        if (ic != null) {
            ic.commitText(emoji, 1)
            android.util.Log.d(TAG, "Committed emoji: $emoji")
        } else {
            android.util.Log.w(TAG, "Cannot commit emoji - no input connection")
        }
    }
    
    /**
     * Shows the symbol picker popup when SYM key is pressed.
     */
    private fun showSymbolPickerPopup() {
        Log.d(TAG, "showSymbolPickerPopup() called")
        
        // Dismiss clipboard popup if showing
        clipboardHistoryPopup?.dismiss()
        
        // Get the input view to anchor the popup
        val anchorView = window?.window?.decorView?.findViewById<android.view.View>(android.R.id.content)
        Log.d(TAG, "Anchor view: $anchorView, window: ${window?.window}")
        
        if (anchorView == null) {
            android.util.Log.w(TAG, "Cannot show symbol picker - no anchor view")
            return
        }
        
        // Reuse existing popup if available, otherwise create new one
        if (symbolPickerPopup == null) {
            Log.d(TAG, "Creating new SymbolPickerPopup")
            symbolPickerPopup = it.srik.TypeQ25.inputmethod.ui.SymbolPickerPopup(
                context = this,
                onSymbolSelected = { symbol ->
                    val ic = currentInputConnection
                    if (ic != null) {
                        ic.commitText(symbol, 1)
                    }
                },
                onDismiss = {
                    // Don't nullify here, keep for reuse
                },
                onClearModifiers = {
                    // Clear all modifier states when popup is shown
                    modifierStateController.clearAltState(resetPressedState = true)
                    modifierStateController.clearCtrlState(resetPressedState = true)
                    shiftOneShot = false
                    updateStatusBarText()
                }
            )
        } else {
            Log.d(TAG, "Reusing existing SymbolPickerPopup")
        }
        
        Log.d(TAG, "Calling show() on symbolPickerPopup: $symbolPickerPopup")
        symbolPickerPopup?.show(anchorView)
        Log.d(TAG, "show() completed")
    }
    
    /**
     * Shows the clipboard history popup when Ctrl+Shift+V is pressed.
     */
    private fun showClipboardHistoryPopup() {
        Log.d(TAG, "showClipboardHistoryPopup() called")
        
        // Dismiss existing popups
        clipboardHistoryPopup?.dismiss()
        symbolPickerPopup?.dismiss()
        
        // Get the input view to anchor the popup
        val anchorView = window?.window?.decorView?.findViewById<android.view.View>(android.R.id.content)
        Log.d(TAG, "Anchor view: $anchorView, window: ${window?.window}")
        
        if (anchorView == null) {
            android.util.Log.w(TAG, "Cannot show clipboard history - no anchor view")
            return
        }
        
        if (!::clipboardHistoryManager.isInitialized) {
            android.util.Log.w(TAG, "Clipboard manager not initialized")
            return
        }
        
        Log.d(TAG, "Creating ClipboardHistoryPopup")
        // Create and show the popup
        clipboardHistoryPopup = it.srik.TypeQ25.inputmethod.ui.ClipboardHistoryPopup(
            context = this,
            clipboardManager = clipboardHistoryManager,
            onItemSelected = { text ->
                val ic = currentInputConnection
                if (ic != null) {
                    ic.commitText(text, 1)
                    Log.d(TAG, "Pasted from clipboard history: ${text.take(50)}...")
                }
            },
            onDismiss = {
                clipboardHistoryPopup = null
            }
        )
        
        Log.d(TAG, "Calling show() on clipboardHistoryPopup: $clipboardHistoryPopup")
        clipboardHistoryPopup?.show(anchorView)
        Log.d(TAG, "show() completed")
    }
    
    private fun toggleFlashlight() {
        try {
            if (cameraManager == null || cameraId == null) {
                Log.w(TAG, "Camera not available for flashlight")
                return
            }
            
            isFlashlightOn = !isFlashlightOn
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager?.setTorchMode(cameraId!!, isFlashlightOn)
                Log.d(TAG, "Flashlight toggled: $isFlashlightOn")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle flashlight", e)
            isFlashlightOn = false
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Turn off flashlight if it's on
        if (isFlashlightOn) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && cameraId != null) {
                    cameraManager?.setTorchMode(cameraId!!, false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to turn off flashlight in onDestroy", e)
            }
        }
        
        // Dismiss symbol picker popup
        symbolPickerPopup?.dismiss()
        symbolPickerPopup = null
        
        // Dismiss clipboard history popup and stop monitoring
        clipboardHistoryPopup?.dismiss()
        clipboardHistoryPopup = null
        if (::clipboardHistoryManager.isInitialized) {
            clipboardHistoryManager.stopMonitoring()
        }
        
        // Dismiss emoji shortcode popup
        emojiShortcodePopup?.dismiss()
        emojiShortcodePopup = null
        
        // Clear instance reference
        instance = null
        
        // Update lifecycle state
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        
        // Remove listener when service is destroyed
        prefsListener?.let {
            prefs.unregisterOnSharedPreferenceChangeListener(it)
        }
        
        // Save user word history and cleanup suggestion engine
        if (::suggestionEngine.isInitialized) {
            suggestionEngine.saveUserHistory()
            suggestionEngine.cleanup()
        }
        
        // Cleanup modifier overlay
        if (::modifierOverlay.isInitialized) {
            modifierOverlay.cleanup()
        }
        
        // Unregister broadcast receiver
        speechResultReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error while unregistering broadcast receiver", e)
            }
        }

        speechResultReceiver = null
        cancelSpaceLongPress()
        multiTapController.cancelAll()
        
        // Clean up pending handler callbacks to prevent memory leaks
        cursorUpdateJob?.let { mainHandler.removeCallbacks(it) }
        cursorUpdateJob = null
    }

    override fun onCreateInputView(): View? {
        val view = keyboardVisibilityController.onCreateInputView()
        
        // Set window token for modifier overlay once input view is created
        if (::modifierOverlay.isInitialized && view != null) {
            view.post {
                modifierOverlay.setWindowToken(view.windowToken)
            }
        }
        
        return view
    }

    /**
     * Creates the candidates view shown when the soft keyboard is disabled.
     * Uses a separate StatusBarController instance to provide identical functionality.
     */
    override fun onCreateCandidatesView(): View? {
        val view = keyboardVisibilityController.onCreateCandidatesView()
        
        // Set window token for modifier overlay once candidates view is created
        if (::modifierOverlay.isInitialized && view != null) {
            view.post {
                modifierOverlay.setWindowToken(view.windowToken)
            }
        }
        
        return view
    }

    /**
     * Determines whether the input view (soft keyboard) should be shown.
     * Respects the system flag (e.g. "Show virtual keyboard" off for physical keyboards):
     * when the system asks for candidate-only mode we hide the main status UI and
     * expose the slim candidates view (LED strip + SYM layout on demand).
     */
    override fun onEvaluateInputViewShown(): Boolean {
        val shouldShowInputView = super.onEvaluateInputViewShown()
        return keyboardVisibilityController.onEvaluateInputViewShown(shouldShowInputView)
    }

    /**
     * Computes the insets for the IME window.
     * This increases the "content" area to include the candidate view area,
     * allowing the application to shift upwards properly without the candidates view
     * covering system UI.
     */
    override fun onComputeInsets(outInsets: InputMethodService.Insets?) {
        super.onComputeInsets(outInsets)
        
        if (outInsets != null && !isFullscreenMode()) {
            outInsets.contentTopInsets = outInsets.visibleTopInsets
        }
    }

    /**
     * Resets all modifier key states.
     * Called when leaving a field or closing/reopening the keyboard.
     * @param preserveNavMode If true, keeps Ctrl latch active when nav mode is enabled.
     */
    private fun resetModifierStates(preserveNavMode: Boolean = false) {
        modifierStateController.resetModifiers(
            preserveNavMode = preserveNavMode,
            onNavModeCancelled = { navModeController.cancelNotification() }
        )
        
        symLayoutController.reset()
        altSymManager.resetTransientState()
        deactivateVariations()
        refreshStatusBar()
    }
    
    /**
     * Forces creation and display of the input view.
     * Called when the first physical key is pressed.
     * Shows the keyboard if there is an active text field.
     * IMPORTANT: UI is never shown in nav mode.
     */
    private fun ensureInputViewCreated() {
        keyboardVisibilityController.ensureInputViewCreated()
    }
    /**
     * Aggiorna la status bar delegando al controller dedicato.
     */
    private fun updateStatusBarText() {
        val variationSnapshot = variationStateController.refreshFromCursor(
            currentInputConnection,
            shouldDisableSmartFeatures
        )
        
        val modifierSnapshot = modifierStateController.snapshot()
        val suggestions = getSuggestions()
        
        // If no variations and no suggestions, provide default special characters
        val finalVariations = if (variationSnapshot.variations.isEmpty() && suggestions.isEmpty()) {
            // Show default special characters (limited to 8)
            it.srik.TypeQ25.data.DefaultSpecialChars.getDefaultChars(8)
        } else {
            variationSnapshot.variations
        }
        
        val snapshot = StatusBarController.StatusSnapshot(
            capsLockEnabled = modifierSnapshot.capsLockEnabled,
            shiftPhysicallyPressed = modifierSnapshot.shiftPhysicallyPressed,
            shiftOneShot = modifierSnapshot.shiftOneShot,
            ctrlLatchActive = modifierSnapshot.ctrlLatchActive,
            ctrlPhysicallyPressed = modifierSnapshot.ctrlPhysicallyPressed,
            ctrlOneShot = modifierSnapshot.ctrlOneShot,
            ctrlLatchFromNavMode = modifierSnapshot.ctrlLatchFromNavMode,
            altLatchActive = modifierSnapshot.altLatchActive,
            altPhysicallyPressed = modifierSnapshot.altPhysicallyPressed,
            altOneShot = modifierSnapshot.altOneShot,
            symPage = symPage,
            variations = finalVariations,
            lastInsertedChar = variationSnapshot.lastInsertedChar,
            shouldDisableSmartFeatures = shouldDisableSmartFeatures,
            suggestions = suggestions
        )
        // Passa anche la mappa emoji quando SYM è attivo (solo pagina 1)
        val emojiMapText = symLayoutController.emojiMapText()
        // Passa le mappature SYM per la griglia emoji/caratteri
        val symMappings = symLayoutController.currentSymMappings()
        // Passa l'inputConnection per rendere i pulsanti clickabili
        val inputConnection = currentInputConnection
        candidatesBarController.updateStatusBars(snapshot, emojiMapText, inputConnection, symMappings)
        
        // Use overlay to show icons at top-center of screen
        if (::modifierOverlay.isInitialized) {
            modifierOverlay.updateModifierState(
                shiftActive = modifierSnapshot.shiftPhysicallyPressed || modifierSnapshot.shiftOneShot,
                shiftLocked = modifierSnapshot.capsLockEnabled,
                ctrlActive = modifierSnapshot.ctrlPhysicallyPressed || modifierSnapshot.ctrlOneShot,
                ctrlLocked = modifierSnapshot.ctrlLatchActive && !modifierSnapshot.ctrlLatchFromNavMode,
                altActive = modifierSnapshot.altPhysicallyPressed || modifierSnapshot.altOneShot,
                altLocked = modifierSnapshot.altLatchActive
            )
        }
    }
    
    /**
     * Deactivate variations.
     */
    private fun deactivateVariations() {
        if (::variationStateController.isInitialized) {
            variationStateController.clear()
        }
    }
    
    /**
     * Checks for emoji/symbol shortcode and shows suggestions if applicable.
     * Called after text is inserted.
     */
    private fun checkAndShowEmojiShortcode() {
        if (!::emojiShortcodeManager.isInitialized) return
        
        // Check if either feature is enabled
        val emojiEnabled = it.srik.TypeQ25.SettingsManager.getEmojiShortcodeEnabled(this)
        val symbolEnabled = it.srik.TypeQ25.SettingsManager.getSymbolShortcodeEnabled(this)
        
        if (!emojiEnabled && !symbolEnabled) {
            // Both features disabled
            emojiShortcodePopup?.dismiss()
            return
        }
        
        val ic = currentInputConnection ?: return
        try {
            val textBefore = ic.getTextBeforeCursor(100, 0)?.toString() ?: return
            
            // Extract current shortcode being typed
            val result = emojiShortcodeManager.extractCurrentShortcode(textBefore)
            if (result != null) {
                val (shortcode, startPos) = result
                
                // Get all suggestions (both emojis and symbols)
                val allSuggestions = emojiShortcodeManager.searchShortcodes(shortcode, 10)
                
                // Filter suggestions based on enabled features
                val filteredSuggestions = if (emojiEnabled && symbolEnabled) {
                    // Both enabled, show all
                    allSuggestions
                } else if (emojiEnabled && !symbolEnabled) {
                    // Only emojis - filter out single-byte ASCII/simple symbols
                    allSuggestions.filter { (char, _) -> 
                        // Keep multi-byte characters (emojis) or non-ASCII Unicode (but exclude simple symbols like ™®©)
                        val codePoint = char.codePointAt(0)
                        char.length > 1 || codePoint > 0x1F000 // Keep emoji ranges
                    }
                } else if (!emojiEnabled && symbolEnabled) {
                    // Only symbols - filter out emoji ranges
                    allSuggestions.filter { (char, _) ->
                        val codePoint = char.codePointAt(0)
                        // Keep ASCII, Latin Extended, and symbol ranges, but exclude emoji ranges
                        codePoint < 0x1F000 || codePoint > 0x1FFFF
                    }
                } else {
                    emptyList()
                }
                
                if (filteredSuggestions.isNotEmpty()) {
                    showEmojiShortcodeSuggestions(filteredSuggestions)
                } else {
                    emojiShortcodePopup?.dismiss()
                }
            } else {
                // No active shortcode, dismiss popup
                emojiShortcodePopup?.dismiss()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking emoji shortcode", e)
        }
    }
    
    /**
     * Shows emoji/symbol shortcode suggestions popup.
     */
    private fun showEmojiShortcodeSuggestions(suggestions: List<Pair<String, String>>) {
        val anchorView = window?.window?.decorView?.findViewById<android.view.View>(android.R.id.content)
        if (anchorView == null) {
            Log.w(TAG, "Cannot show emoji/symbol suggestions - no anchor view")
            return
        }
        
        if (emojiShortcodePopup?.isShowing() == true) {
            // Update existing popup
            emojiShortcodePopup?.updateSuggestions(suggestions)
        } else {
            // Create new popup
            emojiShortcodePopup = it.srik.TypeQ25.inputmethod.ui.EmojiShortcodePopup(
                context = this,
                onEmojiSelected = { character, shortcode ->
                    replaceShortcodeWithEmoji(character, shortcode)
                },
                onDismiss = {
                    emojiShortcodePopup = null
                }
            )
            emojiShortcodePopup?.show(anchorView, suggestions)
        }
    }
    
    /**
     * Replaces the current shortcode with the selected emoji.
     */
    private fun replaceShortcodeWithEmoji(emoji: String, shortcode: String) {
        Log.d(TAG, "replaceShortcodeWithEmoji called - emoji: $emoji, shortcode: $shortcode")
        
        val ic = currentInputConnection
        if (ic == null) {
            Log.e(TAG, "InputConnection is null, cannot insert emoji")
            return
        }
        
        try {
            val textBefore = ic.getTextBeforeCursor(100, 0)?.toString()
            Log.d(TAG, "Text before cursor: '$textBefore'")
            
            if (textBefore == null) {
                Log.e(TAG, "getTextBeforeCursor returned null")
                return
            }
            
            val lastColonIndex = textBefore.lastIndexOf(':')
            Log.d(TAG, "Last colon index: $lastColonIndex")
            
            if (lastColonIndex != -1) {
                // Delete from ':' to cursor (includes the partial shortcode)
                val charsToDelete = textBefore.length - lastColonIndex
                Log.d(TAG, "Deleting $charsToDelete characters")
                
                ic.beginBatchEdit()
                ic.deleteSurroundingText(charsToDelete, 0)
                
                // Insert the emoji
                ic.commitText(emoji, 1)
                ic.endBatchEdit()
                
                Log.d(TAG, "Successfully replaced :$shortcode: with $emoji")
                
                // Dismiss the popup after successful insertion
                emojiShortcodePopup?.dismiss()
            } else {
                Log.e(TAG, "No colon found in text before cursor")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error replacing shortcode with emoji", e)
        }
    }
    

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        
        currentPackageName = info?.packageName
        
        // Track last external app (not our keyboard) for emoji targeting
        if (currentPackageName != null && currentPackageName != packageName) {

        }
        
        updateInputContextState(info)
        val state = inputContextState
        val isEditable = state.isEditable
        val isReallyEditable = state.isReallyEditable
        isInputViewActive = isEditable
        
        if (restarting) {
            enforceSmartFeatureDisabledState()
        }
        
        // Enable Android's built-in auto-correction for editable fields
        if (info != null && isEditable) {
            // Remove NO_SUGGESTIONS flag and add auto-correction flags
            info.inputType = info.inputType and android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS.inv()
            info.inputType = info.inputType or android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
            info.inputType = info.inputType or android.text.InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE
        }
        
        if (isEditable && !restarting) {
            val autoShowKeyboardEnabled = SettingsManager.getAutoShowKeyboard(this)
            if (autoShowKeyboardEnabled && isReallyEditable) {
                if (!isInputViewShown && isInputViewActive) {
                    ensureInputViewCreated()
                    // Request to show the input view explicitly
                    requestShowSelf(0)
                }
            }
        }
        
        if (!restarting) {
            if (ctrlLatchFromNavMode && ctrlLatchActive) {
                val inputConnection = currentInputConnection
                val hasValidInputConnection = inputConnection != null
                
                if (isReallyEditable && hasValidInputConnection) {
                    navModeController.exitNavMode()
                    resetModifierStates(preserveNavMode = false)
                }
            } else if (isEditable || !ctrlLatchFromNavMode) {
                resetModifierStates(preserveNavMode = false)
            }
        }
        
        initializeInputContext(restarting)
        
        if (restarting && isEditable && !shouldDisableSmartFeatures) {
            AutoCapitalizeHelper.checkAutoCapitalizeOnRestart(
                this,
                currentInputConnection,
                shouldDisableSmartFeatures,
                enableShift = { modifierStateController.requestShiftOneShotFromAutoCap() },
                disableShift = { modifierStateController.consumeShiftOneShot() },
                onUpdateStatusBar = { updateStatusBarText() }
            )
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        
        // Update lifecycle to STARTED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        updateInputContextState(info)
        initializeInputContext(restarting)
        
        val isEditable = inputContextState.isEditable
        
        if (restarting && isEditable && !shouldDisableSmartFeatures) {
            AutoCapitalizeHelper.checkAutoCapitalizeOnRestart(
                this,
                currentInputConnection,
                shouldDisableSmartFeatures,
                enableShift = { modifierStateController.requestShiftOneShotFromAutoCap() },
                disableShift = { modifierStateController.consumeShiftOneShot() },
                onUpdateStatusBar = { updateStatusBarText() }
            )
        }
    }
    
    override fun onFinishInput() {
        super.onFinishInput()
        isInputViewActive = false
        inputContextState = InputContextState.EMPTY
        multiTapController.cancelAll()
        cancelSpaceLongPress()
        resetModifierStates(preserveNavMode = true)
    }
    
    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        
        // Update lifecycle to STARTED (from RESUMED)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        
        isInputViewActive = false
        if (finishingInput) {
            multiTapController.cancelAll()
            cancelSpaceLongPress()
            resetModifierStates(preserveNavMode = true)
        }
    }
    
    override fun onWindowShown() {
        super.onWindowShown()
        updateStatusBarText()
    }
    
    override fun onWindowHidden() {
        super.onWindowHidden()
        multiTapController.finalizeCycle()
        cancelSpaceLongPress()
        resetModifierStates(preserveNavMode = true)
    }
    
    /**
     * Called when the cursor position or selection changes in the text field.
     */
    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        
        if (!shouldDisableSmartFeatures) {
            val cursorPositionChanged = (oldSelStart != newSelStart) || (oldSelEnd != newSelEnd)
            if (cursorPositionChanged && newSelStart == newSelEnd) {
                // Remove any pending callback to prevent memory leaks
                cursorUpdateJob?.let { mainHandler.removeCallbacks(it) }
                cursorUpdateJob = Runnable { updateStatusBarText() }
                mainHandler.postDelayed(cursorUpdateJob!!, CURSOR_UPDATE_DELAY)
            }
        }
        
        AutoCapitalizeHelper.checkAutoCapitalizeOnSelectionChange(
            this,
            currentInputConnection,
            shouldDisableSmartFeatures,
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            enableShift = { modifierStateController.requestShiftOneShotFromAutoCap() },
            disableShift = { modifierStateController.consumeShiftOneShot() },
            onUpdateStatusBar = { updateStatusBarText() }
        )
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        // Handle long press even when the keyboard is hidden but we still have a valid InputConnection.
        val inputConnection = currentInputConnection
        if (inputConnection == null) {
            return super.onKeyLongPress(keyCode, event)
        }
        
        // If the keyboard is hidden but we have an InputConnection, reactivate it
        if (!isInputViewActive) {
            isInputViewActive = true
            if (!isInputViewShown) {
                ensureInputViewCreated()
            }
        }
        
        // Intercept long presses BEFORE Android handles them
        if (altSymManager.hasAltMapping(keyCode)) {
            // Consumiamo l'evento per evitare il popup di Android
            return true
        }
        
        return super.onKeyLongPress(keyCode, event)
    }

    /**
     * Checks if a keycode represents a CTRL key.
     * On Q25 devices, keycode 60 acts as CTRL.
     * On other devices, standard CTRL keycodes are used.
     */
    private fun isCtrlKey(keyCode: Int): Boolean {
        return if (deviceType == "Q25") {
            keyCode == 60
        } else {
            keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT
        }
    }

    /**
     * Checks if a keycode represents a SYM key.
     * On Q25 devices, keycode 58 acts as SYM.
     * On other devices, standard SYM keycode is used.
     */
    private fun isSymKey(keyCode: Int): Boolean {
        return if (deviceType == "Q25") {
            keyCode == 58
        } else {
            keyCode == KeyEvent.KEYCODE_SYM
        }
    }

    private fun handleCurrencyKey(event: KeyEvent?): Boolean {
        if (deviceType != "Q25") {
            return false
        }
        
        // Check if Alt is pressed - check both our internal state and the event's metastate
        val eventAltPressed = event?.isAltPressed == true || 
                             (event?.metaState?.and(KeyEvent.META_ALT_ON) != 0) ||
                             (event?.metaState?.and(KeyEvent.META_ALT_LEFT_ON) != 0) ||
                             (event?.metaState?.and(KeyEvent.META_ALT_RIGHT_ON) != 0)
        val altActive = altLatchActive || altOneShot || altPressed || altPhysicallyPressed || eventAltPressed
        Log.d(TAG, "handleCurrencyKey: altLatchActive=$altLatchActive, altOneShot=$altOneShot, altPressed=$altPressed, altPhysicallyPressed=$altPhysicallyPressed, event.isAltPressed=${event?.isAltPressed}, event.metaState=${event?.metaState}, eventAltPressed=$eventAltPressed, altActive=$altActive")
        
        if (altActive) {
            toggleSpeakerphone()
            // Clear Alt state after using it
            modifierStateController.clearAltState(resetPressedState = true)
            updateStatusBarText()
            return true
        }
        
        val currency = CurrencyManager.getCurrency(this)
        currentInputConnection?.commitText(currency, 1)
        return true
    }
    
    private fun toggleSpeakerphone() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager == null) {
                Log.e(TAG, "AudioManager is null")
                return
            }
            
            val currentMode = audioManager.mode
            Log.d(TAG, "Current audio mode: $currentMode (MODE_IN_CALL=${AudioManager.MODE_IN_CALL}, MODE_IN_COMMUNICATION=${AudioManager.MODE_IN_COMMUNICATION})")
            
            // Check if we're in a call using TelecomManager as well
            var inCall = currentMode == AudioManager.MODE_IN_CALL || 
                        currentMode == AudioManager.MODE_IN_COMMUNICATION
            
            // Additional check using TelecomManager if available
            if (!inCall) {
                try {
                    val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
                    if (telecomManager != null && androidx.core.content.ContextCompat.checkSelfPermission(
                            this,
                            android.Manifest.permission.READ_PHONE_STATE
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        inCall = telecomManager.isInCall
                        Log.d(TAG, "TelecomManager.isInCall: $inCall")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking call state with TelecomManager", e)
                }
            }
            
            if (inCall) {
                // Check current speaker state
                val isSpeakerOn = audioManager.isSpeakerphoneOn
                Log.d(TAG, "Current speaker state: ${if (isSpeakerOn) "ON" else "OFF"}, mode: $currentMode")
                
                // Toggle speaker using multiple methods for compatibility
                val targetState = !isSpeakerOn
                
                try {
                    // Ensure we're in the right audio mode
                    if (currentMode != AudioManager.MODE_IN_CALL) {
                        audioManager.mode = AudioManager.MODE_IN_CALL
                        Log.d(TAG, "Set audio mode to MODE_IN_CALL")
                    }
                    
                    // Request audio focus before changing speaker state
                    val audioFocusRequest = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(
                                android.media.AudioAttributes.Builder()
                                    .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                            )
                            .build()
                    } else {
                        null
                    }
                    
                    if (audioFocusRequest != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        audioManager.requestAudioFocus(audioFocusRequest)
                        Log.d(TAG, "Requested audio focus")
                    }
                    
                    // Method 1: Use setSpeakerphoneOn
                    audioManager.setSpeakerphoneOn(targetState)
                    Log.d(TAG, "Called setSpeakerphoneOn($targetState)")
                    
                    // Method 2: Also set the property
                    audioManager.isSpeakerphoneOn = targetState
                    Log.d(TAG, "Set isSpeakerphoneOn = $targetState")
                    
                    // Method 3: Try using audio routing (more reliable on some devices)
                    try {
                        if (targetState) {
                            // Route audio to speaker
                            audioManager.setParameters("AudioSetParam=SET_LOUDSPEAKER_STATUS=1")
                        } else {
                            // Route audio to earpiece
                            audioManager.setParameters("AudioSetParam=SET_LOUDSPEAKER_STATUS=0")
                        }
                        Log.d(TAG, "Set audio routing parameter for speaker: $targetState")
                    } catch (e: Exception) {
                        Log.w(TAG, "Audio routing parameter not supported: ${e.message}")
                    }
                    
                    // Give audio system time to apply changes
                    Thread.sleep(150)
                    
                    // Verify the change
                    val newState = audioManager.isSpeakerphoneOn
                    Log.d(TAG, "Verified speaker state after toggle: ${if (newState) "ON" else "OFF"}")
                    
                    // Note: On many devices, speaker state during calls is controlled by the phone app
                    // and cannot be changed by IME. This is a system limitation.
                    if (newState != targetState) {
                        Log.w(TAG, "Speaker state didn't change - this may be a system limitation during calls")
                    }
                    
                    // Show feedback toast based on target state (not verified state, as verification may not work)
                    val messageResId = if (targetState) 
                        it.srik.TypeQ25.R.string.speaker_toggle_on 
                    else 
                        it.srik.TypeQ25.R.string.speaker_toggle_off
                    Handler(Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(this, messageResId, android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in speaker toggle operation", e)
                    throw e
                }
            } else {
                // Not in a call, show message
                Log.w(TAG, "Speaker toggle attempted but not in call")
                Handler(Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(
                        this, 
                        it.srik.TypeQ25.R.string.speaker_toggle_not_in_call, 
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException toggling speakerphone - missing MODIFY_AUDIO_SETTINGS permission?", e)
            Handler(Looper.getMainLooper()).post {
                android.widget.Toast.makeText(this, "Permission denied for speaker toggle", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling speakerphone", e)
            Handler(Looper.getMainLooper()).post {
                android.widget.Toast.makeText(this, "Error toggling speaker: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Gets the partial word currently being typed (before the cursor).
     * @return The current word prefix, or empty string if none
     */
    private fun getCurrentWord(): String {
        val ic = currentInputConnection ?: return ""
        try {
            val textBefore = ic.getTextBeforeCursor(100, 0) ?: return ""
            
            // Find the last word boundary (space, punctuation, etc.)
            var startIndex = textBefore.length - 1
            while (startIndex >= 0) {
                val char = textBefore[startIndex]
                if (char.isWhitespace() || char in ".,;:!?\"'()[]{}/<>@#\$%^&*+=|\\") {
                    break
                }
                startIndex--
            }
            
            return textBefore.substring(startIndex + 1)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current word", e)
            return ""
        }
    }
    

    
    /**
     * Clears any composing text/underline.
     */
    private fun clearComposingText() {
        val ic = currentInputConnection ?: return
        try {
            ic.finishComposingText()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing composing text", e)
        }
    }
    
    /**
     * Gets word suggestions for the current typing context.
     * @return List of suggested words (up to 3)
     */
    fun getSuggestions(): List<String> {
        if (!::suggestionEngine.isInitialized) {
            Log.d(TAG, "getSuggestions: suggestionEngine not initialized")
            return emptyList()
        }
        
        val currentWord = getCurrentWord()
        Log.d(TAG, "getSuggestions: currentWord='$currentWord'")
        if (currentWord.isEmpty()) return emptyList()
        
        val suggestions = suggestionEngine.getSuggestions(currentWord, 3)
        Log.d(TAG, "getSuggestions: returning ${suggestions.size} suggestions: $suggestions")
        return suggestions
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Handle emoji shortcode popup navigation early (before other processing)
        if (emojiShortcodePopup?.isShowing() == true) {
            // Allow DPAD UP/DOWN for navigation, ENTER/DPAD_CENTER for selection
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                val isAltActive = altLatchActive || altOneShot || altPressed || altPhysicallyPressed || event?.isAltPressed == true
                if (emojiShortcodePopup?.handlePhysicalKey(keyCode, isAltActive, event) == true) {
                    return true // Consumed by popup
                }
            }
            // Block DPAD LEFT/RIGHT when popup is showing (not used for navigation)
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                return true // Consume the event
            }
        }
        
        // Log specific key events for debugging call handling
        when (keyCode) {
            KeyEvent.KEYCODE_HOME -> Log.d(TAG, "HOME key intercepted in onKeyDown")
            KeyEvent.KEYCODE_ENDCALL -> Log.d(TAG, "ENDCALL key intercepted in onKeyDown (keyCode=$keyCode)")
            KeyEvent.KEYCODE_CALL -> Log.d(TAG, "CALL key intercepted in onKeyDown (keyCode=$keyCode)")
            6 -> Log.d(TAG, "KEYCODE_ENDCALL (6) intercepted in onKeyDown")
            5 -> Log.d(TAG, "KEYCODE_CALL (5) intercepted in onKeyDown")
            else -> {
                // Log unknown keycodes that might be call-related
                if (keyCode in 1..10 || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    Log.d(TAG, "Potential call-related key: keyCode=$keyCode")
                }
            }
        }
        
        // Allow D-pad keys to pass through for navigation, except for terminal apps with Ctrl active
        // For terminal apps, we want to handle Ctrl+DPAD keys with our Ctrl mappings
        val shouldPassThroughDpad = (keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
        
        if (shouldPassThroughDpad) {
            // Check if this is a terminal app with Ctrl active - if so, don't pass through
            val isCtrlActiveNow = ctrlPressed || ctrlLatchActive || ctrlOneShot || ctrlPhysicallyPressed
            if (!isTerminalApp || !isCtrlActiveNow) {
                return super.onKeyDown(keyCode, event)
            }
            // Otherwise, let it fall through to terminal handling below
            Log.d(TAG, "Terminal app with Ctrl active: allowing DPAD key $keyCode to be handled")
        }
        
        // Handle SYM key double-tap for flashlight toggle
        if (isSymKey(keyCode)) {
            val currentTime = System.currentTimeMillis()
            val timeSinceLastPress = currentTime - lastSymPressTime
            
            // Log SYM key detection with context for debugging random triggers
            val info = currentInputEditorInfo
            val inputType = info?.inputType ?: EditorInfo.TYPE_NULL
            val isPasswordField = (inputType and EditorInfo.TYPE_TEXT_VARIATION_PASSWORD) != 0 ||
                                 (inputType and EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) != 0 ||
                                 (inputType and EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD) != 0 ||
                                 (inputType and EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD) != 0
            Log.d(TAG, "SYM key detected: keyCode=$keyCode, repeatCount=${event?.repeatCount}, " +
                      "isPasswordField=$isPasswordField, packageName=$currentPackageName, " +
                      "timeSinceLastPress=$timeSinceLastPress, action=${event?.action}")
            
            // Ignore repeat events (when key is held down)
            if (event?.repeatCount ?: 0 > 0) {
                Log.d(TAG, "SYM key: Ignoring repeat event")
                return true
            }
            
            if (timeSinceLastPress < symDoubleTapThreshold && lastSymPressTime > 0) {
                // Double-tap detected - toggle flashlight
                toggleFlashlight()
                lastSymPressTime = 0 // Reset to prevent triple-tap
                return true
            } else {
                // Single tap - check if symbol picker is already showing
                if (symbolPickerPopup?.isShowing() == true) {
                    // Symbol picker is visible - close it
                    symbolPickerPopup?.dismiss()
                    lastSymPressTime = 0 // Reset timer
                    return true
                }
                
                // Single tap - record time
                lastSymPressTime = currentTime
                // Check if there's an active text field before showing symbol picker
                val info = currentInputEditorInfo
                val ic = currentInputConnection
                val inputType = info?.inputType ?: EditorInfo.TYPE_NULL
                
                // Terminal apps (like Termux) often report TYPE_NULL but still need input
                // Allow symbol picker if we have an InputConnection OR if it's a known terminal app
                val hasEditableField = ic != null && (inputType != EditorInfo.TYPE_NULL || isTerminalApp)
                
                Log.d(TAG, "SYM key pressed - hasEditableField=$hasEditableField, inputType=$inputType, packageName=$currentPackageName, isTerminalApp=$isTerminalApp")
                
                if (!hasEditableField) {
                    // Check if power shortcuts are enabled - if so, let it pass through to InputEventRouter
                    val powerShortcutsEnabled = SettingsManager.getPowerShortcutsEnabled(this)
                    if (powerShortcutsEnabled) {
                        Log.d(TAG, "SYM key: No editable field but power shortcuts enabled, letting it pass through")
                        // Don't consume - let it fall through to handleKeyDownWithNoEditableField
                    } else {
                        // No editable field and power shortcuts disabled - just consume the event
                        Log.d(TAG, "SYM key: No editable field, consuming event")
                        return true
                    }
                } else {
                    // Has editable field - let it fall through to routing logic to show symbol picker
                    Log.d(TAG, "SYM key: Has editable field, proceeding to routing logic")
                }
            }
        }
        
        // Handle symbol picker popup key events if it's showing
        if (symbolPickerPopup?.isShowing() == true) {
            val isCtrlActive = ctrlLatchActive || ctrlOneShot || ctrlPressed || ctrlPhysicallyPressed || event?.isCtrlPressed == true
            val isAltActive = altLatchActive || altOneShot || altPressed || altPhysicallyPressed || event?.isAltPressed == true
            
            if (symbolPickerPopup?.handlePhysicalKey(keyCode, isCtrlActive, isAltActive) == true) {
                return true
            }
        }
        
        // Handle clipboard history popup key events if it's showing
        if (clipboardHistoryPopup?.isShowing() == true) {
            val isCtrlActive = ctrlLatchActive || ctrlOneShot || ctrlPressed || ctrlPhysicallyPressed || event?.isCtrlPressed == true
            if (clipboardHistoryPopup?.handlePhysicalKey(keyCode, isCtrlActive) == true) {
                return true
            }
        }
        
        // Handle emoji shortcode popup key events if it's showing
        if (emojiShortcodePopup?.isShowing() == true) {
            Log.d(TAG, "Emoji popup is showing, handling keyCode=$keyCode")
            
            // Dismiss on Backspace or Escape
            if (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                emojiShortcodePopup?.dismiss()
                // For Backspace, let it pass through to delete the character
                if (keyCode == KeyEvent.KEYCODE_DEL) {
                    return false
                }
                return true
            }
            
            val isAltActive = altLatchActive || altOneShot || altPressed || altPhysicallyPressed || event?.isAltPressed == true
            Log.d(TAG, "Calling emoji popup handlePhysicalKey with isAltActive=$isAltActive")
            
            if (emojiShortcodePopup?.handlePhysicalKey(keyCode, isAltActive, event) == true) {
                Log.d(TAG, "Emoji popup consumed the key event")
                return true
            } else {
                Log.d(TAG, "Emoji popup did not consume the key event")
            }
        } else {
            if (keyCode in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9) {
                Log.d(TAG, "Number key $keyCode but emoji popup is not showing: ${emojiShortcodePopup?.isShowing()}")
            }
        }
        
        // Check if we have an editable field at the very start
        val info = currentInputEditorInfo
        val initialInputConnection = currentInputConnection
        val inputType = info?.inputType ?: EditorInfo.TYPE_NULL
        
        // For terminal apps, always treat as having editable field regardless of InputConnection/inputType
        // For other apps, require both InputConnection and proper inputType
        val hasEditableField = isTerminalApp || (initialInputConnection != null && inputType != EditorInfo.TYPE_NULL)
        
        Log.d(TAG, "onKeyDown - keyCode=$keyCode, hasEditableField=$hasEditableField, inputType=$inputType, isTerminalApp=$isTerminalApp, hasInputConnection=${initialInputConnection != null}, packageName=$currentPackageName")
        
        if (hasEditableField && !isInputViewActive) {
            isInputViewActive = true
        }

        val isModifierKey = keyCode == KeyEvent.KEYCODE_SHIFT_LEFT ||
            keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT ||
            isCtrlKey(keyCode) ||
            isSymKey(keyCode) ||
            keyCode == KeyEvent.KEYCODE_ALT_LEFT ||
            keyCode == KeyEvent.KEYCODE_ALT_RIGHT
        
        // Handle keycode 68 (L key) for currency/speaker after checking if it's combined with Alt
        if (keyCode == 68 && !isModifierKey) {
            if (handleCurrencyKey(event)) {
                return true
            }
        }
        
        // Handle Ctrl+Shift+V for clipboard history
        // Only trigger on physically pressed Shift, not auto-capitalize shift (shiftOneShot)
        // to avoid interfering with paste when auto-capitalize is active
        if (
            hasEditableField &&
            keyCode == KeyEvent.KEYCODE_V &&
            (event?.isCtrlPressed == true || ctrlPressed || ctrlLatchActive || ctrlOneShot) &&
            (event?.isShiftPressed == true || shiftPressed || shiftPhysicallyPressed)
        ) {
            showClipboardHistoryPopup()
            return true
        }
            
        // Handle Ctrl+Space layout switching even when Alt is active.
        if (
            hasEditableField &&
            keyCode == KeyEvent.KEYCODE_SPACE &&
            (event?.isCtrlPressed == true || ctrlPressed || ctrlLatchActive || ctrlOneShot)
        ) {
            var shouldUpdateStatusBar = false

            // Clear Alt state if active so we don't leave Alt latched.
            val hadAlt = altLatchActive || altOneShot || altPressed
            if (hadAlt) {
                modifierStateController.clearAltState(resetPressedState = true)
                shouldUpdateStatusBar = true
            }

            // Always reset Ctrl state after Ctrl+Space to avoid leaving it active.
            val hadCtrl = ctrlLatchActive ||
                ctrlOneShot ||
                ctrlPressed ||
                ctrlPhysicallyPressed ||
                ctrlLatchFromNavMode
            if (hadCtrl) {
                val navModeLatched = ctrlLatchFromNavMode
                modifierStateController.clearCtrlState(resetPressedState = true)
                if (navModeLatched) {
                    navModeController.cancelNotification()
                }
                shouldUpdateStatusBar = true
            }

            cycleLayoutFromShortcut()
            shouldUpdateStatusBar = true

            if (shouldUpdateStatusBar) {
                updateStatusBarText()
            }
            return true
        }

        multiTapController.resetForNewKey(keyCode)
        if (!isModifierKey) {
            modifierStateController.registerNonModifierKey()
        }
        
        // If NO editable field is active, handle ONLY nav mode
        if (!hasEditableField) {
            // Handle keycode 68 (L key) for currency/speaker even without editable field
            if (keyCode == 68) {
                if (handleCurrencyKey(event)) {
                    return true
                }
            }
            
            if (keyCode == 60) {
                Log.d(TAG, "onKeyDown: keyCode=60 - NO editable field, going to handleKeyDownWithNoEditableField")
            }
            val powerShortcutsEnabled = SettingsManager.getPowerShortcutsEnabled(this)
            val launcherShortcutsEnabled = SettingsManager.getLauncherShortcutsEnabled(this)
            return inputEventRouter.handleKeyDownWithNoEditableField(
                keyCode = keyCode,
                event = event,
                ctrlKeyMap = ctrlKeyMap,
                callbacks = InputEventRouter.NoEditableFieldCallbacks(
                    isAlphabeticKey = { code -> isAlphabeticKey(code) },
                    isLauncherPackage = { pkg -> launcherShortcutController.isLauncher(pkg) },
                    handleLauncherShortcut = { key -> launcherShortcutController.handleLauncherShortcut(key) },
                    handlePowerShortcut = { key -> launcherShortcutController.handlePowerShortcut(key) },
                    togglePowerShortcutMode = { message, isNavModeActive ->
                        launcherShortcutController.togglePowerShortcutMode(
                            showToast = { showPowerShortcutToast(it) },
                            isNavModeActive = isNavModeActive
                        )
                    },
                    callSuper = { super.onKeyDown(keyCode, event) },
                    currentInputConnection = { currentInputConnection }
                ),
                ctrlLatchActive = ctrlLatchActive,
                editorInfo = info,
                currentPackageName = currentPackageName,
                powerShortcutsEnabled = powerShortcutsEnabled,
                launcherShortcutsEnabled = launcherShortcutsEnabled
            )
        }
        
        val routingResult = inputEventRouter.handleEditableFieldKeyDownPrelude(
            keyCode = keyCode,
            params = InputEventRouter.EditableFieldKeyDownParams(
                ctrlLatchFromNavMode = ctrlLatchFromNavMode,
                ctrlLatchActive = ctrlLatchActive,
                isInputViewActive = isInputViewActive,
                isInputViewShown = isInputViewShown,
                hasInputConnection = initialInputConnection != null
            ),
            callbacks = InputEventRouter.EditableFieldKeyDownCallbacks(
                exitNavMode = { navModeController.exitNavMode() },
                ensureInputViewCreated = { keyboardVisibilityController.ensureInputViewCreated() },
                callSuper = { super.onKeyDown(keyCode, event) }
            )
        )
        when (routingResult) {
            InputEventRouter.EditableFieldRoutingResult.Consume -> return true
            InputEventRouter.EditableFieldRoutingResult.CallSuper -> return super.onKeyDown(keyCode, event)
            InputEventRouter.EditableFieldRoutingResult.Continue -> {}
        }
        
        val ic = currentInputConnection
        
        // For terminal apps, handle modifiers differently
        // Terminal apps like Termux need raw key events for most keys, but Alt mappings as text
        // Modifier keys are handled by normal IME logic to update state properly
        // Skip terminal-specific handling if symbol picker popup is showing
        if (isTerminalApp && !isModifierKey && symbolPickerPopup?.isShowing() != true) {
            // Check if Alt or Ctrl are active from our tracking
            val isAltActive = altPressed || altLatchActive || altOneShot || altPhysicallyPressed
            val isCtrlActive = ctrlPressed || ctrlLatchActive || ctrlOneShot || ctrlPhysicallyPressed
            val isShiftActive = shiftPressed || shiftOneShot || capsLockEnabled
            
            Log.d(TAG, "=== TERMUX KEY PRESS START ===")
            Log.d(TAG, "Terminal app: keyCode=$keyCode, hasInputConnection=${ic != null}")
            Log.d(TAG, "Alt state: pressed=$altPressed, latch=$altLatchActive, oneShot=$altOneShot, physical=$altPhysicallyPressed -> isActive=$isAltActive")
            Log.d(TAG, "Ctrl state: pressed=$ctrlPressed, latch=$ctrlLatchActive, oneShot=$ctrlOneShot, physical=$ctrlPhysicallyPressed -> isActive=$isCtrlActive")
            Log.d(TAG, "Shift state: pressed=$shiftPressed, oneShot=$shiftOneShot, caps=$capsLockEnabled -> isActive=$isShiftActive")
            
            // For Alt+key combinations, use the Alt key mappings (numbers/symbols) via commitText
            if (isAltActive && altSymManager.hasAltMapping(keyCode)) {
                Log.d(TAG, "Terminal app: Alt is active and key has Alt mapping")
                if (ic != null) {
                    val altChar = altSymManager.getAltMappings()[keyCode]
                    if (altChar != null) {
                        ic.commitText(altChar, 1)
                        Log.d(TAG, "Terminal app: committed Alt mapping '$altChar' for keyCode $keyCode")
                        
                        // Clear Alt one-shot after use (latch stays active for multiple uses)
                        // Also clear pressed/physical flags since the key was released in one-shot mode
                        Log.d(TAG, "Terminal app: BEFORE clear - altOneShot=$altOneShot, altLatchActive=$altLatchActive, altPressed=$altPressed")
                        if (altOneShot) {
                            altOneShot = false
                            altPressed = false
                            altPhysicallyPressed = false
                            Log.d(TAG, "Terminal app: AFTER clear - altOneShot=$altOneShot, altPressed=$altPressed, altPhysical=$altPhysicallyPressed (cleared one-shot)")
                        } else if (altLatchActive) {
                            // Alt latch is active - keep it active for next key press
                            Log.d(TAG, "Terminal app: Alt LATCH is active (latch=$altLatchActive), keeping for next key")
                        } else if (altPressed) {
                            // Alt is physically pressed - keep it active
                            Log.d(TAG, "Terminal app: Alt is physically PRESSED (pressed=$altPressed), keeping active")
                        } else {
                            Log.d(TAG, "Terminal app: Alt was active but no special mode detected")
                        }
                        updateStatusBarText()
                        Log.d(TAG, "=== TERMUX KEY PRESS END (Alt mapping committed) ===")
                        
                        return true
                    }
                } else {
                    Log.d(TAG, "Terminal app: No InputConnection available")
                }
            } else {
                if (isAltActive) {
                    Log.d(TAG, "Terminal app: Alt is active but no Alt mapping for keyCode=$keyCode")
                }
            }
            
            // Check if Ctrl is active and the key has a Ctrl mapping (for navigation actions)
            // Also handle Ctrl+Tab as a special case to send Tab
            if (isCtrlActive) {
                // Special case: Ctrl+Tab should send Tab
                if (keyCode == KeyEvent.KEYCODE_TAB) {
                    Log.d(TAG, "Terminal app: Ctrl+Tab pressed, sending Tab")
                    ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB))
                    ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_TAB))
                    
                    // Clear Ctrl one-shot after use
                    if (ctrlOneShot) {
                        ctrlOneShot = false
                        ctrlPressed = false
                        ctrlPhysicallyPressed = false
                        Log.d(TAG, "Terminal app: CLEARED Ctrl one-shot after Ctrl+Tab")
                    }
                    updateStatusBarText()
                    Log.d(TAG, "=== TERMUX KEY PRESS END (Ctrl+Tab sent) ===")
                    return true
                }
                
                val ctrlMapping = ctrlKeyMap[keyCode]
                if (ctrlMapping != null) {
                    Log.d(TAG, "Terminal app: Ctrl is active and key has Ctrl mapping: type=${ctrlMapping.type}, value=${ctrlMapping.value}")
                    when (ctrlMapping.type) {
                        "action" -> {
                            when (ctrlMapping.value) {
                                "expand_selection_left" -> {
                                    TextSelectionHelper.expandSelectionLeft(ic)
                                    Log.d(TAG, "Terminal app: executed expand_selection_left")
                                }
                                "expand_selection_right" -> {
                                    TextSelectionHelper.expandSelectionRight(ic)
                                    Log.d(TAG, "Terminal app: executed expand_selection_right")
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
                                        ic?.performContextMenuAction(actionId)
                                        Log.d(TAG, "Terminal app: executed action ${ctrlMapping.value} (actionId=$actionId)")
                                    }
                                }
                            }
                            
                            // Clear Ctrl one-shot after use
                            if (ctrlOneShot) {
                                ctrlOneShot = false
                                ctrlPressed = false
                                ctrlPhysicallyPressed = false
                                Log.d(TAG, "Terminal app: CLEARED Ctrl one-shot after action")
                            }
                            updateStatusBarText()
                            Log.d(TAG, "=== TERMUX KEY PRESS END (Ctrl action executed) ===")
                            return true
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
                                ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, mappedKeyCode))
                                ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, mappedKeyCode))
                                Log.d(TAG, "Terminal app: sent mapped keycode $mappedKeyCode (${ctrlMapping.value})")
                                
                                // Clear Ctrl one-shot after use
                                if (ctrlOneShot) {
                                    ctrlOneShot = false
                                    ctrlPressed = false
                                    ctrlPhysicallyPressed = false
                                    Log.d(TAG, "Terminal app: CLEARED Ctrl one-shot after keycode mapping")
                                }
                                updateStatusBarText()
                                Log.d(TAG, "=== TERMUX KEY PRESS END (Ctrl keycode mapping sent) ===")
                                return true
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "Terminal app: Ctrl is active but no Ctrl mapping for keyCode=$keyCode")
                }
            }
            
            // For non-Alt keys (or keys without Alt mapping), send as key events with Ctrl/Shift meta states
            Log.d(TAG, "Terminal app: Sending key event with modifiers")
            if (ic != null && event != null) {
                try {
                    // Terminal apps handle Alt with escape sequences - don't add META_ALT_ON
                    // Only add Ctrl and Shift meta states
                    Log.d(TAG, "Terminal app: Original event metaState=0x${event.metaState.toString(16)}")
                    
                    // Only create new KeyEvent if metaState actually needs to change
                    val needsCtrlMeta = isCtrlActive && (event.metaState and KeyEvent.META_CTRL_ON) == 0
                    val needsShiftMeta = isShiftActive && (event.metaState and KeyEvent.META_SHIFT_ON) == 0
                    
                    val eventToSend = if (needsCtrlMeta || needsShiftMeta) {
                        var metaState = event.metaState
                        if (needsCtrlMeta) {
                            metaState = metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
                            Log.d(TAG, "Terminal app: ADDED Ctrl meta state (now 0x${metaState.toString(16)})")
                        }
                        if (needsShiftMeta) {
                            metaState = metaState or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
                            Log.d(TAG, "Terminal app: ADDED Shift meta state (now 0x${metaState.toString(16)})")
                        }
                        
                        KeyEvent(
                            event.downTime,
                            event.eventTime,
                            event.action,
                            event.keyCode,
                            event.repeatCount,
                            metaState,
                            event.deviceId,
                            event.scanCode,
                            event.flags,
                            event.source
                        )
                    } else {
                        event
                    }
                    
                    ic.sendKeyEvent(eventToSend)
                    Log.d(TAG, "Terminal app: SENT key event $keyCode with final metaState=0x${eventToSend.metaState.toString(16)}")
                    
                    // Clear one-shot modifiers after use (latch modes stay active for multiple uses)
                    // Also clear pressed/physical flags for one-shot since the key was released
                    Log.d(TAG, "Terminal app: BEFORE clear - ctrlOneShot=$ctrlOneShot, ctrlLatch=$ctrlLatchActive")
                    Log.d(TAG, "Terminal app: BEFORE clear - altOneShot=$altOneShot, altLatch=$altLatchActive")
                    Log.d(TAG, "Terminal app: BEFORE clear - shiftOneShot=$shiftOneShot")
                    
                    if (isCtrlActive && ctrlOneShot) {
                        ctrlOneShot = false
                        ctrlPressed = false
                        ctrlPhysicallyPressed = false
                        Log.d(TAG, "Terminal app: CLEARED Ctrl one-shot (now ctrlOneShot=$ctrlOneShot, ctrlPressed=$ctrlPressed)")
                    } else if (isCtrlActive) {
                        Log.d(TAG, "Terminal app: Ctrl was active but NOT one-shot (latch=$ctrlLatchActive, pressed=$ctrlPressed)")
                    }
                    
                    if (isAltActive && altOneShot) {
                        altOneShot = false
                        altPressed = false
                        altPhysicallyPressed = false
                        Log.d(TAG, "Terminal app: CLEARED Alt one-shot (now altOneShot=$altOneShot, altPressed=$altPressed)")
                    } else if (isAltActive) {
                        Log.d(TAG, "Terminal app: Alt was active but NOT one-shot (latch=$altLatchActive, pressed=$altPressed)")
                    }
                    
                    if (isShiftActive && shiftOneShot) {
                        shiftOneShot = false
                        modifierStateController.consumeShiftOneShot()
                        Log.d(TAG, "Terminal app: CLEARED Shift one-shot (now shiftOneShot=$shiftOneShot)")
                    }
                    
                    // Update status bar to reflect cleared modifier state
                    updateStatusBarText()
                    Log.d(TAG, "=== TERMUX KEY PRESS END (key event sent) ===")
                    
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Terminal app: failed to send key event, falling back to super", e)
                    return super.onKeyDown(keyCode, event)
                }
            } else {
                Log.d(TAG, "Terminal app: no InputConnection or event, using super.onKeyDown")
                return super.onKeyDown(keyCode, event)
            }
        }
        
        // Continue with normal IME logic
        KeyboardEventTracker.notifyKeyEvent(keyCode, event, "KEY_DOWN")
        if (!isInputViewShown && isInputViewActive) {
            ensureInputViewCreated()
        }
        
        val isAutoCorrectEnabled = SettingsManager.getAutoCorrectEnabled(this) && !shouldDisableSmartFeatures
        if (
            inputEventRouter.handleTextInputPipeline(
                keyCode = keyCode,
                event = event,
                inputConnection = ic,
                shouldDisableSmartFeatures = shouldDisableSmartFeatures,
                isAutoCorrectEnabled = isAutoCorrectEnabled,
                textInputController = textInputController,
                autoCorrectionManager = autoCorrectionManager
            ) { updateStatusBarText() }
        ) {
            return true
        }
        
        val routingDecision = inputEventRouter.routeEditableFieldKeyDown(
            keyCode = keyCode,
            event = event,
            params = InputEventRouter.EditableFieldKeyDownHandlingParams(
                inputConnection = ic,
                isNumericField = isNumericField,
                isPasswordField = inputContextState.isPasswordField,
                isInputViewActive = isInputViewActive,
                shiftPressed = shiftPressed,
                ctrlPressed = ctrlPressed,
                altPressed = altPressed,
                ctrlLatchActive = ctrlLatchActive,
                altLatchActive = altLatchActive,
                ctrlLatchFromNavMode = ctrlLatchFromNavMode,
                ctrlKeyMap = ctrlKeyMap,
                ctrlOneShot = ctrlOneShot,
                altOneShot = altOneShot,
                clearAltOnSpaceEnabled = clearAltOnSpaceEnabled,
                shiftOneShot = shiftOneShot,
                capsLockEnabled = capsLockEnabled,
                cursorUpdateDelayMs = CURSOR_UPDATE_DELAY
            ),
            controllers = InputEventRouter.EditableFieldKeyDownControllers(
                modifierStateController = modifierStateController,
                symLayoutController = symLayoutController,
                altSymManager = altSymManager,
                variationStateController = variationStateController
            ),
            callbacks = InputEventRouter.EditableFieldKeyDownHandlingCallbacks(
                updateStatusBar = { updateStatusBarText() },
                refreshStatusBar = { refreshStatusBar() },
                disableShiftOneShot = {
                    modifierStateController.consumeShiftOneShot()
                },
                clearAltOneShot = { altOneShot = false },
                clearCtrlOneShot = { 
                    ctrlOneShot = false
                    ctrlPressed = false
                    ctrlPhysicallyPressed = false
                    modifierStateController.ctrlLastPressTime = 0
                },
                getCharacterFromLayout = { code, keyEvent, isShiftPressed ->
                    getCharacterFromLayout(code, keyEvent, isShiftPressed)
                },
                isAlphabeticKey = { code -> isAlphabeticKey(code) },
                callSuper = { super.onKeyDown(keyCode, event) },
                callSuperWithKey = { defaultKeyCode, defaultEvent ->
                    super.onKeyDown(defaultKeyCode, defaultEvent)
                },
                startSpeechRecognition = { startSpeechRecognition() },
                getMapping = { code -> LayoutMappingRepository.getMapping(code) },
                handleMultiTapCommit = { code, mapping, uppercase, inputConnection, allowLongPress ->
                    handleMultiTapCommit(code, mapping, uppercase, inputConnection, allowLongPress)
                },
                isLongPressSuppressed = { code ->
                    multiTapController.isLongPressSuppressed(code)
                },
                showSymbolPicker = { showSymbolPickerPopup() }
            )
        )

        // Sync modifier states back from modifierStateController
        // (routeEditableFieldKeyDown updates the states in modifierStateController)
        altPressed = modifierStateController.altPressed
        altLatchActive = modifierStateController.altLatchActive
        altOneShot = modifierStateController.altOneShot
        altPhysicallyPressed = modifierStateController.altPhysicallyPressed
        ctrlPressed = modifierStateController.ctrlPressed
        ctrlLatchActive = modifierStateController.ctrlLatchActive
        ctrlOneShot = modifierStateController.ctrlOneShot
        ctrlPhysicallyPressed = modifierStateController.ctrlPhysicallyPressed
        ctrlLatchFromNavMode = modifierStateController.ctrlLatchFromNavMode
        
        // Update status bar to reflect modifier state changes
        updateStatusBarText()
        
        // Check for emoji shortcodes after text input
        if (routingDecision == InputEventRouter.EditableFieldRoutingResult.Consume) {
            checkAndShowEmojiShortcode()
        }

        return when (routingDecision) {
            InputEventRouter.EditableFieldRoutingResult.Consume -> {
                true
            }
            InputEventRouter.EditableFieldRoutingResult.CallSuper -> {
                super.onKeyDown(keyCode, event)
            }
            InputEventRouter.EditableFieldRoutingResult.Continue -> {
                super.onKeyDown(keyCode, event)
            }
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // Block D-pad input when emoji shortcode popup is visible
        if (emojiShortcodePopup?.isShowing() == true) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                return true // Consume the event
            }
        }
        
        // Allow D-pad keys to pass through for navigation
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            return super.onKeyUp(keyCode, event)
        }
        
        // Check if we have an editable field at the start (same logic as onKeyDown)
        val info = currentInputEditorInfo
        val ic = currentInputConnection
        val inputType = info?.inputType ?: EditorInfo.TYPE_NULL
        val hasEditableField = ic != null && inputType != EditorInfo.TYPE_NULL
        
        // If NO editable field is active, handle ONLY nav mode Ctrl release
        if (!hasEditableField) {
            val powerShortcutsEnabled = SettingsManager.getPowerShortcutsEnabled(this)
            val launcherShortcutsEnabled = SettingsManager.getLauncherShortcutsEnabled(this)
            return inputEventRouter.handleKeyUpWithNoEditableField(
                keyCode = keyCode,
                event = event,
                ctrlKeyMap = ctrlKeyMap,
                callbacks = InputEventRouter.NoEditableFieldCallbacks(
                    isAlphabeticKey = { code -> isAlphabeticKey(code) },
                    isLauncherPackage = { pkg -> launcherShortcutController.isLauncher(pkg) },
                    handleLauncherShortcut = { key -> launcherShortcutController.handleLauncherShortcut(key) },
                    handlePowerShortcut = { key -> launcherShortcutController.handlePowerShortcut(key) },
                    togglePowerShortcutMode = { message, isNavModeActive ->
                        launcherShortcutController.togglePowerShortcutMode(
                            showToast = { showPowerShortcutToast(it) },
                            isNavModeActive = isNavModeActive
                        )
                    },
                    callSuper = { super.onKeyUp(keyCode, event) },
                    currentInputConnection = { currentInputConnection }
                ),
                powerShortcutsEnabled = powerShortcutsEnabled
            )
        }
        
        // Continue with normal IME logic for text fields
        val inputConnection = currentInputConnection ?: return super.onKeyUp(keyCode, event)
        
        // Always notify the tracker (even when the event is consumed)
        KeyboardEventTracker.notifyKeyEvent(keyCode, event, "KEY_UP")
        
        // Handle Shift release for double-tap
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            if (shiftPressed) {
                val result = modifierStateController.handleShiftKeyUp(keyCode)
                if (result.shouldUpdateStatusBar) {
                    updateStatusBarText()
                }
            }
            return super.onKeyUp(keyCode, event)
        }
        
        // Handle Ctrl release for double-tap
        if (isCtrlKey(keyCode)) {
            if (ctrlPressed) {
                val result = modifierStateController.handleCtrlKeyUp(keyCode)
                if (result.shouldUpdateStatusBar) {
                    updateStatusBarText()
                }
            }
            return super.onKeyUp(keyCode, event)
        }
        
        // Handle Alt release for double-tap
        if (keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            if (altPressed) {
                val result = modifierStateController.handleAltKeyUp(keyCode)
                if (result.shouldUpdateStatusBar) {
                    updateStatusBarText()
                }
            }
            return super.onKeyUp(keyCode, event)
        }
        
        // Handle SYM key release (nothing to do; it is a toggle)
        if (keyCode == KEYCODE_SYM) {
            // Consumiamo l'evento
            return true
        }
        
        if (symLayoutController.handleKeyUp(keyCode, shiftPressed)) {
            return true
        }
        
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Aggiunge una nuova mappatura Alt+tasto -> carattere.
     */
    fun addAltKeyMapping(keyCode: Int, character: String) {
        altSymManager.addAltKeyMapping(keyCode, character)
    }

    /**
     * Rimuove una mappatura Alt+tasto esistente.
     */
    fun removeAltKeyMapping(keyCode: Int) {
        altSymManager.removeAltKeyMapping(keyCode)
    }
    
    /**
     * Intercepts trackpad/touch-sensitive keyboard motion events.
     * The Unihertz Titan 2 keyboard can act as a trackpad, sending MotionEvents
     * for scrolling, cursor movement, and gestures.
     */
    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        val handled = motionEventController.handle(event)
        if (handled != null) {
            return handled
        }

        return super.onGenericMotionEvent(event)
    }
}