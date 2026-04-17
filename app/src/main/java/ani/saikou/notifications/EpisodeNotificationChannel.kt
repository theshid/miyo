package ani.saikou.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ani.saikou.MainActivity
import ani.saikou.R

object EpisodeNotificationChannel {

    const val CHANNEL_ID = "new_episodes"
    private const val CHANNEL_NAME = "New Episodes"
    private const val CHANNEL_DESC = "Notifications when new episodes air for anime on your list"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = CHANNEL_DESC
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun showEpisodeNotification(
        context: Context,
        mediaId: Int,
        title: String,
        episode: Int,
        coverBitmap: Bitmap? = null,
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("media_id", mediaId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, mediaId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("Episode $episode is now available")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (coverBitmap != null) {
            builder.setLargeIcon(coverBitmap)
        }

        try {
            NotificationManagerCompat.from(context).notify(mediaId, builder.build())
        } catch (_: SecurityException) {
            // Permission not granted — silently skip
        }
    }
}
