package tn.ennour.maan

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

object Loc {
    fun allowed(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** آخر موقع معروف (سريع). */
    @SuppressLint("MissingPermission")
    fun last(ctx: Context, cb: (Location?) -> Unit) {
        if (!allowed(ctx)) { cb(null); return }
        try {
            LocationServices.getFusedLocationProviderClient(ctx.applicationContext).lastLocation
                .addOnSuccessListener { l: Location? -> cb(l) }
                .addOnFailureListener { cb(null) }
        } catch (e: Exception) {
            cb(null)
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
        }, timeoutMs)
        try {
            LocationServices.getFusedLocationProviderClient(ctx.applicationContext)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { l: Location? ->
                    if (l != null) finish(l) else last(ctx) { x -> finish(x) }
                }
                .addOnFailureListener { last(ctx) { x -> finish(x) } }
        } catch (e: Exception) {
            finish(null)
        }
    }
}
