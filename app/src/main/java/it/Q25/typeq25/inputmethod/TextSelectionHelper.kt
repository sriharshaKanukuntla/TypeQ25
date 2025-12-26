package it.srik.TypeQ25.inputmethod

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.util.Log

/**
 * Helper for handling text selection operations.
 */
object TextSelectionHelper {
    private const val TAG = "TextSelectionHelper"
    
    /**
     * Expands selection one character to the left.
     * If there's no selection, creates a selection of one character to the left of cursor.
     */
    fun expandSelectionLeft(inputConnection: InputConnection): Boolean {
        try {
            // Ottieni la selezione corrente usando ExtractedTextRequest
            val extractedText = inputConnection.getExtractedText(
                ExtractedTextRequest().apply {
                    flags = android.view.inputmethod.ExtractedText.FLAG_SELECTING
                },
                0
            )
            
            if (extractedText == null) {
                // Fallback: usa getTextBeforeCursor e getTextAfterCursor per stimare la posizione
                val textBefore = inputConnection.getTextBeforeCursor(1000, 0)
                val textAfter = inputConnection.getTextAfterCursor(1, 0)
                
                if (textBefore != null && textBefore.isNotEmpty()) {
                    // If there's text after, there's probably a selection
                    // For simplicity, assume cursor is at end of text before
                    val currentPos = textBefore.length
                    val newStart = currentPos - 1
                    
                    if (newStart >= 0) {
                        // Create or expand selection one character to the left
                        inputConnection.setSelection(newStart, currentPos)
                        Log.d(TAG, "expandSelectionLeft: selection created/expanded to [$newStart, $currentPos]")
                        return true
                    }
                }
                return false
            }
            
            val selectionStart = extractedText.selectionStart
            val selectionEnd = extractedText.selectionEnd
            
            if (selectionStart < 0 || selectionEnd < 0) {
                Log.d(TAG, "expandSelectionLeft: unable to get selection")
                return false
            }
            
            // Verify if we can expand left
            // If selectionStart is already 0, we can't expand further
            if (selectionStart <= 0) {
                Log.d(TAG, "expandSelectionLeft: selection already at start of text, can't expand")
                return false
            }
            
            // Get text before cursor to verify there's text
            val textBefore = inputConnection.getTextBeforeCursor(1, 0)
            
            if (textBefore != null && textBefore.isNotEmpty()) {
                val newStart: Int
                
                if (selectionStart == selectionEnd) {
                    // No selection: create selection of one character to the left
                    newStart = selectionStart - 1
                } else {
                    // There's already a selection: expand it one character to the left
                    newStart = selectionStart - 1
                }
                
                // Ensure newStart is not negative and is different from selectionStart
                if (newStart >= 0 && newStart < selectionStart) {
                    inputConnection.setSelection(newStart, selectionEnd)
                    Log.d(TAG, "expandSelectionLeft: selection expanded from [$selectionStart, $selectionEnd] to [$newStart, $selectionEnd]")
                    return true
                } else {
                    Log.d(TAG, "expandSelectionLeft: unable to expand (newStart: $newStart, selectionStart: $selectionStart)")
                    return false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in expandSelectionLeft", e)
        }
        return false
    }
    
    /**
     * Expands selection one character to the right.
     * If there's no selection, creates a selection of one character to the right of cursor.
     */
    fun expandSelectionRight(inputConnection: InputConnection): Boolean {
        try {
            // Ottieni la selezione corrente usando ExtractedTextRequest
            val extractedText = inputConnection.getExtractedText(
                ExtractedTextRequest().apply {
                    flags = android.view.inputmethod.ExtractedText.FLAG_SELECTING
                },
                0
            )
            
            if (extractedText == null) {
                // Fallback: usa getTextBeforeCursor e getTextAfterCursor per stimare la posizione
                val textBefore = inputConnection.getTextBeforeCursor(1000, 0)
                val textAfter = inputConnection.getTextAfterCursor(1000, 0)
                
                if (textAfter != null && textAfter.isNotEmpty()) {
                    // If there's text after, we can expand selection
                    val currentPos = textBefore?.length ?: 0
                    val newEnd = currentPos + 1
                    
                    // Create or expand selection one character to the right
                    inputConnection.setSelection(currentPos, newEnd)
                    Log.d(TAG, "expandSelectionRight: selection created/expanded to [$currentPos, $newEnd]")
                    return true
                }
                return false
            }
            
            val selectionStart = extractedText.selectionStart
            val selectionEnd = extractedText.selectionEnd
            
            if (selectionStart < 0 || selectionEnd < 0) {
                Log.d(TAG, "expandSelectionRight: unable to get selection")
                return false
            }
            
            // Verify total text length
            val fullText = extractedText.text?.toString() ?: ""
            val textLength = fullText.length
            
            // If selectionEnd is already at end of text, we can't expand further
            if (selectionEnd >= textLength) {
                Log.d(TAG, "expandSelectionRight: selection already at end of text (selectionEnd: $selectionEnd, textLength: $textLength), can't expand")
                return false
            }
            
            // Get text after cursor to verify there's text
            val textAfter = inputConnection.getTextAfterCursor(1, 0)
            
            if (textAfter != null && textAfter.isNotEmpty()) {
                val newEnd: Int
                
                if (selectionStart == selectionEnd) {
                    // No selection: create selection of one character to the right
                    newEnd = selectionEnd + 1
                } else {
                    // There's already a selection: expand it one character to the right
                    newEnd = selectionEnd + 1
                }
                
                // Verify newEnd doesn't exceed text length and is different from selectionEnd
                if (newEnd <= textLength && newEnd > selectionEnd) {
                    inputConnection.setSelection(selectionStart, newEnd)
                    Log.d(TAG, "expandSelectionRight: selection expanded from [$selectionStart, $selectionEnd] to [$selectionStart, $newEnd]")
                    return true
                } else {
                    Log.d(TAG, "expandSelectionRight: unable to expand (newEnd: $newEnd, selectionEnd: $selectionEnd, textLength: $textLength)")
                    return false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in expandSelectionRight", e)
        }
        return false
    }
    
    /**
     * Moves cursor one character to the left (without creating a selection).
     * This is safer than using DPAD keys as it only affects the text field, not UI navigation.
     * 
     * @return true if cursor was moved, false if already at start or error occurred
     */
    fun moveCursorLeft(inputConnection: InputConnection): Boolean {
        try {
            // Get current cursor position using ExtractedTextRequest
            val extractedText = inputConnection.getExtractedText(
                ExtractedTextRequest().apply {
                    flags = android.view.inputmethod.ExtractedText.FLAG_SELECTING
                },
                0
            )
            
            if (extractedText == null) {
                // Fallback: use getTextBeforeCursor to estimate position
                val textBefore = inputConnection.getTextBeforeCursor(1000, 0)
                
                if (textBefore != null && textBefore.isNotEmpty()) {
                    val currentPos = textBefore.length
                    val newPos = currentPos - 1
                    
                    if (newPos >= 0) {
                        // Move cursor without creating selection (same start and end)
                        inputConnection.setSelection(newPos, newPos)
                        Log.d(TAG, "moveCursorLeft: cursor moved from $currentPos to $newPos")
                        return true
                    }
                }
                return false
            }
            
            val selectionStart = extractedText.selectionStart
            val selectionEnd = extractedText.selectionEnd
            
            if (selectionStart < 0 || selectionEnd < 0) {
                Log.d(TAG, "moveCursorLeft: unable to get cursor position")
                return false
            }
            
            // If there's a selection, collapse it to the start position first
            val currentPos = if (selectionStart != selectionEnd) {
                selectionStart // Collapse selection to start
            } else {
                selectionStart // Already at cursor position
            }
            
            // Can't move left if already at start
            if (currentPos <= 0) {
                Log.d(TAG, "moveCursorLeft: cursor already at start of text")
                return false
            }
            
            val newPos = currentPos - 1
            
            // Move cursor without creating selection (same start and end)
            inputConnection.setSelection(newPos, newPos)
            Log.d(TAG, "moveCursorLeft: cursor moved from $currentPos to $newPos")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error in moveCursorLeft", e)
        }
        return false
    }
    
    /**
     * Moves cursor one character to the right (without creating a selection).
     * This is safer than using DPAD keys as it only affects the text field, not UI navigation.
     * 
     * @return true if cursor was moved, false if already at end or error occurred
     */
    fun moveCursorRight(inputConnection: InputConnection): Boolean {
        try {
            // Get current cursor position using ExtractedTextRequest
            val extractedText = inputConnection.getExtractedText(
                ExtractedTextRequest().apply {
                    flags = android.view.inputmethod.ExtractedText.FLAG_SELECTING
                },
                0
            )
            
            if (extractedText == null) {
                // Fallback: use getTextBeforeCursor and getTextAfterCursor to estimate position
                val textBefore = inputConnection.getTextBeforeCursor(1000, 0)
                val textAfter = inputConnection.getTextAfterCursor(1, 0)
                
                if (textAfter != null && textAfter.isNotEmpty()) {
                    val currentPos = textBefore?.length ?: 0
                    val newPos = currentPos + 1
                    
                    // Move cursor without creating selection (same start and end)
                    inputConnection.setSelection(newPos, newPos)
                    Log.d(TAG, "moveCursorRight: cursor moved from $currentPos to $newPos")
                    return true
                }
                return false
            }
            
            val selectionStart = extractedText.selectionStart
            val selectionEnd = extractedText.selectionEnd
            
            if (selectionStart < 0 || selectionEnd < 0) {
                Log.d(TAG, "moveCursorRight: unable to get cursor position")
                return false
            }
            
            // Verify total text length
            val fullText = extractedText.text?.toString() ?: ""
            val textLength = fullText.length
            
            // If there's a selection, collapse it to the end position first
            val currentPos = if (selectionStart != selectionEnd) {
                selectionEnd // Collapse selection to end
            } else {
                selectionEnd // Already at cursor position
            }
            
            // Can't move right if already at end
            if (currentPos >= textLength) {
                Log.d(TAG, "moveCursorRight: cursor already at end of text (pos: $currentPos, length: $textLength)")
                return false
            }
            
            val newPos = currentPos + 1
            
            // Move cursor without creating selection (same start and end)
            inputConnection.setSelection(newPos, newPos)
            Log.d(TAG, "moveCursorRight: cursor moved from $currentPos to $newPos")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error in moveCursorRight", e)
        }
        return false
    }
    
    /**
     * Deletes last word before cursor.
     */
    fun deleteLastWord(inputConnection: InputConnection): Boolean {
        try {
            // Get text before cursor (up to 100 characters)
            val textBeforeCursor = inputConnection.getTextBeforeCursor(100, 0)
            
            if (textBeforeCursor != null && textBeforeCursor.isNotEmpty()) {
                // Find last word (separated by spaces or at start of text)
                var endIndex = textBeforeCursor.length
                var startIndex = endIndex
                
                // Find end of last word (ignore spaces at end)
                while (startIndex > 0 && textBeforeCursor[startIndex - 1].isWhitespace()) {
                    startIndex--
                }
                
                // Find start of last word (first space or start of text)
                while (startIndex > 0 && !textBeforeCursor[startIndex - 1].isWhitespace()) {
                    startIndex--
                }
                
                // Calculate how many characters to delete
                val charsToDelete = endIndex - startIndex
                
                if (charsToDelete > 0) {
                    // Delete last word (including any spaces after)
                    inputConnection.deleteSurroundingText(charsToDelete, 0)
                    Log.d(TAG, "deleteLastWord: deleted $charsToDelete characters")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in deleteLastWord", e)
        }
        return false
    }
}

