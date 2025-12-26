package it.srik.TypeQ25

import it.srik.TypeQ25.BuildConfig

/**
 * Provides information about the app build.
 */
object BuildInfo {
    /**
     * Gets the incremental build number.
     */
    fun getBuildNumber(): Int {
        return BuildConfig.BUILD_NUMBER
    }
    
    /**
     * Gets the build date.
     */
    fun getBuildDate(): String {
        return BuildConfig.BUILD_DATE
    }
    
    /**
     * Gets the formatted string with build number and date.
     * Format: "Build X - DD MMM YYYY"
     */
    fun getBuildInfoString(): String {
        val buildNumber = getBuildNumber()
        val buildDate = getBuildDate()
        return if (buildDate.isNotEmpty()) {
            "Build $buildNumber - $buildDate"
        } else {
            "Build $buildNumber"
        }
    }
}

