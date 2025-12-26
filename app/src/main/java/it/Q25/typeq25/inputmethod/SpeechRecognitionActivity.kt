package it.srik.TypeQ25.inputmethod

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import it.srik.TypeQ25.R
import it.srik.TypeQ25.SettingsManager

/**
 * Helper activity for handling speech recognition.
 * Receives the result and sends it via broadcast to the input method service.
 */
class SpeechRecognitionActivity : Activity() {
    companion object {
        private const val TAG = "SpeechRecognition"
        const val REQUEST_CODE_SPEECH = 1000
        const val REQUEST_CODE_CHOOSER = 1001
        const val ACTION_SPEECH_RESULT = "it.srik.TypeQ25.SPEECH_RESULT"
        const val EXTRA_TEXT = "text"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val preferredApp = SettingsManager.getPreferredSpeechApp(this)
        
        if (preferredApp == null) {
            // First time use - show app chooser
            Log.d(TAG, "No preferred speech app set, showing chooser")
            val chooserIntent = Intent(this, SpeechAppChooserActivity::class.java)
            startActivityForResult(chooserIntent, REQUEST_CODE_CHOOSER)
            return
        }
        
        // Use preferred app
        launchSpeechRecognition(preferredApp)
    }
    
    private fun launchSpeechRecognition(packageName: String?) {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                if (packageName != null) {
                    setPackage(packageName)
                }
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.speech_recognition_prompt))
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            
            if (intent.resolveActivity(packageManager) != null) {
                Log.d(TAG, "Launching speech recognition with package: $packageName")
                startActivityForResult(intent, REQUEST_CODE_SPEECH)
            } else {
                Log.e(TAG, "Speech recognition app not available: $packageName")
                // Clear the invalid preference and show chooser
                SettingsManager.setPreferredSpeechApp(this, null)
                val chooserIntent = Intent(this, SpeechAppChooserActivity::class.java)
                startActivityForResult(chooserIntent, REQUEST_CODE_CHOOSER)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching speech recognition", e)
            finish()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REQUEST_CODE_CHOOSER -> {
                if (resultCode == RESULT_OK && data != null) {
                    val selectedPackage = data.getStringExtra(SpeechAppChooserActivity.EXTRA_SELECTED_PACKAGE)
                    if (selectedPackage != null) {
                        Log.d(TAG, "User selected speech app: $selectedPackage")
                        launchSpeechRecognition(selectedPackage)
                    } else {
                        finish()
                    }
                } else {
                    Log.d(TAG, "User cancelled app selection")
                    finish()
                }
            }
            REQUEST_CODE_SPEECH -> {
                if (resultCode == RESULT_OK && data != null) {
                    val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    val spokenText = results?.get(0) ?: ""
                    
                    if (spokenText.isNotEmpty()) {
                        Log.d(TAG, "Recognized text: $spokenText")
                        
                        // Send result via broadcast to this app's package
                        val broadcastIntent = Intent(ACTION_SPEECH_RESULT).apply {
                            putExtra(EXTRA_TEXT, spokenText)
                            setPackage("it.srik.TypeQ25") // Send to our own app package
                        }
                        sendBroadcast(broadcastIntent)
                        Log.d(TAG, "Broadcast sent to it.srik.TypeQ25: $ACTION_SPEECH_RESULT with text: $spokenText")
                    } else {
                        Log.d(TAG, "No text recognized")
                    }
                } else {
                    Log.d(TAG, "Speech recognition cancelled or failed")
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    try {
                        finishAndRemoveTask()
                    } catch (e: Exception) {
                        finish()
                    }
                } else {
                    finish()
                }
            }
        }
    }
}

