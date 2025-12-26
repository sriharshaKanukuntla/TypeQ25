package it.srik.TypeQ25

import android.view.KeyEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.srik.TypeQ25.data.layout.JsonLayoutLoader
import it.srik.TypeQ25.data.layout.LayoutFileStore
import it.srik.TypeQ25.data.layout.LayoutMapping
import it.srik.TypeQ25.data.layout.TapMapping
import kotlinx.coroutines.launch

private data class KeyMappingRowModel(
    val keyCode: Int,
    val keyLabel: String,
    val lowercase: String,
    val uppercase: String,
    val multiTapEnabled: Boolean,
    val taps: List<TapMapping>
)

/**
 * Compact viewer for a keyboard layout mapping.
 */
@Composable
fun KeyboardLayoutViewerScreen(
    layoutName: String,
    modifier: Modifier = Modifier,
    isEditMode: Boolean = false,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isLoading by remember(layoutName) { mutableStateOf(true) }
    var layoutState by remember(layoutName) { mutableStateOf<Map<Int, LayoutMapping>?>(null) }
    var editingKey by remember { mutableStateOf<Int?>(null) }
    
    // Load initial layout
    remember(layoutName) {
        isLoading = true
        layoutState = JsonLayoutLoader.loadLayout(context.assets, layoutName, context)
        isLoading = false
    }

    val items = remember(layoutState) {
        layoutState?.entries
            ?.sortedBy { it.key }
            ?.map { (keyCode, mapping) ->
                KeyMappingRowModel(
                    keyCode = keyCode,
                    keyLabel = KeyEvent.keyCodeToString(keyCode),
                    lowercase = mapping.lowercase,
                    uppercase = mapping.uppercase,
                    multiTapEnabled = mapping.multiTapEnabled,
                    taps = mapping.taps
                )
            }.orEmpty()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                        Text(
                            text = stringResource(if (isEditMode) R.string.keyboard_layout_editor_title else R.string.keyboard_layout_viewer_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.keyboard_layout_viewer_subtitle, layoutName),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isEditMode) {
                        IconButton(
                            onClick = {
                                val currentLayout = layoutState
                                if (currentLayout != null) {
                                    val metadata = LayoutFileStore.getLayoutMetadataFromAssets(
                                        context.assets,
                                        layoutName
                                    )
                                    val success = LayoutFileStore.saveLayout(
                                        context = context,
                                        layoutName = layoutName,
                                        layout = currentLayout,
                                        name = metadata?.name ?: layoutName,
                                        description = metadata?.description ?: ""
                                    )
                                    coroutineScope.launch {
                                        if (success) {
                                            snackbarHostState.showSnackbar(context.getString(R.string.keyboard_layout_saved))
                                            onBack()
                                        } else {
                                            snackbarHostState.showSnackbar(context.getString(R.string.keyboard_layout_save_failed))
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = stringResource(R.string.keyboard_layout_save)
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading && layoutState == null -> {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }

                layoutState == null -> {
                    Text(
                        text = stringResource(R.string.keyboard_layout_viewer_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }

                items.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.keyboard_layout_viewer_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items, key = { it.keyCode }) { item ->
                            KeyMappingRow(
                                model = item,
                                isEditMode = isEditMode,
                                onClick = if (isEditMode) {{ editingKey = item.keyCode }} else null
                            )
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                    
                    // Edit dialog
                    if (isEditMode && editingKey != null) {
                        val currentMapping = layoutState?.get(editingKey!!)
                        if (currentMapping != null) {
                            KeyEditDialog(
                                keyCode = editingKey!!,
                                keyLabel = KeyEvent.keyCodeToString(editingKey!!),
                                currentMapping = currentMapping,
                                onDismiss = { editingKey = null },
                                onSave = { newMapping ->
                                    layoutState = layoutState?.toMutableMap()?.apply {
                                        put(editingKey!!, newMapping)
                                    }
                                    editingKey = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyMappingRow(
    model: KeyMappingRowModel,
    isEditMode: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        shape = MaterialTheme.shapes.extraSmall,
        color = if (isEditMode) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 0.dp,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = model.keyLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Text(
                text = "${model.lowercase}/${model.uppercase}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.widthIn(min = 72.dp)
            )

            MultiTapBadge(enabled = model.multiTapEnabled)

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                model.taps.forEachIndexed { index, tap ->
                    val label = if (tap.uppercase.isNotBlank()) {
                        "${tap.lowercase}/${tap.uppercase}"
                    } else {
                        tap.lowercase
                    }
                    TapChip(label = label)
                }
            }
        }
    }
}

@Composable
private fun MultiTapBadge(enabled: Boolean) {
    val containerColor = if (enabled) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        tonalElevation = 0.dp,
        color = containerColor
    ) {
        Text(
            text = if (enabled) {
                stringResource(R.string.keyboard_layout_viewer_multitap_on)
            } else {
                stringResource(R.string.keyboard_layout_viewer_multitap_off)
            },
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun TapChip(
    label: String
) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        tonalElevation = 0.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun KeyEditDialog(
    keyCode: Int,
    keyLabel: String,
    currentMapping: LayoutMapping,
    onDismiss: () -> Unit,
    onSave: (LayoutMapping) -> Unit
) {
    var lowercase by remember { mutableStateOf(currentMapping.lowercase) }
    var uppercase by remember { mutableStateOf(currentMapping.uppercase) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.keyboard_layout_edit_key_title, keyLabel),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = lowercase,
                    onValueChange = { lowercase = it },
                    label = { Text(stringResource(R.string.keyboard_layout_lowercase)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = uppercase,
                    onValueChange = { uppercase = it },
                    label = { Text(stringResource(R.string.keyboard_layout_uppercase)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.keyboard_layout_edit_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newMapping = currentMapping.copy(
                        lowercase = lowercase,
                        uppercase = uppercase
                    )
                    onSave(newMapping)
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
