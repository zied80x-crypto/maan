package tn.ennour.maan

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/** خدمة تعمل في الخلفية وترسل الموقع للمرافق حتى والشاشة مقفلة. */
class LocationService : Service() {
    private lateinit var fused: FusedLocationProviderClient
    private val handler = Handler(Looper.getMainLooper())
    private var lastLoc: Location? = null
    private var mode = ""
    private var lastMe = 0L

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val l = result.lastLocation
            if (l != null) lastLoc = l
            maybeSend()
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            maybeSend()
            handler.postDelayed(this, interval())
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Store.token(this) == null || !Loc.allowed(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(
                this, Notifier.TRACK_ID, Notifier.tracking(this),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        configure(true)
        return START_STICKY
    }

    private fun interval(): Long =
        if (Store.isSos(this)) Config.SOS_INTERVAL_MS else Config.NORMAL_INTERVAL_MS

    /** يضبط سرعة التحديث حسب الحالة (عادي أو استغاثة). */
    @SuppressLint("MissingPermission")
    private fun configure(force: Boolean) {
        val m = if (Store.isSos(this)) "sos" else "ok"
        if (!force && m == mode) return
        mode = m
        try {
            fused.removeLocationUpdates(callback)
            val priority = if (m == "sos") Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            val req = LocationRequest.Builder(priority, interval())
                .setMinUpdateIntervalMillis(interval() / 2)
                .build()
            fused.requestLocationUpdates(req, callback, Looper.getMainLooper())
        } catch (e: Exception) {
        }
        try {
            ServiceCompat.startForeground(
                this, Notifier.TRACK_ID, Notifier.tracking(this),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } catch (e: Exception) {
        }
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, interval())
    }

    private fun maybeSend() {
        if (Store.token(this) == null) {
            stopSelf()
            return
        }
        val now = System.currentTimeMillis()
        if (now - Store.lastSent(this) < interval() * 8 / 10) return
        Store.setLastSent(this, now)   // يمنع الإرسال المكرر في نفس اللحظة

        val l = lastLoc
        val fresh = if (l != null && now - l.time < 5 * 60 * 1000L) l else null
        val action = if (Store.sosPending(this)) "sos" else "update"
        Sync.send(this, action, fresh) { _, _ ->
            if (Store.token(this) == null) stopSelf() else configure(false)
        }
        if (now - lastMe > 60 * 1000L) {
            lastMe = now
            Sync.refresh(this)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        try {
            fused.removeLocationUpdates(callback)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }
}
