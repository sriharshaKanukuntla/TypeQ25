package it.srik.TypeQ25.emoji

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import it.srik.TypeQ25.inputmethod.ui.EmojiDataParser

/**
 * Manages emoji and symbol shortcode conversion (e.g., :smile: -> 😊, :tm: -> ™).
 * Loads shortcode mappings from emoji.json, symbol_shortcodes.json, and custom emojis.
 */
class EmojiShortcodeManager(private val context: Context) {
    
    companion object {
        private const val TAG = "EmojiShortcode"
        private const val EMOJI_DATA_FILE = "emoji.json"
        private const val SYMBOL_SHORTCODES_FILE = "symbol_shortcodes.json"
    }
    
    private val shortcodeMap = mutableMapOf<String, String>()
    private val reverseMap = mutableMapOf<String, String>() // emoji/symbol -> shortcode for display
    private val emojiDataParser by lazy { EmojiDataParser(context) }
    
    init {
        loadShortcodes()
    }
    
    /**
     * Reload shortcodes (call this after custom emojis are added/removed)
     */
    fun reloadShortcodes() {
        shortcodeMap.clear()
        reverseMap.clear()
        loadShortcodes()
    }
    
    /**
     * Converts hex codepoints to emoji character
     */
    private fun hexToEmoji(hex: String): String {
        val codePoints = hex.split("-").map { it.toInt(16) }
        val chars = codePoints.flatMap { Character.toChars(it).toList() }.toCharArray()
        return String(chars)
    }
    
    /**
     * Loads emoji and symbol shortcodes from assets.
     */
    private fun loadShortcodes() {
        // Load emoji shortcodes from emoji.json
        try {
            val json = context.assets.open(EMOJI_DATA_FILE).bufferedReader().use { it.readText() }
            val emojiArray = JSONArray(json)
            
            for (i in 0 until emojiArray.length()) {
                val emojiObj = emojiArray.getJSONObject(i)
                val unified = emojiObj.getString("unified")
                val emoji = hexToEmoji(unified)
                
                // Get short_names array (multiple aliases)
                val shortNamesArray = emojiObj.optJSONArray("short_names")
                if (shortNamesArray != null) {
                    for (j in 0 until shortNamesArray.length()) {
                        val shortcode = shortNamesArray.getString(j)
                        shortcodeMap[shortcode] = emoji
                        
                        // Use the first shortcode as the primary one in reverse map
                        if (j == 0 && !reverseMap.containsKey(emoji)) {
                            reverseMap[emoji] = shortcode
                        }
                    }
                } else {
                    // Fallback to short_name if short_names doesn't exist
                    val shortcode = emojiObj.optString("short_name", "")
                    if (shortcode.isNotEmpty()) {
                        shortcodeMap[shortcode] = emoji
                        if (!reverseMap.containsKey(emoji)) {
                            reverseMap[emoji] = shortcode
                        }
                    }
                }
            }
            
            Log.d(TAG, "Loaded ${shortcodeMap.size} emoji shortcodes from emoji.json")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading emoji shortcodes from emoji.json", e)
        }
        
        // Load symbol shortcodes (symbols override emojis for duplicates)
        try {
            val json = context.assets.open(SYMBOL_SHORTCODES_FILE).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(json)
            
            val keys = jsonObject.keys()
            var symbolCount = 0
            var overriddenCount = 0
            while (keys.hasNext()) {
                val shortcode = keys.next()
                val symbol = jsonObject.getString(shortcode)
                // Symbols override emojis if both exist
                if (shortcodeMap.containsKey(shortcode)) {
                    reverseMap.remove(shortcodeMap[shortcode])
                    overriddenCount++
                }
                shortcodeMap[shortcode] = symbol
                reverseMap[symbol] = shortcode
                symbolCount++
            }
            
            Log.d(TAG, "Loaded $symbolCount symbol shortcodes ($overriddenCount overridden, total: ${shortcodeMap.size})")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading symbol shortcodes", e)
        }
        
        // Load custom emoji shortcodes (last, so they take precedence)
        try {
            val customEmojis = emojiDataParser.getAllCustomEmojisWithShortcodes()
            var customCount = 0
            customEmojis.forEach { (shortcode, emoji) ->
                shortcodeMap[shortcode] = emoji
                reverseMap[emoji] = shortcode
                customCount++
            }
            Log.d(TAG, "Loaded $customCount custom emoji shortcodes (total: ${shortcodeMap.size})")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading custom emoji shortcodes", e)
        }
    }
    
    /**
     * Converts a shortcode to its emoji or symbol (without colons).
     * @param shortcode The shortcode without colons (e.g., "smile" or "tm")
     * @return The emoji/symbol string or null if not found
     */
    fun getEmoji(shortcode: String): String? {
        return shortcodeMap[shortcode.lowercase()]
    }
    
    /**
     * Gets the shortcode for an emoji or symbol.
     * @param character The emoji or symbol character
     * @return The shortcode without colons, or null if not found
     */
    fun getShortcode(character: String): String? {
        return reverseMap[character]
    }
    
    /**
     * Searches for shortcodes matching a prefix.
     * @param prefix The search prefix (without colons)
     * @param limit Maximum number of results
     * @return List of matching shortcodes with their emojis/symbols
     */
    fun searchShortcodes(prefix: String, limit: Int = 10): List<Pair<String, String>> {
        if (prefix.isEmpty()) return emptyList()
        
        val lowercasePrefix = prefix.lowercase()
        return shortcodeMap
            .filter { it.key.startsWith(lowercasePrefix) }
            .toList()
            .sortedBy { it.first.length } // Shorter matches first
            .take(limit)
            .map { (shortcode, character) -> Pair(character, shortcode) } // Return as (character, shortcode)
    }
    
    /**
     * Checks if a shortcode exists.
     */
    fun hasShortcode(shortcode: String): Boolean {
        return shortcodeMap.containsKey(shortcode.lowercase())
    }
    
    /**
     * Gets all available shortcodes.
     */
    fun getAllShortcodes(): List<String> {
        return shortcodeMap.keys.sorted()
    }
    
    /**
     * Gets the total number of shortcodes.
     */
    fun getShortcodeCount(): Int {
        return shortcodeMap.size
    }
    
    /**
     * Extracts the current shortcode being typed from text before cursor.
     * Returns the shortcode (without colons) and its start position, or null.
     */
    fun extractCurrentShortcode(textBeforeCursor: String): Pair<String, Int>? {
        if (textBeforeCursor.isEmpty()) return null
        
        // Find the last ':' character
        val lastColonIndex = textBeforeCursor.lastIndexOf(':')
        if (lastColonIndex == -1) return null
        
        // Extract text after the last ':'
        val afterColon = textBeforeCursor.substring(lastColonIndex + 1)
        
        // Check if it contains only valid shortcode characters (letters, numbers, underscore)
        if (!afterColon.matches(Regex("[a-zA-Z0-9_]+"))) return null
        
        // Don't suggest if shortcode is too long (probably not a shortcode)
        if (afterColon.length > 30) return null
        
        return Pair(afterColon, lastColonIndex)
    }
}
