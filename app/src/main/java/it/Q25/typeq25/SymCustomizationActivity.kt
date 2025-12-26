package it.srik.TypeQ25

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import it.srik.TypeQ25.ui.theme.TypeQ25Theme

class SymCustomizationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TypeQ25Theme {
                SymCustomizationScreen(
                    modifier = Modifier.fillMaxSize(),
                    onBack = {
                        // Confirm pending restore when user presses back
                        SettingsManager.confirmPendingRestoreSymPage(this@SymCustomizationActivity)
                        finish()
                    }
                )
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // If activity is destroyed without finish() (e.g., user goes to another app),
        // clear the pending restore to avoid restoring SYM layout
        if (!isFinishing) {
            SettingsManager.clearPendingRestoreSymPage(this)
        }
    }
}

