package it.srik.TypeQ25.inputmethod

import android.view.KeyEvent

/**
 * Handles modifier key state management and double-tap detection
 * for Ctrl (latch) and Alt (latch).
 */
class ModifierKeyHandler(
    private val doubleTapThreshold: Long = 500L,
    private val isCtrlKeyFunc: ((Int) -> Boolean)? = null
) {
    
    private fun isCtrlKey(keyCode: Int): Boolean {
        return isCtrlKeyFunc?.invoke(keyCode) ?: (
            keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT
        )
    }
    data class CtrlState(
        var pressed: Boolean = false,
        var oneShot: Boolean = false,
        var latchActive: Boolean = false,
        var physicallyPressed: Boolean = false,
        var lastPressTime: Long = 0,  // Track press time instead of release (onKeyUp unreliable)
        var latchFromNavMode: Boolean = false,
        var tapCount: Int = 0  // Track consecutive taps for triple-press exit
    )

    data class AltState(
        var pressed: Boolean = false,
        var oneShot: Boolean = false,
        var latchActive: Boolean = false,
        var physicallyPressed: Boolean = false,
        var lastPressTime: Long = 0  // Track press time instead of release (onKeyUp unreliable)
    )

    data class ModifierKeyResult(
        val shouldConsume: Boolean = false,
        val shouldUpdateStatusBar: Boolean = false,
        val shouldRefreshStatusBar: Boolean = false
    )

    // ========== Ctrl Handling ==========

    fun handleCtrlKeyDown(
        keyCode: Int,
        state: CtrlState,
        isInputViewActive: Boolean,
        isConsecutiveTap: Boolean,
        onNavModeDeactivated: (() -> Unit)? = null
    ): ModifierKeyResult {
        if (!isCtrlKey(keyCode)) {
            return ModifierKeyResult()
        }

        // Allow repeated presses for double-tap and triple-tap
        state.physicallyPressed = true
        val currentTime = System.currentTimeMillis()
        val timeSinceLastPress = currentTime - state.lastPressTime
        val allowDoubleTap = isConsecutiveTap && timeSinceLastPress < doubleTapThreshold && state.lastPressTime > 0

        when {
            state.latchActive -> {
                // Latch active: single CTRL press disables it
                state.latchActive = false
                state.latchFromNavMode = false
                state.oneShot = false
                state.tapCount = 0
                state.lastPressTime = 0
                state.pressed = false
                state.physicallyPressed = false
                onNavModeDeactivated?.invoke()
                return ModifierKeyResult(shouldUpdateStatusBar = true)
            }
            state.oneShot -> {
                // One-shot active: check if this is a double-tap to enable latch
                if (allowDoubleTap) {
                    // Double-tap detected: activate latch
                    state.oneShot = false
                    state.latchActive = true
                    state.lastPressTime = currentTime
                    state.tapCount = 0
                    state.pressed = true
                    return ModifierKeyResult(shouldUpdateStatusBar = true)
                } else {
                    // Single press after timeout: just disable one-shot
                    state.oneShot = false
                    state.lastPressTime = 0
                    state.tapCount = 0
                    state.pressed = false
                    state.physicallyPressed = false
                    return ModifierKeyResult(shouldUpdateStatusBar = true)
                }
            }
            else -> {
                // Check for double-tap to enable latch
                if (allowDoubleTap) {
                    state.latchActive = true
                    state.lastPressTime = currentTime
                    state.tapCount = 0
                    state.pressed = true
                    return ModifierKeyResult(shouldUpdateStatusBar = true)
                } else {
                    // Single tap: enable one-shot (CTRL active until next non-CTRL key)
                    state.oneShot = true
                    state.lastPressTime = currentTime
                    state.tapCount = 0
                    state.pressed = true
                    return ModifierKeyResult(shouldUpdateStatusBar = true)
                }
            }
        }
    }

    fun handleCtrlKeyUp(keyCode: Int, state: CtrlState): ModifierKeyResult {
        if (!isCtrlKey(keyCode)) {
            return ModifierKeyResult()
        }

        if (state.pressed) {
            // Note: onKeyUp may not be reliable, but keep for compatibility
            state.pressed = false
            state.physicallyPressed = false
            return ModifierKeyResult(shouldUpdateStatusBar = true)
        }
        return ModifierKeyResult()
    }

    // ========== Alt Handling ==========

    fun handleAltKeyDown(
        keyCode: Int,
        state: AltState,
        isConsecutiveTap: Boolean
    ): ModifierKeyResult {
        if (keyCode != KeyEvent.KEYCODE_ALT_LEFT && keyCode != KeyEvent.KEYCODE_ALT_RIGHT) {
            return ModifierKeyResult()
        }

        if (state.pressed) {
            return ModifierKeyResult()
        }

        state.physicallyPressed = true
        val currentTime = System.currentTimeMillis()
        val timeSinceLastPress = currentTime - state.lastPressTime
        val allowDoubleTap = isConsecutiveTap && timeSinceLastPress < doubleTapThreshold && state.lastPressTime > 0

        when {
            state.latchActive -> {
                // Latch active: single tap disables it
                state.latchActive = false
                state.lastPressTime = 0
                return ModifierKeyResult(shouldUpdateStatusBar = true)
            }
            state.oneShot -> {
                // One-shot active: check for double-tap
                if (allowDoubleTap) {
                    state.oneShot = false
                    state.latchActive = true
                    state.lastPressTime = currentTime
                    return ModifierKeyResult(shouldUpdateStatusBar = true)
                } else {
                    // Single tap: disable one-shot
                    state.oneShot = false
                    state.lastPressTime = 0
                    return ModifierKeyResult(shouldUpdateStatusBar = true)
                }
            }
            else -> {
                // Check for double-tap to enable latch
                if (allowDoubleTap) {
                    state.latchActive = true
                    state.lastPressTime = currentTime
                    return ModifierKeyResult(shouldUpdateStatusBar = true)
                } else {
                    // Single tap: enable one-shot
                    state.oneShot = true
                    state.lastPressTime = currentTime
                    return ModifierKeyResult(shouldUpdateStatusBar = true)
                }
            }
        }
    }

    fun handleAltKeyUp(keyCode: Int, state: AltState): ModifierKeyResult {
        if (keyCode != KeyEvent.KEYCODE_ALT_LEFT && keyCode != KeyEvent.KEYCODE_ALT_RIGHT) {
            return ModifierKeyResult()
        }

        if (state.pressed) {
            // Note: onKeyUp may not be reliable, but keep for compatibility
            state.pressed = false
            state.physicallyPressed = false
            return ModifierKeyResult(shouldUpdateStatusBar = true)
        }
        return ModifierKeyResult()
    }

    // ========== Reset Helpers ==========

    fun resetCtrlState(state: CtrlState, preserveNavMode: Boolean = false) {
        if (!preserveNavMode || !state.latchFromNavMode) {
            state.latchActive = false
            state.latchFromNavMode = false
        }
        state.pressed = false
        state.oneShot = false
        state.lastPressTime = 0
    }

    fun resetAltState(state: AltState) {
        state.pressed = false
        state.oneShot = false
        state.latchActive = false
        state.lastPressTime = 0
    }
}
