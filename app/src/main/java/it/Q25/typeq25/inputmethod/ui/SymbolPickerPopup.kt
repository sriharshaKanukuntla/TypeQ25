package it.srik.TypeQ25.inputmethod.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.view.MotionEvent
import android.view.GestureDetector
import androidx.core.view.GestureDetectorCompat
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.view.inputmethod.EditorInfo
import it.srik.TypeQ25.inputmethod.ui.EmojiDataParser

/**
 * Popup window for selecting symbols and emojis via SYM key.
 * Works in both full UI and minimal UI modes with category-based browsing.
 */
class SymbolPickerPopup(
    private val context: Context,
    private val onSymbolSelected: (String) -> Unit,
    private val onDismiss: () -> Unit,
    private val onClearModifiers: () -> Unit = {}
) {
    
    // Lazy initialization of emoji parser
    private val emojiParser by lazy { EmojiDataParser(context) }
    
    // Cache for emoji categories to avoid reloading every time
    private var cachedEmojiCategories: Map<String, List<String>>? = null
    
    // Get emoji categories dynamically from emoji-data
    private fun getEmojiCategories(): Map<String, List<String>> {
        if (cachedEmojiCategories != null) {
            return cachedEmojiCategories!!
        }
        
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
        
        // Include custom emojis per category
        val categories = categoryNames.associateWith { categoryName ->
            emojiParser.getEmojisForCategory(categoryName)
        }
        cachedEmojiCategories = categories
        return categories
    }
    
    
    private var popupWindow: PopupWindow? = null
    private var cachedContentView: View? = null
    private var currentTab: Tab = Tab.FAVORITES
    private var currentSymbolCategory: String = "Common"
    private var currentEmojiCategory: String = "Smileys & Emotion"
    private var currentPage: Int = 0
    private var favoritesButton: TextView? = null
    private var symbolsButton: TextView? = null
    private var emojisButton: TextView? = null
    private var categoryButtonsContainer: HorizontalScrollView? = null
    private var frequentItemsContainer: LinearLayout? = null
    private var pageIndicatorText: TextView? = null
    private var keyboardLayoutContainer: LinearLayout? = null
    private var searchEditText: EditText? = null
    private var searchQuery: String = ""
    
    // Map physical keys to their positions in keyboard layout
    private val keyboardLayout = listOf(
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
        listOf("Z", "X", "C", "V", "B", "N", "M")
    )
    
    // SharedPreferences for tracking frequently used items
    private val prefs = context.getSharedPreferences("symbol_picker_prefs", Context.MODE_PRIVATE)
    
    // Cache for frequent items to avoid repeated parsing
    private var cachedFrequentItems: List<String>? = null
    private var usageMapCache: MutableMap<String, Int>? = null
    
    // Cache for button dimensions
    private var cachedButtonWidth: Int? = null
    private var cachedNavButtonWidth: Int? = null
    
    // Gesture detector for swipe navigation
    private var gestureDetector: GestureDetectorCompat? = null
    
    enum class Tab {
        FAVORITES,
        SYMBOLS,
        EMOJIS
    }
    
    companion object {
        // Organized symbol categories
        private val SYMBOL_CATEGORIES = mapOf(
            "Common" to listOf(
                "!", "@", "#", "$", "%", "^", "&", "*", "(", ")",
                "-", "_", "=", "+", "[", "]", "{", "}", "\\", "|",
                ";", ":", "'", "\"", ",", ".", "<", ">", "/", "?",
                "`", "~"
            ),
            "Currency" to listOf(
                "$", "€", "£", "¥", "₹", "¢", "₽", "₩", "₪", "฿",
                "₫", "₱", "₡", "₦", "₨", "₴", "₵"
            ),
            "Math" to listOf(
                "+", "-", "×", "÷", "=", "≠", "±", "≈", "<", ">",
                "≤", "≥", "∞", "∑", "∏", "√", "∫", "∂", "∆", "∇",
                "∈", "∉", "⊂", "⊃", "∪", "∩", "∧", "∨", "¬", "⊕",
                "⊗", "α", "β", "γ", "δ", "π", "μ", "σ", "ω"
            ),
            "Arrows" to listOf(
                "←", "→", "↑", "↓", "↔", "↕", "⇐", "⇒", "⇑", "⇓",
                "⇔", "⇕", "↖", "↗", "↘", "↙", "⟵", "⟶", "⟷"
            ),
            "Special" to listOf(
                "©", "®", "™", "℗", "℠", "№", "℃", "℉", "Ω", "§",
                "¶", "†", "‡", "•", "°", "′", "″", "‴", "℞", "℮"
            ),
            "Quotes" to listOf(
                "'", "'", "\"", """, """, "‚", "„", "‛", "‟",
                "«", "»", "‹", "›", "〈", "〉", "…", "–", "—"
            )
        )
    }
    
    // Favorites management
    private val FAVORITES_KEY = "favorites_list"
    private val MAX_FAVORITES = 26 // Match keyboard layout (Q-Z)
    
    /**
     * Get the list of favorite symbols/emojis.
     */
    private fun getFavorites(): List<String> {
        val favoritesString = prefs.getString(FAVORITES_KEY, "") ?: ""
        return if (favoritesString.isBlank()) {
            emptyList()
        } else {
            favoritesString.split("|").filter { it.isNotBlank() }
        }
    }
    
    /**
     * Save favorites list to SharedPreferences.
     */
    private fun saveFavorites(favorites: List<String>) {
        prefs.edit()
            .putString(FAVORITES_KEY, favorites.joinToString("|"))
            .apply()
    }
    
    /**
     * Add an item to favorites. Returns true if added successfully, false if at limit or already exists.
     */
    private fun addToFavorites(item: String): Boolean {
        val favorites = getFavorites().toMutableList()
        
        // Check if already in favorites
        if (favorites.contains(item)) {
            return false
        }
        
        // Check if at max capacity
        if (favorites.size >= MAX_FAVORITES) {
            return false
        }
        
        favorites.add(item)
        saveFavorites(favorites)
        return true
    }
    
    /**
     * Remove an item from favorites.
     */
    private fun removeFromFavorites(item: String) {
        val favorites = getFavorites().toMutableList()
        favorites.remove(item)
        saveFavorites(favorites)
    }
    
    /**
     * Check if an item is in favorites.
     */
    private fun isFavorite(item: String): Boolean {
        return getFavorites().contains(item)
    }
    
    fun show(anchorView: View) {
        Log.d("SymbolPickerPopup", "show() called")
        if (popupWindow?.isShowing == true) {
            popupWindow?.dismiss()
        }
        
        // Reset state to always start with Favorites tab
        currentTab = Tab.FAVORITES
        currentSymbolCategory = "Common"
        currentEmojiCategory = "Smileys & Emotion"
        currentPage = 0
        
        // Reuse cached content view if available, otherwise create new one
        val contentView = cachedContentView ?: createContentView().also { cachedContentView = it }
        
        // Update the keyboard layout to reflect current state
        updateKeyboardLayout()
        
        // Create popup window if not exists or reuse
        if (popupWindow == null) {
            val popup = PopupWindow(
                contentView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(400f).toInt(),  // Increased height for keyboard layout
                false  // Not focusable - key events handled by handlePhysicalKey from service
            )
            
            popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            popup.isOutsideTouchable = false  // Don't dismiss when touching outside
            popup.isTouchable = true  // Allow touch events inside popup
            popup.elevation = dpToPx(8f)
            popup.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            
            popup.setOnDismissListener {
                onDismiss()
            }
            
            popupWindow = popup
        } else {
            // Update content view if needed
            popupWindow?.contentView = contentView
        }
        
        // Show above the anchor view
        popupWindow?.showAtLocation(anchorView, Gravity.BOTTOM, 0, 0)
    }
    
    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
    }
    
    fun isShowing(): Boolean = popupWindow?.isShowing == true
    
    /**
     * Handle physical key press to insert corresponding symbol/emoji from current page
     * Returns true if the key was handled, false otherwise
     */
    fun handlePhysicalKey(keyCode: Int, isCtrlPressed: Boolean = false, isAltPressed: Boolean = false): Boolean {
        if (!isShowing()) return false
        
        // Don't consume Ctrl or Alt key presses themselves - let them be handled by the modifier system
        // This allows Ctrl latch and Alt latch to work properly
        if (keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT ||
            keyCode == 60 || // Q25 uses keycode 60 for CTRL
            keyCode == KeyEvent.KEYCODE_ALT_LEFT || keyCode == KeyEvent.KEYCODE_ALT_RIGHT ||
            keyCode == 58) { // Q25 uses keycode 58 for ALT (which is actually SYM key)
            return false  // Let the modifier system handle these keys
        }
        
        // Handle Ctrl+T: Cycle through Favorites, Symbols, and Emojis tabs
        if (isCtrlPressed && keyCode == KeyEvent.KEYCODE_T) {
            val newTab = when (currentTab) {
                Tab.FAVORITES -> Tab.SYMBOLS
                Tab.SYMBOLS -> Tab.EMOJIS
                Tab.EMOJIS -> Tab.FAVORITES
            }
            switchTab(newTab)
            return true
        }
        
        // Handle Ctrl+S or Ctrl+J: Previous page (when Ctrl is latched)
        if (isCtrlPressed && (keyCode == KeyEvent.KEYCODE_S || keyCode == KeyEvent.KEYCODE_J)) {
            navigatePage(-1)
            return true
        }
        
        // Handle Ctrl+F or Ctrl+L: Next page (when Ctrl is latched)
        if (isCtrlPressed && (keyCode == KeyEvent.KEYCODE_F || keyCode == KeyEvent.KEYCODE_L)) {
            navigatePage(1)
            return true
        }
        
        // Handle Ctrl+E or Ctrl+I: Previous category (when Ctrl is latched)
        if (isCtrlPressed && (keyCode == KeyEvent.KEYCODE_E || keyCode == KeyEvent.KEYCODE_I)) {
            navigateCategory(-1)
            return true
        }
        
        // Handle Ctrl+D or Ctrl+K: Next category (when Ctrl is latched)
        if (isCtrlPressed && (keyCode == KeyEvent.KEYCODE_D || keyCode == KeyEvent.KEYCODE_K)) {
            navigateCategory(1)
            return true
        }
        
        // Don't consume DPad/Tab/Arrow keys when Ctrl is pressed - allow them for UI navigation
        if (isCtrlPressed) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_TAB) {
                return false  // Allow navigation keys through when Ctrl is active
            }
            // Consume all other key presses when Ctrl is active
            return true
        }
        
        // Consume DPad keys to prevent them from reaching the input field behind the popup
        // Only when Ctrl is NOT pressed
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
            keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            return true  // Consume DPad events to prevent background interaction
        }
        
        // Handle Alt+number keys for recent items on Q25 keyboard
        // On Q25, Alt+letter keys produce numbers: W=1, E=2, R=3, S=4, D=5, F=6, Z=7, X=8, C=9, 0=0
        if (isAltPressed) {
            // Check for 0 key (direct keycode)
            if (keyCode == KeyEvent.KEYCODE_0) {
                insertRecentItem(9)
                return true
            }
            
            // Check for letter keys that produce numbers with Alt on Q25
            // W=1, E=2, R=3, S=4, D=5, F=6, Z=7, X=8, C=9
            val numberIndex = when (keyCode) {
                KeyEvent.KEYCODE_W -> 0  // Alt+W = 1 (index 0)
                KeyEvent.KEYCODE_E -> 1  // Alt+E = 2 (index 1)
                KeyEvent.KEYCODE_R -> 2  // Alt+R = 3 (index 2)
                KeyEvent.KEYCODE_S -> 3  // Alt+S = 4 (index 3)
                KeyEvent.KEYCODE_D -> 4  // Alt+D = 5 (index 4)
                KeyEvent.KEYCODE_F -> 5  // Alt+F = 6 (index 5)
                KeyEvent.KEYCODE_Z -> 6  // Alt+Z = 7 (index 6)
                KeyEvent.KEYCODE_X -> 7  // Alt+X = 8 (index 7)
                KeyEvent.KEYCODE_C -> 8  // Alt+C = 9 (index 8)
                else -> -1
            }
            
            if (numberIndex >= 0) {
                insertRecentItem(numberIndex)
                return true
            }
            
            // Consume all other Alt+key combinations
            return true
        }
        
        // Map keycode to keyboard position
        val keyChar = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
        val items = getCurrentCategoryItems()
        val itemsPerPage = 26  // 10 + 9 + 7
        val startIndex = currentPage * itemsPerPage
        val pageItems = items.drop(startIndex).take(itemsPerPage)
        
        // Find the position of this key in the keyboard layout
        var position = -1
        var currentPos = 0
        for (row in keyboardLayout) {
            for (key in row) {
                if (key == keyChar) {
                    position = currentPos
                    break
                }
                currentPos++
            }
            if (position >= 0) break
        }
        
        // If key found and has a symbol/emoji on current page
        if (position >= 0 && position < pageItems.size) {
            val symbol = pageItems[position]
            trackSymbolUsage(symbol)
            onSymbolSelected(symbol)
            dismiss()
            return true
        }
        
        return false
    }
    
    private fun insertRecentItem(index: Int) {
        val frequentItems = getFrequentItems()
        if (index < frequentItems.size) {
            val symbol = frequentItems[index]
            trackSymbolUsage(symbol)
            // Clear modifier state BEFORE selection to ensure it's cleared immediately
            onClearModifiers()
            onSymbolSelected(symbol)
            dismiss()
        }
    }
    
    private fun navigateCategory(direction: Int) {
        val categories = if (currentTab == Tab.SYMBOLS) {
            SYMBOL_CATEGORIES.keys.toList()
        } else {
            getEmojiCategories().keys.toList()
        }
        
        val currentCategory = if (currentTab == Tab.SYMBOLS) currentSymbolCategory else currentEmojiCategory
        val currentIndex = categories.indexOf(currentCategory)
        val newIndex = (currentIndex + direction + categories.size) % categories.size
        val newCategory = categories[newIndex]
        
        switchCategory(newCategory)
    }
    
    private fun createContentView(): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(getBackgroundColor())
            setPadding(
                dpToPx(16f).toInt(),
                dpToPx(16f).toInt(),
                dpToPx(16f).toInt(),
                dpToPx(16f).toInt()
            )
        }
        
        // Add tabs (Symbols/Emojis) with close button
        val tabLayout = createTabLayout()
        container.addView(tabLayout)
        
        // Add search field (hidden for now)
        val searchField = createSearchField()
        searchField.visibility = View.GONE
        container.addView(searchField)
        
        // Add frequent items row (horizontal scrollable)
        val frequentItemsScroll = createFrequentItemsRow()
        container.addView(frequentItemsScroll)
        
        // Add category buttons (horizontal scrollable)
        val categoryButtonsScroll = createCategoryButtons()
        categoryButtonsContainer = categoryButtonsScroll
        // Hide category buttons if starting with Favorites tab
        if (currentTab == Tab.FAVORITES) {
            categoryButtonsScroll.visibility = View.GONE
        }
        container.addView(categoryButtonsScroll)
        
        // Add content area
        val contentArea = createContentArea()
        container.addView(contentArea)
        
        return container
    }
    
    private fun createTabLayout(): LinearLayout {
        val tabLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
        }
        
        // Fixed height for all buttons
        val buttonHeight = dpToPx(44f).toInt()
        
        favoritesButton = createTabButton("★", currentTab == Tab.FAVORITES, buttonHeight)
        symbolsButton = createTabButton("Symbols", currentTab == Tab.SYMBOLS, buttonHeight)
        emojisButton = createTabButton("Emojis", currentTab == Tab.EMOJIS, buttonHeight)
        
        favoritesButton?.setOnClickListener {
            switchTab(Tab.FAVORITES)
        }
        
        symbolsButton?.setOnClickListener {
            switchTab(Tab.SYMBOLS)
        }
        
        emojisButton?.setOnClickListener {
            switchTab(Tab.EMOJIS)
        }
        
        // Add tab buttons
        tabLayout.addView(favoritesButton)
        tabLayout.addView(symbolsButton)
        tabLayout.addView(emojisButton)
        
        // Create close button with fixed width and same height
        val closeButton = TextView(context).apply {
            text = "✕"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(getTextColor())
            setPadding(
                dpToPx(16f).toInt(),
                0,
                dpToPx(16f).toInt(),
                0
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                buttonHeight
            ).apply {
                setMargins(dpToPx(4f).toInt(), 0, 0, 0)
                gravity = Gravity.CENTER_VERTICAL
            }
            setBackgroundColor(getBackgroundColorLight())
            setOnClickListener {
                dismiss()
            }
        }
        
        tabLayout.addView(closeButton)
        
        return tabLayout
    }
    
    private fun createSearchField(): LinearLayout {
        val searchContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dpToPx(8f).toInt(), 0, dpToPx(8f).toInt())
            }
            setBackgroundColor(getBackgroundColorLight())
            setPadding(
                dpToPx(12f).toInt(),
                dpToPx(8f).toInt(),
                dpToPx(12f).toInt(),
                dpToPx(8f).toInt()
            )
        }
        
        searchEditText = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            hint = "Search symbols/emojis..."
            textSize = 16f
            setTextColor(getTextColor())
            setHintTextColor(getTextColorSecondary())
            background = null
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchQuery = s?.toString() ?: ""
                    filterContent()
                }
                
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        
        val clearButton = TextView(context).apply {
            text = "✕"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(getTextColorSecondary())
            setPadding(
                dpToPx(8f).toInt(),
                dpToPx(4f).toInt(),
                dpToPx(8f).toInt(),
                dpToPx(4f).toInt()
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
            setOnClickListener {
                searchEditText?.setText("")
            }
        }
        
        // Show/hide clear button based on search text
        searchEditText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                clearButton.visibility = if (s?.isNotEmpty() == true) View.VISIBLE else View.GONE
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
        
        searchContainer.addView(searchEditText)
        searchContainer.addView(clearButton)
        
        return searchContainer
    }
    
    private fun createTabButton(text: String, isActive: Boolean, buttonHeight: Int): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(
                dpToPx(20f).toInt(),
                dpToPx(10f).toInt(),
                dpToPx(20f).toInt(),
                dpToPx(10f).toInt()
            )
            layoutParams = LinearLayout.LayoutParams(
                0,
                buttonHeight,
                1f
            ).apply {
                setMargins(0, 0, dpToPx(4f).toInt(), 0)
            }
            
            if (isActive) {
                setTextColor(getTextColor())
                setBackgroundColor(getAccentColor())
            } else {
                setTextColor(getTextColorSecondary())
                setBackgroundColor(getBackgroundColorLight())
            }
        }
    }
    
    private fun createCategoryButtons(): HorizontalScrollView {
        val scrollView = HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dpToPx(4f).toInt())
            }
            isHorizontalScrollBarEnabled = true
            isFocusable = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        
        val buttonContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Add category buttons based on current tab
        val categories = if (currentTab == Tab.SYMBOLS) {
            SYMBOL_CATEGORIES.keys
        } else {
            getEmojiCategories().keys
        }
        
        categories.forEachIndexed { index, categoryName ->
            val isActive = if (currentTab == Tab.SYMBOLS) {
                categoryName == currentSymbolCategory
            } else {
                categoryName == currentEmojiCategory
            }
            
            val button = createCategoryButton(categoryName, isActive)
            button.setOnClickListener {
                switchCategory(categoryName)
            }
            
            // Add focus listener to scroll button into view when navigating with keypad
            button.onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    scrollView.post {
                        val buttonLeft = view.left
                        val buttonRight = view.right
                        val buttonWidth = view.width
                        val scrollViewWidth = scrollView.width
                        
                        // Calculate scroll position to center the focused button
                        val scrollX = buttonLeft - (scrollViewWidth / 2) + (buttonWidth / 2)
                        scrollView.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
                    }
                }
            }
            
            buttonContainer.addView(button)
        }
        
        scrollView.addView(buttonContainer)
        return scrollView
    }
    
    private fun createCategoryButton(text: String, isActive: Boolean): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(
                dpToPx(16f).toInt(),
                dpToPx(8f).toInt(),
                dpToPx(16f).toInt(),
                dpToPx(8f).toInt()
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dpToPx(4f).toInt(), 0, dpToPx(4f).toInt(), 0)
            }
            
            // Make button focusable for keypad navigation
            isFocusable = true
            isFocusableInTouchMode = false
            
            if (isActive) {
                setTextColor(getTextColor())
                setBackgroundColor(getAccentColor())
            } else {
                setTextColor(getTextColorSecondary())
                setBackgroundColor(getBackgroundColorLight())
            }
        }
    }
    
    private fun createContentArea(): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setPadding(0, dpToPx(8f).toInt(), 0, dpToPx(8f).toInt())
        }
        
        // Add spacer to push keyboard to bottom
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f  // Takes up all available space
            )
        }
        container.addView(spacer)
        
        // Add page indicator
        val pageIndicator = TextView(context).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(getTextColorSecondary())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dpToPx(8f).toInt())
            }
        }
        pageIndicatorText = pageIndicator
        container.addView(pageIndicator)
        
        // Add keyboard layout container
        val keyboardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
            id = View.generateViewId()
        }
        keyboardLayoutContainer = keyboardLayout
        
        // Setup gesture detector for swipe navigation
        gestureDetector = GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val diffX = (e2.x - (e1?.x ?: 0f))
                val diffY = (e2.y - (e1?.y ?: 0f))
                
                // Only handle horizontal swipes (more horizontal than vertical)
                if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY) && kotlin.math.abs(diffX) > 100) {
                    if (diffX > 0) {
                        // Swipe right - go to previous page
                        navigatePage(-1)
                    } else {
                        // Swipe left - go to next page
                        navigatePage(1)
                    }
                    return true
                }
                return false
            }
        })
        
        keyboardLayout.setOnTouchListener { _, event ->
            gestureDetector?.onTouchEvent(event) ?: false
        }
        
        container.addView(keyboardLayout)
        
        // Load default category
        updateKeyboardLayout()
        
        return container
    }
    
    private fun navigatePage(direction: Int) {
        val items = getCurrentCategoryItems()
        val totalPages = getTotalPages(items.size)
        
        currentPage = (currentPage + direction + totalPages) % totalPages
        updateKeyboardLayout()
    }
    
    private fun getTotalPages(itemCount: Int, itemsPerPage: Int = 26): Int {
        return maxOf(1, (itemCount + itemsPerPage - 1) / itemsPerPage)
    }
    
    private fun getCurrentCategoryItems(): List<String> {
        // If there's a search query, return filtered items
        if (searchQuery.isNotEmpty()) {
            return getFilteredItems()
        }
        
        return when (currentTab) {
            Tab.FAVORITES -> getFavorites()
            Tab.SYMBOLS -> SYMBOL_CATEGORIES[currentSymbolCategory] ?: emptyList()
            Tab.EMOJIS -> getEmojiCategories()[currentEmojiCategory] ?: emptyList()
        }
    }
    
    private fun updateKeyboardLayout() {
        val layout = keyboardLayoutContainer ?: return
        layout.removeAllViews()
        
        val items = getCurrentCategoryItems()
        
        // Show "No results" message when search returns no items
        if (searchQuery.isNotEmpty() && items.isEmpty()) {
            val noResultsMessage = TextView(context).apply {
                text = "No results found for \"$searchQuery\""
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(getTextColorSecondary())
                setPadding(
                    dpToPx(24f).toInt(),
                    dpToPx(48f).toInt(),
                    dpToPx(24f).toInt(),
                    dpToPx(48f).toInt()
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            layout.addView(noResultsMessage)
            pageIndicatorText?.text = "Page 1 of 1"
            return
        }
        
        // Show helpful message when favorites is empty
        if (currentTab == Tab.FAVORITES && items.isEmpty()) {
            val emptyMessage = TextView(context).apply {
                text = "No favorites yet!\n\nLong-press any symbol or emoji to add it to favorites.\n\n(Maximum $MAX_FAVORITES items)"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(getTextColorSecondary())
                setPadding(
                    dpToPx(24f).toInt(),
                    dpToPx(48f).toInt(),
                    dpToPx(24f).toInt(),
                    dpToPx(48f).toInt()
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            layout.addView(emptyMessage)
            pageIndicatorText?.text = "Page 1 of 1"
            return
        }
        
        // No special handling for empty emoji categories - just show the normal layout with + button
        
        val itemsPerPage = 26  // 26 keys: row 1=10, row 2=9, row 3=7
        val totalPages = getTotalPages(items.size, itemsPerPage)
        val startIndex = currentPage * itemsPerPage
        val pageItems = items.drop(startIndex).take(itemsPerPage)
        
        // Update page indicator
        val pageText = "Page ${currentPage + 1} of $totalPages"
        pageIndicatorText?.text = pageText
        pageIndicatorText?.setOnClickListener(null)
        
        // Calculate button dimensions once and cache them
        val screenWidth = context.resources.displayMetrics.widthPixels
        val totalPadding = dpToPx(32f).toInt()
        val buttonMargin = dpToPx(3f).toInt() * 2
        
        val buttonWidth = cachedButtonWidth ?: run {
            val width = ((screenWidth - totalPadding) / 10) - buttonMargin
            cachedButtonWidth = width
            width
        }
        
        val navButtonWidth = cachedNavButtonWidth ?: run {
            val width = (buttonWidth * 1.5f).toInt() + (buttonMargin * 0.5f).toInt()
            cachedNavButtonWidth = width
            width
        }
        
        // Create keyboard rows
        var itemIndex = 0
        
        // Row 1: 10 keys (Q-P)
        val row1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(2f).toInt(), 0, dpToPx(2f).toInt())
        }
        keyboardLayout[0].forEach { key ->
            val item = if (itemIndex < pageItems.size) pageItems[itemIndex] else ""
            val button = createKeyboardButton(key, item, buttonWidth)
            row1.addView(button)
            itemIndex++
        }
        layout.addView(row1)
        
        // Row 2: 9 keys (A-L)
        val row2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(2f).toInt(), 0, dpToPx(2f).toInt())
        }
        keyboardLayout[1].forEach { key ->
            val item = if (itemIndex < pageItems.size) pageItems[itemIndex] else ""
            val button = createKeyboardButton(key, item, buttonWidth)
            row2.addView(button)
            itemIndex++
        }
        layout.addView(row2)
        
        // Row 3: Navigation buttons + 7 keys (Z-M) + Navigation buttons
        val row3 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(2f).toInt(), 0, dpToPx(2f).toInt())
        }
        
        // Determine if there's only 1 page
        val hasMultiplePages = totalPages > 1
        
        // Prev button (◀)
        val prevButton = createNavigationButton("◀", -1, navButtonWidth, hasMultiplePages)
        row3.addView(prevButton)
        
        // 7 keys (Z-M - all keys from row 3)
        keyboardLayout[2].forEach { key ->
            val item = if (itemIndex < pageItems.size) pageItems[itemIndex] else ""
            val button = createKeyboardButton(key, item, buttonWidth)
            row3.addView(button)
            itemIndex++
        }
        
        // Next button (▶)
        val nextButton = createNavigationButton("▶", 1, navButtonWidth, hasMultiplePages)
        row3.addView(nextButton)
        
        layout.addView(row3)
    }
    
    private fun createNavigationButton(text: String, direction: Int, width: Int, enabled: Boolean = true): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 24f  // Larger icon
            gravity = Gravity.CENTER
            setTextColor(if (enabled) getTextColor() else getTextColorSecondary())
            layoutParams = LinearLayout.LayoutParams(
                width,
                dpToPx(55f).toInt()
            ).apply {
                setMargins(dpToPx(3f).toInt(), dpToPx(2f).toInt(), dpToPx(3f).toInt(), dpToPx(2f).toInt())
            }
            setBackgroundColor(if (enabled) getAccentColor() else getBackgroundColorDark())
            isEnabled = enabled
            alpha = if (enabled) 1.0f else 0.4f
            setOnClickListener {
                if (enabled) {
                    navigatePage(direction)
                }
            }
        }
    }
    
    private fun createKeyboardButton(keyLabel: String, symbol: String, width: Int): LinearLayout {
        val buttonContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                width,
                dpToPx(55f).toInt()
            ).apply {
                setMargins(dpToPx(3f).toInt(), dpToPx(2f).toInt(), dpToPx(3f).toInt(), dpToPx(2f).toInt())
            }
            gravity = Gravity.CENTER
            setBackgroundColor(if (symbol.isNotEmpty()) getBackgroundColorLight() else getBackgroundColorDark())
            setPadding(dpToPx(4f).toInt(), dpToPx(6f).toInt(), dpToPx(4f).toInt(), dpToPx(6f).toInt())
            
            if (symbol.isNotEmpty()) {
                // Click to insert symbol
                setOnClickListener {
                    trackSymbolUsage(symbol)
                    onSymbolSelected(symbol)
                    dismiss()
                }
                
                // Long-press to add/remove from favorites
                setOnLongClickListener {
                    if (currentTab == Tab.FAVORITES) {
                        // Remove from favorites if in favorites tab
                        removeFromFavorites(symbol)
                        updateKeyboardLayout()
                        android.widget.Toast.makeText(context, "Removed from favorites", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        // Add to favorites if not in favorites tab
                        val added = addToFavorites(symbol)
                        if (added) {
                            // Refresh UI to show star indicator
                            updateKeyboardLayout()
                            android.widget.Toast.makeText(context, "Added to favorites", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            if (isFavorite(symbol)) {
                                android.widget.Toast.makeText(context, "Already in favorites", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(context, "Favorites full (max $MAX_FAVORITES)", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    true
                }
            }
        }
        
        // Symbol/Emoji display (larger, top)
        val symbolText = TextView(context).apply {
            text = symbol.ifEmpty { "" }
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(getTextColor())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        
        // Key label (smaller, bottom) - show star if in favorites
        val keyText = TextView(context).apply {
            text = if (symbol.isNotEmpty() && isFavorite(symbol)) "★ $keyLabel" else keyLabel
            textSize = 9f
            gravity = Gravity.CENTER
            setTextColor(if (symbol.isNotEmpty() && isFavorite(symbol)) getAccentColor() else getTextColorSecondary())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        buttonContainer.addView(symbolText)
        buttonContainer.addView(keyText)
        
        return buttonContainer
    }
    
    private fun switchTab(tab: Tab) {
        if (currentTab == tab) return
        
        currentTab = tab
        currentPage = 0  // Reset to first page when switching tabs
        
        // Update tab button styles immediately for instant feedback
        when (tab) {
            Tab.FAVORITES -> {
                favoritesButton?.setTextColor(getTextColor())
                favoritesButton?.setBackgroundColor(getAccentColor())
                symbolsButton?.setTextColor(getTextColorSecondary())
                symbolsButton?.setBackgroundColor(getBackgroundColorLight())
                emojisButton?.setTextColor(getTextColorSecondary())
                emojisButton?.setBackgroundColor(getBackgroundColorLight())
            }
            Tab.SYMBOLS -> {
                favoritesButton?.setTextColor(getTextColorSecondary())
                favoritesButton?.setBackgroundColor(getBackgroundColorLight())
                symbolsButton?.setTextColor(getTextColor())
                symbolsButton?.setBackgroundColor(getAccentColor())
                emojisButton?.setTextColor(getTextColorSecondary())
                emojisButton?.setBackgroundColor(getBackgroundColorLight())
            }
            Tab.EMOJIS -> {
                favoritesButton?.setTextColor(getTextColorSecondary())
                favoritesButton?.setBackgroundColor(getBackgroundColorLight())
                emojisButton?.setTextColor(getTextColor())
                emojisButton?.setBackgroundColor(getAccentColor())
                symbolsButton?.setTextColor(getTextColorSecondary())
                symbolsButton?.setBackgroundColor(getBackgroundColorLight())
            }
        }
        
        // Update category buttons (hide for favorites)
        if (tab == Tab.FAVORITES) {
            categoryButtonsContainer?.visibility = View.GONE
        } else {
            categoryButtonsContainer?.visibility = View.VISIBLE
            refreshCategoryButtons()
        }
        
        // Update keyboard layout
        updateKeyboardLayout()
    }
    
    private fun refreshCategoryButtons() {
        val scrollView = categoryButtonsContainer
        val buttonContainer = scrollView?.getChildAt(0) as? LinearLayout
        
        buttonContainer?.removeAllViews()
        
        // Add category buttons based on current tab (not for favorites)
        if (currentTab == Tab.FAVORITES) {
            return
        }
        
        val categories = if (currentTab == Tab.SYMBOLS) {
            SYMBOL_CATEGORIES.keys
        } else {
            getEmojiCategories().keys
        }
        
        categories.forEach { categoryName ->
            val isActive = if (currentTab == Tab.SYMBOLS) {
                categoryName == currentSymbolCategory
            } else {
                categoryName == currentEmojiCategory
            }
            
            val button = createCategoryButton(categoryName, isActive)
            button.setOnClickListener {
                switchCategory(categoryName)
            }
            buttonContainer?.addView(button)
        }
        
        // Scroll to active category after buttons are laid out
        buttonContainer?.post {
            scrollToActiveCategory()
        }
    }
    
    private fun switchCategory(categoryName: String) {
        // Update current category
        if (currentTab == Tab.SYMBOLS) {
            currentSymbolCategory = categoryName
        } else {
            currentEmojiCategory = categoryName
        }
        
        currentPage = 0  // Reset to first page when switching categories
        
        // Update category button styles
        refreshCategoryButtons()
        
        // Scroll to make the active category button visible
        scrollToActiveCategory()
        
        // Update keyboard layout
        updateKeyboardLayout()
    }
    
    private fun scrollToActiveCategory() {
        val scrollView = categoryButtonsContainer as? HorizontalScrollView ?: return
        val buttonContainer = scrollView.getChildAt(0) as? LinearLayout ?: return
        
        val categories = if (currentTab == Tab.SYMBOLS) {
            SYMBOL_CATEGORIES.keys.toList()
        } else {
            getEmojiCategories().keys.toList()
        }
        
        val currentCategory = if (currentTab == Tab.SYMBOLS) currentSymbolCategory else currentEmojiCategory
        val categoryIndex = categories.indexOf(currentCategory)
        
        if (categoryIndex >= 0 && categoryIndex < buttonContainer.childCount) {
            val activeButton = buttonContainer.getChildAt(categoryIndex)
            
            // Calculate scroll position to center the button
            val scrollViewWidth = scrollView.width
            val buttonLeft = activeButton.left
            val buttonWidth = activeButton.width
            val scrollX = buttonLeft - (scrollViewWidth / 2) + (buttonWidth / 2)
            
            // Smooth scroll to position
            scrollView.smoothScrollTo(scrollX.coerceAtLeast(0), 0)
        }
    }
    
    private fun filterContent() {
        if (searchQuery.isEmpty()) {
            // No search query - show normal content
            categoryButtonsContainer?.visibility = if (currentTab == Tab.FAVORITES) View.GONE else View.VISIBLE
            currentPage = 0
            updateKeyboardLayout()
            return
        }
        
        // Hide category buttons when searching
        categoryButtonsContainer?.visibility = View.GONE
        currentPage = 0
        
        // Update keyboard layout with filtered results
        updateKeyboardLayout()
    }
    
    private fun getFilteredItems(): List<String> {
        if (searchQuery.isEmpty()) {
            return getCurrentCategoryItems()
        }
        
        val query = searchQuery.lowercase()
        val allItems = mutableListOf<String>()
        
        // Search in current tab's content
        when (currentTab) {
            Tab.FAVORITES -> {
                // Search in favorites
                allItems.addAll(getFavorites().filter { it.contains(query, ignoreCase = true) })
            }
            Tab.SYMBOLS -> {
                // Search across all symbol categories
                SYMBOL_CATEGORIES.values.forEach { symbols ->
                    allItems.addAll(symbols.filter { it.contains(query, ignoreCase = true) })
                }
            }
            Tab.EMOJIS -> {
                // Search across all emoji categories
                getEmojiCategories().values.forEach { emojis ->
                    allItems.addAll(emojis.filter { it.contains(query, ignoreCase = true) })
                }
            }
        }
        
        return allItems.distinct()
    }
    
    
    private fun trackSymbolUsage(symbol: String) {
        // Use cached usage map if available
        val usageMap = usageMapCache ?: loadUsageMap()
        
        // Increment count for this symbol
        val oldCount = usageMap[symbol] ?: 0
        usageMap[symbol] = oldCount + 1
        
        // Also track last used timestamp
        val timestamp = System.currentTimeMillis()
        prefs.edit().putLong("timestamp_$symbol", timestamp).apply()
        
        // Update cache
        usageMapCache = usageMap
        cachedFrequentItems = null  // Invalidate frequent items cache
        
        // Save to preferences (synchronously to ensure it's available immediately)
        val updatedData = usageMap.entries.joinToString("|") { "${it.key}:${it.value}" }
        prefs.edit().putString("usage_counts", updatedData).commit()
    }
    
    private fun loadUsageMap(): MutableMap<String, Int> {
        val usageData = prefs.getString("usage_counts", "") ?: ""
        val usageMap = mutableMapOf<String, Int>()
        
        if (usageData.isNotEmpty()) {
            usageData.split("|").forEach { entry ->
                val parts = entry.split(":", limit = 2)
                if (parts.size == 2) {
                    usageMap[parts[0]] = parts[1].toIntOrNull() ?: 0
                }
            }
        }
        
        return usageMap
    }
    
    private fun getFrequentItems(): List<String> {
        // Return cached items if available
        cachedFrequentItems?.let { 
            return it 
        }
        
        // Load and cache usage map
        val usageMap = usageMapCache ?: loadUsageMap().also { usageMapCache = it }
        
        if (usageMap.isEmpty()) {
            cachedFrequentItems = emptyList()
            return emptyList()
        }
        
        // Calculate and cache top 15 items sorted by usage count (most used first)
        val items = usageMap.entries
            .map { entry ->
                val timestamp = prefs.getLong("timestamp_${entry.key}", 0L)
                Triple(entry.key, entry.value, timestamp)
            }
            .sortedWith(compareByDescending<Triple<String, Int, Long>> { it.second }.thenByDescending { it.third })
            .take(15)
            .map { it.first }
        
        cachedFrequentItems = items
        return items
    }
    
    private fun createFrequentItemsRow(): HorizontalScrollView {
        val frequentItems = getFrequentItems()
        
        val scrollView = HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, dpToPx(4f).toInt())
            }
            isHorizontalScrollBarEnabled = true
        }
        
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dpToPx(4f).toInt(), 0, dpToPx(4f).toInt())
        }
        
        frequentItemsContainer = container
        
        if (frequentItems.isEmpty()) {
            // Show "Recently Used" label when empty
            val emptyLabel = TextView(context).apply {
                text = "Recently Used: (empty)"
                textSize = 12f
                setTextColor(Color.parseColor("#888888"))
                gravity = Gravity.CENTER
                setPadding(
                    dpToPx(12f).toInt(),
                    dpToPx(8f).toInt(),
                    dpToPx(12f).toInt(),
                    dpToPx(8f).toInt()
                )
            }
            container.addView(emptyLabel)
        } else {
            // Add "Recently Used" label
            val label = TextView(context).apply {
                text = "Recent:"
                textSize = 12f
                setTextColor(Color.parseColor("#AAAAAA"))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(
                    dpToPx(8f).toInt(),
                    dpToPx(4f).toInt(),
                    dpToPx(8f).toInt(),
                    dpToPx(4f).toInt()
                )
            }
            container.addView(label)
            
            // Add frequent item buttons
            frequentItems.forEach { item ->
                val button = TextView(context).apply {
                    text = item
                    textSize = 18f
                    gravity = Gravity.CENTER
                    setTextColor(getTextColor())
                    layoutParams = LinearLayout.LayoutParams(
                        dpToPx(45f).toInt(),
                        dpToPx(45f).toInt()
                    ).apply {
                        setMargins(dpToPx(3f).toInt(), dpToPx(2f).toInt(), dpToPx(3f).toInt(), dpToPx(2f).toInt())
                    }
                    setBackgroundColor(getBackgroundColorLight())
                    
                    setOnClickListener {
                        trackSymbolUsage(item)
                        onSymbolSelected(item)
                        dismiss()
                    }
                }
                container.addView(button)
            }
        }
        
        scrollView.addView(container)
        return scrollView
    }
    
    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }
    
    /**
     * Get colors matching the app's UI theme
     * Based on StatusBarController colors: #1A1A1A (main bg), #2D2D2D (light bg)
     */
    private fun getBackgroundColor(): Int {
        // Use app's main background color
        return Color.parseColor("#1A1A1A")
    }
    
    private fun getBackgroundColorLight(): Int {
        // Use app's lighter background color for buttons/categories
        return Color.parseColor("#2D2D2D")
    }
    
    private fun getBackgroundColorDark(): Int {
        // Slightly darker than main background for empty keys
        return Color.parseColor("#0F0F0F")
    }
    
    private fun getTextColor(): Int {
        // White text for main content
        return Color.WHITE
    }
    
    private fun getTextColorSecondary(): Int {
        // Gray text for secondary content (key labels, page indicators)
        return Color.parseColor("#AAAAAA")
    }
    
    private fun getAccentColor(): Int {
        // Blue accent for active buttons and navigation
        return Color.parseColor("#0A7AFF")
    }
}
