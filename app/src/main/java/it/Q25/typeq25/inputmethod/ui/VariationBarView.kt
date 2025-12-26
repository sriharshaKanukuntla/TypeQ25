package it.srik.TypeQ25.inputmethod.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import it.srik.TypeQ25.DeviceManager
import it.srik.TypeQ25.R
import it.srik.TypeQ25.SettingsActivity
import it.srik.TypeQ25.inputmethod.StatusBarController
import it.srik.TypeQ25.inputmethod.TextSelectionHelper
import it.srik.TypeQ25.inputmethod.VariationButtonHandler
import it.srik.TypeQ25.inputmethod.SpeechRecognitionActivity
import kotlin.math.abs
import kotlin.math.max

/**
 * Handles the variations row (suggestions + microphone/settings) rendered above the LED strip.
 */
class VariationBarView(
    private val context: Context
) {
    companion object {
        private const val TAG = "VariationBarView"
    }

    // Reusable handler to avoid creating new instances
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Cache device check to avoid repeated calls
    private val isQ25Device = DeviceManager.getDevice(context) == "Q25"

    var onVariationSelectedListener: VariationButtonHandler.OnVariationSelectedListener? = null
    var onCursorMovedListener: (() -> Unit)? = null

    private var wrapper: FrameLayout? = null
    private var container: LinearLayout? = null
    private var overlay: View? = null
    private var currentScrollView: HorizontalScrollView? = null
    private var currentVariationsRow: LinearLayout? = null
    private var variationButtons: MutableList<TextView> = mutableListOf()
    private var menuButtonView: ImageView? = null
    private var quickActionsMenu: HorizontalScrollView? = null
    private var microphoneButtonView: ImageView? = null
    private var lastDisplayedVariations: List<String> = emptyList()
    private var isSymModeActive = false
    private var isSwipeInProgress = false
    private var swipeDirection: Int? = null
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var lastCursorMoveX = 0f
    private var currentInputConnection: android.view.inputmethod.InputConnection? = null

    fun ensureView(): FrameLayout {
        if (wrapper != null) {
            return wrapper!!
        }

        val leftPadding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            64f,
            context.resources.displayMetrics
        ).toInt()
        val rightPadding = (leftPadding * 0.31f).toInt()
        val variationsVerticalPadding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            context.resources.displayMetrics
        ).toInt()
        val variationsContainerHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            35f,
            context.resources.displayMetrics
        ).toInt()

        container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(0, variationsVerticalPadding, 0, variationsVerticalPadding)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                variationsContainerHeight
            )
            visibility = View.GONE
            
            // Add menu button on the left
            addView(createMenuButton())
        }

        wrapper = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                variationsContainerHeight
            )
            visibility = View.GONE
            addView(container)
        }

        overlay = View(context).apply {
            background = ColorDrawable(Color.TRANSPARENT)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }.also { overlayView ->
            wrapper?.addView(overlayView)
            installOverlayTouchListener(overlayView)
        }

        return wrapper!!
    }

    private fun createMenuButton(): ImageView {
        val buttonSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            32f,
            context.resources.displayMetrics
        ).toInt()
        
        val marginStart = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            10f,
            context.resources.displayMetrics
        ).toInt()
        
        val marginEnd = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            8f,
            context.resources.displayMetrics
        ).toInt()

        return ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize, 0f).apply {
                setMargins(marginStart, 0, marginEnd, 0)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }
            // Three equal horizontal bars (hamburger menu)
            setImageDrawable(createHamburgerIcon())
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = createRoundedBackground(Color.parseColor("#2D2D2D"))
            setOnClickListener {
                toggleQuickActionsMenu()
            }
        }.also {
            menuButtonView = it
        }
    }

    private fun toggleQuickActionsMenu() {
        val menu = quickActionsMenu
        if (menu != null && menu.visibility == View.VISIBLE) {
            hideQuickActionsMenu()
        } else {
            showQuickActionsMenu()
        }
    }

    private fun showQuickActionsMenu() {
        if (quickActionsMenu == null) {
            quickActionsMenu = createQuickActionsMenu()
        }
        
        val menu = quickActionsMenu ?: return
        val containerView = container ?: return
        
        // Remove from parent if already added
        (menu.parent as? ViewGroup)?.removeView(menu)
        
        // Add to container at position 1 (after menu button)
        if (containerView.childCount > 0) {
            containerView.addView(menu, 1)
        } else {
            containerView.addView(menu)
        }
        
        menu.visibility = View.VISIBLE
        menu.alpha = 0f
        menu.animate().alpha(1f).setDuration(200).start()
        
        // Request focus on first button for D-Pad navigation
        menu.postDelayed({
            (menu.getChildAt(0) as? LinearLayout)?.getChildAt(0)?.requestFocus()
        }, 250)
    }

    private fun hideQuickActionsMenu() {
        val menu = quickActionsMenu ?: return
        menu.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                menu.visibility = View.GONE
                (menu.parent as? ViewGroup)?.removeView(menu)
            }
            .start()
    }

    private fun createQuickActionsMenu(): HorizontalScrollView {
        val buttonSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            36f,
            context.resources.displayMetrics
        ).toInt()
        
        val marginBetween = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            context.resources.displayMetrics
        ).toInt()

        val buttonsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            
            // Copy button
            addView(createQuickActionButton("📋", "Copy") {
                hideQuickActionsMenu()
                currentInputConnection?.let { ic ->
                    val selectedText = ic.getSelectedText(0)
                    if (!selectedText.isNullOrEmpty()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("text", selectedText))
                    }
                }
            })
            
            // Paste button
            addView(createQuickActionButton("📄", "Paste") {
                hideQuickActionsMenu()
                currentInputConnection?.let { ic ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipData = clipboard.primaryClip
                    if (clipData != null && clipData.itemCount > 0) {
                        val text = clipData.getItemAt(0).text
                        if (text != null) {
                            ic.commitText(text, 1)
                        }
                    }
                }
            })
            
            // Cut button
            addView(createQuickActionButton("✂️", "Cut") {
                hideQuickActionsMenu()
                currentInputConnection?.let { ic ->
                    val selectedText = ic.getSelectedText(0)
                    if (!selectedText.isNullOrEmpty()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("text", selectedText))
                        ic.commitText("", 1)
                    }
                }
            })
            
            // Select All button
            addView(createQuickActionButton("📝", "Select All") {
                hideQuickActionsMenu()
                currentInputConnection?.let { ic ->
                    ic.performContextMenuAction(android.R.id.selectAll)
                }
            })
            
            // Undo button
            addView(createQuickActionButton("↶", "Undo") {
                hideQuickActionsMenu()
                currentInputConnection?.let { ic ->
                    ic.performContextMenuAction(android.R.id.undo)
                }
            })
            
            // Clear All button
            addView(createQuickActionButton("🗑️", "Clear") {
                hideQuickActionsMenu()
                currentInputConnection?.let { ic ->
                    ic.performContextMenuAction(android.R.id.selectAll)
                    ic.commitText("", 1)
                }
            })
            
            // Cursor to End button
            addView(createQuickActionButton("⇥", "End") {
                hideQuickActionsMenu()
                currentInputConnection?.let { ic ->
                    val extractedText = ic.getExtractedText(ExtractedTextRequest(), 0)
                    extractedText?.let {
                        val textLength = it.text?.length ?: 0
                        ic.setSelection(textLength, textLength)
                    }
                }
            })
            
            // Cursor to Start button
            addView(createQuickActionButton("⇤", "Start") {
                hideQuickActionsMenu()
                currentInputConnection?.let { ic ->
                    ic.setSelection(0, 0)
                }
            })
        }
        
        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
            addView(buttonsContainer)
        }
    }

    private fun createQuickActionButton(icon: String, label: String, onClick: () -> Unit): TextView {
        val buttonHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            32f,
            context.resources.displayMetrics
        ).toInt()
        
        val buttonWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            60f,
            context.resources.displayMetrics
        ).toInt()
        
        val marginEnd = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            context.resources.displayMetrics
        ).toInt()

        return TextView(context).apply {
            text = icon
            textSize = 16f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(buttonWidth, buttonHeight).apply {
                gravity = Gravity.CENTER_VERTICAL
                setMargins(0, 0, marginEnd, 0)
            }
            
            // Enable focus for D-Pad navigation
            isFocusable = true
            isFocusableInTouchMode = false
            
            val normalBackground = createRoundedBackground(Color.parseColor("#3F3F3F"))
            val focusedBackground = createRoundedBackground(Color.parseColor("#5F5F5F"))
            
            background = normalBackground
            setTextColor(Color.parseColor("#E0E0E0"))
            
            setOnClickListener { onClick() }
            
            // Handle D-pad key events
            setOnKeyListener { _, keyCode, event ->
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    when (keyCode) {
                        android.view.KeyEvent.KEYCODE_ENTER,
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER -> {
                            // Activate button on Enter/Center
                            performClick()
                            true
                        }
                        android.view.KeyEvent.KEYCODE_BACK,
                        android.view.KeyEvent.KEYCODE_ESCAPE -> {
                            // Close menu
                            hideQuickActionsMenu()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            
            // Change background on focus for D-Pad navigation
            setOnFocusChangeListener { _, hasFocus ->
                background = if (hasFocus) focusedBackground else normalBackground
            }
        }
    }

    private fun createRoundedBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                4f,
                context.resources.displayMetrics
            )
        }
    }

    private fun createHamburgerIcon(): android.graphics.drawable.Drawable {
        return object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint().apply {
                color = Color.WHITE
                strokeWidth = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    2f,
                    context.resources.displayMetrics
                )
                strokeCap = android.graphics.Paint.Cap.ROUND
                isAntiAlias = true
            }

            override fun draw(canvas: android.graphics.Canvas) {
                val width = bounds.width().toFloat()
                val height = bounds.height().toFloat()
                val barHeight = height / 8f
                val spacing = height / 4f
                val startX = width * 0.25f
                val endX = width * 0.75f
                
                // Top bar
                val y1 = height * 0.25f
                canvas.drawLine(startX, y1, endX, y1, paint)
                
                // Middle bar
                val y2 = height * 0.5f
                canvas.drawLine(startX, y2, endX, y2, paint)
                
                // Bottom bar
                val y3 = height * 0.75f
                canvas.drawLine(startX, y3, endX, y3, paint)
            }

            override fun setAlpha(alpha: Int) {
                paint.alpha = alpha
            }

            override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
                paint.colorFilter = colorFilter
            }

            override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
        }
    }

    fun getWrapper(): FrameLayout? = wrapper

    fun setSymModeActive(active: Boolean) {
        isSymModeActive = active
        if (active) {
            overlay?.visibility = View.GONE
        }
    }

    fun updateInputConnection(inputConnection: android.view.inputmethod.InputConnection?) {
        currentInputConnection = inputConnection
    }

    fun resetVariationsState() {
        lastDisplayedVariations = emptyList()
    }

    fun hideImmediate() {
        currentVariationsRow?.let { row ->
            (row.parent as? ViewGroup)?.removeView(row)
        }
        currentVariationsRow = null
        variationButtons.clear()
        hideQuickActionsMenu()
        removeMicrophoneImmediate()
        container?.visibility = View.GONE
        wrapper?.visibility = View.GONE
        overlay?.visibility = View.GONE
    }

    fun hideForSym(onHidden: () -> Unit) {
        val containerView = container ?: run {
            onHidden()
            return
        }
        val row = currentVariationsRow
        val overlayView = overlay

        hideQuickActionsMenu()
        removeMicrophoneImmediate()

        if (row != null && row.parent == containerView && row.visibility == View.VISIBLE) {
            animateVariationsOut(row) {
                (row.parent as? ViewGroup)?.removeView(row)
                if (currentVariationsRow == row) {
                    currentVariationsRow = null
                }
                containerView.visibility = View.GONE
                wrapper?.visibility = View.GONE
                overlayView?.visibility = View.GONE
                onHidden()
            }
        } else {
            currentVariationsRow = null
            containerView.visibility = View.GONE
            wrapper?.visibility = View.GONE
            overlayView?.visibility = View.GONE
            onHidden()
        }
    }

    fun showVariations(snapshot: StatusBarController.StatusSnapshot, inputConnection: android.view.inputmethod.InputConnection?) {
        // Hide quick actions menu when user types (returns to variations view)
        hideQuickActionsMenu()
        
        val containerView = container ?: run {
            Log.e("VariationBarView", "showVariations: container is null!")
            return
        }
        val wrapperView = wrapper ?: run {
            Log.e("VariationBarView", "showVariations: wrapper is null!")
            return
        }
        val overlayView = overlay ?: run {
            Log.e("VariationBarView", "showVariations: overlay is null!")
            return
        }

        currentInputConnection = inputConnection

        Log.d("VariationBarView", "showVariations: CALLED - setting views visible")
        Log.d("VariationBarView", "showVariations: wrapper layoutParams = ${wrapperView.layoutParams}")
        Log.d("VariationBarView", "showVariations: container layoutParams = ${containerView.layoutParams}")
        Log.d("VariationBarView", "showVariations: wrapper parent = ${wrapperView.parent}")
        
        containerView.visibility = View.VISIBLE
        wrapperView.visibility = View.VISIBLE
        overlayView.visibility = if (isSymModeActive) View.GONE else View.VISIBLE

        // Prioritize suggestions over variations - allow showing more items now with scrolling
        // Allow variations without lastInsertedChar (for default special chars)
        val hasVariations = snapshot.variations.isNotEmpty()
        val hasSuggestions = snapshot.suggestions.isNotEmpty()
        
        val limitedVariations = if (hasSuggestions && hasVariations) {
            // Show more suggestions (up to 10), then variations
            val suggestions = snapshot.suggestions.take(10)
            val variations = snapshot.variations.take(8)
            Log.d("VariationBarView", "showVariations: mixing ${suggestions.size} suggestions + ${variations.size} variations")
            suggestions + variations
        } else if (hasSuggestions) {
            // Show up to 15 suggestions when no variations
            Log.d("VariationBarView", "showVariations: showing ${snapshot.suggestions.size} suggestions: ${snapshot.suggestions}")
            snapshot.suggestions.take(15)
        } else if (hasVariations) {
            // Show variations (could be character variations or default special chars)
            snapshot.variations.take(20)
        } else {
            Log.d("VariationBarView", "showVariations: NO variations or suggestions - will show empty bar with mic/settings")
            emptyList()
        }

        Log.d("VariationBarView", "showVariations: hasVariations=$hasVariations, hasSuggestions=$hasSuggestions, limitedVariations=${limitedVariations.size}, lastDisplayed=${lastDisplayedVariations.size}")

        val variationsChanged = limitedVariations != lastDisplayedVariations
        val hasExistingRow = currentVariationsRow != null &&
            currentVariationsRow?.parent != null &&
            currentVariationsRow?.visibility == View.VISIBLE

        if (!variationsChanged && hasExistingRow) {
            Log.d("VariationBarView", "showVariations: no change, ensuring visibility")
            // Still ensure visibility in case it was hidden
            containerView.visibility = View.VISIBLE
            wrapperView.visibility = View.VISIBLE
            currentScrollView?.visibility = View.VISIBLE
            currentVariationsRow?.visibility = View.VISIBLE
            return
        }

        variationButtons.clear()
        currentScrollView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }
        currentVariationsRow?.let {
            (it.parent as? ViewGroup)?.removeView(it)
        }
        currentScrollView = null
        currentVariationsRow = null

        val screenWidth = context.resources.displayMetrics.widthPixels
        val leftPadding = containerView.paddingLeft
        val rightPadding = containerView.paddingRight
        val availableWidth = screenWidth - leftPadding - rightPadding

        val spacingBetweenButtons = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            2f, // Reduced from 3 to 2
            context.resources.displayMetrics
        ).toInt()
        
        // Calculate button width to show 6 variation buttons + 1 mic button
        // Make variation buttons smaller to fit all 6 on screen
        val micButtonWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            35f, // Reduced from 40 to 35
            context.resources.displayMetrics
        ).toInt()
        val micRightMargin = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            8f, // Reduced from 10 to 8
            context.resources.displayMetrics
        ).toInt()
        
        // Available width for variation buttons (6 buttons)
        // Use 7 button slots to make each button smaller
        val micSpaceNeeded = micButtonWidth + micRightMargin + spacingBetweenButtons
        val variationButtonCount = 7 // Calculate as if 7 buttons to make them smaller
        val totalSpacing = spacingBetweenButtons * (variationButtonCount + 1)
        val calculatedWidth = (availableWidth - micSpaceNeeded - totalSpacing) / variationButtonCount
        // Reduce width by 25% to make characters smaller
        val buttonWidth = max(1, (calculatedWidth * 0.75).toInt())

        // Create HorizontalScrollView for sliding
        val scrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isSmoothScrollingEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            // Enable edge effects for visual feedback
            isHorizontalFadingEdgeEnabled = true
            setFadingEdgeLength(buttonWidth / 2)
            visibility = View.VISIBLE
        }
        currentScrollView = scrollView
        
        Log.d("VariationBarView", "showVariations: created scrollView")

        val variationsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(spacingBetweenButtons, 0, spacingBetweenButtons, 0)
            visibility = View.VISIBLE
        }
        currentVariationsRow = variationsRow
        
        Log.d("VariationBarView", "showVariations: created variationsRow")

        scrollView.addView(variationsRow, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val scrollLayoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        )
        // Add after menu button (menu button is at index 0, quick actions may be at index 1 if open)
        val insertPosition = if (quickActionsMenu?.parent == containerView) 2 else 1
        containerView.addView(scrollView, insertPosition, scrollLayoutParams)
        
        Log.d("VariationBarView", "showVariations: added scrollView to container, buttonWidth=$buttonWidth, limitedVariations.size=${limitedVariations.size}")
        Log.d("VariationBarView", "showVariations: container.childCount=${containerView.childCount}, wrapper.height=${wrapperView.height}, container.height=${containerView.height}")
        Log.d("VariationBarView", "showVariations: scrollView layoutParams = ${scrollView.layoutParams}, scrollView.height=${scrollView.height}")

        lastDisplayedVariations = limitedVariations

        // Create buttons - determine which are suggestions vs variations
        val suggestionCount = if (hasSuggestions) minOf(snapshot.suggestions.size, limitedVariations.size) else 0
        
        // For character variations (when lastInsertedChar exists), we delete before inserting
        // For default special chars (when lastInsertedChar is null), we just insert
        val shouldDeleteBeforeInsert = snapshot.lastInsertedChar != null
        
        for ((index, item) in limitedVariations.withIndex()) {
            val button = if (index < suggestionCount) {
                // This is a suggestion
                createSuggestionButton(item, inputConnection, buttonWidth)
            } else {
                // This is a variation
                createVariationButton(item, inputConnection, buttonWidth, shouldDeleteBeforeInsert)
            }
            
            // Suggestions already have WRAP_CONTENT width from createSuggestionButton
            // Variations need fixed width layout params
            if (index >= suggestionCount) {
                val buttonParams = LinearLayout.LayoutParams(buttonWidth, buttonWidth)
                variationButtons.add(button)
                variationsRow.addView(button, buttonParams)
                
                // Add separator after variation buttons (except the last one)
                if (index < limitedVariations.size - 1) {
                    variationsRow.addView(createVerticalSeparator())
                }
            } else {
                // For suggestions, don't override the WRAP_CONTENT layout params
                variationButtons.add(button)
                variationsRow.addView(button)
                // Add separator after suggestions (except the last one)
                if (index < limitedVariations.size - 1) {
                    variationsRow.addView(createVerticalSeparator())
                }
            }
        }

        // Add microphone button AFTER the scrollable area (fixed position)
        val microphoneButton = microphoneButtonView ?: createMicrophoneButton(buttonWidth)
        microphoneButtonView = microphoneButton
        (microphoneButton.parent as? ViewGroup)?.removeView(microphoneButton)
        val micParams = LinearLayout.LayoutParams(buttonWidth, buttonWidth).apply {
            gravity = Gravity.CENTER_VERTICAL
            setMargins(0, 0, micRightMargin, 0)
        }
        containerView.addView(microphoneButton, micParams)
        microphoneButton.setOnClickListener {
            startSpeechRecognition(inputConnection)
        }
        microphoneButton.alpha = 1f
        microphoneButton.visibility = View.VISIBLE

        if (variationsChanged) {
            animateVariationsIn(variationsRow)
        } else {
            variationsRow.alpha = 1f
            variationsRow.visibility = View.VISIBLE
        }
        
        Log.d("VariationBarView", "showVariations: DONE - wrapper.visibility=${wrapperView.visibility}, container.visibility=${containerView.visibility}, scrollView.visibility=${scrollView.visibility}")
        Log.d("VariationBarView", "showVariations: wrapper in parent? ${wrapperView.parent != null}, parent type: ${wrapperView.parent?.javaClass?.simpleName}")
    }

    private fun installOverlayTouchListener(overlayView: View) {
        // Disable swipe pad navigation for Q25 device
        if (isQ25Device) {
            overlayView.setOnTouchListener { _, _ -> false }
            return
        }
        
        val swipeThreshold = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            6f,
            context.resources.displayMetrics
        )
        val incrementalThreshold = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            9.6f,
            context.resources.displayMetrics
        )

        overlayView.setOnTouchListener { _, motionEvent ->
            if (isSymModeActive) {
                return@setOnTouchListener false
            }

            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    isSwipeInProgress = false
                    swipeDirection = null
                    touchStartX = motionEvent.x
                    touchStartY = motionEvent.y
                    lastCursorMoveX = motionEvent.x
                    Log.d(TAG, "Touch down on overlay at ($touchStartX, $touchStartY)")
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = motionEvent.x - touchStartX
                    val deltaY = abs(motionEvent.y - touchStartY)
                    val incrementalDeltaX = motionEvent.x - lastCursorMoveX

                    if (isSwipeInProgress || (abs(deltaX) > swipeThreshold && abs(deltaX) > deltaY)) {
                        if (!isSwipeInProgress) {
                            isSwipeInProgress = true
                            swipeDirection = if (deltaX > 0) 1 else -1
                            Log.d(TAG, "Swipe started: ${if (swipeDirection == 1) "RIGHT" else "LEFT"}")
                        } else {
                            val currentDirection = if (incrementalDeltaX > 0) 1 else -1
                            if (currentDirection != swipeDirection && abs(incrementalDeltaX) > swipeThreshold) {
                                swipeDirection = currentDirection
                                Log.d(TAG, "Swipe direction changed: ${if (swipeDirection == 1) "RIGHT" else "LEFT"}")
                            }
                        }

                        if (isSwipeInProgress && swipeDirection != null) {
                            val inputConnection = currentInputConnection
                            if (inputConnection != null) {
                                val movementInDirection = if (swipeDirection == 1) incrementalDeltaX else -incrementalDeltaX
                                if (movementInDirection > incrementalThreshold) {
                                    val moved = if (swipeDirection == 1) {
                                        TextSelectionHelper.moveCursorRight(inputConnection)
                                    } else {
                                        TextSelectionHelper.moveCursorLeft(inputConnection)
                                    }

                                    if (moved) {
                                        lastCursorMoveX = motionEvent.x
                                        mainHandler.postDelayed({
                                            onCursorMovedListener?.invoke()
                                        }, 50)
                                    }
                                }
                            }
                        }
                        true
                    } else {
                        true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isSwipeInProgress) {
                        isSwipeInProgress = false
                        swipeDirection = null
                        Log.d(TAG, "Swipe ended on overlay")
                        true
                    } else {
                        val x = motionEvent.x
                        val y = motionEvent.y
                        val clickedView = container?.let { findClickableViewAt(it, x, y) }
                        if (clickedView != null) {
                            clickedView.performClick()
                        }
                        true
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    isSwipeInProgress = false
                    swipeDirection = null
                    true
                }
                else -> true
            }
        }
    }

    private fun startSpeechRecognition(inputConnection: android.view.inputmethod.InputConnection?) {
        try {
            val intent = Intent(context, SpeechRecognitionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_HISTORY or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }
            context.startActivity(intent)
            Log.d(TAG, "Speech recognition started")
        } catch (e: Exception) {
            Log.e(TAG, "Unable to launch speech recognition", e)
        }
    }



    private fun removeMicrophoneImmediate() {
        microphoneButtonView?.let { microphone ->
            (microphone.parent as? ViewGroup)?.removeView(microphone)
            microphone.visibility = View.GONE
            microphone.alpha = 1f
        }
    }



    private fun createVariationButton(
        variation: String,
        inputConnection: android.view.inputmethod.InputConnection?,
        buttonWidth: Int,
        shouldDeleteBeforeInsert: Boolean = true
    ): TextView {
        val dp4 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            context.resources.displayMetrics
        ).toInt()
        val dp6 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            6f,
            context.resources.displayMetrics
        ).toInt()

        val buttonHeight = buttonWidth

        val drawable = GradientDrawable().apply {
            setColor(Color.parseColor("#2D2D2D")) // BlackBerry dark gray
            cornerRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                4f,
                context.resources.displayMetrics
            )
        }
        val pressedDrawable = GradientDrawable().apply {
            setColor(Color.parseColor("#00A0DC")) // BlackBerry blue
            cornerRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                4f,
                context.resources.displayMetrics
            )
        }
        val stateListDrawable = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), drawable)
        }

        return TextView(context).apply {
            text = variation
            textSize = 17.6f
            setTextColor(Color.parseColor("#E0E0E0")) // BlackBerry light text
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp6, dp4, dp6, dp4)
            background = stateListDrawable
            layoutParams = LinearLayout.LayoutParams(buttonWidth, buttonHeight)
            isClickable = true
            isFocusable = true
            setOnClickListener(
                VariationButtonHandler.createVariationClickListener(
                    variation,
                    inputConnection,
                    onVariationSelectedListener,
                    shouldDeleteBeforeInsert
                )
            )
        }
    }
    
    private fun createSuggestionButton(
        suggestion: String,
        inputConnection: android.view.inputmethod.InputConnection?,
        buttonWidth: Int
    ): TextView {
        val dp4 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            context.resources.displayMetrics
        ).toInt()
        val dp6 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            6f,
            context.resources.displayMetrics
        ).toInt()

        val buttonHeight = buttonWidth

        // Use different color for suggestions (BlackBerry theme)
        val drawable = GradientDrawable().apply {
            setColor(Color.parseColor("#3F3F3F")) // BlackBerry medium gray
            cornerRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                4f,
                context.resources.displayMetrics
            )
        }
        val pressedDrawable = GradientDrawable().apply {
            setColor(Color.parseColor("#00A0DC")) // BlackBerry blue
            cornerRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                4f,
                context.resources.displayMetrics
            )
        }
        val stateListDrawable = android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), drawable)
        }

        return TextView(context).apply {
            text = suggestion
            textSize = 16f
            setTextColor(Color.parseColor("#BDBDBD")) // BlackBerry silver
            setTypeface(null, android.graphics.Typeface.NORMAL)
            gravity = Gravity.CENTER
            setPadding(dp6, dp4, dp6, dp4)
            background = stateListDrawable
            // Use WRAP_CONTENT for width to fit text, fixed height
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                buttonHeight
            )
            // Allow single line display
            maxLines = 1
            isSingleLine = true
            isClickable = true
            isFocusable = true
            setOnClickListener {
                inputConnection?.let { ic ->
                    // Get the current partial word length to delete
                    val textBefore = ic.getTextBeforeCursor(100, 0) ?: ""
                    var wordStart = textBefore.length - 1
                    while (wordStart >= 0) {
                        val char = textBefore[wordStart]
                        if (char.isWhitespace() || char in ".,;:!?\"'()[]{}/<>@#\$%^&*+=|\\") {
                            break
                        }
                        wordStart--
                    }
                    val partialWordLength = textBefore.length - wordStart - 1
                    
                    // Delete partial word and insert suggestion
                    if (partialWordLength > 0) {
                        ic.deleteSurroundingText(partialWordLength, 0)
                    }
                    ic.commitText(suggestion, 1)
                    
                    // Notify service to record this word
                    (context as? it.srik.TypeQ25.inputmethod.PhysicalKeyboardInputMethodService)?.let { service ->
                        service.suggestionEngine.recordWord(suggestion)
                    }
                    
                    onVariationSelectedListener?.onVariationSelected(suggestion)
                }
            }
        }
    }

    private fun createPlaceholderButton(buttonWidth: Int): View {
        val dp3 = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            3f,
            context.resources.displayMetrics
        ).toInt()
        val drawable = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = 0f
        }
        return View(context).apply {
            background = drawable
            layoutParams = LinearLayout.LayoutParams(buttonWidth, buttonWidth).apply {
                marginEnd = dp3
            }
            isClickable = false
            isFocusable = false
        }
    }

    private fun createMicrophoneButton(buttonSize: Int): ImageView {
        val drawable = GradientDrawable().apply {
            setColor(Color.parseColor("#2D2D2D")) // BlackBerry dark gray
            cornerRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                4f,
                context.resources.displayMetrics
            )
        }
        val padding = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            6f,
            context.resources.displayMetrics
        ).toInt()
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_baseline_mic_24)
            setColorFilter(Color.parseColor("#BDBDBD")) // BlackBerry silver
            background = drawable
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(padding, padding, padding, padding)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(buttonSize, buttonSize).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
        }
    }
    


    private fun createVerticalSeparator(): View {
        val separatorHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            24f,
            context.resources.displayMetrics
        ).toInt()
        
        val separatorWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            1f,
            context.resources.displayMetrics
        ).toInt()
        
        val marginHorizontal = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            4f,
            context.resources.displayMetrics
        ).toInt()
        
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(separatorWidth, separatorHeight).apply {
                gravity = Gravity.CENTER_VERTICAL
                setMargins(marginHorizontal, 0, marginHorizontal, 0)
            }
            setBackgroundColor(Color.parseColor("#3F3F3F")) // Medium gray
        }
    }

    private fun createStatusBarSettingsButton(buttonSize: Int): TextView {
        val actualHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            27f,
            context.resources.displayMetrics
        ).toInt()
        return TextView(context).apply {
            text = "⚙️"
            textSize = 18f
            gravity = Gravity.CENTER
            background = createRoundedBackground(Color.parseColor("#2D2D2D")) // Dark gray background
            setTextColor(Color.parseColor("#FFFFFF")) // White for emoji
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(buttonSize, actualHeight).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            // Add elevation for better visibility
            elevation = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                2f,
                context.resources.displayMetrics
            )
        }
    }

    private fun animateVariationsIn(view: View) {
        view.alpha = 0f
        view.visibility = View.VISIBLE
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 75
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                view.alpha = progress
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.alpha = 1f
                }
            })
        }.start()
    }

    private fun animateVariationsOut(view: View, onAnimationEnd: (() -> Unit)? = null) {
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 50
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                view.alpha = progress
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = View.GONE
                    view.alpha = 1f
                    onAnimationEnd?.invoke()
                }
            })
        }.start()
    }

    private fun findClickableViewAt(parent: View, x: Float, y: Float): View? {
        if (parent !is ViewGroup) {
            return if (x >= 0 && x < parent.width &&
                y >= 0 && y < parent.height &&
                parent.isClickable) {
                parent
            } else {
                null
            }
        }

        for (i in parent.childCount - 1 downTo 0) {
            val child = parent.getChildAt(i)
            if (child.visibility == View.VISIBLE) {
                val childLeft = child.left.toFloat()
                val childTop = child.top.toFloat()
                val childRight = child.right.toFloat()
                val childBottom = child.bottom.toFloat()

                if (x >= childLeft && x < childRight &&
                    y >= childTop && y < childBottom) {
                    val childX = x - childLeft
                    val childY = y - childTop
                    val result = findClickableViewAt(child, childX, childY)
                    if (result != null) {
                        return result
                    }
                }
            }
        }

        return if (parent.isClickable) parent else null
    }
}


