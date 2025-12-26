package it.srik.TypeQ25

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.srik.TypeQ25.R
import it.srik.TypeQ25.data.variation.VariationRepository
import it.srik.TypeQ25.data.DefaultSpecialChars
import org.json.JSONObject
import java.io.File

/**
 * Screen for customizing letter variations.
 */
@Composable
fun VariationCustomizationScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    // Load AllVariations.json (static map with all possibilities)
    val allVariations = remember {
        loadAllVariationsFromJson(context)
    }
    
    // Load active variations
    var variations by remember {
        val allVariations = loadAllCustomVariations(context)
        mutableStateOf(allVariations)
    }
    
    // Generate alphabet list with uppercase followed by lowercase for each letter (A, a, B, b, ...)
    val alphabet = remember {
        ('A'..'Z').flatMap { listOf(it, it.lowercaseChar()) }
    }
    
    // State for picker dialog
    var showPickerDialog by remember { mutableStateOf(false) }
    var selectedLetter by remember { mutableStateOf<String?>(null) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    
    // State for reset confirmation dialog
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showImportSuccessDialog by remember { mutableStateOf(false) }
    var showImportErrorDialog by remember { mutableStateOf(false) }
    var importErrorMessage by remember { mutableStateOf("") }
    
    // File picker for importing variations
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val jsonString = inputStream?.bufferedReader()?.use { it.readText() }
                    
                    if (jsonString != null) {
                        val result = importVariationsFromJson(context, jsonString)
                        if (result.first) {
                            // Success - reload variations and DefaultSpecialChars
                            variations = loadAllCustomVariations(context)
                            DefaultSpecialChars.reload(context.assets)
                            showImportSuccessDialog = true
                        } else {
                            // Error
                            importErrorMessage = result.second
                            showImportErrorDialog = true
                        }
                    }
                } catch (e: Exception) {
                    importErrorMessage = "Failed to read file: ${e.message}"
                    showImportErrorDialog = true
                }
            }
        }
    }
    
    // File picker for exporting variations
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    exportVariationsToUri(context, uri, variations)
                    Toast.makeText(context, "Variations exported successfully", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.settings_back_content_description)
                            )
                        }
                        Text(
                            text = "Customize Variations",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Import button
                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "application/json"
                                }
                                importLauncher.launch(intent)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FileUpload,
                                contentDescription = "Import variations",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        // Export button
                        if (hasCustomVariationsFile(context)) {
                            IconButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                        addCategory(Intent.CATEGORY_OPENABLE)
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_TITLE, "variations.json")
                                    }
                                    exportLauncher.launch(intent)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FileDownload,
                                    contentDescription = "Export variations",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        // Reset to default button
                        if (hasCustomVariationsFile(context)) {
                            IconButton(
                                onClick = { showResetConfirmDialog = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Reset to default",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Info text
            Text(
                text = "Tap on any box to select a variation character. Up to 8 variations per letter. Default special characters (6 slots) appear in the suggestions bar when no variations are shown.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            HorizontalDivider()
            
            // Default Special Characters Section
            Text(
                text = "Default Special Characters (6 slots)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            
            val defaultSpecialChars = variations["default"] ?: emptyList()
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "★",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        repeat(8) { index ->
                            val character = defaultSpecialChars.getOrNull(index) ?: ""
                            val isEmpty = index >= defaultSpecialChars.size
                            
                            VariationBox(
                                character = character,
                                isEmpty = isEmpty,
                                onClick = {
                                    selectedLetter = "default"
                                    selectedIndex = index
                                    showPickerDialog = true
                                }
                            )
                        }
                    }
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            
            // Alphabet Variations Section
            Text(
                text = "Character Variations",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            
            // Alphabet grid
            alphabet.forEach { letter ->
                key(letter) {
                    val letterStr = letter.toString()
                    val letterVariations = variations[letterStr] ?: emptyList()
                    
                    VariationRow(
                        letter = letterStr,
                        variations = letterVariations,
                        onBoxClick = { index ->
                            selectedLetter = letterStr
                            selectedIndex = index
                            showPickerDialog = true
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    
    // Variation picker dialog
    if (showPickerDialog && selectedLetter != null) {
        val letterKey = selectedLetter!!
        
        // For default special characters, use a predefined list
        val availableVariations = if (letterKey == "default") {
            listOf(
                // Common symbols
                "%", "&", "@", "#", "$", "^", "*", "=", "+", "-",
                "_", "~", "`", "|", "\\", "/", "<", ">", "¦", "‖",
                
                // Currency symbols
                "€", "£", "¥", "¢", "₹", "₽", "₩", "₪", "₫", "₱",
                
                // Punctuation & quotes
                "…", "–", "—", "¡", "¿", "¶", "§", "†", "‡",
                "«", "»", "‹", "›", """, """, "'", "'", "„", "‚",
                "′", "″", "‴",
                
                // Copyright & trademarks
                "©", "®", "™", "℗", "℠",
                
                // Bullets & separators
                "•", "·", "◦", "‣", "⁃", "∙",
                
                // Superscript & subscript numbers (not duplicates - different styles)
                "⁰", "¹", "²", "³", "⁴", "⁵", "⁶", "⁷", "⁸", "⁹",
                "₀", "₁", "₂", "₃", "₄", "₅", "₆", "₇", "₈", "₉",
                
                // Fractions
                "½", "⅓", "⅔", "¼", "¾", "⅕", "⅖", "⅗", "⅘", "⅙", "⅚", "⅛", "⅜", "⅝", "⅞",
                
                // Math symbols
                "±", "×", "÷", "≠", "≈", "≤", "≥", "∞",
                "∑", "∏", "∫", "√", "∛", "∜", "∂", "∆", "∇",
                "∈", "∉", "∩", "∪", "⊂", "⊃", "⊆", "⊇",
                "∧", "∨", "¬", "∀", "∃", "∄",
                
                // Temperature & measurements
                "°", "℃", "℉", "Ω", "℮", "№", "℞", "㎡", "㎥", "㎝", "㎞", "㎏", "㎎",
                
                // Arrows
                "←", "→", "↑", "↓", "↔", "↕", "⇐", "⇒", "⇔", 
                "↩", "↪", "↰", "↱", "↲", "↳", "↴", "↵",
                "⤴", "⤵", "↺", "↻", "⟲", "⟳", "⇄", "⇆", "⇅",
                
                // Card suits & symbols
                "♠", "♣", "♥", "♦", "♤", "♧", "♡", "♢",
                
                // Stars & check marks
                "★", "☆", "✓", "✔", "✗", "✘", "✕", "☑", "☒",
                
                // Warning & signs
                "⚠", "⚡", "☠", "☢", "☣", "⛔", "🚫",
                
                // Misc symbols
                "☮", "☯", "☪", "☭", "♀", "♂", "⚥", "♻", "☘", "✝", "☪", "☸", "✡", "☦",
                
                // Emoji-style (optional, may not render on all devices)
                "⭐", "🌟", "💡", "🔥", "⚙"
            )
        } else {
            allVariations[letterKey]
                ?: allVariations[letterKey.uppercase()]
                ?: emptyList()
        }
        
        VariationPickerDialog(
            letter = letterKey,
            availableVariations = availableVariations,
            onVariationSelected = { character ->
                val updatedVariations = variations.toMutableMap()
                val currentVariations = updatedVariations[letterKey] ?: emptyList()
                
                // Apply different limits: 8 for default, 8 for regular characters
                val maxSlots = if (letterKey == "default") 8 else 8
                val trimmedVariations = updateVariationEntries(currentVariations, selectedIndex, character, maxSlots)
                
                if (trimmedVariations.isEmpty()) {
                    updatedVariations.remove(letterKey)
                } else {
                    updatedVariations[letterKey] = trimmedVariations
                }
                
                variations = updatedVariations
                
                // Save to file and reload DefaultSpecialChars
                saveVariationsToFile(context, variations)
                DefaultSpecialChars.reload(context.assets)
            },
            onDismiss = {
                showPickerDialog = false
                selectedLetter = null
                selectedIndex = null
            }
        )
    }
    
    // Reset confirmation dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = {
                Text("Reset to Default?")
            },
            text = {
                Text("This will reset all variations to their default values.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        resetVariationsToDefault(context)
                        variations = loadAllCustomVariations(context)
                        DefaultSpecialChars.reload(context.assets)
                        showResetConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetConfirmDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Import success dialog
    if (showImportSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showImportSuccessDialog = false },
            title = {
                Text("Import Successful")
            },
            text = {
                Text("Variations have been imported successfully.")
            },
            confirmButton = {
                TextButton(
                    onClick = { showImportSuccessDialog = false }
                ) {
                    Text("OK")
                }
            }
        )
    }
    
    // Import error dialog
    if (showImportErrorDialog) {
        AlertDialog(
            onDismissRequest = { showImportErrorDialog = false },
            title = {
                Text("Import Failed")
            },
            text = {
                Text(importErrorMessage)
            },
            confirmButton = {
                TextButton(
                    onClick = { showImportErrorDialog = false }
                ) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun VariationRow(
    letter: String,
    variations: List<String>,
    onBoxClick: (Int) -> Unit,
    labelWidth: Dp = 40.dp,
    labelColor: androidx.compose.ui.graphics.Color? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Letter label
            Text(
                text = letter,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(labelWidth),
                textAlign = TextAlign.Center,
                maxLines = 1,
                color = labelColor ?: MaterialTheme.colorScheme.onSurface
            )
            
            // 8 variation boxes
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                repeat(8) { index ->
                    val character = variations.getOrNull(index) ?: ""
                    val isEmpty = index >= variations.size
                    
                    VariationBox(
                        character = character,
                        isEmpty = isEmpty,
                        onClick = { onBoxClick(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VariationBox(
    character: String,
    isEmpty: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(48.dp)
            .height(48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isEmpty) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = if (isEmpty) {
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        } else null
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (!isEmpty) {
                Text(
                    text = character,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Applies picker changes for a row slot, trimming trailing blanks and enforcing the slot cap.
 */
private fun updateVariationEntries(
    currentEntries: List<String>,
    index: Int?,
    newValue: String,
    maxSlots: Int = 8
): List<String> {
    val targetIndex = index ?: return currentEntries
    val updatedEntries = currentEntries.toMutableList()
    
    while (updatedEntries.size <= targetIndex) {
        updatedEntries.add("")
    }
    
    if (newValue.isEmpty()) {
        if (targetIndex < updatedEntries.size) {
            updatedEntries.removeAt(targetIndex)
        }
    } else {
        if (targetIndex < updatedEntries.size) {
            updatedEntries[targetIndex] = newValue
        } else {
            updatedEntries.add(newValue)
        }
    }
    
    while (updatedEntries.isNotEmpty() && updatedEntries.last().isEmpty()) {
        updatedEntries.removeLast()
    }
    
    return updatedEntries.take(maxSlots)
}

/**
 * Load AllVariations.json file (static map with all possibilities).
 */
private fun loadAllVariationsFromJson(context: Context): Map<String, List<String>> {
    return try {
        val inputStream = context.assets.open("common/variations/AllVariations.json")
        val jsonString = inputStream.bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonString)
        val variationsObject = jsonObject.getJSONObject("variations")
        
        val result = mutableMapOf<String, List<String>>()
        val keys = variationsObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val variationsArray = variationsObject.getJSONArray(key)
            val variationsList = mutableListOf<String>()
            for (i in 0 until variationsArray.length()) {
                variationsList.add(variationsArray.getString(i))
            }
            result[key] = variationsList
            // Also add lowercase version if uppercase
            if (key.length == 1 && key[0].isUpperCase()) {
                result[key.lowercase()] = variationsList
            }
        }
        result
    } catch (e: Exception) {
        emptyMap()
    }
}

/**
 * Save variations to a custom file.
 */
private fun saveVariationsToFile(context: Context, variations: Map<String, List<String>>) {
    try {
        val jsonObject = JSONObject()
        val variationsObject = JSONObject()
        
        variations.forEach { (key, value) ->
            variationsObject.put(key, org.json.JSONArray(value))
        }
        
        jsonObject.put("variations", variationsObject)
        
        val file = java.io.File(context.filesDir, "variations.json")
        file.writeText(jsonObject.toString(2))
    } catch (e: Exception) {
        android.util.Log.e("VariationCustomization", "Error saving variations", e)
    }
}

/**
 * Check if custom variations file exists.
 */
private fun hasCustomVariationsFile(context: Context): Boolean {
    val file = java.io.File(context.filesDir, "variations.json")
    return file.exists()
}

/**
 * Reset variations to default by deleting custom file.
 */
private fun resetVariationsToDefault(context: Context) {
    try {
        val file = java.io.File(context.filesDir, "variations.json")
        if (file.exists()) {
            file.delete()
        }
    } catch (e: Exception) {
        android.util.Log.e("VariationCustomization", "Error resetting variations", e)
    }
}

/**
 * Import variations from JSON string.
 * Returns Pair<success: Boolean, errorMessage: String>
 */
private fun importVariationsFromJson(context: Context, jsonString: String): Pair<Boolean, String> {
    return try {
        val jsonObject = JSONObject(jsonString)
        
        // Validate structure
        if (!jsonObject.has("variations")) {
            return Pair(false, "Invalid format: Missing 'variations' key")
        }
        
        val variationsObject = jsonObject.getJSONObject("variations")
        val importedVariations = mutableMapOf<String, List<String>>()
        
        // Parse and validate variations
        val keys = variationsObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            
            // Only allow single character keys (A-Z, a-z) or "default"
            if (key != "default" && (key.length != 1 || !key[0].isLetter())) {
                continue // Skip invalid keys
            }
            
            val variationsArray = variationsObject.getJSONArray(key)
            val variationsList = mutableListOf<String>()
            
            // Limit to 8 variations for letters, 8 for default
            val maxVariations = if (key == "default") 8 else 8
            for (i in 0 until minOf(variationsArray.length(), maxVariations)) {
                val variation = variationsArray.getString(i)
                if (variation.isNotEmpty()) {
                    variationsList.add(variation)
                }
            }
            
            if (variationsList.isNotEmpty()) {
                importedVariations[key] = variationsList
            }
        }
        
        if (importedVariations.isEmpty()) {
            return Pair(false, "No valid variations found in file")
        }
        
        // Save to file
        saveVariationsToFile(context, importedVariations)
        
        Pair(true, "")
    } catch (e: Exception) {
        Pair(false, "Failed to parse JSON: ${e.message}")
    }
}

/**
 * Export variations to URI.
 */
private fun exportVariationsToUri(context: Context, uri: Uri, variations: Map<String, List<String>>) {
    try {
        val jsonObject = JSONObject()
        val variationsObject = JSONObject()
        
        variations.forEach { (key, value) ->
            variationsObject.put(key, org.json.JSONArray(value))
        }
        
        jsonObject.put("variations", variationsObject)
        
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(jsonObject.toString(2).toByteArray())
        }
    } catch (e: Exception) {
        throw e
    }
}

/**
 * Load all custom variations including the "default" key.
 * This function loads both single character keys (A-Z, a-z) and the special "default" key.
 */
private fun loadAllCustomVariations(context: Context): Map<String, List<String>> {
    val customFile = File(context.filesDir, "variations.json")
    
    if (!customFile.exists()) {
        // Fall back to loading from VariationRepository and return as String-keyed map
        val repoVariations = VariationRepository.loadVariations(context, context.assets)
        val variationsMap = repoVariations.mapKeys { it.key.toString() }.toMutableMap()
        
        // Also load default special chars from asset file
        try {
            val filePath = "common/default_special_chars.json"
            val jsonString = context.assets.open(filePath).bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val defaultBarArray = jsonObject.getJSONArray("default_bar")
            val defaultChars = mutableListOf<String>()
            
            // Load first 8 characters for default
            for (i in 0 until minOf(defaultBarArray.length(), 8)) {
                defaultChars.add(defaultBarArray.getString(i))
            }
            
            if (defaultChars.isNotEmpty()) {
                variationsMap["default"] = defaultChars
            }
        } catch (e: Exception) {
            Log.e("VariationCustomization", "Error loading default special chars from assets", e)
        }
        
        return variationsMap
    }
    
    try {
        val jsonString = customFile.readText()
        val jsonObject = JSONObject(jsonString)
        val variationsObject = jsonObject.getJSONObject("variations")
        val variations = mutableMapOf<String, List<String>>()
        
        val keys = variationsObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            
            // Allow single character keys (A-Z, a-z) or "default"
            if (key != "default" && (key.length != 1 || !key[0].isLetter())) {
                continue
            }
            
            val variationsArray = variationsObject.getJSONArray(key)
            val variationsList = mutableListOf<String>()
            
            // Limit to 8 variations for letters, 8 for default
            val maxVariations = if (key == "default") 8 else 8
            for (i in 0 until minOf(variationsArray.length(), maxVariations)) {
                val variation = variationsArray.getString(i)
                if (variation.isNotEmpty()) {
                    variationsList.add(variation)
                }
            }
            
            if (variationsList.isNotEmpty()) {
                variations[key] = variationsList
            }
        }
        
        return variations
    } catch (e: Exception) {
        // Fall back to loading from VariationRepository
        val repoVariations = VariationRepository.loadVariations(context, context.assets)
        return repoVariations.mapKeys { it.key.toString() }
    }
}
