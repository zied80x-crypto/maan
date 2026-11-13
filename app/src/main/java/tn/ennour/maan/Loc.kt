package tn.ennour.maan

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

object Loc {
    fun allowed(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** هل خدمة الموقع (GPS) مشغّلة في الهاتف؟ */
    fun enabled(ctx: Context): Boolean {
        return try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            LocationManagerCompat.isLocationEnabled(lm)
        } catch (e: Exception) {
            true
        }
    }

    /** آخر موقع يعرفه نظام أندرويد نفسه (احتياط إذا لم تُرجع خدمات Google شيئاً). */
    @SuppressLint("MissingPermission")
    private fun systemLast(ctx: Context): Location? {
        var best: Location? = null
        try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            for (provider in lm.getProviders(true)) {
                val l = try { lm.getLastKnownLocation(provider) } catch (e: Exception) { null }
                val b = best
                if (l != null && (b == null || l.time > b.time)) best = l
            }
        } catch (e: Exception) {
        }
        return best
    }

    /** آخر موقع معروف (سريع). */
    @SuppressLint("MissingPermission")
    fun last(ctx: Context, cb: (Location?) -> Unit) {
        if (!allowed(ctx)) { cb(null); return }
        try {
            LocationServices.getFusedLocationProviderClient(ctx.applicationContext).lastLocation
                .addOnSuccessListener { l: Location? -> cb(l ?: systemLast(ctx)) }
                .addOnFailureListener { cb(systemLast(ctx)) }
        } catch (e: Exception) {
            cb(systemLast(ctx))
        }
    }

    /** موقع جديد دقيق، وإذا تأخر نرجع لآخر موقع معروف. */
    @SuppressLint("MissingPermission")
    fun current(ctx: Context, timeoutMs: Long, cb: (Location?) -> Unit) {
        if (!allowed(ctx)) { cb(null); return }
        var done = false
        fun finish(l: Location?) {
            if (!done) { done = true; cb(l) }
        }
        Handler(Looper.getMainLooper()).postDelayed({
            if (!done) last(ctx) { l -> finish(l) }
        }, timeoutMs + 1500)
        try {
            val req = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setDurationMillis(timeoutMs)
                .setMaxUpdateAgeMillis(20000)
                .build()
            LocationServices.getFusedLocationProviderClient(ctx.applicationContext)
                .getCurrentLocation(req, CancellationTokenSource().token)
                .addOnSuccessListener { l: Location? ->
                    if (l != null) finish(l) else last(ctx) { x -> finish(x) }
                }
                .addOnFailureListener { last(ctx) { x -> finish(x) } }
        } catch (e: Exception) {
            last(ctx) { x -> finish(x) }
        }
    }
}
