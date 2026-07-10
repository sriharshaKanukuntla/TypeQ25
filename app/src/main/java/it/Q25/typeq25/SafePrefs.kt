package it.srik.TypeQ25

import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager

/**
 * SharedPreferences access that is safe in Direct Boot mode.
 *
 * Before the user unlocks the device for the first time after boot,
 * credential-encrypted storage is unavailable and
 * [Context.getSharedPreferences] throws [IllegalStateException]. Since the
 * IME service is directBootAware, any component it touches while locked must
 * use device-protected storage instead, or the whole keyboard crashes at the
 * lock screen password prompt.
 *
 * While locked this reads/writes the device-protected copy of the named
 * prefs file; once unlocked it uses the regular credential-encrypted file.
 */
fun Context.safeSharedPreferences(name: String): SharedPreferences {
    val userManager = getSystemService(UserManager::class.java)
    return if (userManager?.isUserUnlocked == false) {
        createDeviceProtectedStorageContext().getSharedPreferences(name, Context.MODE_PRIVATE)
    } else {
        getSharedPreferences(name, Context.MODE_PRIVATE)
    }
}
