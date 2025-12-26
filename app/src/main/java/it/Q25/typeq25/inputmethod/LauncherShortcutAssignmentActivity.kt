package it.srik.TypeQ25.inputmethod

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import it.srik.TypeQ25.*
import android.view.KeyEvent
import android.widget.ImageView
import android.view.ViewGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.res.stringResource
import it.srik.TypeQ25.R

/**
 * Activity per assegnare una scorciatoia del launcher a un tasto.
 * Viene mostrata quando si preme un tasto non assegnato nel launcher.
 * Usa un BottomSheet che appare sopra il launcher.
 */
class LauncherShortcutAssignmentActivity : ComponentActivity() {
    companion object {
        const val EXTRA_KEY_CODE = "key_code"
        const val RESULT_ASSIGNED = 1
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Disable activity transition animations for instant appearance
        overridePendingTransition(0, 0)
        
        // Configure window to be fully transparent and overlay
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        window.setBackgroundDrawableResource(android.R.color.transparent)
        
        val keyCode = intent.getIntExtra(EXTRA_KEY_CODE, -1)
        if (keyCode == -1) {
            finish()
            return
        }
        
        // Usa un tema trasparente per mostrare il bottom sheet sopra il launcher
        setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { finish() }, // Tocca fuori per chiudere
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // Overlay semi-trasparente
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)
                    ) {}
                    
                    // Bottom Sheet
                    LauncherShortcutAssignmentBottomSheet(
                        keyCode = keyCode,
                        onAppSelected = { app ->
                            // Save the shortcut
                            SettingsManager.setLauncherShortcut(
                                this@LauncherShortcutAssignmentActivity,
                                keyCode,
                                app.packageName,
                                app.appName
                            )
                            
                            // Don't launch the app - just stay in settings
                            setResult(RESULT_ASSIGNED)
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
    
    override fun onBackPressed() {
        finish()
        overridePendingTransition(0, 0)
    }
    
    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
    
    /**
     * Launches an app by package name.
     */
    private fun launchApp(packageName: String) {
        try {
            val pm = packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                Log.d("LauncherShortcutAssignment", "App launched: $packageName")
            } else {
                Log.w("LauncherShortcutAssignment", "No launch intent found for: $packageName")
            }
        } catch (e: Exception) {
            Log.e("LauncherShortcutAssignment", "Error launching app $packageName", e)
        }
    }
}

/**
 * Bottom Sheet per assegnare un'app a un tasto.
 * Appare dal basso e non occupa tutto lo schermo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LauncherShortcutAssignmentBottomSheet(
    keyCode: Int,
    onAppSelected: (InstalledApp) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Check if there's already a shortcut assigned (using state to trigger recomposition)
    var currentShortcut by remember { mutableStateOf(SettingsManager.getLauncherShortcut(context, keyCode)) }
    
    // Carica le app installate
    val installedApps by remember {
        mutableStateOf(AppListHelper.getInstalledApps(context))
    }
    
    var searchQuery by remember { mutableStateOf("") }
    
    // Funzione helper per ottenere la lettera del tasto
    fun getKeyLetter(keyCode: Int): Char? {
        return when (keyCode) {
            KeyEvent.KEYCODE_Q -> 'Q'
            KeyEvent.KEYCODE_W -> 'W'
            KeyEvent.KEYCODE_E -> 'E'
            KeyEvent.KEYCODE_R -> 'R'
            KeyEvent.KEYCODE_T -> 'T'
            KeyEvent.KEYCODE_Y -> 'Y'
            KeyEvent.KEYCODE_U -> 'U'
            KeyEvent.KEYCODE_I -> 'I'
            KeyEvent.KEYCODE_O -> 'O'
            KeyEvent.KEYCODE_P -> 'P'
            KeyEvent.KEYCODE_A -> 'A'
            KeyEvent.KEYCODE_S -> 'S'
            KeyEvent.KEYCODE_D -> 'D'
            KeyEvent.KEYCODE_F -> 'F'
            KeyEvent.KEYCODE_G -> 'G'
            KeyEvent.KEYCODE_H -> 'H'
            KeyEvent.KEYCODE_J -> 'J'
            KeyEvent.KEYCODE_K -> 'K'
            KeyEvent.KEYCODE_L -> 'L'
            KeyEvent.KEYCODE_Z -> 'Z'
            KeyEvent.KEYCODE_X -> 'X'
            KeyEvent.KEYCODE_C -> 'C'
            KeyEvent.KEYCODE_V -> 'V'
            KeyEvent.KEYCODE_B -> 'B'
            KeyEvent.KEYCODE_N -> 'N'
            KeyEvent.KEYCODE_M -> 'M'
            else -> null
        }
    }
    
    // Filtra e ordina le app in base alla query di ricerca e alla lettera del tasto
    val filteredApps = remember(installedApps, searchQuery, keyCode) {
        val apps = if (searchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
        
        // Ordina: prima le app che iniziano con la lettera del tasto, poi le altre
        val keyLetter = getKeyLetter(keyCode)?.lowercaseChar()
        if (keyLetter != null && searchQuery.isBlank()) {
            val appsStartingWithLetter = apps.filter { 
                it.appName.isNotEmpty() && it.appName[0].lowercaseChar() == keyLetter 
            }.sortedBy { it.appName.lowercase() }
            
            val otherApps = apps.filter { 
                it.appName.isEmpty() || it.appName[0].lowercaseChar() != keyLetter 
            }.sortedBy { it.appName.lowercase() }
            
            appsStartingWithLetter + otherApps
        } else {
            // If there's an active search, sort normally
            apps.sortedBy { it.appName.lowercase() }
        }
    }
    
    // Funzione helper per ottenere il nome del tasto
    @Composable
    fun getKeyName(keyCode: Int): String {
        val keyName = when (keyCode) {
            KeyEvent.KEYCODE_Q -> "Q"
            KeyEvent.KEYCODE_W -> "W"
            KeyEvent.KEYCODE_E -> "E"
            KeyEvent.KEYCODE_R -> "R"
            KeyEvent.KEYCODE_T -> "T"
            KeyEvent.KEYCODE_Y -> "Y"
            KeyEvent.KEYCODE_U -> "U"
            KeyEvent.KEYCODE_I -> "I"
            KeyEvent.KEYCODE_O -> "O"
            KeyEvent.KEYCODE_P -> "P"
            KeyEvent.KEYCODE_A -> "A"
            KeyEvent.KEYCODE_S -> "S"
            KeyEvent.KEYCODE_D -> "D"
            KeyEvent.KEYCODE_F -> "F"
            KeyEvent.KEYCODE_G -> "G"
            KeyEvent.KEYCODE_H -> "H"
            KeyEvent.KEYCODE_J -> "J"
            KeyEvent.KEYCODE_K -> "K"
            KeyEvent.KEYCODE_L -> "L"
            KeyEvent.KEYCODE_Z -> "Z"
            KeyEvent.KEYCODE_X -> "X"
            KeyEvent.KEYCODE_C -> "C"
            KeyEvent.KEYCODE_V -> "V"
            KeyEvent.KEYCODE_B -> "B"
            KeyEvent.KEYCODE_N -> "N"
            KeyEvent.KEYCODE_M -> "M"
            else -> null
        }
        return keyName ?: stringResource(R.string.launcher_shortcut_assignment_key_name, keyCode)
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(1f),
        dragHandle = {
            HorizontalDivider(
                modifier = Modifier
                    .width(40.dp)
                    .padding(vertical = 8.dp)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Shortcut",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = getKeyName(keyCode),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.launcher_shortcut_assignment_close)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            
            // Current shortcut card with delete button
            currentShortcut?.let { shortcut ->
                if (shortcut.type == SettingsManager.LauncherShortcut.TYPE_APP) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.launcher_shortcut_assignment_current),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = shortcut.appName ?: stringResource(R.string.launcher_shortcuts_app_name_unavailable),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                shortcut.packageName?.let { packageName ->
                                    Text(
                                        text = packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            FilledTonalButton(
                                onClick = {
                                    SettingsManager.removeLauncherShortcut(context, keyCode)
                                    currentShortcut = null // Update state to trigger recomposition
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Text(stringResource(R.string.launcher_shortcuts_remove))
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = stringResource(R.string.launcher_shortcut_assignment_or_choose_new),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
            
            // Campo di ricerca
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.launcher_shortcut_assignment_search_placeholder)) },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.launcher_shortcut_assignment_search_description)
                    )
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Griglia delle app
            if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) {
                            stringResource(R.string.launcher_shortcut_assignment_no_apps)
                        } else {
                            stringResource(R.string.launcher_shortcut_assignment_no_results, searchQuery)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = filteredApps,
                        key = { app -> app.packageName }
                    ) { app ->
                        AppGridItem(
                            app = app,
                            onClick = {
                                onAppSelected(app)
                            }
                        )
                    }
                }
            }
            
        }
    }
}

/**
 * Item della griglia per un'app (versione compatta per griglia).
 */
@Composable
private fun AppGridItem(
    app: InstalledApp,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icona app usando AndroidView (ottimizzata con remember)
            val iconDrawable = remember(app.packageName) { app.icon }
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        android.widget.ImageView(ctx).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                            setImageDrawable(iconDrawable)
                        }
                    },
                    update = { imageView ->
                        imageView.setImageDrawable(iconDrawable)
                    },
                    modifier = Modifier.size(56.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Nome app (centrato, max 2 righe)
            Text(
                text = app.appName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

