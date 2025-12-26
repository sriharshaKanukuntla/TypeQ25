package it.srik.TypeQ25.data.variation

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Loads character variations from JSON assets or custom file.
 */
object VariationRepository {
    private const val TAG = "VariationRepository"
    private const val CUSTOM_VARIATIONS_FILE = "variations.json"

    fun loadVariations(assets: AssetManager): Map<Char, List<String>> {
        val variationsMap = mutableMapOf<Char, List<String>>()
        return try {
            val filePath = "common/variations/variations.json"
            val inputStream = assets.open(filePath)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val variationsObject = jsonObject.getJSONObject("variations")

            val keys = variationsObject.keys()
            while (keys.hasNext()) {
                val baseChar = keys.next()
                if (baseChar.length == 1) {
                    val variationsArray = variationsObject.getJSONArray(baseChar)
                    val variationsList = mutableListOf<String>()
                    for (i in 0 until variationsArray.length()) {
                        variationsList.add(variationsArray.getString(i))
                    }
                    variationsMap[baseChar[0]] = variationsList
                }
            }
            variationsMap
        } catch (e: Exception) {
            Log.e(TAG, "Error loading character variations", e)
            // Fallback to basic variations
            variationsMap['e'] = listOf("è", "é", "€")
            variationsMap['a'] = listOf("à", "á", "ä")
            variationsMap
        }
    }

    /**
     * Load variations with custom file support (for settings screen).
     * Checks for custom variations.json in filesDir first, then falls back to assets.
     */
    fun loadVariations(context: Context, assets: AssetManager): Map<Char, List<String>> {
        // Try to load custom variations first
        val customFile = File(context.filesDir, CUSTOM_VARIATIONS_FILE)
        if (customFile.exists()) {
            try {
                val jsonString = customFile.readText()
                val jsonObject = JSONObject(jsonString)
                val variationsObject = jsonObject.getJSONObject("variations")
                
                val variationsMap = mutableMapOf<Char, List<String>>()
                val keys = variationsObject.keys()
                while (keys.hasNext()) {
                    val baseChar = keys.next()
                    if (baseChar.length == 1) {
                        val variationsArray = variationsObject.getJSONArray(baseChar)
                        val variationsList = mutableListOf<String>()
                        for (i in 0 until variationsArray.length()) {
                            variationsList.add(variationsArray.getString(i))
                        }
                        variationsMap[baseChar[0]] = variationsList
                    }
                }
                Log.d(TAG, "Loaded custom variations from filesDir")
                return variationsMap
            } catch (e: Exception) {
                Log.e(TAG, "Error loading custom variations, falling back to default", e)
            }
        }
        
        // Fall back to default variations from assets
        return loadVariations(assets)
    }
}

