package tn.ennour.maan

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

/** نداء النجدة: نفس الشي من الزرّ الأحمر ولا من كشف السقوط. */
object Sos {
    fun trigger(ctx: Context, done: ((Boolean) -> Unit)? = null) {
        val app = ctx.applicationContext
        Store.set(app, Store.STATUS, "sos")
        Store.setSosPending(app, true)
        Tracker.start(app)
        Loc.last(app) { quick ->
            Sync.send(app, "sos", quick) { ok, _ -> done?.invoke(ok) }
            Loc.current(app, 12000) { fresh ->
                if (fresh != null && Store.isSos(app)) Sync.send(app, "sos", fresh)
            }
        }
    }
}

/**
 * كشف السقوط: طيحة حرّة قصيرة ومبعدها ضربة قوية.
 * يطلع إشعار بالصوت «لاباس عليك؟»، وإذا ما ضغطش «أنا بخير» في 30 ثانية، نبعثو نداء النجدة وحدو.
 */
object Fall : SensorEventListener {
    private const val CH = "fall_alarm"
    const val NOTIF_ID = 3
    const val WAIT_MS = 30_000L
    private const val FREE_FALL = 3.0f      // م/ث² (أقل من ثلث الجاذبية)
    private const val IMPACT = 27.0f        // م/ث² (قرابة 2.8 مرة الجاذبية)
    private var sm: SensorManager? = null
    private var app: Context? = null
    private var freeFallAt = 0L
    private var lastAlert = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val fire = Runnable {
        val c = app ?: return@Runnable
        if (Store.get(c, "fall_pending") == "1" && !Store.isSos(c)) {
            Store.set(c, "fall_pending", "")
            cancelNotice(c)
            Sos.trigger(c)
            Notifier.message(c, "🚨 تبعث نداء النجدة", "ما جاوبتش بعد الطيحة، المرافق وصلو نداء وهو في الطريق إليك.")
        }
    }

    fun start(ctx: Context) {
        if (sm != null) return
        app = ctx.applicationContext
        val m = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val acc = m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sm = m
        m.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sm?.unregisterListener(this)
        sm = null
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(e: SensorEvent) {
        val c = app ?: return
        val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
        val now = System.currentTimeMillis()
        if (g < FREE_FALL) { if (freeFallAt == 0L || now - freeFallAt > 1500) freeFallAt = now; return }
        if (g > IMPACT && freeFallAt != 0L && now - freeFallAt in 80L..1000L) {
            freeFallAt = 0L
            if (now - lastAlert < 60_000 || Store.isSos(c) || Store.token(c) == null) return
            lastAlert = now
            ask(c)
        }
    }

    /** «لاباس عليك؟» مع عدّ 30 ثانية. */
    private fun ask(c: Context) {
        Store.set(c, "fall_pending", "1")
        handler.removeCallbacks(fire)
        handler.postDelayed(fire, WAIT_MS)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val alarm = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            nm.createNotificationChannel(NotificationChannel(CH, "كشف السقوط", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(alarm, android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).build())
                enableVibration(true); vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
        }
        val ok = PendingIntent.getBroadcast(c, 11, Intent(c, FallReceiver::class.java).setAction("ok"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val help = PendingIntent.getBroadcast(c, 12, Intent(c, FallReceiver::class.java).setAction("help"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(c, 13, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(c, CH)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("لاباس عليك؟ 🤍")
            .setContentText("حسّينا بطيحة. اضغط «أنا بخير»، وإلا بعد 30 ثانية نبعثو نداء النجدة للمرافق.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("حسّينا بطيحة. اضغط «أنا بخير»، وإلا بعد 30 ثانية نبعثو نداء النجدة للمرافق."))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM))
            .setVibrate(longArrayOf(0, 600, 300, 600, 300, 600))
            .setTimeoutAfter(WAIT_MS + 2000)
            .setContentIntent(open)
            .addAction(android.R.drawable.checkbox_on_background, "✅ أنا بخير", ok)
            .addAction(android.R.drawable.ic_dialog_alert, "🚨 اطلب النجدة", help)
            .setUsesChronometer(true).setChronometerCountDown(true).setWhen(System.currentTimeMillis() + WAIT_MS)
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        try { NotificationManagerCompat.from(c).notify(NOTIF_ID, n) } catch (e: SecurityException) {}
    }

    fun answer(c: Context, help: Boolean) {
        handler.removeCallbacks(fire)
        Store.set(c, "fall_pending", "")
        cancelNotice(c)
        if (help && !Store.isSos(c)) Sos.trigger(c)
    }

    private fun cancelNotice(c: Context) {
        try { NotificationManagerCompat.from(c).cancel(NOTIF_ID) } catch (e: Exception) {}
    }
}

class FallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Fall.answer(context, intent.action == "help")
    }
}
