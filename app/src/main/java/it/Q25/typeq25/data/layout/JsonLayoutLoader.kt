package it.srik.TypeQ25.data.layout

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import android.view.KeyEvent
import it.srik.TypeQ25.DeviceManager
import it.srik.TypeQ25.SettingsManager

/**
 * Loads layout mappings from either bundled JSON assets or user-provided files.
 */
object JsonLayoutLoader {
    private const val TAG = "JsonLayoutLoader"
    
    // Map Western numerals (0-9) to Arabic-Indic numerals (٠-٩)
    private val westernToArabicNumerals = mapOf(
        '0' to '٠', '1' to '١', '2' to '٢', '3' to '٣', '4' to '٤',
        '5' to '٥', '6' to '٦', '7' to '٧', '8' to '٨', '9' to '٩'
    )
    
    // Number key codes
    private val numberKeyCodes = listOf(
        KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2,
        KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5,
        KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8,
        KeyEvent.KEYCODE_9
    )

    fun loadLayout(
        assets: AssetManager,
        layoutName: String,
        context: Context? = null
    ): Map<Int, LayoutMapping>? {
        if (context != null) {
            val customLayout = LayoutFileStore.loadLayoutFromFile(
                LayoutFileStore.getLayoutFile(context, layoutName)
            )
            if (customLayout != null) {
                Log.d(TAG, "Loaded custom layout: $layoutName with ${customLayout.size} mappings")
                return customLayout
            }
        }

        // Device-specific layout selection: Use arabic_Q25.json for Q25 device, arabic.json for others
        val actualLayoutName = if (context != null && layoutName == "arabic") {
            val device = DeviceManager.getDevice(context)
            if (device == "Q25") "arabic_Q25" else layoutName
        } else {
            layoutName
        }

        val layout = loadLayoutFromAssets(assets, actualLayoutName)
        
        // Apply Arabic numeral conversion if enabled and layout is Arabic
        return if (context != null && 
                   (layoutName == "arabic" || actualLayoutName.startsWith("arabic")) &&
                   SettingsManager.getUseArabicNumerals(context) &&
                   layout != null) {
            convertNumeralsToArabic(layout)
        } else {
            layout
        }
    }
    
    /**
     * Converts Western numerals (0-9) to Arabic-Indic numerals (٠-٩) in the layout.
     */
    private fun convertNumeralsToArabic(layout: Map<Int, LayoutMapping>): Map<Int, LayoutMapping> {
        return layout.mapValues { (keyCode, mapping) ->
            if (keyCode in numberKeyCodes) {
                mapping.copy(
                    lowercase = convertString(mapping.lowercase),
                    uppercase = convertString(mapping.uppercase),
                    taps = mapping.taps.map { tap ->
                        tap.copy(
                            lowercase = convertString(tap.lowercase),
                            uppercase = convertString(tap.uppercase)
                        )
                    }
                )
            } else {
                mapping
            }
        }
    }
    
    /**
     * Converts Western digits in a string to Arabic-Indic numerals.
     */
    private fun convertString(text: String): String {
        return text.map { char -> westernToArabicNumerals[char] ?: char }.joinToString("")
    }

    private fun loadLayoutFromAssets(
        assets: AssetManager,
        layoutName: String
    ): Map<Int, LayoutMapping>? {
        return try {
            val filePath = "common/layouts/$layoutName.json"
            assets.open(filePath).use { inputStream ->
                LayoutFileStore.loadLayoutFromStream(inputStream)
            }?.also {
                Log.d(TAG, "Loaded layout from assets: $layoutName with ${it.size} mappings")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading layout from assets: $layoutName", e)
            null
        }
    }

    fun getAvailableLayouts(
        assets: AssetManager,
        context: Context? = null
    ): List<String> {
        val layouts = mutableSetOf<String>()
        context?.let { layouts.addAll(LayoutFileStore.getCustomLayoutNames(it)) }

        return try {
            val layoutFiles = assets.list("common/layouts")
            layoutFiles?.forEach { fileName ->
                if (fileName.endsWith(".json")) {
                    layouts.add(fileName.removeSuffix(".json"))
                }
            }
            layouts.sorted()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available layouts from assets", e)
            layouts.sorted()
        }
    }
}

