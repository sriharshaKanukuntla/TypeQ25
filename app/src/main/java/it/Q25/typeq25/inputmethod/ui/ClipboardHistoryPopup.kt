package it.srik.TypeQ25.inputmethod.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import it.srik.TypeQ25.clipboard.ClipboardHistoryManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * Popup window that displays clipboard history and allows selection.
 * Triggered by Ctrl+Shift+V.
 */
class ClipboardHistoryPopup(
    private val context: Context,
    private val clipboardManager: ClipboardHistoryManager,
    private val onItemSelected: (String) -> Unit,
    private val onDismiss: () -> Unit
) {
    companion object {
        private const val TAG = "ClipboardHistoryPopup"
    }
    
    private var popupView: View? = null
    private var windowManager: WindowManager? = null
    private var selectedIndex by mutableStateOf(0)
    
    /**
     * Shows the popup.
     */
    fun show(anchorView: View) {
        if (isShowing()) {
            Log.w(TAG, "Popup already showing")
            return
        }
        
        val history = clipboardManager.getHistory()
        if (history.isEmpty()) {
            Log.d(TAG, "No clipboard history to show")
            onDismiss()
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
            
            setContent {
                ClipboardHistoryContent(
                    history = history,
                    selectedIndex = selectedIndex,
                    onItemClick = { index ->
                        val item = history.getOrNull(index)
                        if (item != null) {
                            onItemSelected(item.text)
                            dismiss()
                        }
                    },
                    onClear = {
                        clipboardManager.clearHistory()
                        dismiss()
                    },
                    onClose = { dismiss() }
                )
            }
        }
        
        popupView = composeView
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            token = anchorView.windowToken
        }
        
        try {
            windowManager?.addView(composeView, params)
            Log.d(TAG, "Clipboard history popup shown with ${history.size} items")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show popup", e)
            dismiss()
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
    fun handlePhysicalKey(keyCode: Int, isCtrlActive: Boolean): Boolean {
        val history = clipboardManager.getHistory()
        if (history.isEmpty()) return false
        
        when (keyCode) {
            KeyEvent.KEYCODE_ESCAPE -> {
                dismiss()
                return true
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                val item = history.getOrNull(selectedIndex)
                if (item != null) {
                    onItemSelected(item.text)
                    dismiss()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_K -> {
                if (selectedIndex > 0) {
                    selectedIndex--
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_J -> {
                if (selectedIndex < history.size - 1) {
                    selectedIndex++
                }
                return true
            }
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> {
                clipboardManager.removeItem(selectedIndex)
                if (selectedIndex >= history.size && selectedIndex > 0) {
                    selectedIndex--
                }
                if (history.isEmpty()) {
                    dismiss()
                }
                return true
            }
            // Number keys 1-9 for quick selection
            in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> {
                val index = keyCode - KeyEvent.KEYCODE_1
                val item = history.getOrNull(index)
                if (item != null) {
                    onItemSelected(item.text)
                    dismiss()
                }
                return true
            }
        }
        
        return false
    }
}

@Composable
private fun ClipboardHistoryContent(
    history: List<ClipboardHistoryManager.ClipboardItem>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    val backgroundColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)
    val surfaceColor = if (isDarkTheme) Color(0xFF2D2D2D) else Color.White
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
            .fillMaxWidth()
            .heightIn(max = 400.dp)
            .background(backgroundColor)
            .border(1.dp, borderColor)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(surfaceColor)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Clipboard History (${history.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClear) {
                    Text("Clear All", fontSize = 12.sp)
                }
                TextButton(onClick = onClose) {
                    Text("Close", fontSize = 12.sp)
                }
            }
        }
        
        Divider(color = borderColor)
        
        // Help text
        Text(
            text = "↑↓ Navigate • Enter: Paste • Del: Remove • Esc: Close • 1-9: Quick select",
            fontSize = 11.sp,
            color = textColor.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
        
        Divider(color = borderColor)
        
        // List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            itemsIndexed(history) { index, item ->
                ClipboardItemRow(
                    item = item,
                    index = index,
                    isSelected = index == selectedIndex,
                    textColor = textColor,
                    selectedColor = selectedColor,
                    borderColor = borderColor,
                    onClick = { onItemClick(index) }
                )
            }
        }
    }
}

@Composable
private fun ClipboardItemRow(
    item: ClipboardHistoryManager.ClipboardItem,
    index: Int,
    isSelected: Boolean,
    textColor: Color,
    selectedColor: Color,
    borderColor: Color,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        selectedColor.copy(alpha = 0.3f)
    } else {
        Color.Transparent
    }
    
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val timeText = dateFormat.format(Date(item.timestamp))
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Index badge
            if (index < 9) {
                Text(
                    text = "${index + 1}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) selectedColor else textColor.copy(alpha = 0.5f),
                    modifier = Modifier
                        .background(
                            if (isSelected) textColor.copy(alpha = 0.2f)
                            else textColor.copy(alpha = 0.1f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            // Preview text
            Text(
                text = item.preview,
                fontSize = 14.sp,
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Timestamp
            Text(
                text = timeText,
                fontSize = 11.sp,
                color = textColor.copy(alpha = 0.5f)
            )
        }
        
        // Character count
        Text(
            text = "${item.text.length} chars",
            fontSize = 10.sp,
            color = textColor.copy(alpha = 0.4f),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    
    Divider(color = borderColor.copy(alpha = 0.3f))
}
