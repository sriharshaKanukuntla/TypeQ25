package it.srik.TypeQ25.core

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import it.srik.TypeQ25.SettingsManager
import it.srik.TypeQ25.inputmethod.AutoCapitalizeHelper

/**
 * Orchestrates text-level helpers such as double-space-to-period and
 * auto-capitalization triggers. Keeps state like double-space timing isolated
 * from the IME service.
 */
class TextInputController(
    private val context: Context,
    private val modifierStateController: ModifierStateController,
    private val doubleTapThreshold: Long
) {

    private var lastSpacePressTime: Long = 0L

    fun handleDoubleSpaceToPeriod(
        keyCode: Int,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        onStatusBarUpdate: () -> Unit
    ): Boolean {
        val isSpace = keyCode == KeyEvent.KEYCODE_SPACE
        if (isSpace && !shouldDisableSmartFeatures) {
            val doubleSpaceToPeriodEnabled = SettingsManager.getDoubleSpaceToPeriod(context)
            if (doubleSpaceToPeriodEnabled) {
                val currentTime = System.currentTimeMillis()
                val isDoubleTap = lastSpacePressTime > 0 &&
                    (currentTime - lastSpacePressTime) < doubleTapThreshold

            if (isDoubleTap && inputConnection != null) {
                val textBeforeCursor = inputConnection.getTextBeforeCursor(100, 0)
                if (textBeforeCursor != null && textBeforeCursor.endsWith(" ")) {
                    if (textBeforeCursor.length >= 2 && textBeforeCursor[textBeforeCursor.length - 2] == ' ') {
                        // Multiple spaces already present: ignore
                        } else {
                            var lastCharIndex = textBeforeCursor.length - 2
                            while (lastCharIndex >= 0 && textBeforeCursor[lastCharIndex].isWhitespace()) {
                                lastCharIndex--
                            }

                            // Allow period insertion after letters, numbers, and closing symbols
                            // but not after existing punctuation like . ! ? , ; :
                            val shouldReplace = if (lastCharIndex >= 0) {
                                val lastChar = textBeforeCursor[lastCharIndex]
                                lastChar.isLetterOrDigit() || lastChar in setOf(')', ']', '}', '"', '\'', '%', '$', '€', '£', '¥')
                            } else {
                                false
                            }
                            if (shouldReplace) {
                                inputConnection.deleteSurroundingText(1, 0)
                                inputConnection.commitText(". ", 1)

                                modifierStateController.shiftOneShot = true
                                onStatusBarUpdate()

                                lastSpacePressTime = 0
                                return true
                            }
                        }
                    }
                }
                lastSpacePressTime = currentTime
            } else {
                lastSpacePressTime = 0
            }
        } else {
            if (lastSpacePressTime > 0) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastSpacePressTime >= doubleTapThreshold) {
                    lastSpacePressTime = 0
                }
            }
        }
        return false
    }

    fun handleAutoCapAfterPeriod(
        keyCode: Int,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        onStatusBarUpdate: () -> Unit
    ) {
        val autoCapitalizeAfterPeriodEnabled =
            SettingsManager.getAutoCapitalizeAfterPeriod(context) && !shouldDisableSmartFeatures
        if (autoCapitalizeAfterPeriodEnabled &&
            keyCode == KeyEvent.KEYCODE_SPACE &&
            !modifierStateController.shiftOneShot
        ) {
            AutoCapitalizeHelper.enableAfterPunctuation(
                inputConnection,
                onEnableShift = { modifierStateController.requestShiftOneShotFromAutoCap() },
                onUpdateStatusBar = onStatusBarUpdate
            )
        }
    }

    fun handleAutoCapAfterEnter(
        keyCode: Int,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        onStatusBarUpdate: () -> Unit
    ) {
        if (keyCode == KeyEvent.KEYCODE_ENTER && !shouldDisableSmartFeatures) {
            AutoCapitalizeHelper.enableAfterEnter(
                context,
                inputConnection,
                shouldDisableSmartFeatures,
                onEnableShift = { modifierStateController.requestShiftOneShotFromAutoCap() },
                onUpdateStatusBar = onStatusBarUpdate
            )
        }
    }
}

