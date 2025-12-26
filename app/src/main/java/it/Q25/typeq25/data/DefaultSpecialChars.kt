package it.srik.TypeQ25.data

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Provides default special characters to display in the variations bar
 * when no other suggestions or variations are available.
 */
object DefaultSpecialChars {
    private const val TAG = "DefaultSpecialChars"
    private const val CUSTOM_VARIATIONS_FILE = "variations.json"
    private var defaultChars: List<String> = emptyList()
    private var context: Context? = null
    
    /**
     * Loads default special characters from custom variations file or JSON asset file.
     */
    fun load(context: Context, assets: AssetManager) {
        this.context = context
        reload(assets)
    }
    
    /**
     * Reloads default special characters (useful after customization changes).
     */
    fun reload(assets: AssetManager) {
        // Try to load from custom variations file first
        val customFile = context?.let { File(it.filesDir, CUSTOM_VARIATIONS_FILE) }
        if (customFile?.exists() == true) {
            try {
                val jsonString = customFile.readText()
                val jsonObject = JSONObject(jsonString)
                val variationsObject = jsonObject.getJSONObject("variations")
                
                if (variationsObject.has("default")) {
                    val defaultArray = variationsObject.getJSONArray("default")
                    val chars = mutableListOf<String>()
                    
                    // Load up to 8 custom default characters
                    for (i in 0 until minOf(defaultArray.length(), 8)) {
                        chars.add(defaultArray.getString(i))
                    }
                    
                    defaultChars = chars
                    Log.d(TAG, "Loaded ${defaultChars.size} custom default special characters")
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading custom default special characters", e)
            }
        }
        
        // Fall back to default_special_chars.json from assets
        try {
            val filePath = "common/default_special_chars.json"
            val jsonString = assets.open(filePath).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            
            val defaultBarArray = jsonObject.getJSONArray("default_bar")
            val chars = mutableListOf<String>()
            
            // Load up to 8 characters from default_bar
            for (i in 0 until minOf(defaultBarArray.length(), 8)) {
                chars.add(defaultBarArray.getString(i))
            }
            
            defaultChars = chars
            Log.d(TAG, "Loaded ${defaultChars.size} default special characters from assets")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading default special characters", e)
            // Fallback to hardcoded list (8 characters)
            defaultChars = listOf(
                "€", "$", "£", "¥", "%", "&", "=", "~"
            )
        }
    }
    
    /**
     * Returns the list of default special characters.
     * These are shown in the variations bar when no other content is available.
     */
    fun getDefaultChars(): List<String> {
        return defaultChars
    }
    
    /**
     * Returns a subset of default characters (useful for limiting display).
     */
    fun getDefaultChars(limit: Int): List<String> {
        return defaultChars.take(limit)
    }
}
