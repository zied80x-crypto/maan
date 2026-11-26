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
    private const val CH_HELP = "helper_alarm"
    const val TRACK_ID = 1
    const val HELP_ID = 2

    private fun channels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CH_TRACK, "متابعة الموقع", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_MSG, "رسائل المرافق", NotificationManager.IMPORTANCE_HIGH)
        )
        val alarm = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
        nm.createNotificationChannel(
            NotificationChannel(CH_HELP, "إنذار مساعد المرافق", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "نداءات النجدة في الرحلة"
                setSound(alarm, android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 300, 800, 300, 800)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    /** إنذار بالصوت لمساعد المرافق: يرنّ حتى يفتح الإشعار. */
    fun helperAlarm(ctx: Context, name: String, where: String, guidePhone: String) {
        channels(ctx)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val text = (if (where.isNotEmpty()) "$where · " else "") + "فيّق المرافق ولا عاونو 📞"
        val b = NotificationCompat.Builder(ctx, CH_HELP)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 $name طلب النجدة")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM))
            .setVibrate(longArrayOf(0, 800, 300, 800, 300, 800))
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
        val ph = guidePhone.filter { it.isDigit() || it == '+' }
        if (ph.isNotEmpty()) {
            val call = PendingIntent.getActivity(
                ctx, 1, Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:$ph")),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            b.addAction(android.R.drawable.ic_menu_call, "📞 اتصل بالمرافق", call)
        }
        val n = b.build()
        n.flags = n.flags or Notification.FLAG_INSISTENT   // يعاود الرنّة حتى يتفتح
        try {
            NotificationManagerCompat.from(ctx).notify(HELP_ID, n)
        } catch (e: SecurityException) {
        }
    }

    fun cancelHelperAlarm(ctx: Context) {
        try { NotificationManagerCompat.from(ctx).cancel(HELP_ID) } catch (e: Exception) {}
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
