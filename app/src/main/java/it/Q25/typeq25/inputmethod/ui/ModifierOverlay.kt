package it.srik.TypeQ25.inputmethod.ui

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Shows individual modifier key icons at the top-right of the screen (near status bar).
 * Optimized for Q25's 720x720 display.
 */
class ModifierOverlay(private val context: Context) {
    
    private var windowManager: WindowManager? = null
    private var overlayContainer: LinearLayout? = null
    private var shiftIcon: TextView? = null
    private var ctrlIcon: TextView? = null
    private var altIcon: TextView? = null
    private var isShowing = false
    private var windowToken: IBinder? = null
    
    fun setWindowToken(token: IBinder?) {
        windowToken = token
        Log.d("ModifierOverlay", "Window token set: ${token != null}")
    }
    
    fun updateModifierState(
        shiftActive: Boolean,
        shiftLocked: Boolean,
        ctrlActive: Boolean,
        ctrlLocked: Boolean,
        altActive: Boolean,
        altLocked: Boolean
    ) {
        Log.d("ModifierOverlay", "updateModifierState: shift=$shiftActive/$shiftLocked, ctrl=$ctrlActive/$ctrlLocked, alt=$altActive/$altLocked")
        
        // Create overlay if needed
        if (overlayContainer == null) {
            createOverlay()
        }
        
        // Update each icon visibility and symbol
        shiftIcon?.apply {
            visibility = if (shiftActive || shiftLocked) View.VISIBLE else View.GONE
            text = if (shiftLocked) "⇪ CAPS" else "⇧ SHIFT"
        }
        
        ctrlIcon?.apply {
            visibility = if (ctrlActive || ctrlLocked) View.VISIBLE else View.GONE
            text = if (ctrlLocked) "⌃ CTRL ⊙" else "⌃ CTRL"
        }
        
        altIcon?.apply {
            visibility = if (altActive || altLocked) View.VISIBLE else View.GONE
            text = if (altLocked) "⎇ ALT ⊙" else "⎇ ALT"
        }
        
        // Show overlay if any modifier is active
        val anyActive = shiftActive || shiftLocked || ctrlActive || ctrlLocked || altActive || altLocked
        if (anyActive) {
            show()
        } else {
            hide()
        }
    }
    
    private fun show() {
        try {
            if (windowToken == null) {
                Log.w("ModifierOverlay", "Cannot show overlay: window token is null")
                return
            }
            
            if (overlayContainer == null) {
                createOverlay()
            }
            
            if (!isShowing && overlayContainer != null) {
                windowManager?.addView(overlayContainer, createLayoutParams())
                isShowing = true
                Log.d("ModifierOverlay", "Showing overlay")
            }
        } catch (e: Exception) {
            Log.e("ModifierOverlay", "Error showing overlay", e)
        }
    }
    
    fun hide() {
        try {
            if (isShowing && overlayContainer != null) {
                windowManager?.removeView(overlayContainer)
                isShowing = false
                Log.d("ModifierOverlay", "Hiding overlay")
            }
        } catch (e: Exception) {
            Log.e("ModifierOverlay", "Error hiding overlay", e)
        }
    }
    
    private fun createOverlay() {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        val textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            16f,  // Slightly larger for better visibility
            context.resources.displayMetrics
        )
        
        val padding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            context.resources.displayMetrics
        ).toInt()
        
        val iconMargin = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            2f,
            context.resources.displayMetrics
        ).toInt()
        
        // Create individual icon TextViews
        shiftIcon = createIconView(textSize, padding)
        ctrlIcon = createIconView(textSize, padding)
        altIcon = createIconView(textSize, padding)
        
        // Create horizontal container for icons
        overlayContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.argb(230, 29, 29, 29)) // BlackBerry dark with more opacity
            setPadding(padding * 2, padding, padding * 2, padding)
            elevation = 10f
            
            // Add icons with margins
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(iconMargin, 0, iconMargin, 0)
            }
            
            addView(shiftIcon, layoutParams)
            addView(ctrlIcon, layoutParams)
            addView(altIcon, layoutParams)
        }
    }
    
    private fun createIconView(textSize: Float, padding: Int): TextView {
        return TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
            gravity = Gravity.CENTER
            visibility = View.GONE  // Hidden by default
        }
    }
    
    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
            token = windowToken
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL  // Top-center position
            x = 0
            y = 0  // No spacing from top
        }
    }
    
    fun cleanup() {
        hide()
        overlayContainer = null
        shiftIcon = null
        ctrlIcon = null
        altIcon = null
        windowManager = null
    }
}
