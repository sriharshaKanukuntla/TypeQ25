package it.srik.TypeQ25.inputmethod

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.content.ContextCompat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.util.Log
import android.util.TypedValue
import it.srik.TypeQ25.R
import it.srik.TypeQ25.MainActivity
import it.srik.TypeQ25.SymCustomizationActivity
import it.srik.TypeQ25.SettingsManager
import kotlin.math.max
import android.view.MotionEvent
import android.view.KeyEvent
import kotlin.math.abs
import it.srik.TypeQ25.inputmethod.ui.VariationBarView

/**
 * Manages the status bar shown by the IME, handling view creation
 * and updating text/style based on modifier states.
 */
class StatusBarController(
    private val context: Context,
    private val mode: Mode = Mode.FULL
) {
    enum class Mode {
        FULL,
        CANDIDATES_ONLY
    }

    // Listener for variation selection
    var onVariationSelectedListener: VariationButtonHandler.OnVariationSelectedListener? = null
        set(value) {
            field = value
            variationBarView.onVariationSelectedListener = value
        }
    
    // Listener for cursor movement (to update variations)
    var onCursorMovedListener: (() -> Unit)? = null
        set(value) {
            field = value
            variationBarView.onCursorMovedListener = value
        }

    companion object {
        private const val TAG = "StatusBarController"
        private const val NAV_MODE_LABEL = "NAV MODE"
        private val DEFAULT_BACKGROUND = Color.parseColor("#1A1A1A")
        private val NAV_MODE_BACKGROUND = Color.argb(100, 0, 160, 220)
        
        // Emoji keyboard constants - defined once to avoid repeated allocations
        private val KEYBOARD_ROWS = listOf(
            listOf(KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_E, 
                   KeyEvent.KEYCODE_R, KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_Y, 
                   KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_O, 
                   KeyEvent.KEYCODE_P),
            listOf(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D, 
                   KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H, 
                   KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L),
            listOf(KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_X, KeyEvent.KEYCODE_C, 
                   KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_N, 
                   KeyEvent.KEYCODE_M)
        )
        
        private val KEY_LABELS = mapOf(
            KeyEvent.KEYCODE_Q to "Q", KeyEvent.KEYCODE_W to "W", KeyEvent.KEYCODE_E to "E",
            KeyEvent.KEYCODE_R to "R", KeyEvent.KEYCODE_T to "T", KeyEvent.KEYCODE_Y to "Y",
            KeyEvent.KEYCODE_U to "U", KeyEvent.KEYCODE_I to "I", KeyEvent.KEYCODE_O to "O",
            KeyEvent.KEYCODE_P to "P", KeyEvent.KEYCODE_A to "A", KeyEvent.KEYCODE_S to "S",
            KeyEvent.KEYCODE_D to "D", KeyEvent.KEYCODE_F to "F", KeyEvent.KEYCODE_G to "G",
            KeyEvent.KEYCODE_H to "H", KeyEvent.KEYCODE_J to "J", KeyEvent.KEYCODE_K to "K",
            KeyEvent.KEYCODE_L to "L", KeyEvent.KEYCODE_Z to "Z", KeyEvent.KEYCODE_X to "X",
            KeyEvent.KEYCODE_C to "C", KeyEvent.KEYCODE_V to "V", KeyEvent.KEYCODE_B to "B",
            KeyEvent.KEYCODE_N to "N", KeyEvent.KEYCODE_M to "M"
        )
    }

    data class StatusSnapshot(
        val capsLockEnabled: Boolean,
        val shiftPhysicallyPressed: Boolean,
        val shiftOneShot: Boolean,
        val ctrlLatchActive: Boolean,
        val ctrlPhysicallyPressed: Boolean,
        val ctrlOneShot: Boolean,
        val ctrlLatchFromNavMode: Boolean,
        val altLatchActive: Boolean,
        val altPhysicallyPressed: Boolean,
        val altOneShot: Boolean,
        val symPage: Int, // 0=disabled, 1=page1 emoji, 2=page2 characters
        val variations: List<String> = emptyList(),
        val lastInsertedChar: Char? = null,
        val shouldDisableSmartFeatures: Boolean = false,
        val suggestions: List<String> = emptyList()
    ) {
        val navModeActive: Boolean
            get() = ctrlLatchActive && ctrlLatchFromNavMode
    }

    private var statusBarLayout: LinearLayout? = null
    private var emojiMapTextView: TextView? = null
    private var emojiKeyboardContainer: LinearLayout? = null
    private var emojiKeyButtons: MutableList<View> = mutableListOf()
    private var lastSymPageRendered: Int = 0
    private var lastSymMappingsRendered: Map<Int, String>? = null
    private var wasSymActive: Boolean = false
    private var symShown: Boolean = false
    private val variationBarView: VariationBarView = VariationBarView(context)
    private var variationsWrapper: View? = null
    private var forceMinimalUi: Boolean = false
    
    // Callback to notify when IME window needs to resize (especially for minimal UI mode)
    var onRequestWindowResize: (() -> Unit)? = null
    
    fun setForceMinimalUi(force: Boolean) {
        if (mode != Mode.FULL) {
            return
        }
        if (forceMinimalUi == force) {
            return
        }
        forceMinimalUi = force
        if (force) {
            variationBarView.hideImmediate()
        }
    }

    fun getLayout(): LinearLayout? = statusBarLayout
    
    /**
     * Clears the cached layout, forcing it to be recreated on next access.
     */
    fun clearLayout() {
        statusBarLayout = null
    }

    fun getOrCreateLayout(emojiMapText: String = ""): LinearLayout {
        if (statusBarLayout == null) {
            statusBarLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setBackgroundColor(DEFAULT_BACKGROUND)
            }

            // Container for modifier indicators (horizontal, left-aligned).
            // Add left padding to avoid the IME collapse button.
            val leftPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 
                64f, 
                context.resources.displayMetrics
            ).toInt()
            val horizontalPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 
                16f, 
                context.resources.displayMetrics
            ).toInt()
            val verticalPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 
                8f, 
                context.resources.displayMetrics
            ).toInt()
            
            // Container for emoji grid (when SYM is active) - placed at the bottom
            val emojiKeyboardHorizontalPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                8f,
                context.resources.displayMetrics
            ).toInt()
            val emojiKeyboardBottomPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                12f, // Bottom padding to avoid IME controls
                context.resources.displayMetrics
            ).toInt()
            
            emojiKeyboardContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                // No top padding, only horizontal and bottom
                setPadding(emojiKeyboardHorizontalPadding, 0, emojiKeyboardHorizontalPadding, emojiKeyboardBottomPadding)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                visibility = View.GONE
            }
            
            // Keep the TextView for backward compatibility (hidden)
            emojiMapTextView = TextView(context).apply {
                visibility = View.GONE
            }

            variationsWrapper = variationBarView.ensureView()
            
            statusBarLayout?.apply {
                variationsWrapper?.let { addView(it) }
                addView(emojiKeyboardContainer) // Emoji grid at the bottom
            }
        } else if (emojiMapText.isNotEmpty()) {
            emojiMapTextView?.text = emojiMapText
        }
        return statusBarLayout!!
    }
    
    /**
     * Ensures the layout is created before updating.
     * This is important for candidates view which may not have been created yet.
     */
    private fun ensureLayoutCreated(emojiMapText: String = ""): LinearLayout? {
        return statusBarLayout ?: getOrCreateLayout(emojiMapText)
    }
    
    /**
     * Recursively finds a clickable view at the given coordinates in the view hierarchy.
     * Coordinates are relative to the parent view.
     */
    private fun findClickableViewAt(parent: View, x: Float, y: Float): View? {
        if (parent !is ViewGroup) {
            // Single view: check if it's clickable and contains the point
            if (x >= 0 && x < parent.width &&
                y >= 0 && y < parent.height &&
                parent.isClickable) {
                return parent
            }
            return null
        }
        
        // For ViewGroup, check children first (they are on top)
        // Iterate in reverse to check topmost views first
        for (i in parent.childCount - 1 downTo 0) {
            val child = parent.getChildAt(i)
            if (child.visibility == View.VISIBLE) {
                val childLeft = child.left.toFloat()
                val childTop = child.top.toFloat()
                val childRight = child.right.toFloat()
                val childBottom = child.bottom.toFloat()
                
                if (x >= childLeft && x < childRight &&
                    y >= childTop && y < childBottom) {
                    // Point is inside this child, recurse with relative coordinates
                    val childX = x - childLeft
                    val childY = y - childTop
                    val found = findClickableViewAt(child, childX, childY)
                    if (found != null) {
                        return found
                    }
                    
                    // If child itself is clickable, return it
                    if (child.isClickable) {
                        return child
                    }
                }
            }
        }
        
        // If no child was found and parent is clickable, return parent
        if (parent.isClickable) {
            return parent
        }
        
        return null
    }
    
    /**
     * Creates a modifier indicator (deprecated, kept for compatibility).
     */
    private fun createModifierIndicator(text: String, isActive: Boolean): TextView {
        val dp8 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 
            8f, 
            context.resources.displayMetrics
        ).toInt()
        val dp6 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 
            6f, 
            context.resources.displayMetrics
        ).toInt()
        
        return TextView(context).apply {
            this.text = text
            textSize = 12f
            setTextColor(if (isActive) Color.WHITE else Color.argb(180, 255, 255, 255))
            gravity = Gravity.CENTER
            setPadding(dp6, dp8, dp6, dp8)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp8 // Right margin between indicators
            }
        }
    }
    
    /**
     * Creates a modern modifier badge with colored background.
     */
    private fun createModifierBadge(text: String): TextView {
        val dp12 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            12f,
            context.resources.displayMetrics
        ).toInt()
        val dp6 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            6f,
            context.resources.displayMetrics
        ).toInt()
        val dp4 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            context.resources.displayMetrics
        ).toInt()
        val cornerRadius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            6f,
            context.resources.displayMetrics
        )
        
        return TextView(context).apply {
            this.text = text
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp12, dp4, dp12, dp4)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp6
            }
            visibility = View.GONE
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.TRANSPARENT)
                this.cornerRadius = cornerRadius
            }
        }
    }
    
    /**
     * Updates the emoji/character grid with SYM mappings.
     * @param symMappings The mappings to display
     * @param page The active page (1=emoji, 2=characters)
     * @param inputConnection The input connection to insert characters when buttons are clicked
     */
    private fun updateEmojiKeyboard(symMappings: Map<Int, String>, page: Int, inputConnection: android.view.inputmethod.InputConnection? = null) {
        val container = emojiKeyboardContainer ?: return
        if (lastSymPageRendered == page && lastSymMappingsRendered == symMappings) {
            return
        }
        
        // Remove all existing keys
        container.removeAllViews()
        emojiKeyButtons.clear()
        
        val keySpacing = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            context.resources.displayMetrics
        ).toInt()
        
        // Calculate fixed key width based on first row (10 keys)
        val maxKeysInRow = 10 // First row has 10 keys
        val screenWidth = context.resources.displayMetrics.widthPixels
        val horizontalPadding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            8f * 2, // left + right padding
            context.resources.displayMetrics
        ).toInt()
        val availableWidth = screenWidth - horizontalPadding
        val totalSpacing = keySpacing * (maxKeysInRow - 1)
        val fixedKeyWidth = (availableWidth - totalSpacing) / maxKeysInRow
        
        val keyHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            56f,
            context.resources.displayMetrics
        ).toInt()
        
        // Create each keyboard row
        for ((rowIndex, row) in KEYBOARD_ROWS.withIndex()) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL // Center shorter rows
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    // Add margin only between rows, not after the last one
                    if (rowIndex < KEYBOARD_ROWS.size - 1) {
                        bottomMargin = keySpacing
                    }
                }
            }
            
            // For third row, add transparent placeholder on left
            if (rowIndex == 2) {
                val leftPlaceholder = createPlaceholderButton(keyHeight)
                rowLayout.addView(leftPlaceholder, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
                    marginEnd = keySpacing
                })
            }
            
            for ((index, keyCode) in row.withIndex()) {
                val label = KEY_LABELS[keyCode] ?: ""
                val content = symMappings[keyCode] ?: ""
                
                val keyButton = createEmojiKeyButton(label, content, keyHeight, page)
                emojiKeyButtons.add(keyButton)
                
                // Aggiungi click listener per rendere il pulsante touchabile
                if (content.isNotEmpty() && inputConnection != null) {
                    keyButton.isClickable = true
                    keyButton.isFocusable = true
                    keyButton.setOnClickListener {
                        // Inserisci il carattere/emoji quando si clicca
                        inputConnection.commitText(content, 1)
                        Log.d(TAG, "Clicked SYM button for keyCode $keyCode: $content")
                    }
                    
                    // Aggiungi feedback visivo quando il pulsante viene premuto
                    val originalBackground = keyButton.background
                    keyButton.setOnTouchListener { view, motionEvent ->
                        when (motionEvent.action) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                // Dim the background when pressed
                                if (originalBackground is GradientDrawable) {
                                    val pressedColor = Color.argb(80, 255, 255, 255) // More opaque
                                    originalBackground.setColor(pressedColor)
                                }
                                view.invalidate()
                            }
                            android.view.MotionEvent.ACTION_UP,
                            android.view.MotionEvent.ACTION_CANCEL -> {
                                // Ripristina lo sfondo originale
                                if (originalBackground is GradientDrawable) {
                                    val normalColor = Color.argb(40, 255, 255, 255) // Sfondo normale
                                    originalBackground.setColor(normalColor)
                                }
                                view.invalidate()
                            }
                        }
                        false // Non consumare l'evento, lascia che il click listener funzioni
                    }
                }
                
                // Use fixed width instead of weight
                rowLayout.addView(keyButton, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
                    // Add margin only if not the last key in the row
                    if (index < row.size - 1) {
                        marginEnd = keySpacing
                    }
                })
            }
            
            // Per la terza riga, aggiungi placeholder con icona matita a destra
            if (rowIndex == 2) {
                val rightPlaceholder = createPlaceholderWithPencilButton(keyHeight)
                rowLayout.addView(rightPlaceholder, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
                    marginStart = keySpacing
                })
            }
            
            container.addView(rowLayout)
        }

        // Cache what was rendered to avoid rebuilding on each status refresh
        lastSymPageRendered = page
        lastSymMappingsRendered = HashMap(symMappings)
    }
    
    /**
     * Creates a transparent placeholder to align rows.
     */
    private fun createPlaceholderButton(height: Int): View {
        return FrameLayout(context).apply {
            background = null // Trasparente
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
            isClickable = false
            isFocusable = false
        }
    }
    
    /**
     * Creates a placeholder with pencil icon to open SYM customization screen.
     */
    private fun createPlaceholderWithPencilButton(height: Int): View {
        val placeholder = FrameLayout(context).apply {
            setPadding(0, 0, 0, 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
        }
        
        // Transparent background
        placeholder.background = null
        
        // Larger icon size
        val iconSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            20f, // Further increased for better visibility
            context.resources.displayMetrics
        ).toInt()
        
        val button = ImageView(context).apply {
            background = null
            setImageResource(R.drawable.ic_edit_24)
            setColorFilter(Color.WHITE) // Bianco
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            maxWidth = iconSize
            maxHeight = iconSize
            layoutParams = FrameLayout.LayoutParams(
                iconSize,
                iconSize
            ).apply {
                gravity = Gravity.CENTER
            }
            isClickable = true
            isFocusable = true
        }
        
        button.setOnClickListener {
            // Save current SYM page state temporarily (will be confirmed only if user presses back)
            val prefs = context.getSharedPreferences("TypeQ25_prefs", Context.MODE_PRIVATE)
            val currentSymPage = prefs.getInt("current_sym_page", 0)
            if (currentSymPage > 0) {
                // Save as pending - will be converted to restore only if user presses back
                SettingsManager.setPendingRestoreSymPage(context, currentSymPage)
            }
            
            // Open SymCustomizationActivity directly
            val intent = Intent(context, SymCustomizationActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error opening SYM customization screen", e)
            }
        }
        
        placeholder.addView(button)
        return placeholder
    }
    
    /**
     * Creates an emoji/character grid key.
     * @param label The key letter
     * @param content The emoji or character to display
     * @param height The key height
     * @param page The active page (1=emoji, 2=characters)
     */
    private fun createEmojiKeyButton(label: String, content: String, height: Int, page: Int): View {
        val keyLayout = FrameLayout(context).apply {
            setPadding(0, 0, 0, 0) // No padding to allow emoji to occupy full space
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
        }
        
        // Key background with slightly rounded corners
        val cornerRadius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            6f, // Slightly rounded corners
            context.resources.displayMetrics
        )
        val drawable = GradientDrawable().apply {
            setColor(Color.argb(40, 255, 255, 255)) // Semi-transparent white
            setCornerRadius(cornerRadius)
            // No border
        }
        keyLayout.background = drawable
        
        // Emoji/character must occupy entire key, centered
        // Calculate textSize based on available height (converting from pixels to sp)
        val heightInDp = height / context.resources.displayMetrics.density
        val contentTextSize = if (page == 2) {
            // For unicode characters, use a smaller size
            (heightInDp * 0.5f)
        } else {
            // For emoji, use normal size
            (heightInDp * 0.75f)
        }
        
        val contentText = TextView(context).apply {
            text = content
            textSize = contentTextSize // textSize is in sp
            gravity = Gravity.CENTER
            // For page 2 (characters), make white and bold
            if (page == 2) {
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            // Width and height to occupy all available space
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }
        
        // Label (letter) - positioned at bottom right, in front of emoji
        val labelPadding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            2f, // Very little margin
            context.resources.displayMetrics
        ).toInt()
        
        val labelText = TextView(context).apply {
            text = label
            textSize = 12f
            setTextColor(Color.WHITE) // White 100% opaque
            gravity = Gravity.END or Gravity.BOTTOM
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                rightMargin = labelPadding
                bottomMargin = labelPadding
            }
        }
        
        // Add content first (background) then text (foreground)
        keyLayout.addView(contentText)
        keyLayout.addView(labelText)
        
        return keyLayout
    }
    
    /**
     * Creates a customizable emoji grid (for the customization screen).
     * Returns a View that can be embedded in Compose via AndroidView.
     * 
     * @param symMappings The emoji mappings to display
     * @param onKeyClick Callback called when a key is clicked (keyCode, emoji)
     */
    fun createCustomizableEmojiKeyboard(
        symMappings: Map<Int, String>,
        onKeyClick: (Int, String) -> Unit,
        page: Int = 1 // Default to page 1 (emoji)
    ): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bottomPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                12f,
                context.resources.displayMetrics
            ).toInt()
            setPadding(0, 0, 0, bottomPadding) // No horizontal padding, only bottom
            // Add black background to improve character visibility with light theme
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        val keySpacing = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            context.resources.displayMetrics
        ).toInt()
        
        // Calcola la larghezza fissa dei tasti basata sulla prima riga (10 caselle)
        // Usa ViewTreeObserver per ottenere la larghezza effettiva del container dopo il layout
        val maxKeysInRow = 10 // First row has 10 boxes
        
        // Initialize with a temporary width, will be updated after layout
        var fixedKeyWidth = 0
        
        container.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val containerWidth = container.width
                if (containerWidth > 0) {
                    val totalSpacing = keySpacing * (maxKeysInRow - 1)
                    fixedKeyWidth = (containerWidth - totalSpacing) / maxKeysInRow
                    
                    // Update all keys with the correct width
                    for (i in 0 until container.childCount) {
                        val rowLayout = container.getChildAt(i) as? LinearLayout
                        rowLayout?.let { row ->
                            for (j in 0 until row.childCount) {
                                val keyButton = row.getChildAt(j)
                                val layoutParams = keyButton.layoutParams as? LinearLayout.LayoutParams
                                layoutParams?.let {
                                    it.width = fixedKeyWidth
                                    keyButton.layoutParams = it
                                }
                            }
                        }
                    }
                    
                    // Rimuovi il listener dopo il primo layout
                    container.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        })
        
        // Initial value based on screen width (will be updated by listener)
        val screenWidth = context.resources.displayMetrics.widthPixels
        val totalSpacing = keySpacing * (maxKeysInRow - 1)
        fixedKeyWidth = (screenWidth - totalSpacing) / maxKeysInRow
        
        val keyHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            56f,
            context.resources.displayMetrics
        ).toInt()
        
        // Create each row of the keyboard (same structure as the real keyboard)
        for ((rowIndex, row) in KEYBOARD_ROWS.withIndex()) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL // Center shorter rows
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (rowIndex < KEYBOARD_ROWS.size - 1) {
                        bottomMargin = keySpacing
                    }
                }
            }
            
            // Per la terza riga, aggiungi placeholder trasparente a sinistra
            if (rowIndex == 2) {
                val leftPlaceholder = createPlaceholderButton(keyHeight)
                rowLayout.addView(leftPlaceholder, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
                    marginEnd = keySpacing
                })
            }
            
            for ((index, keyCode) in row.withIndex()) {
                val label = KEY_LABELS[keyCode] ?: ""
                val emoji = symMappings[keyCode] ?: ""
                
                // Use the same createEmojiKeyButton function as the real keyboard
                val keyButton = createEmojiKeyButton(label, emoji, keyHeight, page)
                
                // Add click listener
                keyButton.setOnClickListener {
                    onKeyClick(keyCode, emoji)
                }
                
                // Use fixed width instead of weight (same layout as the real keyboard)
                rowLayout.addView(keyButton, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
                    if (index < row.size - 1) {
                        marginEnd = keySpacing
                    }
                })
            }
            
            // Per la terza riga nella schermata di personalizzazione, aggiungi placeholder trasparente a destra
            // per mantenere l'allineamento (senza matita e senza click listener)
            if (rowIndex == 2) {
                val rightPlaceholder = createPlaceholderButton(keyHeight)
                rowLayout.addView(rightPlaceholder, LinearLayout.LayoutParams(fixedKeyWidth, keyHeight).apply {
                    marginStart = keySpacing
                })
            }
            
            container.addView(rowLayout)
        }
        
        return container
    }
    
    /**
     * Anima l'apparizione della griglia emoji solo con slide up (nessun fade).
     * @param backgroundView Il view dello sfondo da impostare a opaco immediatamente
     */
    private fun animateEmojiKeyboardIn(view: View, backgroundView: View? = null) {
        val height = view.height
        if (height == 0) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
        }
        val measuredHeight = view.measuredHeight

        view.alpha = 1f
        view.translationY = measuredHeight.toFloat()
        view.visibility = View.VISIBLE

        // Set background to opaque immediately without animation
        backgroundView?.let { bgView ->
            if (bgView.background !is ColorDrawable) {
                bgView.background = ColorDrawable(DEFAULT_BACKGROUND)
            }
            (bgView.background as? ColorDrawable)?.alpha = 255
        }

        val animator = ValueAnimator.ofFloat(measuredHeight.toFloat(), 0f).apply {
            duration = 125
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                view.translationY = value
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.translationY = 0f
                    view.alpha = 1f
                }
            })
        }
        animator.start()
    }
    
    /**
     * Anima la scomparsa della griglia emoji (slide down + fade out).
     * @param backgroundView Il view dello sfondo (non animato, rimane opaco)
     * @param onAnimationEnd Callback chiamato quando l'animazione è completata
     */
    private fun animateEmojiKeyboardOut(view: View, backgroundView: View? = null, onAnimationEnd: (() -> Unit)? = null) {
        val height = view.height
        if (height == 0) {
            view.visibility = View.GONE
            onAnimationEnd?.invoke()
            return
        }

        // Background remains opaque, no animation

        val animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 100
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                view.alpha = progress
                view.translationY = height * (1f - progress)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = View.GONE
                    view.translationY = 0f
                    view.alpha = 1f
                    onAnimationEnd?.invoke()
                }
            })
        }
        animator.start()
    }

    
    

    fun update(snapshot: StatusSnapshot, emojiMapText: String = "", inputConnection: android.view.inputmethod.InputConnection? = null, symMappings: Map<Int, String>? = null) {
        variationBarView.onVariationSelectedListener = onVariationSelectedListener
        variationBarView.onCursorMovedListener = onCursorMovedListener
        variationBarView.updateInputConnection(inputConnection)
        variationBarView.setSymModeActive(snapshot.symPage > 0)
        
        val layout = ensureLayoutCreated(emojiMapText) ?: return
        
        // On Q25, use minimal status bar (modifiers shown in notification instead)
        val deviceType = it.srik.TypeQ25.DeviceManager.getDevice(context)
        val isQ25 = deviceType == "Q25"
        
        if (isQ25 && snapshot.symPage == 0) {
            // On Q25, keep layout at WRAP_CONTENT to show variations bar
            // The modifiers container will be hidden, but variations remain visible
            layout.layoutParams = layout.layoutParams?.apply {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            } ?: LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        
        val emojiView = emojiMapTextView ?: return
        val emojiKeyboardView = emojiKeyboardContainer ?: return
        emojiView.visibility = View.GONE
        
        if (snapshot.navModeActive) {
            layout.visibility = View.GONE
            return
        }
        layout.visibility = View.VISIBLE
        
        // On Q25, make background transparent
        if (isQ25 && snapshot.symPage == 0) {
            // Transparent background on Q25
            layout.setBackgroundColor(Color.parseColor("#2D2D2D"))
        } else {
            if (layout.background !is ColorDrawable) {
                layout.background = ColorDrawable(DEFAULT_BACKGROUND)
            } else if (snapshot.symPage == 0) {
                (layout.background as ColorDrawable).alpha = 255
            }
        }
        
        // Always show variations/suggestions
        val variationsBar = variationBarView
        val variationsWrapperView = variationsWrapper
        
        if (snapshot.symPage > 0 && symMappings != null) {
            Log.d(TAG, "SYM active: page=${snapshot.symPage}, mode=$mode, symMappings.size=${symMappings.size}")
            updateEmojiKeyboard(symMappings, snapshot.symPage, inputConnection)
            variationsBar.resetVariationsState()

            // Pin background to opaque IME color and hide variations so SYM animates on a solid canvas.
            if (layout.background !is ColorDrawable) {
                layout.background = ColorDrawable(DEFAULT_BACKGROUND)
            }
            (layout.background as? ColorDrawable)?.alpha = 255
            variationsWrapperView?.apply {
                visibility = View.INVISIBLE // keep space to avoid shrink/flash
                isEnabled = false
                isClickable = false
            }
            variationsBar.hideImmediate()

            val symHeight = ensureEmojiKeyboardMeasuredHeight(emojiKeyboardView, layout)
            Log.d(TAG, "Emoji keyboard height: $symHeight, container visibility will be set to VISIBLE")
            emojiKeyboardView.setBackgroundColor(Color.parseColor("#1A1A1A"))
            emojiKeyboardView.visibility = View.VISIBLE
            emojiKeyboardView.layoutParams = (emojiKeyboardView.layoutParams ?: LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                symHeight
            )).apply { height = symHeight }
            
            // Calculate total height needed: candidates bar + variations + emoji keyboard
            val variationsHeight = variationsWrapperView?.height ?: 0
            val totalHeight = variationsHeight + symHeight
            
            // Ensure parent layout expands to show emoji keyboard (especially important for minimal UI mode)
            // Use explicit height instead of WRAP_CONTENT for minimal UI compatibility
            layout.layoutParams = (layout.layoutParams ?: LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                totalHeight
            )).apply {
                height = totalHeight
                Log.d(TAG, "Setting layout height to $totalHeight (variations: $variationsHeight + emoji: $symHeight)")
            }
            
            // Force parent to re-measure and layout, especially important for minimal UI
            layout.parent?.let { parent ->
                if (parent is ViewGroup) {
                    parent.requestLayout()
                }
            }
            layout.requestLayout()
            layout.invalidate()
            
            // Notify service to update window insets (critical for minimal UI mode)
            onRequestWindowResize?.invoke()
            
            if (!symShown && !wasSymActive) {
                emojiKeyboardView.alpha = 1f // keep black visible immediately
                emojiKeyboardView.translationY = symHeight.toFloat()
                animateEmojiKeyboardIn(emojiKeyboardView, layout)
                symShown = true
                wasSymActive = true
            } else {
                emojiKeyboardView.alpha = 1f
                emojiKeyboardView.translationY = 0f
                wasSymActive = true
            }
            return
        }
        
        if (emojiKeyboardView.visibility == View.VISIBLE) {
            animateEmojiKeyboardOut(emojiKeyboardView, layout) {
                variationsWrapperView?.apply {
                    visibility = View.VISIBLE
                    isEnabled = true
                    isClickable = true
                }
                variationsBar.showVariations(snapshot, inputConnection)
            }
            symShown = false
            wasSymActive = false
        } else {
            emojiKeyboardView.visibility = View.GONE
            variationsWrapperView?.apply {
                visibility = View.VISIBLE
                isEnabled = true
                isClickable = true
            }
            variationsBar.showVariations(snapshot, inputConnection)
            symShown = false
            wasSymActive = false
        }
    }

    private fun ensureEmojiKeyboardMeasuredHeight(view: View, parent: View): Int {
        if (view.height > 0) {
            return view.height
        }
        val width = if (parent.width > 0) parent.width else context.resources.displayMetrics.widthPixels
        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        return view.measuredHeight
    }
}


