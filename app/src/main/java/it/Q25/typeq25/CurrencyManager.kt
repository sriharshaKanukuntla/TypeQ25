package it.srik.TypeQ25

import android.content.Context
import android.content.SharedPreferences

object CurrencyManager {

    private const val PREFS_NAME = "currency_prefs"
    private const val KEY_CURRENCY = "selected_currency"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun setCurrency(context: Context, currency: String) {
        getPreferences(context).edit().putString(KEY_CURRENCY, currency).apply()
    }

    fun getCurrency(context: Context): String {
        return getPreferences(context).getString(KEY_CURRENCY, "$") ?: "$"
    }
}
