package tn.ennour.maan

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifier {
    private const val CH_TRACK = "tracking"
    private const val CH_MSG = "messages"
    const val TRACK_ID = 1

    private fun channels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH_TRACK, "متابعة الموقع", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_MSG, "رسائل المرافق", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun tracking(ctx: Context): Notification {
        channels(ctx)
        val text = if (Store.isSos(ctx)) "تم إرسال نداء المساعدة للمرافق" else "موقعك يُشارك مع مرافق الرحلة"
        return NotificationCompat.Builder(ctx, CH_TRACK)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("في عيني")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openApp(ctx))
            .build()
    }

    fun message(ctx: Context, title: String, text: String) {
        channels(ctx)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val n = NotificationCompat.Builder(ctx, CH_MSG)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify((System.currentTimeMillis() % 100000).toInt() + 10, n)
        } catch (e: SecurityException) {
        }
    }
}
