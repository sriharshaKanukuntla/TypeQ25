package it.srik.TypeQ25.inputmethod

import android.content.Context
import android.provider.UserDictionary
import android.util.Log
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Offline spell checker using SymSpell algorithm for fast spell checking.
 * Checks words against dictionary and suggests corrections for misspelled words.
 * Loads dictionaries from multiple sources:
 * - Android system dictionary files (/system/usr/share/dict/words)
 * - Android UserDictionary provider
 * - App's built-in dictionary
 * 
 * Uses SymSpell for O(n*d) lookup performance vs O(m*n*dict_size) for traditional algorithms.
 */
class OfflineSpellChecker(private val context: Context) {
    
    companion object {
        private const val TAG = "OfflineSpellChecker"
        private const val MAX_EDIT_DISTANCE = 2 // Maximum edits allowed for suggestions
        private const val MAX_SUGGESTIONS = 5
        
        // Common Android system dictionary locations
        private val SYSTEM_DICT_PATHS = listOf(
            "/system/usr/share/dict/words",
            "/usr/share/dict/words",
            "/system/share/dict/words",
            "/data/usr/share/dict/words"
        )
    }
    
    private val symSpell = SymSpell(maxEditDistance = MAX_EDIT_DISTANCE, prefixLength = 7)
    private val dictionary = mutableSetOf<String>() // Keep for isCorrect() checks
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    init {
        // Load dictionaries asynchronously to avoid blocking main thread
        scope.launch {
            Log.d(TAG, "Starting background dictionary loading...")
            // Try to load system dictionaries
            loadSystemDictionaries()
            // Load user's personal dictionary
            loadUserDictionary()
            Log.d(TAG, "Background dictionary loading completed")
        }
    }
    
    /**
     * Loads system dictionaries from Android device.
     */
    private fun loadSystemDictionaries() {
        var wordsLoaded = 0
        
        for (path in SYSTEM_DICT_PATHS) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    Log.d(TAG, "Found system dictionary at: $path")
                    file.bufferedReader().useLines { lines ->
                        lines.forEachIndexed { index, word ->
                            val trimmed = word.trim().lowercase()
                            if (trimmed.length >= 2 && trimmed.all { it.isLetter() }) {
                                dictionary.add(trimmed)
                                // Add to SymSpell with frequency based on position (more common words first)
                                val frequency = 100 - (index / 1000).coerceAtMost(90)
                                symSpell.addWord(trimmed, frequency)
                                wordsLoaded++
                            }
                        }
                    }
                    Log.d(TAG, "Loaded $wordsLoaded words from system dictionary into SymSpell: $path")
                    break // Stop after finding first valid dictionary
                }
            } catch (e: Exception) {
                Log.d(TAG, "Could not read system dictionary at $path: ${e.message}")
            }
        }
        
        if (wordsLoaded == 0) {
            Log.d(TAG, "No system dictionaries found, will use app dictionaries only")
        }
    }
    
    /**
     * Loads user's personal dictionary from Android UserDictionary provider.
     */
    private fun loadUserDictionary() {
        try {
            val projection = arrayOf(UserDictionary.Words.WORD, UserDictionary.Words.FREQUENCY)
            val cursor = context.contentResolver.query(
                UserDictionary.Words.CONTENT_URI,
                projection,
                null,
                null,
                null
            )
            
            cursor?.use {
                val wordIndex = it.getColumnIndex(UserDictionary.Words.WORD)
                val freqIndex = it.getColumnIndex(UserDictionary.Words.FREQUENCY)
                var userWordsLoaded = 0
                
                while (it.moveToNext()) {
                    val word = it.getString(wordIndex)?.trim()?.lowercase()
                    if (!word.isNullOrEmpty() && word.length >= 2) {
                        dictionary.add(word)
                        // User dictionary words get high frequency (200+)
                        val frequency = if (freqIndex >= 0) {
                            200 + it.getInt(freqIndex).coerceAtMost(50)
                        } else {
                            200
                        }
                        symSpell.addWord(word, frequency)
                        userWordsLoaded++
                    }
                }
                
                Log.d(TAG, "Loaded $userWordsLoaded words from user dictionary into SymSpell")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading user dictionary: ${e.message}")
        }
    }
    
    /**
     * Loads words with frequencies into the spell checker.
     */
    fun loadDictionaryWithFrequency(wordsWithFreq: Map<String, Int>) {
        wordsWithFreq.forEach { (word, freq) ->
            val lower = word.lowercase()
            dictionary.add(lower)
            symSpell.addWord(lower, freq)
        }
        Log.d(TAG, "Total dictionary size: ${dictionary.size} words, SymSpell size: ${symSpell.size()}")
    }
    
    /**
     * Gets spell check suggestions for a misspelled word using SymSpell.
     */
    fun getSuggestions(word: String, maxDistance: Int = MAX_EDIT_DISTANCE): List<String> {
        if (word.isEmpty() || word.length < 2) return emptyList()
        
        val lowerWord = word.lowercase()
        
        // Word is correct, no suggestions needed
        if (dictionary.contains(lowerWord)) {
            return emptyList()
        }
        
        Log.d(TAG, "Finding suggestions for misspelled word: '$word' using SymSpell")
        
        // Use SymSpell for fast lookup
        val symSpellResults = symSpell.lookup(lowerWord, MAX_SUGGESTIONS)
        
        val result = symSpellResults.map { it.term }
        
        Log.d(TAG, "Found ${result.size} suggestions for '$word': $result (distances: ${symSpellResults.map { it.distance }})")
        return result
    }
    
    /**
     * Gets detailed spell check info for a word.
     */
    fun checkWord(word: String): SpellCheckResult {
        val lowerWord = word.lowercase()
        val isCorrect = dictionary.contains(lowerWord)
        val suggestions = if (!isCorrect) getSuggestions(word) else emptyList()
        
        return SpellCheckResult(
            word = word,
            isCorrect = isCorrect,
            suggestions = suggestions
        )
    }
    
    data class SpellCheckResult(
        val word: String,
        val isCorrect: Boolean,
        val suggestions: List<String>
    )
}
