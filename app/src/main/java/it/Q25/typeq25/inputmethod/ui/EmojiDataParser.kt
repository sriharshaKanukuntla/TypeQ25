package it.srik.TypeQ25.inputmethod.ui

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parser for emoji-data library format
 * Converts Unicode codepoints to emoji characters
 */
class EmojiDataParser(private val context: Context) {
    
    private val emojiList: List<EmojiData>
    private val categories: Map<String, List<String>>
    private val prefs: SharedPreferences = context.getSharedPreferences("emoji_prefs", Context.MODE_PRIVATE)
    private val CUSTOM_EMOJIS_PREFIX = "custom_emojis_"
    
    init {
        // Load and parse emoji.json
        val emojiJsonString = context.assets.open("emoji.json").bufferedReader().use { it.readText() }
        val emojiArray = JSONArray(emojiJsonString)
        
        emojiList = (0 until emojiArray.length()).map { i ->
            val obj = emojiArray.getJSONObject(i)
            EmojiData(
                emoji = hexToEmoji(obj.getString("unified")),
                shortName = obj.getString("short_name"),
                shortNames = getStringArrayOrEmpty(obj, "short_names"),
                category = obj.optString("category", ""),
                subcategory = obj.optString("subcategory", ""),
                sortOrder = obj.optInt("sort_order", 0)
            )
        }
        
        // Load and parse categories.json
        val categoriesJsonString = context.assets.open("categories.json").bufferedReader().use { it.readText() }
        val categoriesObject = JSONObject(categoriesJsonString)
        
        categories = mutableMapOf<String, List<String>>().apply {
            categoriesObject.keys().forEach { categoryKey ->
                val categoryObj = categoriesObject.getJSONObject(categoryKey)
                val allShortNames = mutableListOf<String>()
                
                categoryObj.keys().forEach { subcategoryKey ->
                    val subcategoryArray = categoryObj.getJSONArray(subcategoryKey)
                    for (i in 0 until subcategoryArray.length()) {
                        allShortNames.add(subcategoryArray.getString(i))
                    }
                }
                
                this[categoryKey] = allShortNames
            }
        }
    }
    
    /**
     * Get all emojis for a category
     * Filters out skin tone variations and ZWJ compound emojis
     * to show only simple, universally-supported base emojis
     * Includes custom emojis added by the user for this category
     */
    fun getEmojisForCategory(categoryName: String): List<String> {
        val shortNames = categories[categoryName] ?: return emptyList()
        val shortNameSet = shortNames.toSet()
        
        val standardEmojis = emojiList
            .filter { it.shortName in shortNameSet }
            .filter { !isComplexEmoji(it.emoji) }
            .sortedBy { it.sortOrder }
            .map { it.emoji }
        
        // Add custom emojis for this category
        val customEmojis = getCustomEmojisForCategory(categoryName)
        return standardEmojis + customEmojis
    }
    
    /**
     * Check if an emoji is complex (contains skin tone modifiers or ZWJ sequences)
     * These often don't render correctly and should be filtered out
     */
    private fun isComplexEmoji(emoji: String): Boolean {
        return emoji.codePoints().anyMatch { codePoint ->
            // Skin tone modifiers: U+1F3FB-U+1F3FF
            codePoint in 0x1F3FB..0x1F3FF ||
            // Zero Width Joiner (ZWJ): U+200D - used for compound emojis
            codePoint == 0x200D
        }
    }
    
    /**
     * Get all category names
     */
    fun getCategoryNames(): List<String> {
        return categories.keys.toList()
    }
    
    /**
     * Get custom emojis added by the user for a specific category
     * Returns only the emoji characters (not the shortcodes)
     */
    fun getCustomEmojisForCategory(categoryName: String): List<String> {
        val key = CUSTOM_EMOJIS_PREFIX + categoryName
        val customString = prefs.getString(key, "") ?: ""
        return if (customString.isBlank()) {
            emptyList()
        } else {
            customString.split("|").filter { it.isNotBlank() }.map { entry ->
                // Format is "emoji:shortcode", extract just the emoji
                entry.substringBefore(":")
            }
        }
    }
    
    /**
     * Get custom emoji with its shortcode for a specific category
     * Returns list of Pair(emoji, shortcode)
     */
    fun getCustomEmojisWithShortcodes(categoryName: String): List<Pair<String, String>> {
        val key = CUSTOM_EMOJIS_PREFIX + categoryName
        val customString = prefs.getString(key, "") ?: ""
        return if (customString.isBlank()) {
            emptyList()
        } else {
            customString.split("|").filter { it.isNotBlank() }.mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) {
                    Pair(parts[0], parts[1])
                } else {
                    null
                }
            }
        }
    }
    
    /**
     * Get all custom emojis from all categories with their shortcodes
     * Returns map of shortcode -> emoji
     */
    fun getAllCustomEmojisWithShortcodes(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val categoryNames = listOf(
            "Smileys & Emotion",
            "People & Body",
            "Animals & Nature",
            "Food & Drink",
            "Travel & Places",
            "Activities",
            "Objects",
            "Symbols",
            "Flags"
        )
        
        categoryNames.forEach { categoryName ->
            getCustomEmojisWithShortcodes(categoryName).forEach { (emoji, shortcode) ->
                result[shortcode] = emoji
            }
        }
        
        return result
    }
    
    /**
     * Add a custom emoji to a specific category with a shortcode name
     * @param emoji The emoji/symbol to add
     * @param shortcode The shortcode name (without colons)
     * @param categoryName The category to add to
     */
    fun addCustomEmoji(emoji: String, shortcode: String, categoryName: String): Boolean {
        if (emoji.isBlank() || shortcode.isBlank()) return false
        
        // Validate shortcode format (alphanumeric and underscore only)
        if (!shortcode.matches(Regex("[a-zA-Z0-9_]+"))) return false
        
        val key = CUSTOM_EMOJIS_PREFIX + categoryName
        val customString = prefs.getString(key, "") ?: ""
        val currentEntries = if (customString.isBlank()) {
            emptyList()
        } else {
            customString.split("|").filter { it.isNotBlank() }
        }
        
        // Check if emoji or shortcode already exists
        val existingEmojis = currentEntries.map { it.substringBefore(":") }
        val existingShortcodes = currentEntries.mapNotNull { 
            val parts = it.split(":")
            if (parts.size == 2) parts[1] else null
        }
        
        if (existingEmojis.contains(emoji)) return false // Emoji already exists
        if (existingShortcodes.contains(shortcode.lowercase())) return false // Shortcode already exists
        
        val newEntry = "$emoji:${shortcode.lowercase()}"
        val updatedEntries = currentEntries + newEntry
        
        prefs.edit()
            .putString(key, updatedEntries.joinToString("|"))
            .apply()
        return true
    }
    
    /**
     * Remove a custom emoji from a specific category
     */
    fun removeCustomEmoji(emoji: String, categoryName: String) {
        val key = CUSTOM_EMOJIS_PREFIX + categoryName
        val customString = prefs.getString(key, "") ?: ""
        val currentEntries = if (customString.isBlank()) {
            emptyList()
        } else {
            customString.split("|").filter { it.isNotBlank() }
        }
        
        // Remove entries that start with the emoji
        val updatedEntries = currentEntries.filter { !it.startsWith("$emoji:") }
        
        prefs.edit()
            .putString(key, updatedEntries.joinToString("|"))
            .apply()
    }
    
    /**
     * Get the shortcode for a custom emoji in a specific category
     */
    fun getCustomEmojiShortcode(emoji: String, categoryName: String): String? {
        return getCustomEmojisWithShortcodes(categoryName)
            .find { it.first == emoji }?.second
    }
    
    /**
     * Check if an emoji is a custom emoji in a specific category
     */
    fun isCustomEmoji(emoji: String, categoryName: String): Boolean {
        return getCustomEmojisForCategory(categoryName).contains(emoji)
    }
    
    /**
     * Search emojis by name
     * Filters out skin tone variations and ZWJ compound emojis
     */
    fun searchEmojis(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        
        val lowerQuery = query.lowercase()
        return emojiList
            .filter { emoji ->
                emoji.shortName.contains(lowerQuery) ||
                emoji.shortNames.any { it.contains(lowerQuery) }
            }
            .filter { !isComplexEmoji(it.emoji) }
            .sortedBy { it.sortOrder }
            .map { it.emoji }
    }
    
    /**
     * Convert hex codepoint string to emoji character
     * Handles single and multi-codepoint emojis
     */
    private fun hexToEmoji(hexCode: String): String {
        return try {
            hexCode.split("-")
                .map { Integer.parseInt(it, 16) }
                .flatMap { Character.toChars(it).toList() }
                .joinToString("") { it.toString() }
        } catch (e: Exception) {
            // Fallback for parsing errors
            ""
        }
    }
    
    private fun getStringArrayOrEmpty(obj: JSONObject, key: String): List<String> {
        return try {
            val array = obj.getJSONArray(key)
            (0 until array.length()).map { array.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    data class EmojiData(
        val emoji: String,
        val shortName: String,
        val shortNames: List<String>,
        val category: String,
        val subcategory: String,
        val sortOrder: Int
    )
}
