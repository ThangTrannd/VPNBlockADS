package com.vpnblockads.core.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

internal class VpnNotifications(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.vpn_channel_name),
            NotificationManager.IMPORTANCE_LOW, // không kêu, không rung
        ).apply { setShowBadge(false) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun running(blockedCount: Int): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_notification)
            .setContentTitle(context.getString(R.string.vpn_running_title))
            .setContentText(context.getString(R.string.vpn_running_text, blockedCount))
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, context.getString(R.string.vpn_action_stop), serviceIntent(AdBlockVpnService.stopIntent(context, fromNotification = true)))
            .build()

    fun updateRunning(blockedCount: Int) = notifySafely(RUNNING_ID, running(blockedCount))

    /** Hiện sau khi tắt từ notification để người dùng bật lại được ngay. */
    fun showStopped() = notifySafely(
        STOPPED_ID,
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_notification)
            .setContentTitle(context.getString(R.string.vpn_stopped_title))
            .setContentText(context.getString(R.string.vpn_stopped_text))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.vpn_action_start), foregroundServiceIntent(AdBlockVpnService.startIntent(context)))
            .build(),
    )

    fun showPermissionNeeded() = notifySafely(
        STOPPED_ID,
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_notification)
            .setContentTitle(context.getString(R.string.vpn_permission_title))
            .setContentText(context.getString(R.string.vpn_permission_text))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build(),
    )

    fun cancelStopped() = manager.cancel(STOPPED_ID)

    private fun notifySafely(id: Int, notification: Notification) {
        // Android 13+: chưa cấp POST_NOTIFICATIONS thì notify() bị bỏ qua/ném SecurityException.
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun openAppIntent(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun serviceIntent(intent: Intent) =
        PendingIntent.getService(context, intent.action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun foregroundServiceIntent(intent: Intent): PendingIntent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(context, intent.action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            serviceIntent(intent)
        }

    companion object {
        const val CHANNEL_ID = "vpn_status"
        const val RUNNING_ID = 1
        const val STOPPED_ID = 2
    }
}
