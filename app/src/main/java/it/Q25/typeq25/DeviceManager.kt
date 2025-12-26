package it.srik.TypeQ25

import android.content.Context
import android.content.SharedPreferences

object DeviceManager {

    private const val PREFS_NAME = "device_prefs"
    private const val KEY_DEVICE = "selected_device"
    
    // Cache device type to avoid repeated SharedPreferences reads
    @Volatile
    private var cachedDevice: String? = null

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun setDevice(context: Context, device: String) {
        cachedDevice = device
        getPreferences(context).edit().putString(KEY_DEVICE, device).apply()
    }

    fun getDevice(context: Context): String {
        // Return cached value if available
        cachedDevice?.let { return it }
        
        // Otherwise read from SharedPreferences and cache it
        val device = getPreferences(context).getString(KEY_DEVICE, "Q25") ?: "Q25"
        cachedDevice = device
        return device
    }

    fun isDeviceSelected(context: Context): Boolean {
        return getPreferences(context).contains(KEY_DEVICE)
    }

    fun getAltKeyMappingsJson(context: Context): String {
        val device = getDevice(context)
        return context.assets.open("devices/$device/alt_key_mappings.json").bufferedReader().use {
            it.readText()
        }
    }
}
