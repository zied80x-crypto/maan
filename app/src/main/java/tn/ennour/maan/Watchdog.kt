package tn.ennour.maan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * حارس التتبع: كي أندرويد يوقف خدمة الموقع (تسكير التطبيق، توفير البطارية، إعادة التشغيل)
 * يعاود يشعلها، ويبعث الموقع كل 15 دقيقة على الأقل حتى والخدمة موقوفة.
 */
object Tracker {
    private const val PERIODIC = "maan_watchdog"
    private const val KICK = "maan_kick"

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        if (Store.token(app) == null || !Loc.allowed(app)) return
        try {
            ContextCompat.startForegroundService(app, Intent(app, LocationService::class.java))
        } catch (e: Exception) {
        }
    }

    fun schedule(ctx: Context) {
        val app = ctx.applicationContext
        if (Store.token(app) == null) return
        try {
            WorkManager.getInstance(app).enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build()
            )
        } catch (e: Exception) {
        }
    }

    /** يعاود يشعل الخدمة بعد ثواني (مثلاً كي المعتمر يسكّر التطبيق من قائمة التطبيقات). */
    fun kickSoon(ctx: Context) {
        val app = ctx.applicationContext
        if (Store.token(app) == null) return
        try {
            WorkManager.getInstance(app).enqueueUniqueWork(
                KICK, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WatchdogWorker>().setInitialDelay(5, TimeUnit.SECONDS).build()
            )
        } catch (e: Exception) {
        }
    }

    fun cancel(ctx: Context) {
        try {
            val wm = WorkManager.getInstance(ctx.applicationContext)
            wm.cancelUniqueWork(PERIODIC); wm.cancelUniqueWork(KICK)
        } catch (e: Exception) {
        }
    }
}

class WatchdogWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val app = applicationContext
        if (Store.token(app) == null) { Tracker.cancel(app); return Result.success() }
        Tracker.start(app)
        // نبضة: نبعث آخر موقع معروف حتى لو الخدمة ما خدمتش (المرافق ما يشوفوش «غير متصل»)
        val now = System.currentTimeMillis()
        if (now - Store.lastOk(app) > 4 * 60 * 1000L && Loc.allowed(app)) {
            val latch = CountDownLatch(1)
            Loc.last(app) { l ->
                val fresh = if (l != null && now - l.time < 30 * 60 * 1000L) l else null
                val action = if (Store.sosPending(app)) "sos" else "update"
                Sync.send(app, action, fresh) { _, _ -> latch.countDown() }
            }
            try { latch.await(25, TimeUnit.SECONDS) } catch (e: Exception) {}
        }
        return Result.success()
    }
}

/** بعد إعادة تشغيل التليفون ولا تحديث التطبيق: نرجّعو التتبع وحدو. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Tracker.start(context)
        Tracker.schedule(context)
    }
}
