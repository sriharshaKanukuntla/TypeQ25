package it.srik.TypeQ25.ui

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import it.srik.TypeQ25.BuildConfig
import it.srik.TypeQ25.R
import it.srik.TypeQ25.update.checkForUpdate
import it.srik.TypeQ25.update.showUpdateDialog

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Update check button disabled
        /*
        findViewById<Button>(R.id.check_updates_button).setOnClickListener {
            checkForUpdate(
                context = this,
                currentVersion = BuildConfig.VERSION_NAME,
                ignoreDismissedReleases = false
            ) { hasUpdate, latestVersion, downloadUrl ->
                if (hasUpdate && latestVersion != null) {
                    showUpdateDialog(this, latestVersion, downloadUrl)
                } else {
                    Toast.makeText(this, getString(R.string.settings_update_up_to_date), Toast.LENGTH_SHORT).show()
                }
            }
        }
        */
    }
}

