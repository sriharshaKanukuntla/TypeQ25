package it.srik.TypeQ25.inputmethod

import android.content.Context
import android.util.Log
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Android system spell checker implementation using TextServicesManager.
 * Uses the device's configured spell checker from Android settings.
 */
class AndroidSpellChecker(private val context: Context) : SpellCheckerSession.SpellCheckerSessionListener {
    
    companion object {
        private const val TAG = "AndroidSpellChecker"
        private const val SPELL_CHECK_TIMEOUT_MS = 1000L
        private const val MAX_SUGGESTIONS = 5
    }
    
    private var spellCheckerSession: SpellCheckerSession? = null
    private val textServicesManager: TextServicesManager? = 
        context.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager
    
    // For synchronous spell check results
    private var pendingResult: SpellCheckResult? = null
    private var resultLatch: CountDownLatch? = null
    private var currentWord: String = ""
    
    data class SpellCheckResult(
        val word: String,
        val isCorrect: Boolean,
        val suggestions: List<String>
    )
    
    init {
        initializeSession()
    }
    
    private fun initializeSession() {
        try {
            if (textServicesManager == null) {
                Log.w(TAG, "TextServicesManager not available")
                return
            }
            
            // Check if spell checker is enabled in Android settings
            if (!textServicesManager.isSpellCheckerEnabled) {
                Log.w(TAG, "Spell checker is disabled in Android settings")
                return
            }
            
            Log.d(TAG, "Spell checker is enabled, creating session")
            
            // Create spell checker session with current locale
            // Using null locale and referToSpellCheckerLanguageSettings=true ensures 
            // it uses the system spell checker's configured language
            spellCheckerSession = textServicesManager.newSpellCheckerSession(
                null,  // Bundle - use default
                null,  // Locale - use system default
                this,  // SpellCheckerSessionListener
                true   // referToSpellCheckerLanguageSettings - use system spell checker language
            )
            
            if (spellCheckerSession != null) {
                Log.d(TAG, "Spell checker session initialized successfully")
            } else {
                Log.w(TAG, "Failed to create spell checker session")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing spell checker session", e)
        }
    }
    
    /**
     * Checks if a word is spelled correctly and gets suggestions if misspelled.
     * This is a synchronous call that waits for the spell checker response.
     */
    fun checkWord(word: String): SpellCheckResult {
        val session = spellCheckerSession
        
        if (session == null) {
            Log.w(TAG, "Spell checker session not available")
            return SpellCheckResult(word, true, emptyList()) // Assume correct if no checker
        }
        
        try {
            // Set up latch for synchronous result
            resultLatch = CountDownLatch(1)
            pendingResult = null
            currentWord = word
            
            // Request spell check
            val textInfo = TextInfo(word)
            session.getSuggestions(textInfo, MAX_SUGGESTIONS)
            
            // Wait for result with timeout
            val gotResult = resultLatch?.await(SPELL_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS) ?: false
            
            return if (gotResult && pendingResult != null) {
                pendingResult!!
            } else {
                Log.w(TAG, "Spell check timed out for word: $word")
                SpellCheckResult(word, true, emptyList()) // Assume correct on timeout
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking word: $word", e)
            return SpellCheckResult(word, true, emptyList())
        }
    }
    
    /**
     * Callback when spell checker returns suggestions for a single word.
     */
    override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) {
        if (results == null || results.isEmpty()) {
            pendingResult = SpellCheckResult(currentWord, true, emptyList())
            resultLatch?.countDown()
            return
        }
        
        val suggestionsInfo = results[0]
        val suggestionsCount = suggestionsInfo.suggestionsCount
        val suggestionsAttributes = suggestionsInfo.suggestionsAttributes
        
        // Check if word is in dictionary (correct)
        val isCorrect = (suggestionsAttributes and SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY) != 0
        
        // Get suggestions if word is misspelled
        val suggestions = if (!isCorrect && suggestionsCount > 0) {
            (0 until suggestionsCount).mapNotNull { i ->
                suggestionsInfo.getSuggestionAt(i)
            }
        } else {
            emptyList()
        }
        
        pendingResult = SpellCheckResult(currentWord, isCorrect, suggestions)
        resultLatch?.countDown()
    }
    
    /**
     * Callback when spell checker returns suggestions for sentences.
     * Not used in this implementation but required by interface.
     */
    override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
        // Not used for single word checking
    }
    
    /**
     * Closes the spell checker session.
     */
    fun cleanup() {
        try {
            spellCheckerSession?.close()
            spellCheckerSession = null
            Log.d(TAG, "Spell checker session closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing spell checker session", e)
        }
    }
    
    /**
     * Checks if the spell checker is available and enabled.
     */
    fun isAvailable(): Boolean {
        return textServicesManager?.isSpellCheckerEnabled == true && spellCheckerSession != null
    }
}
