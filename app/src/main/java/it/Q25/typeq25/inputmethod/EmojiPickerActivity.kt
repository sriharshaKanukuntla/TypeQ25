package it.srik.TypeQ25.inputmethod

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import it.srik.TypeQ25.EmojiPickerDialog

class EmojiPickerActivity : ComponentActivity() {
    companion object {
        const val ACTION_EMOJI_PICKED = "it.srik.TypeQ25.ACTION_EMOJI_PICKED"
        const val EXTRA_EMOJI = "extra_emoji"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                EmojiPickerDialog(
                    selectedLetter = null,
                    onEmojiSelected = { emoji ->
                        android.util.Log.d("EmojiPickerActivity", "Emoji selected: '$emoji', calling service directly")
                        // Call service directly - service will handle delayed commit
                        val service = PhysicalKeyboardInputMethodService.getInstance()
                        if (service != null) {
                            service.commitEmoji(emoji)
                        } else {
                            android.util.Log.e("EmojiPickerActivity", "Service instance not available")
                        }
                        // Close immediately - commit happens after IME session restarts
                        finish()
                    },
                    onDismiss = {
                        finish()
                    }
                )
            }
        }
    }
}
