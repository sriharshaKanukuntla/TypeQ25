package it.srik.TypeQ25.core

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import it.srik.TypeQ25.inputmethod.AutoCorrector

class AutoCorrectionManager(
    private val context: Context
) {

    companion object {
        private const val TAG = "AutoCorrectionManager"
    }

    fun handleBackspaceUndo(
        keyCode: Int,
        inputConnection: InputConnection?,
        isAutoCorrectEnabled: Boolean,
        onStatusBarUpdate: () -> Unit
    ): Boolean {
        if (!isAutoCorrectEnabled || keyCode != KeyEvent.KEYCODE_DEL || inputConnection == null) {
            return false
        }

        val correction = AutoCorrector.getLastCorrection() ?: return false

        val textBeforeCursor = inputConnection.getTextBeforeCursor(
            correction.correctedWord.length + 4,
            0
        ) ?: return false

        // Split off any trailing whitespace/punctuation between the corrected
        // word and the cursor. `core` should then end with the corrected word.
        val trailing = textBeforeCursor.takeLastWhile { !it.isLetterOrDigit() }
        val core = textBeforeCursor.dropLast(trailing.length)

        if (!core.endsWith(correction.correctedWord)) {
            return false
        }

        // Delete the corrected word and its trailing run
        // Restore the original word followed by the same trailing text.
        val charsToDelete = correction.correctedWord.length + trailing.length

        // Use batch edit to ensure operations are atomic and prevent issues with
        // interleaved input processing that could cause duplicate characters
        inputConnection.beginBatchEdit()
        try {
            inputConnection.deleteSurroundingText(charsToDelete, 0)
            inputConnection.commitText(correction.originalWord + trailing, 1)
        } finally {
            inputConnection.endBatchEdit()
        }
        
        AutoCorrector.undoLastCorrection()
        onStatusBarUpdate()
        Log.d(TAG, "Auto-correction undone: '${correction.correctedWord}' → '${correction.originalWord}'")
        return true
    }

    fun handleSpaceOrPunctuation(
        keyCode: Int,
        event: KeyEvent?,
        inputConnection: InputConnection?,
        isAutoCorrectEnabled: Boolean,
        onStatusBarUpdate: () -> Unit
    ): Boolean {
        if (!isAutoCorrectEnabled || inputConnection == null) {
            return false
        }

        val isSpace = keyCode == KeyEvent.KEYCODE_SPACE
        val isPunctuation = event?.unicodeChar != null &&
            event.unicodeChar != 0 &&
            event.unicodeChar.toChar() in ".,;:!?()[]{}\"'"

        if (!isSpace && !isPunctuation) {
            return false
        }

        val textBeforeCursor = inputConnection.getTextBeforeCursor(100, 0)
        val correction = AutoCorrector.processText(textBeforeCursor, context = context) ?: return false

        val (wordToReplace, correctedWord) = correction
        
        // Use batch edit to ensure operations are atomic
        inputConnection.beginBatchEdit()
        try {
            inputConnection.deleteSurroundingText(wordToReplace.length, 0)
            inputConnection.commitText(correctedWord, 1)
            AutoCorrector.recordCorrection(wordToReplace, correctedWord)

            when {
                isSpace -> inputConnection.commitText(" ", 1)
                isPunctuation && event?.unicodeChar != null && event.unicodeChar != 0 -> {
                    inputConnection.commitText(event.unicodeChar.toChar().toString(), 1)
                }
            }
        } finally {
            inputConnection.endBatchEdit()
        }

        onStatusBarUpdate()
        return true
    }

    fun handleAcceptOrResetOnOtherKeys(
        keyCode: Int,
        event: KeyEvent?,
        isAutoCorrectEnabled: Boolean
    ) {
        if (!isAutoCorrectEnabled || keyCode == KeyEvent.KEYCODE_DEL) {
            return
        }

        AutoCorrector.acceptLastCorrection()
        if (event != null && event.unicodeChar != 0) {
            val char = event.unicodeChar.toChar()
            if (char.isLetterOrDigit()) {
                AutoCorrector.clearRejectedWords()
            }
        }
    }
}

