package it.srik.TypeQ25.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages clipboard history by monitoring system clipboard changes
 * and storing recent items for quick access.
 */
class ClipboardHistoryManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ClipboardHistory"
        private const val PREFS_NAME = "clipboard_history"
        private const val KEY_HISTORY = "history_items"
        private const val MAX_HISTORY_SIZE = 20
        private const val MAX_ITEM_LENGTH = 500 // Truncate very long items
    }
    
    data class ClipboardItem(
        val text: String,
        val timestamp: Long,
        val preview: String = text.take(100) // First 100 chars for display
    )
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val history = mutableListOf<ClipboardItem>()
    
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var lastClipText: String? = null
    
    init {
        loadHistory()
    }
    
    /**
     * Starts monitoring the system clipboard for changes.
     */
    fun startMonitoring() {
        if (clipboardListener != null) return
        
        clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
            try {
                val clip = clipboardManager.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0).text?.toString()
                    if (!text.isNullOrBlank() && text != lastClipText) {
                        addItem(text)
                        lastClipText = text
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling clipboard change", e)
            }
        }
        
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        Log.d(TAG, "Clipboard monitoring started")
        
        // Add current clipboard item if exists
        try {
            val currentClip = clipboardManager.primaryClip
            if (currentClip != null && currentClip.itemCount > 0) {
                val text = currentClip.getItemAt(0).text?.toString()
                if (!text.isNullOrBlank()) {
                    lastClipText = text
                    // Don't add to history yet, just track it
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting initial clipboard", e)
        }
    }
    
    /**
     * Stops monitoring the system clipboard.
     */
    fun stopMonitoring() {
        clipboardListener?.let {
            clipboardManager.removePrimaryClipChangedListener(it)
            clipboardListener = null
            Log.d(TAG, "Clipboard monitoring stopped")
        }
    }
    
    /**
     * Adds a new item to clipboard history.
     */
    private fun addItem(text: String) {
        val trimmedText = text.trim().take(MAX_ITEM_LENGTH)
        if (trimmedText.isEmpty()) return
        
        // Remove duplicate if exists
        history.removeAll { it.text == trimmedText }
        
        // Add new item at the beginning
        val item = ClipboardItem(
            text = trimmedText,
            timestamp = System.currentTimeMillis(),
            preview = generatePreview(trimmedText)
        )
        history.add(0, item)
        
        // Keep only MAX_HISTORY_SIZE items
        while (history.size > MAX_HISTORY_SIZE) {
            history.removeAt(history.size - 1)
        }
        
        saveHistory()
        Log.d(TAG, "Added clipboard item: ${item.preview}")
    }
    
    /**
     * Generates a preview string for display (removes line breaks, limits length).
     */
    private fun generatePreview(text: String): String {
        return text
            .replace("\n", " ")
            .replace("\r", "")
            .replace("\\s+".toRegex(), " ")
            .trim()
            .take(100)
    }
    
    /**
     * Returns the clipboard history.
     */
    fun getHistory(): List<ClipboardItem> {
        return history.toList()
    }
    
    /**
     * Clears all clipboard history.
     */
    fun clearHistory() {
        history.clear()
        saveHistory()
        Log.d(TAG, "Clipboard history cleared")
    }
    
    /**
     * Removes a specific item from history.
     */
    fun removeItem(index: Int) {
        if (index in history.indices) {
            history.removeAt(index)
            saveHistory()
        }
    }
    
    /**
     * Sets clipboard to a specific history item.
     */
    fun setClipboard(text: String) {
        try {
            val clip = ClipData.newPlainText("text", text)
            clipboardManager.setPrimaryClip(clip)
            lastClipText = text
        } catch (e: Exception) {
            Log.e(TAG, "Error setting clipboard", e)
        }
    }
    
    /**
     * Saves history to SharedPreferences.
     */
    private fun saveHistory() {
        try {
            val jsonArray = JSONArray()
            for (item in history) {
                val jsonObject = JSONObject()
                jsonObject.put("text", item.text)
                jsonObject.put("timestamp", item.timestamp)
                jsonArray.put(jsonObject)
            }
            prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving history", e)
        }
    }
    
    /**
     * Loads history from SharedPreferences.
     */
    private fun loadHistory() {
        try {
            val jsonString = prefs.getString(KEY_HISTORY, null) ?: return
            val jsonArray = JSONArray(jsonString)
            
            history.clear()
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val text = jsonObject.getString("text")
                val timestamp = jsonObject.getLong("timestamp")
                
                history.add(
                    ClipboardItem(
                        text = text,
                        timestamp = timestamp,
                        preview = generatePreview(text)
                    )
                )
            }
            Log.d(TAG, "Loaded ${history.size} clipboard items")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading history", e)
        }
    }
}
