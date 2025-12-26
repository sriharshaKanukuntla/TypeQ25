package it.srik.TypeQ25.inputmethod

import android.view.inputmethod.InputConnection
import it.srik.TypeQ25.SettingsManager

/**
 * Helper for handling auto-capitalization logic.
 * Each entry point toggles Shift through the provided callbacks so only one
 * source of truth tracks the modifier state.
 */
object AutoCapitalizeHelper {
    private var firstLetterShiftRequested = false

    private fun shouldAutoCapitalizeFirstLetter(inputConnection: InputConnection): Boolean {
        val textBeforeCursor = inputConnection.getTextBeforeCursor(1, 0) ?: return false
        val textAfterCursor = inputConnection.getTextAfterCursor(1, 0) ?: ""
        val isCursorAtStart = textBeforeCursor.isEmpty()
        val isFieldEmpty = isCursorAtStart && textAfterCursor.isEmpty()
        val isAfterNewline = textBeforeCursor.lastOrNull() == '\n'
        return isFieldEmpty || isAfterNewline
    }

    private fun clearFirstLetterShift(
        disableShift: () -> Boolean,
        onUpdateStatusBar: () -> Unit
    ) {
        if (firstLetterShiftRequested) {
            val changed = disableShift()
            firstLetterShiftRequested = false
            if (changed) onUpdateStatusBar()
        }
    }

    private fun applyAutoCapitalizeFirstLetter(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        enableShift: () -> Boolean,
        disableShift: () -> Boolean,
        onUpdateStatusBar: () -> Unit
    ) {
        val autoCapEnabled = SettingsManager.getAutoCapitalizeFirstLetter(context)
        if (!autoCapEnabled || shouldDisableSmartFeatures) {
            clearFirstLetterShift(disableShift, onUpdateStatusBar)
            return
        }

        val ic = inputConnection ?: run {
            clearFirstLetterShift(disableShift, onUpdateStatusBar)
            return
        }

        val shouldCapitalize = shouldAutoCapitalizeFirstLetter(ic)
        if (shouldCapitalize) {
            if (enableShift()) {
                firstLetterShiftRequested = true
                onUpdateStatusBar()
            }
        } else {
            clearFirstLetterShift(disableShift, onUpdateStatusBar)
        }
    }

    fun checkAndEnableAutoCapitalize(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        enableShift: () -> Boolean,
        disableShift: () -> Boolean = { false },
        onUpdateStatusBar: () -> Unit
    ) {
        applyAutoCapitalizeFirstLetter(
            context = context,
            inputConnection = inputConnection,
            shouldDisableSmartFeatures = shouldDisableSmartFeatures,
            enableShift = enableShift,
            disableShift = disableShift,
            onUpdateStatusBar = onUpdateStatusBar
        )
    }

    fun checkAutoCapitalizeOnSelectionChange(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        enableShift: () -> Boolean,
        disableShift: () -> Boolean,
        onUpdateStatusBar: () -> Unit
    ) {
        if (newSelStart != newSelEnd) {
            if (disableShift()) onUpdateStatusBar()
            return
        }
        applyAutoCapitalizeFirstLetter(
            context = context,
            inputConnection = inputConnection,
            shouldDisableSmartFeatures = shouldDisableSmartFeatures,
            enableShift = enableShift,
            disableShift = disableShift,
            onUpdateStatusBar = onUpdateStatusBar
        )
    }

    fun checkAutoCapitalizeOnRestart(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        enableShift: () -> Boolean,
        disableShift: () -> Boolean = { false },
        onUpdateStatusBar: () -> Unit
    ) {
        applyAutoCapitalizeFirstLetter(
            context = context,
            inputConnection = inputConnection,
            shouldDisableSmartFeatures = shouldDisableSmartFeatures,
            enableShift = enableShift,
            disableShift = disableShift,
            onUpdateStatusBar = onUpdateStatusBar
        )
    }

    fun enableAfterPunctuation(
        inputConnection: InputConnection?,
        onEnableShift: () -> Boolean,
        onUpdateStatusBar: () -> Unit
    ): Boolean {
        val textBeforeCursor = inputConnection?.getTextBeforeCursor(100, 0) ?: return false
        if (textBeforeCursor.isEmpty()) return false

        val lastChar = textBeforeCursor.last()
        val shouldCapitalize = when (lastChar) {
            '.' -> textBeforeCursor.length >= 2 && textBeforeCursor[textBeforeCursor.length - 2] != '.'
            '!', '?' -> true
            else -> false
        }

        if (shouldCapitalize && onEnableShift()) {
            onUpdateStatusBar()
            return true
        }
        return false
    }

    fun enableAfterEnter(
        context: android.content.Context,
        inputConnection: InputConnection?,
        shouldDisableSmartFeatures: Boolean,
        onEnableShift: () -> Boolean,
        onUpdateStatusBar: () -> Unit
    ) {
        applyAutoCapitalizeFirstLetter(
            context = context,
            inputConnection = inputConnection,
            shouldDisableSmartFeatures = shouldDisableSmartFeatures,
            enableShift = onEnableShift,
            disableShift = { false },
            onUpdateStatusBar = onUpdateStatusBar
        )
    }
}
