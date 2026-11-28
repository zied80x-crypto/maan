package tn.ennour.maan

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.PI
import kotlin.math.roundToInt

/** «تهت؟»: سهم بوصلة يدلّك على الفندق بدون إنترنت (يستعمل GPS والبوصلة فقط). */
class LostActivity : Activity(), SensorEventListener {
    private lateinit var arrow: ArrowView
    private lateinit var info: TextView
    private lateinit var sub: TextView
    private var sm: SensorManager? = null
    private var azimuth = 0f              // اتجاه الهاتف (درجات من الشمال الحقيقي)
    private var haveAzimuth = false
    private var loc: Location? = null
    private var target: Location? = null
    private val rot = FloatArray(9)
    private val ori = FloatArray(3)
    private val cb = object : LocationCallback() {
        override fun onLocationResult(r: LocationResult) { r.lastLocation?.let { loc = it; refresh() } }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val lat = Store.get(this, Store.HOTEL_LAT).toDoubleOrNull()
        val lng = Store.get(this, Store.HOTEL_LNG).toDoubleOrNull()
        if (lat != null && lng != null) target = Location("hotel").apply { latitude = lat; longitude = lng }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#F6F3EA"))
            setPadding(dp(16), dp(24), dp(16), dp(16))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val hotel = Store.get(this, Store.HOTEL)
        root.addView(TextView(this).apply {
            text = "🏨 " + (if (hotel.isEmpty()) "الفندق" else hotel)
            textSize = 26f; setTextColor(Color.parseColor("#0B2A23")); gravity = Gravity.CENTER
        })
        arrow = ArrowView(this)
        root.addView(arrow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        info = TextView(this).apply { textSize = 34f; setTextColor(Color.parseColor("#0B2A23")); gravity = Gravity.CENTER; text = "…" }
        sub = TextView(this).apply { textSize = 17f; setTextColor(Color.parseColor("#555555")); gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(12)) }
        root.addView(info); root.addView(sub)

        fun btn(t: String, color: String, click: () -> Unit) = Button(this).apply {
            text = t; textSize = 20f; setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor(color)); setOnClickListener { click() }
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val lp = { LinearLayout.LayoutParams(0, dp(64), 1f).apply { setMargins(dp(4), 0, dp(4), 0) } }
        row.addView(btn("📞 المرافق", "#1A7F4B") {
            val ph = Store.get(this, Store.GUIDE_PHONE)
            if (ph.isNotEmpty()) startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + ph.filter { it.isDigit() || it == '+' })))
        }, lp())
        row.addView(btn("🚨 نجدة", "#C62828") { Sos.trigger(this); finish() }, lp())
        root.addView(row)
        setContentView(root)

        if (target == null) {
            info.text = "موقع الفندق غير مسجّل"
            sub.text = "اتصل بالمرافق، أو أرِ بطاقة الفندق لسائق التاكسي."
        }
    }

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()
        val m = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sm = m
        val rv = m?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rv != null) m.registerListener(this, rv, SensorManager.SENSOR_DELAY_UI)
        else sub.text = "هذا الهاتف ما عندوش بوصلة"
        if (Loc.allowed(this)) {
            Loc.last(this) { l -> if (l != null && loc == null) { loc = l; refresh() } }
            try {
                val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L).setMinUpdateIntervalMillis(1000L).build()
                LocationServices.getFusedLocationProviderClient(this).requestLocationUpdates(req, cb, mainLooper)
            } catch (e: Exception) { }
        } else sub.text = "فعّل إذن الموقع للتطبيق"
    }

    override fun onPause() {
        super.onPause()
        sm?.unregisterListener(this)
        try { LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(cb) } catch (e: Exception) { }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}

    override fun onSensorChanged(e: SensorEvent) {
        android.hardware.SensorManager.getRotationMatrixFromVector(rot, e.values)
        // نستعمل المحور الخلفي للهاتف (مسطّح في اليد): الاتجاه نحو أعلى الشاشة
        android.hardware.SensorManager.getOrientation(rot, ori)
        var az = Math.toDegrees(ori[0].toDouble()).toFloat()
        val l = loc
        if (l != null) {
            val decl = GeomagneticField(l.latitude.toFloat(), l.longitude.toFloat(), l.altitude.toFloat(), System.currentTimeMillis()).declination
            az += decl
        }
        if (az < 0) az += 360f
        // تنعيم بسيط
        azimuth = if (!haveAzimuth) az else {
            var d = az - azimuth
            while (d > 180) d -= 360; while (d < -180) d += 360
            (azimuth + d * 0.25f + 360f) % 360f
        }
        haveAzimuth = true
        refresh()
    }

    private fun refresh() {
        val t = target ?: return
        val l = loc
        if (l == null) { info.text = "نحوّس على موقعك…"; sub.text = "اخرج لمكان مفتوح باش يلقى الـGPS"; return }
        val dist = l.distanceTo(t)
        val bearing = l.bearingTo(t).let { if (it < 0) it + 360 else it }
        arrow.angle = bearing - azimuth
        arrow.visibility = View.VISIBLE
        arrow.invalidate()
        info.text = if (dist >= 1000) String.format("%.1f كم", dist / 1000) else "${(dist / 10).roundToInt() * 10} متر"
        sub.text = when {
            dist < 40 -> "وصلت تقريباً 🎉"
            !haveAzimuth -> "حرّك التليفون على شكل 8 باش تتظبط البوصلة"
            else -> "امشِ في اتجاه السهم (سهم مسطّح: التليفون أفقي)"
        }
    }

    private class ArrowView(c: Context) : View(c) {
        var angle = 0f
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C9A75E"); style = Paint.Style.FILL }
        private val o = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0B2A23"); style = Paint.Style.STROKE; strokeWidth = 10f }
        override fun onDraw(cv: Canvas) {
            val cx = width / 2f; val cy = height / 2f
            val r = minOf(width, height) * 0.42f
            cv.save()
            cv.rotate(angle, cx, cy)
            val path = Path().apply {
                moveTo(cx, cy - r)
                lineTo(cx + r * 0.55f, cy + r * 0.55f)
                lineTo(cx, cy + r * 0.25f)
                lineTo(cx - r * 0.55f, cy + r * 0.55f)
                close()
            }
            cv.drawPath(path, p); cv.drawPath(path, o)
            cv.restore()
        }
    }
}
