package it.srik.TypeQ25.inputmethod

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import it.srik.TypeQ25.R
import it.srik.TypeQ25.SettingsManager

/**
 * Activity for choosing the preferred speech recognition app.
 * Shows a list of available speech recognition apps for the user to select.
 */
class SpeechAppChooserActivity : ComponentActivity() {
    companion object {
        private const val TAG = "SpeechAppChooser"
        const val EXTRA_SELECTED_PACKAGE = "selected_package"
    }

    data class SpeechApp(
        val packageName: String,
        val appName: String,
        val icon: android.graphics.drawable.Drawable?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val availableApps = getAvailableSpeechApps()
        val currentPreference = SettingsManager.getPreferredSpeechApp(this)

        setContent {
            MaterialTheme {
                SpeechAppChooserScreen(
                    apps = availableApps,
                    currentPackage = currentPreference,
                    onAppSelected = { app ->
                        SettingsManager.setPreferredSpeechApp(this, app.packageName)
                        val resultIntent = Intent().apply {
                            putExtra(EXTRA_SELECTED_PACKAGE, app.packageName)
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    },
                    onDismiss = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }

    private fun getAvailableSpeechApps(): List<SpeechApp> {
        val apps = mutableListOf<SpeechApp>()
        val pm = packageManager

        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)

            for (info in resolveInfos) {
                val packageName = info.activityInfo.packageName
                val appName = info.loadLabel(pm).toString()
                val icon = info.loadIcon(pm)
                
                apps.add(SpeechApp(packageName, appName, icon))
                Log.d(TAG, "Found speech app: $appName ($packageName)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying speech apps", e)
        }

        return apps.sortedBy { it.appName }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeechAppChooserScreen(
    apps: List<SpeechAppChooserActivity.SpeechApp>,
    currentPackage: String?,
    onAppSelected: (SpeechAppChooserActivity.SpeechApp) -> Unit,
    onDismiss: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.speech_app_chooser_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (apps.isEmpty()) {
                // No speech apps available
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.speech_app_chooser_no_apps),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Info card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.speech_app_chooser_info_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.speech_app_chooser_info_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // List of apps
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(apps) { app ->
                        SpeechAppItem(
                            app = app,
                            isSelected = app.packageName == currentPackage,
                            onClick = { onAppSelected(app) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeechAppItem(
    app: SpeechAppChooserActivity.SpeechApp,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App icon
            if (app.icon != null) {
                AndroidView(
                    factory = { context ->
                        ImageView(context).apply {
                            setImageDrawable(app.icon)
                        }
                    },
                    modifier = Modifier.size(40.dp)
                )
            }

            // App name and package
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Check icon if selected
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
