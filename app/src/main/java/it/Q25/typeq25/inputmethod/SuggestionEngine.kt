package it.srik.TypeQ25.inputmethod

import android.content.Context
import android.util.Log
import it.srik.TypeQ25.SettingsManager

/**
 * Provides word suggestions based on partial input.
 * Supports both offline (privacy-focused) and online (system-based) prediction modes.
 * Uses existing auto-correction dictionaries and tracks user's word frequency.
 * Integrates with Android's spell checker for correction suggestions.
 */
class SuggestionEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "SuggestionEngine"
        private const val MAX_SUGGESTIONS = 3
        private const val MIN_WORD_LENGTH = 2
        private const val USER_HISTORY_PREFS = "user_word_history"
    }
    
    // Word frequency map from dictionaries (word -> frequency score)
    private val dictionaryWords = mutableMapOf<String, Int>()
    
    // User's personal word usage (word -> usage count)
    private val userHistory = mutableMapOf<String, Int>()
    
    // Recently used words get priority
    private val recentWords = mutableListOf<String>()
    private val maxRecentWords = 50
    
    // Android system spell checker
    private val androidSpellChecker = AndroidSpellChecker(context)
    
    init {
        loadUserHistory()
        loadSuggestionsFromAssets()
    }
    
    /**
     * Loads additional suggestion words from dedicated JSON files.
     */
    private fun loadSuggestionsFromAssets() {
        try {
            val assetManager = context.assets
            val suggestionsFile = "common/autocorrect/suggestions_en.json"
            
            if (assetManager.list("common/autocorrect")?.contains("suggestions_en.json") == true) {
                val json = assetManager.open(suggestionsFile).bufferedReader().use { it.readText() }
                val suggestions = org.json.JSONObject(json)
                
                for (key in suggestions.keys()) {
                    val frequency = suggestions.getInt(key)
                    dictionaryWords[key.lowercase()] = frequency
                }
                
                Log.d(TAG, "Loaded ${suggestions.length()} suggestions from assets")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading suggestions from assets", e)
        }
    }
    
    /**
     * Loads word frequency data from auto-correction dictionaries.
     * Called when dictionaries are loaded/changed.
     * Only loads words from enabled auto-correction languages.
     */
    fun loadDictionaryWords(corrections: Map<String, Map<String, String>>) {
        Log.d(TAG, "loadDictionaryWords: loading from ${corrections.size} language dictionaries")
        dictionaryWords.clear()
        
        // Get enabled languages from settings
        val enabledLanguages = it.srik.TypeQ25.SettingsManager.getAutoCorrectEnabledLanguages(context)
        Log.d(TAG, "loadDictionaryWords: enabled languages = $enabledLanguages")
        
        for ((lang, languageCorrections) in corrections) {
            // Skip languages that are not enabled
            if (lang !in enabledLanguages) {
                Log.d(TAG, "loadDictionaryWords: skipping disabled language=$lang")
                continue
            }
            
            Log.d(TAG, "loadDictionaryWords: processing language=$lang with ${languageCorrections.size} corrections")
            for ((original, corrected) in languageCorrections) {
                // Add both original and corrected forms
                // Give corrected forms higher weight
                dictionaryWords[original.lowercase()] = dictionaryWords.getOrDefault(original.lowercase(), 0) + 1
                dictionaryWords[corrected.lowercase()] = dictionaryWords.getOrDefault(corrected.lowercase(), 0) + 2
                
                // Also add individual words if they contain spaces
                original.split(" ").forEach { word ->
                    if (word.length >= MIN_WORD_LENGTH) {
                        dictionaryWords[word.lowercase()] = dictionaryWords.getOrDefault(word.lowercase(), 0) + 1
                    }
                }
                corrected.split(" ").forEach { word ->
                    if (word.length >= MIN_WORD_LENGTH) {
                        dictionaryWords[word.lowercase()] = dictionaryWords.getOrDefault(word.lowercase(), 0) + 1
                    }
                }
            }
        }
        
        Log.d(TAG, "loadDictionaryWords: loaded ${dictionaryWords.size} words from dictionaries")
    }
    
    /**
     * Gets word suggestions for the given prefix.
     * Uses only offline dictionary and history for fast, real-time suggestions.
     * @param prefix The partial word typed so far
     * @param limit Maximum number of suggestions to return
     * @return List of suggested words, sorted by relevance
     */
    fun getSuggestions(prefix: String, limit: Int = MAX_SUGGESTIONS): List<String> {
        if (prefix.length < MIN_WORD_LENGTH) {
            Log.d(TAG, "getSuggestions: prefix too short: '$prefix'")
            return emptyList()
        }
        
        // Use only offline suggestions for fast, real-time response
        // Spell checker is too slow for real-time typing (1000ms timeout per word)
        return getOfflineSuggestions(prefix, limit)
    }
    
    /**
     * Checks if a word is spelled correctly and gets spelling suggestions if needed.
     * Uses Android system spell checker from settings.
     * @param word The complete word to check
     * @return SpellCheckResult with correctness status and suggestions
     */
    fun checkSpelling(word: String): AndroidSpellChecker.SpellCheckResult {
        // Add user's personal words to temporary check
        val isInUserHistory = userHistory.containsKey(word.lowercase())
        if (isInUserHistory) {
            // User's personal words are considered correct
            return AndroidSpellChecker.SpellCheckResult(word, true, emptyList())
        }
        
        return androidSpellChecker.checkWord(word)
    }
    
    /**
     * Gets suggestions using offline dictionary and user history.
     */
    private fun getOfflineSuggestions(prefix: String, limit: Int): List<String> {
        
        val lowerPrefix = prefix.lowercase()
        val suggestions = mutableListOf<ScoredWord>()
        
        Log.d(TAG, "getSuggestions: searching for prefix='$prefix', dictSize=${dictionaryWords.size}, userHistorySize=${userHistory.size}")
        
        // Search user history (high priority)
        for ((word, count) in userHistory) {
            if (word.lowercase().startsWith(lowerPrefix) && word.lowercase() != lowerPrefix) {
                val score = calculateScore(word, prefix, count * 10, isUserWord = true)
                suggestions.add(ScoredWord(word, score))
            }
        }
        
        // Search recent words (medium priority)
        for (word in recentWords) {
            if (word.lowercase().startsWith(lowerPrefix) && word.lowercase() != lowerPrefix) {
                val existingScore = suggestions.find { it.word.lowercase() == word.lowercase() }?.score
                if (existingScore == null) {
                    val score = calculateScore(word, prefix, 50, isRecent = true)
                    suggestions.add(ScoredWord(word, score))
                }
            }
        }
        
        // Search dictionary words (lower priority)
        for ((word, freq) in dictionaryWords) {
            if (word.startsWith(lowerPrefix) && word != lowerPrefix) {
                val existingScore = suggestions.find { it.word.lowercase() == word.lowercase() }?.score
                if (existingScore == null) {
                    val score = calculateScore(word, prefix, freq)
                    suggestions.add(ScoredWord(word, score))
                }
            }
        }
        
        Log.d(TAG, "getSuggestions: found ${suggestions.size} raw suggestions")
        
        // Sort by score (descending) and return top N
        val result = suggestions
            .sortedByDescending { it.score }
            .take(limit)
            .map { matchCase(it.word, prefix) }
        
        Log.d(TAG, "getSuggestions: returning $result")
        return result
    }
    
    /**
     * Records a word that was typed/selected by the user.
     * Updates frequency and recent words list.
     */
    fun recordWord(word: String) {
        if (word.length < MIN_WORD_LENGTH) return
        
        val lowerWord = word.lowercase()
        
        // Update user history
        userHistory[lowerWord] = userHistory.getOrDefault(lowerWord, 0) + 1
        
        // Update recent words
        recentWords.remove(lowerWord)
        recentWords.add(0, lowerWord)
        if (recentWords.size > maxRecentWords) {
            recentWords.removeAt(recentWords.size - 1)
        }
        
        // Save periodically (every 10 words)
        if (userHistory.values.sum() % 10 == 0) {
            saveUserHistory()
        }
    }
    
    /**
     * Clears all suggestions state (for new input field, etc.)
     */
    fun clear() {
        // Keep user history and recent words, just clear any temporary state
    }
    
    /**
     * Saves user word history to SharedPreferences.
     */
    fun saveUserHistory() {
        try {
            val prefs = context.getSharedPreferences(USER_HISTORY_PREFS, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            
            // Save top 500 most used words
            val topWords = userHistory.entries
                .sortedByDescending { it.value }
                .take(500)
            
            editor.clear()
            for ((word, count) in topWords) {
                editor.putInt(word, count)
            }
            editor.apply()
            
            Log.d(TAG, "Saved ${topWords.size} user words")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user history", e)
        }
    }
    
    /**
     * Loads user word history from SharedPreferences.
     */
    private fun loadUserHistory() {
        try {
            val prefs = context.getSharedPreferences(USER_HISTORY_PREFS, Context.MODE_PRIVATE)
            userHistory.clear()
            
            for ((word, _) in prefs.all) {
                val count = prefs.getInt(word, 0)
                if (count > 0) {
                    userHistory[word] = count
                }
            }
            
            Log.d(TAG, "Loaded ${userHistory.size} user words")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading user history", e)
        }
    }
    
    /**
     * Calculates relevance score for a suggestion.
     */
    private fun calculateScore(
        word: String,
        prefix: String,
        baseFrequency: Int,
        isUserWord: Boolean = false,
        isRecent: Boolean = false
    ): Int {
        var score = baseFrequency
        
        // Boost user words significantly
        if (isUserWord) {
            score *= 5
        }
        
        // Boost recent words
        if (isRecent) {
            score *= 2
        }
        
        // Prefer shorter words (more likely to be completed sooner)
        val lengthPenalty = (word.length - prefix.length) * 2
        score -= lengthPenalty
        
        // Boost exact prefix matches at word boundary
        if (word.lowercase().startsWith(prefix.lowercase())) {
            score += 10
        }
        
        return maxOf(0, score)
    }
    
    /**
     * Cleans up resources (saves user history).
     * Call when the suggestion engine is no longer needed.
     */
    fun cleanup() {
        saveUserHistory()
        androidSpellChecker.cleanup()
    }
    
    /**
     * Matches the case of the suggestion to the input prefix.
     * Examples:
     * - "hel" + "hello" -> "hello"
     * - "Hel" + "hello" -> "Hello"
     * - "HEL" + "hello" -> "HELLO"
     */
    private fun matchCase(suggestion: String, prefix: String): String {
        if (prefix.isEmpty()) return suggestion
        
        return when {
            // All uppercase prefix
            prefix.all { it.isUpperCase() } -> suggestion.uppercase()
            // First letter uppercase
            prefix[0].isUpperCase() -> suggestion.replaceFirstChar { it.uppercase() }
            // All lowercase or mixed
            else -> suggestion.lowercase()
        }
    }
    
    /**
     * Internal class for scoring suggestions.
     */
    private data class ScoredWord(val word: String, val score: Int)
}
