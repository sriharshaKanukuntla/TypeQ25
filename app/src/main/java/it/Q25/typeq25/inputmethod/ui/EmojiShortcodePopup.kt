package it.srik.TypeQ25.inputmethod.ui

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Popup window that displays emoji and symbol shortcode suggestions.
 * Shows matching emojis/symbols as the user types :shortcode:
 */
class EmojiShortcodePopup(
    private val context: Context,
    private val onEmojiSelected: (String, String) -> Unit, // (character, shortcode) -> Unit
    private val onDismiss: () -> Unit
) {
    companion object {
        private const val TAG = "EmojiShortcodePopup"
    }
    
    private var popupView: View? = null
    private var windowManager: WindowManager? = null
    // Use mutableIntStateOf for proper state observation
    private var _selectedIndex = mutableIntStateOf(0)
    private var selectedIndex: Int
        get() = _selectedIndex.intValue
        set(value) { _selectedIndex.intValue = value }
    private var suggestions by mutableStateOf<List<Pair<String, String>>>(emptyList())
    
    /**
     * Shows the popup with emoji/symbol suggestions.
     */
    fun show(anchorView: View, suggestions: List<Pair<String, String>>) {
        if (suggestions.isEmpty()) {
            dismiss()
            return
        }
        
        this.suggestions = suggestions
        this.selectedIndex = 0
        
        if (isShowing()) {
            // Update existing popup
            return
        }
        
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val composeView = ComposeView(context).apply {
            // Force set lifecycle owner from context
            try {
                if (context is LifecycleOwner) {
                    setViewTreeLifecycleOwner(context as LifecycleOwner)
                    Log.d(TAG, "Set ViewTreeLifecycleOwner successfully")
                } else {
                    Log.e(TAG, "Context is not a LifecycleOwner: ${context::class.java.name}")
                }
                
                if (context is SavedStateRegistryOwner) {
                    setViewTreeSavedStateRegistryOwner(context as SavedStateRegistryOwner)
                    Log.d(TAG, "Set ViewTreeSavedStateRegistryOwner successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set lifecycle owners", e)
            }
            
            // Handle touch events - dismiss on outside touch
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    Log.d(TAG, "Touch outside detected, dismissing popup")
                    dismiss()
                    true
                } else {
                    false
                }
            }
            
            setContent {
                // Observe the state properly within Composable scope
                val currentSelectedIndex = _selectedIndex.intValue
                EmojiSuggestionContent(
                    suggestions = this@EmojiShortcodePopup.suggestions,
                    selectedIndex = currentSelectedIndex,
                    onItemClick = { index ->
                        val (emoji, shortcode) = this@EmojiShortcodePopup.suggestions[index]
                        onEmojiSelected(emoji, shortcode)
                        dismiss()
                    }
                )
            }
        }
        
        popupView = composeView
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = 50 // Offset from left
            y = 200 // Offset from bottom
            token = anchorView.windowToken
        }
        
        try {
            windowManager?.addView(composeView, params)
            Log.d(TAG, "Emoji shortcode popup shown with ${suggestions.size} suggestions")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show popup", e)
            dismiss()
        }
    }
    
    /**
     * Updates the suggestions without recreating the popup.
     */
    fun updateSuggestions(newSuggestions: List<Pair<String, String>>) {
        if (newSuggestions.isEmpty()) {
            dismiss()
            return
        }
        
        suggestions = newSuggestions
        if (selectedIndex >= newSuggestions.size) {
            selectedIndex = 0
        }
    }
    
    /**
     * Dismisses the popup.
     */
    fun dismiss() {
        popupView?.let { view ->
            try {
                windowManager?.removeView(view)
                Log.d(TAG, "Popup dismissed")
            } catch (e: Exception) {
                Log.e(TAG, "Error dismissing popup", e)
            }
        }
        popupView = null
        windowManager = null
        suggestions = emptyList()
        onDismiss()
    }
    
    /**
     * Checks if popup is currently showing.
     */
    fun isShowing(): Boolean {
        return popupView != null && popupView?.parent != null
    }
    
    /**
     * Handles physical keyboard input for navigation.
     */
    fun handlePhysicalKey(keyCode: Int, isAltActive: Boolean = false, event: KeyEvent? = null): Boolean {
        if (suggestions.isEmpty()) {
            Log.d(TAG, "handlePhysicalKey: No suggestions, returning false")
            return false
        }
        
        Log.d(TAG, "handlePhysicalKey: keyCode=$keyCode, isAltActive=$isAltActive, suggestions.size=${suggestions.size}")
        
        when (keyCode) {
            KeyEvent.KEYCODE_ESCAPE -> {
                dismiss()
                return true
            }
            KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                val (emoji, shortcode) = suggestions[selectedIndex]
                onEmojiSelected(emoji, shortcode)
                dismiss()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (selectedIndex > 0) {
                    selectedIndex--
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (selectedIndex < suggestions.size - 1) {
                    selectedIndex++
                }
                return true
            }
        }
        
        // Check if Alt is pressed and the key produces a digit character
        if (isAltActive && event != null) {
            // Get the Unicode character this key produces
            val unicodeChar = event.getUnicodeChar(KeyEvent.META_ALT_ON)
            val charProduced = unicodeChar.toChar()
            
            Log.d(TAG, "Alt pressed: unicodeChar=$unicodeChar, char='$charProduced'")
            
            // Check if it's a digit 0-9
            if (charProduced in '0'..'9') {
                // Map: 1-9 to indices 0-8, and 0 to index 9 (10th item)
                val index = if (charProduced == '0') 9 else charProduced - '1'
                Log.d(TAG, "Alt+key produces digit '$charProduced', index=$index")
                
                if (index < suggestions.size) {
                    val (emoji, shortcode) = suggestions[index]
                    Log.d(TAG, "Inserting emoji at index $index: $emoji ($shortcode)")
                    onEmojiSelected(emoji, shortcode)
                    dismiss()
                    return true
                } else {
                    Log.d(TAG, "Index $index out of range (size=${suggestions.size})")
                    return true // Still consume the event
                }
            }
        }
        
        return false
    }
}

@Composable
private fun EmojiSuggestionContent(
    suggestions: List<Pair<String, String>>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    val backgroundColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color.White
    val textColor = if (isDarkTheme) Color(0xFFE0E0E0) else Color(0xFF1F1F1F)
    val selectedColor = if (isDarkTheme) Color(0xFF0D47A1) else Color(0xFF2196F3)
    val borderColor = if (isDarkTheme) Color(0xFF404040) else Color(0xFFE0E0E0)
    
    val listState = rememberLazyListState()
    
    // Auto-scroll to selected item
    LaunchedEffect(selectedIndex) {
        listState.animateScrollToItem(selectedIndex)
    }
    
    Column(
        modifier = Modifier
            .widthIn(min = 280.dp, max = 400.dp)
            .heightIn(max = 300.dp)
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
    ) {
        // Header
        Text(
            text = "Shortcuts (${suggestions.size})",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = textColor.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
        
        Divider(color = borderColor, thickness = 0.5.dp)
        
        // List
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(suggestions) { index, (emoji, shortcode) ->
                EmojiSuggestionRow(
                    emoji = emoji,
                    shortcode = shortcode,
                    index = index,
                    isSelected = index == selectedIndex,
                    textColor = textColor,
                    selectedColor = selectedColor,
                    onClick = { onItemClick(index) }
                )
            }
        }
        
        // Footer hint
        Text(
            text = "Enter/Tab: Select • ↑↓: Navigate • Alt+1-9,0: Quick • Esc/Bksp: Close",
            fontSize = 10.sp,
            color = textColor.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmojiSuggestionRow(
    emoji: String,
    shortcode: String,
    index: Int,
    isSelected: Boolean,
    textColor: Color,
    selectedColor: Color,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        selectedColor.copy(alpha = 0.25f)
    } else {
        Color.Transparent
    }
    
    val borderColor = if (isSelected) {
        selectedColor.copy(alpha = 0.6f)
    } else {
        Color.Transparent
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Index badge (for items 1-10, showing 1-9 and 0)
        if (index < 10) {
            val displayNumber = if (index == 9) "0" else "${index + 1}"
            Text(
                text = displayNumber,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) selectedColor else textColor.copy(alpha = 0.5f),
                modifier = Modifier
                    .background(
                        if (isSelected) selectedColor.copy(alpha = 0.2f)
                        else Color.Transparent,
                        RoundedCornerShape(3.dp)
                    )
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }
        
        // Emoji (large)
        Text(
            text = emoji,
            fontSize = 24.sp,
            modifier = Modifier.width(36.dp)
        )
        
        // Shortcode
        Text(
            text = ":$shortcode:",
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = textColor,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 120.dp)
        )
    }
}
