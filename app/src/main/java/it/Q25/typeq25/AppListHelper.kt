package it.srik.TypeQ25

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.Log

/**
 * Data class per rappresentare un'app installata.
 */
data class InstalledApp(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean = false
)

/**
 * Helper per ottenere la lista di tutte le app installate che possono essere avviate.
 */
object AppListHelper {
    private const val TAG = "AppListHelper"
    
    /**
     * Ottiene tutte le app installate che possono essere avviate.
     */
    fun getInstalledApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val apps = mutableListOf<InstalledApp>()
        
        try {
            // Ottieni tutte le app che hanno un launcher activity
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            
            // Raggruppa per package per evitare duplicati
            val packageNames = mutableSetOf<String>()
            
            for (resolveInfo in resolveInfos) {
                val packageName = resolveInfo.activityInfo.packageName
                
                // Evita duplicati
                if (packageNames.contains(packageName)) {
                    continue
                }
                packageNames.add(packageName)
                
                try {
                    val appInfo: ApplicationInfo = pm.getApplicationInfo(packageName, 0)
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(packageName)
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    
                    apps.add(InstalledApp(
                        packageName = packageName,
                        appName = appName,
                        icon = icon,
                        isSystemApp = isSystemApp
                    ))
                } catch (e: Exception) {
                    Log.w(TAG, "Errore nel caricamento info per $packageName", e)
                }
            }
            
            // Ordina alfabeticamente per nome
            apps.sortBy { it.appName.lowercase() }
            
            Log.d(TAG, "Caricate ${apps.size} app installate")
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel recupero delle app installate", e)
        }
        
        return apps
    }
}

