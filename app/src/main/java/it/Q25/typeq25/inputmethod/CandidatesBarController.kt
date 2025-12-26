package it.srik.TypeQ25.inputmethod

import android.content.Context
import android.widget.LinearLayout
import android.view.inputmethod.InputConnection
import it.srik.TypeQ25.SettingsManager

/**
 * Coordinates the two StatusBarController instances (full input view vs
 * candidates-only view) so the IME service can treat them as a single surface.
 */
class CandidatesBarController(
    private val context: Context
) {

    private val inputStatusBar = StatusBarController(context, StatusBarController.Mode.FULL)
    private val candidatesStatusBar = StatusBarController(context, StatusBarController.Mode.CANDIDATES_ONLY)

    var onVariationSelectedListener: VariationButtonHandler.OnVariationSelectedListener? = null
        set(value) {
            field = value
            inputStatusBar.onVariationSelectedListener = value
            candidatesStatusBar.onVariationSelectedListener = value
        }

    var onCursorMovedListener: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onCursorMovedListener = value
            candidatesStatusBar.onCursorMovedListener = value
        }

    var onRequestWindowResize: (() -> Unit)? = null
        set(value) {
            field = value
            inputStatusBar.onRequestWindowResize = value
            candidatesStatusBar.onRequestWindowResize = value
        }

    fun getInputView(emojiMapText: String = ""): LinearLayout {
        val useMinimalUi = SettingsManager.getUseMinimalUi(context)
        return if (useMinimalUi) {
            candidatesStatusBar.getOrCreateLayout(emojiMapText)
        } else {
            inputStatusBar.getOrCreateLayout(emojiMapText)
        }
    }

    fun getCandidatesView(emojiMapText: String = ""): LinearLayout {
        return candidatesStatusBar.getOrCreateLayout(emojiMapText)
    }

    fun setForceMinimalUi(force: Boolean) {
        inputStatusBar.setForceMinimalUi(force)
    }
    
    /**
     * Clears all cached layouts, forcing them to be recreated on next access.
     * Call this when the minimal UI setting changes.
     */
    fun clearLayouts() {
        inputStatusBar.clearLayout()
        candidatesStatusBar.clearLayout()
    }

    fun updateStatusBars(
        snapshot: StatusBarController.StatusSnapshot,
        emojiMapText: String,
        inputConnection: InputConnection?,
        symMappings: Map<Int, String>?
    ) {
        // Only update the active controller based on minimal UI setting
        // This prevents the inactive controller from showing UI elements
        val useMinimalUi = SettingsManager.getUseMinimalUi(context)
        if (useMinimalUi) {
            candidatesStatusBar.update(snapshot, emojiMapText, inputConnection, symMappings)
        } else {
            inputStatusBar.update(snapshot, emojiMapText, inputConnection, symMappings)
        }
    }
    
    /**
     * Gets the currently active layout (based on minimal UI setting) without caching
     */
    fun getCurrentLayout(emojiMapText: String = ""): LinearLayout {
        val useMinimalUi = SettingsManager.getUseMinimalUi(context)
        return if (useMinimalUi) {
            candidatesStatusBar.getLayout() ?: candidatesStatusBar.getOrCreateLayout(emojiMapText)
        } else {
            inputStatusBar.getLayout() ?: inputStatusBar.getOrCreateLayout(emojiMapText)
        }
    }
}


