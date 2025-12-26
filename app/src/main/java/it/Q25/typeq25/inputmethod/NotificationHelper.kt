package it.srik.TypeQ25.inputmethod

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import it.srik.TypeQ25.R

/**
 * Helper for managing app notifications.
 */
object NotificationHelper {
    private const val CHANNEL_ID = "TypeQ25_nav_mode_channel"
    private const val NOTIFICATION_ID = 1
    
    /**
     * Checks whether notification permission is granted.
     * On Android 13+ (API 33+) POST_NOTIFICATIONS is required.
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ requires POST_NOTIFICATIONS permission
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 12 and below do not require explicit notification permissions
            true
        }
    }
    
    /**
     * Creates the notification channel (required on Android 8.0+).
     * Uses IMPORTANCE_DEFAULT for normal priority notification.
     * Deletes and recreates the channel if it already exists to apply new settings.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Delete existing channel if it exists to recreate with new settings
            try {
                notificationManager.deleteNotificationChannel(CHANNEL_ID)
            } catch (e: Exception) {
                // Channel doesn't exist, that's fine
            }
            
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_nav_mode_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT // Normal priority notification
            ).apply {
                description = context.getString(R.string.notification_nav_mode_channel_description)
                setShowBadge(false)
                enableVibration(true) // Enable vibration
                // Set vibration pattern: short vibration (50ms)
                vibrationPattern = longArrayOf(0, 50)
                setSound(null, null) // No sound
            }
            
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Creates a bitmap icon with the letter "N" for the nav mode notification.
     * @param size Icon size in pixels
     * @param backgroundColor Background color (default transparent)
     * @param textColor Text color (default white)
     */
    private fun createNavModeIcon(
        size: Int,
        backgroundColor: Int = Color.TRANSPARENT,
        textColor: Int = Color.WHITE
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Draw background
        if (backgroundColor != Color.TRANSPARENT) {
            canvas.drawColor(backgroundColor)
        } else {
            canvas.drawColor(Color.TRANSPARENT)
        }
        
        // Draw the "N" letter
        val paint = Paint().apply {
            color = textColor
            textSize = size * 0.7f // 70% of size to keep margins
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        
        // Compute vertical position to center the text
        val textY = (canvas.height / 2) - ((paint.descent() + paint.ascent()) / 2)
        
        // Draw "N"
        canvas.drawText("N", canvas.width / 2f, textY, paint)
        
        return bitmap
    }
    
    /**
     * Shows a notification when nav mode is activated.
     * Checks permissions before showing it.
     */
    fun showNavModeActivatedNotification(context: Context) {
        // Check permission first
        if (!hasNotificationPermission(context)) {
            android.util.Log.w("NotificationHelper", "Notification permission not granted")
            return
        }
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create the channel if needed (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(context)
        }
        
        // Create custom "N" icon for the small icon (status bar)
        // Standard size for small icon: 24dp converted to pixels
        val smallIconSize = (24 * context.resources.displayMetrics.density).toInt().coerceAtLeast(24)
        val smallIconBitmap = createNavModeIcon(smallIconSize, Color.TRANSPARENT, Color.WHITE)
        val smallIcon = IconCompat.createWithBitmap(smallIconBitmap)
        
        // Create large icon for expanded notification
        val largeIconSize = (64 * context.resources.displayMetrics.density).toInt().coerceAtLeast(64)
        val largeIconBitmap = createNavModeIcon(largeIconSize, Color.TRANSPARENT, Color.WHITE)
        
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_nav_mode_activated_title))
            .setContentText(context.getString(R.string.notification_nav_mode_activated_text))
            .setSmallIcon(smallIcon) // Custom "N" icon for status bar
            .setLargeIcon(largeIconBitmap) // Large "N" icon for expanded notification
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Normal priority notification
            .setAutoCancel(true) // Automatically dismissed when tapped
            .setOngoing(true) // Persistent, stays visible
            .setCategory(NotificationCompat.CATEGORY_STATUS) // Status category for status bar
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Visible on lock screen
            // Sound is disabled via channel settings (setSound(null, null))
        
        // For Android < 8.0, set vibration pattern directly on notification
        // (On Android 8.0+ this is controlled by the channel)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Vibration pattern: short vibration (50ms)
            // Pattern: [delay, vibrate, delay, vibrate, ...]
            // 0 = no delay before first vibration, 50 = vibrate for 50ms
            @Suppress("DEPRECATION")
            notificationBuilder.setVibrate(longArrayOf(0, 50))
        }
        
        val notification = notificationBuilder.build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Cancels the nav mode notification.
     */
    fun cancelNavModeNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
}

