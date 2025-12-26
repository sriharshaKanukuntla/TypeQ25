package it.srik.TypeQ25.inputmethod

import android.util.Log
import android.view.View
import android.view.inputmethod.InputConnection

/**
 * Handles clicks on variation buttons.
 */
object VariationButtonHandler {
    private const val TAG = "VariationButtonHandler"
    
    /**
     * Callback called when a variation is selected.
     */
    interface OnVariationSelectedListener {
        /**
         * Called when a variation is selected.
         * @param variation The selected variation character
         */
        fun onVariationSelected(variation: String)
    }
    
    /**
     * Creates a listener for a variation button.
     * When clicked, deletes character before cursor and inserts the variation.
     */
    fun createVariationClickListener(
        variation: String,
        inputConnection: InputConnection?,
        listener: OnVariationSelectedListener? = null,
        shouldDeleteBeforeInsert: Boolean = true
    ): View.OnClickListener {
        return View.OnClickListener {
            Log.d(TAG, "Click on variation button: $variation (shouldDeleteBeforeInsert=$shouldDeleteBeforeInsert, hasInputConnection=${inputConnection != null})")
            
            if (inputConnection == null) {
                Log.e(TAG, "No inputConnection available to insert variation '$variation'")
                return@OnClickListener
            }
            
            try {
                // Delete character before cursor (backspace) only for character variations
                if (shouldDeleteBeforeInsert) {
                    val deleted = inputConnection.deleteSurroundingText(1, 0)
                    Log.d(TAG, "Delete before insert: $deleted")
                }
                
                // Insert variation
                val committed = inputConnection.commitText(variation, 1)
                Log.d(TAG, "Variation '$variation' committed: $committed")
                
                // Notify listener if present
                listener?.onVariationSelected(variation)
            } catch (e: Exception) {
                Log.e(TAG, "Error inserting variation '$variation'", e)
            }
        }
    }
}

