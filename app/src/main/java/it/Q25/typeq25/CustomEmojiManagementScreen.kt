package it.srik.TypeQ25

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import it.srik.TypeQ25.inputmethod.ui.EmojiDataParser

/**
 * Custom Emoji Management screen in Settings.
 */
@Composable
fun CustomEmojiManagementScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val emojiParser = remember { EmojiDataParser(context) }
    
    // Trigger state to force recomposition
    var refreshTrigger by remember { mutableStateOf(0) }
    
    // Dialog states
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("") }
    var emojiToRemove by remember { mutableStateOf<Pair<String, String>?>(null) }
    
    // Get all categories with custom emojis
    val categoriesWithCustomEmojis = remember(refreshTrigger) {
        val allCategories = listOf(
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
        allCategories.associateWith { category ->
            emojiParser.getCustomEmojisWithShortcodes(category)
        }
    }
    
    // Show add dialog
    if (showAddDialog && selectedCategory.isNotEmpty()) {
        AddCustomEmojiDialog(
            category = selectedCategory,
            emojiParser = emojiParser,
            onDismiss = { showAddDialog = false },
            onEmojiAdded = {
                showAddDialog = false
                refreshTrigger++
            }
        )
    }
    
    // Show remove confirmation
    emojiToRemove?.let { (emoji, category) ->
        val shortcode = emojiParser.getCustomEmojiShortcode(emoji, category)
        AlertDialog(
            onDismissRequest = { emojiToRemove = null },
            title = { Text(if (shortcode != null) "Remove :$shortcode:?" else "Remove Custom Emoji?") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(emoji, style = MaterialTheme.typography.displayLarge)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        emojiParser.removeCustomEmoji(emoji, category)
                        emojiToRemove = null
                        refreshTrigger++
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { emojiToRemove = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Text(
                        text = "Custom Emojis",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            categoriesWithCustomEmojis.forEach { (category, customEmojis) ->
                EmojiCategorySection(
                    category = category,
                    customEmojis = customEmojis,
                    onAddClick = {
                        selectedCategory = category
                        showAddDialog = true
                    },
                    onRemoveClick = { emoji ->
                        emojiToRemove = emoji to category
                    }
                )
            }
            
            if (categoriesWithCustomEmojis.values.all { it.isEmpty() }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No custom emojis yet.\nTap + to add emojis to any category.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun EmojiCategorySection(
    category: String,
    customEmojis: List<Pair<String, String>>,
    onAddClick: () -> Unit,
    onRemoveClick: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (customEmojis.isNotEmpty()) {
                        Text(
                            text = "${customEmojis.size} custom ${if (customEmojis.size == 1) "emoji" else "emojis"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                IconButton(onClick = onAddClick) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add emoji",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            if (customEmojis.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                customEmojis.forEach { (emoji, shortcode) ->
                    CustomEmojiItem(
                        emoji = emoji,
                        shortcode = shortcode,
                        onRemove = { onRemoveClick(emoji) }
                    )
                }
            }
        }
    }
    Divider()
}

@Composable
fun CustomEmojiItem(
    emoji: String,
    shortcode: String,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = emoji,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = ":$shortcode:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun AddCustomEmojiDialog(
    category: String,
    emojiParser: EmojiDataParser,
    onDismiss: () -> Unit,
    onEmojiAdded: () -> Unit
) {
    val context = LocalContext.current
    var shortcodeInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Try to get clipboard content
    val clipboardEmoji = remember {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = clipboard?.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                clipData.getItemAt(0).text?.toString()?.trim() ?: ""
            } else ""
        } catch (e: Exception) {
            ""
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Add to $category",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Copy emoji to clipboard first",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Emoji preview
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (clipboardEmoji.isNotEmpty()) {
                            Text(
                                text = clipboardEmoji.take(10),
                                style = MaterialTheme.typography.displayMedium
                            )
                        } else {
                            Text(
                                text = "(empty)",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Shortcode input
                TextField(
                    value = shortcodeInput,
                    onValueChange = {
                        shortcodeInput = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }
                        errorMessage = null
                    },
                    label = { Text("Shortcode name") },
                    placeholder = { Text("e.g. my_emoji") },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = if (errorMessage != null) {
                        { Text(errorMessage!!, color = MaterialTheme.colorScheme.error) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = {
                            if (clipboardEmoji.isEmpty()) {
                                errorMessage = "Clipboard is empty"
                                return@Button
                            }
                            
                            if (shortcodeInput.isEmpty()) {
                                errorMessage = "Shortcode cannot be empty"
                                return@Button
                            }
                            
                            if (!shortcodeInput.matches(Regex("^[a-z0-9_]+$"))) {
                                errorMessage = "Only lowercase letters, numbers, and underscore allowed"
                                return@Button
                            }
                            
                            if (emojiParser.addCustomEmoji(clipboardEmoji, shortcodeInput, category)) {
                                onEmojiAdded()
                            } else {
                                errorMessage = "Shortcode already exists or emoji already added"
                            }
                        }
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}
