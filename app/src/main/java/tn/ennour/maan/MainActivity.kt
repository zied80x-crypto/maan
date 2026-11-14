package tn.ennour.maan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

/** الشاشة الرئيسية للمعتمر: ثلاثة أزرار كبيرة. */
class MainActivity : AppCompatActivity() {
    private lateinit var tvName: TextView
    private lateinit var tvRoom: TextView
    private lateinit var tvHotel: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvBroadcast: TextView
    private lateinit var tvSos: TextView
    private lateinit var tvNetWarn: TextView
    private lateinit var spLandmark: Spinner
    private lateinit var spFloor: Spinner
    private lateinit var btnOk: MaterialButton
    private lateinit var btnSos: MaterialButton
    private lateinit var btnFamily: MaterialButton

    private val handler = Handler(Looper.getMainLooper())
    private var shownCity: String? = null
    private var ready = false
    private var lastRefresh = 0L

    private val makkah = listOf(
        "— اختر أقرب مكان لك —", "صحن المطاف", "المسعى", "باب الملك فهد", "باب الملك عبد العزيز",
        "باب العمرة", "باب الفتح", "التوسعة الشمالية", "الساحة الخارجية", "الفندق"
    )
    private val madinah = listOf(
        "— اختر أقرب مكان لك —", "داخل المسجد النبوي", "الروضة الشريفة", "باب السلام", "باب البقيع",
        "الساحة الشمالية", "الساحة الجنوبية", "الساحة الغربية", "مصلى النساء", "الفندق"
    )
    private val floors = listOf("— الطابق —", "الطابق الأرضي", "الطابق الأول", "الطابق الثاني", "السطح", "القبو")

    private var askedFromButton = false
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        startTracking()
        if (Loc.allowed(this)) requestBackgroundAndBattery()
        if (askedFromButton && !Loc.allowed(this)) {
            // رُفض الإذن نهائياً: نفتح إعدادات التطبيق
            AlertDialog.Builder(this)
                .setTitle("إذن الموقع مرفوض")
                .setMessage("افتح الإعدادات ← الأذونات ← الموقع ← واختر «السماح».")
                .setPositiveButton("فتح الإعدادات") { _, _ ->
                    try {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    } catch (e: Exception) {
                    }
                }
                .setNegativeButton("لاحقاً", null)
                .show()
        }
        askedFromButton = false
    }
    private val bgLocPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        startTracking(); promptBattery()
    }

    private val ticker = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (now - lastRefresh > 30 * 1000L) {
                lastRefresh = now
                Sync.refresh(this@MainActivity) { render() }
            }
            render()
            handler.postDelayed(this, 3000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Store.guideMode(this)) {
            startActivity(Intent(this, GuideActivity::class.java))
            finish()
            return
        }
        if (Store.token(this) == null) { goJoin(); return }

        setContentView(R.layout.activity_main)
        tvName = findViewById(R.id.tvName)
        tvRoom = findViewById(R.id.tvRoom)
        tvHotel = findViewById(R.id.tvHotel)
        tvStatus = findViewById(R.id.tvStatus)
        tvBroadcast = findViewById(R.id.tvBroadcast)
        tvSos = findViewById(R.id.tvSos)
        tvNetWarn = findViewById(R.id.tvNetWarn)
        spLandmark = findViewById(R.id.spLandmark)
        spFloor = findViewById(R.id.spFloor)
        btnOk = findViewById(R.id.btnOk)
        btnSos = findViewById(R.id.btnSos)

        btnOk.setOnClickListener { onOk() }
        btnSos.setOnClickListener { sendSos() }
        findViewById<MaterialButton>(R.id.btnCall).setOnClickListener { callGuide() }
        findViewById<MaterialButton>(R.id.btnWhatsapp).setOnClickListener { whatsappGuide() }
        findViewById<TextView>(R.id.tvLeave).setOnClickListener { confirmLeave() }
        btnFamily = findViewById(R.id.btnFamily)
        btnFamily.setOnClickListener { familyShare() }
        findViewById<MaterialButton>(R.id.btnHotel).setOnClickListener { showHotelCard() }
        tvStatus.setOnClickListener {
            when {
                !Loc.allowed(this) || !Loc.enabled(this) -> checkLocationReady()
                !isBatteryFree() -> openBatterySettings()
            }
        }

        spFloor.adapter = adapter(floors)
        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { onPlaceChanged() }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spLandmark.onItemSelectedListener = listener
        spFloor.onItemSelectedListener = listener

        ready = true
        askPermissions()
    }

    override fun onResume() {
        super.onResume()
        if (!ready) return
        if (Store.token(this) == null) { goJoin(); return }
        startTracking()
        lastRefresh = 0L
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private fun adapter(items: List<String>): ArrayAdapter<String> {
        val a = ArrayAdapter(this, R.layout.spinner_item, items)
        a.setDropDownViewResource(R.layout.spinner_item)
        return a
    }

    private fun goJoin() {
        stopService(Intent(this, LocationService::class.java))
        startActivity(Intent(this, JoinActivity::class.java))
        finish()
    }

    // ---------------- الأذونات والتتبع ----------------
    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun askPermissions() {
        val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = list.filter { !granted(it) }
        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
        else if (Loc.allowed(this)) requestBackgroundAndBattery()
    }

    // طلب «السماح طوال الوقت» ثم استثناء البطارية — أهم عاملين لعمل التتبع في الخلفية
    private fun requestBackgroundAndBattery() {
        if (Build.VERSION.SDK_INT >= 29 && !granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            AlertDialog.Builder(this)
                .setTitle("السماح بالموقع طوال الوقت")
                .setMessage("لكي يعرف المرافق مكانك والتطبيق في الخلفية، اختر في الشاشة التالية «السماح طوال الوقت».")
                .setPositiveButton("متابعة") { _, _ -> try { bgLocPermission.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) } catch (e: Exception) { promptBattery() } }
                .setNegativeButton("لاحقاً") { _, _ -> promptBattery() }
                .show()
        } else promptBattery()
    }

    private fun isBatteryFree(): Boolean {
        return try {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            pm.isIgnoringBatteryOptimizations(packageName)
        } catch (e: Exception) { true }
    }

    private var batteryPrompted = false
    private fun promptBattery() {
        if (batteryPrompted || isBatteryFree()) return
        batteryPrompted = true
        AlertDialog.Builder(this)
            .setTitle("مهم جداً: لا توقف التطبيق")
            .setMessage("حتى يبقى موقعك يصل للمرافق والشاشة مقفلة، اسمح لـ «في عيني» بالعمل بدون قيود على البطارية.")
            .setPositiveButton("السماح") { _, _ -> openBatterySettings() }
            .setNegativeButton("لاحقاً", null)
            .show()
    }

    private fun openBatterySettings() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) } catch (e2: Exception) {}
        }
    }

    private fun startTracking() {
        if (Store.token(this) == null || !Loc.allowed(this)) return
        try {
            ContextCompat.startForegroundService(this, Intent(this, LocationService::class.java))
        } catch (e: Exception) {
        }
    }

    private fun isOnline(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val n = cm.activeNetwork ?: return false
            val c = cm.getNetworkCapabilities(n) ?: return false
            c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) { true }
    }

    // ---------------- العرض ----------------
    private fun render() {
        if (isFinishing || !ready) return
        if (Store.token(this) == null) { goJoin(); return }

        tvName.text = Store.get(this, Store.NAME)
        val room = Store.get(this, Store.ROOM)
        tvRoom.text = if (room.isEmpty()) "" else "🛏️ غرفة $room"
        val hotel = Store.get(this, Store.HOTEL)
        val city = Store.get(this, Store.CITY)
        tvHotel.text = when {
            hotel.isEmpty() -> ""
            city.isEmpty() -> "🏨 $hotel"
            else -> "🏨 $hotel · $city"
        }
        tvHotel.visibility = if (hotel.isEmpty()) View.GONE else View.VISIBLE

        val last = Store.lastOk(this)
        val mins = (System.currentTimeMillis() - last) / 60000
        val gps = when {
            !Loc.allowed(this) -> " · ⚠️ إذن الموقع مرفوض — اضغط هنا"
            !Loc.enabled(this) -> " · ⚠️ شغّل الموقع (GPS) — اضغط هنا"
            !isBatteryFree() -> " · ⚠️ أوقف تقييد البطارية — اضغط هنا"
            else -> ""
        }
        tvStatus.text = when {
            last == 0L -> "لم يُرسل الموقع بعد$gps"
            mins < 1 -> "🟢 آخر تحديث: الآن$gps"
            else -> "آخر تحديث: منذ $mins دقيقة$gps"
        }

        val msg = Store.get(this, Store.MSG)
        tvBroadcast.text = "📢 رسالة من المرافق:\n$msg"
        tvBroadcast.visibility = if (msg.isEmpty()) View.GONE else View.VISIBLE

        btnFamily.text = if (Store.get(this, Store.FAMILY) == "1") "👪  عائلتي تتابعني ✓" else "👪  طمّن عائلتي"
        val sos = Store.isSos(this)
        if (!isOnline()) {
            tvNetWarn.text = if (sos)
                "⚠️ لا يوجد اتصال بالإنترنت\nالرجاء تشغيل الإنترنت والبقاء في مكانك"
            else
                "⚠️ لا يوجد اتصال بالإنترنت\nموقعك لا يصل للمرافق الآن"
            tvNetWarn.visibility = View.VISIBLE
        } else {
            tvNetWarn.visibility = View.GONE
        }
        tvSos.visibility = if (sos) View.VISIBLE else View.GONE
        btnSos.visibility = if (sos) View.GONE else View.VISIBLE
        btnOk.text = if (sos) "✅  أنا بخير الآن\nإلغاء النداء" else "✅  أنا بخير\nتحديث موقعي الآن"

        if (shownCity != city) {
            shownCity = city
            spLandmark.adapter = adapter(if (city.contains("المدينة")) madinah else makkah)
        }
    }

    // ---------------- المكان اليدوي ----------------
    private fun onPlaceChanged() {
        if (spLandmark.adapter == null) return
        val place = if (spLandmark.selectedItemPosition > 0) spLandmark.selectedItem.toString() else ""
        val floor = if (spFloor.selectedItemPosition > 0) spFloor.selectedItem.toString() else ""
        if (place.isEmpty()) return
        val value = if (floor.isEmpty()) place else "$place · $floor"
        if (value == Store.get(this, Store.LANDMARK)) return
        Store.set(this, Store.LANDMARK, value)
        Loc.last(this) { loc ->
            Sync.send(this, if (Store.sosPending(this)) "sos" else "update", loc) { ok, _ ->
                if (ok) toast("تم إرسال مكانك للمرافق")
                render()
            }
        }
    }

    // ---------------- الأزرار ----------------
    /** يتأكد أن الموقع مسموح ومشغّل، وإلا يطلب من المستخدم تفعيله. */
    private fun checkLocationReady() {
        if (!Loc.allowed(this)) {
            askedFromButton = true
            permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else if (!Loc.enabled(this)) {
            AlertDialog.Builder(this)
                .setTitle("الموقع (GPS) مغلق")
                .setMessage("شغّل الموقع في الهاتف حتى يعرف المرافق مكانك.")
                .setPositiveButton("تشغيل الموقع") { _, _ ->
                    try {
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    } catch (e: Exception) {
                    }
                }
                .setNegativeButton("لاحقاً", null)
                .show()
        }
    }

    private fun onOk() {
        btnOk.isEnabled = false
        checkLocationReady()
        toast("جاري تحديد موقعك…")
        startTracking()
        Loc.current(this, 12000) { loc ->
            Sync.send(this, "ok", loc) { ok, err ->
                btnOk.isEnabled = true
                toast(
                    when {
                        !ok -> err ?: "تعذّر الإرسال"
                        loc != null -> "✅ تم إرسال موقعك للمرافق"
                        else -> "تم الإرسال، لكن لم نجد موقعك. اختر مكانك من القائمة."
                    }
                )
                startTracking()
                render()
            }
        }
    }

    /** الاستغاثة: تُرسل فوراً بدون سؤال، ثم يُرسل الموقع الدقيق حالما يتوفر. */
    private fun sendSos() {
        Store.set(this, Store.STATUS, "sos")
        Store.setSosPending(this, true)
        render()
        checkLocationReady()
        startTracking()

        // 1) فوراً بآخر موقع معروف
        Loc.last(this) { quick ->
            Sync.send(this, "sos", quick) { ok, _ ->
                if (!ok) toast("لا يوجد إنترنت. سنعيد المحاولة تلقائياً. اتصل بالمرافق.")
                startTracking()
                render()
            }
            // 2) ثم موقع جديد دقيق
            Loc.current(this, 12000) { fresh ->
                if (fresh != null && Store.isSos(this)) {
                    Sync.send(this, "sos", fresh) { _, _ -> render() }
                }
            }
        }
    }

    // ---------------- رجّعني للفندق ----------------
    /** بطاقة الفندق: يريها المعتمر لسائق التاكسي، أو يفتح منها الطريق. */
    private fun showHotelCard() {
        val hotel = Store.get(this, Store.HOTEL)
        val city = Store.get(this, Store.CITY)
        val room = Store.get(this, Store.ROOM)
        val lat = Store.get(this, Store.HOTEL_LAT).toDoubleOrNull()
        val lng = Store.get(this, Store.HOTEL_LNG).toDoubleOrNull()
        val sb = StringBuilder()
        sb.append(if (hotel.isEmpty()) "اسم الفندق غير مسجّل" else hotel)
        if (city.isNotEmpty()) sb.append("\n").append(city)
        if (room.isNotEmpty()) sb.append("\nغرفة ").append(room)
        val gName = Store.get(this, Store.GUIDE_NAME)
        if (guidePhone().isNotEmpty()) sb.append("\n\nالمرافق: ").append(gName).append("\n").append(guidePhone())
        if (lat == null || lng == null) sb.append("\n\nالمرافق لم يحفظ موقع الفندق بعد. اتصل به أو أرِ هذه البطاقة لسائق التاكسي.")

        val tv = TextView(this)
        tv.text = sb.toString()
        tv.textSize = 24f
        tv.setTextColor(ContextCompat.getColor(this, R.color.ink))
        tv.gravity = android.view.Gravity.CENTER
        tv.setLineSpacing(8f, 1f)
        val pad = (20 * resources.displayMetrics.density).toInt()
        tv.setPadding(pad, pad, pad, pad / 2)

        val d = AlertDialog.Builder(this).setTitle("🏨 فندقي").setView(tv)
            .setNegativeButton("إغلاق", null)
            .setNeutralButton("📞 المرافق") { _, _ -> callGuide() }
        if (lat != null && lng != null) {
            d.setPositiveButton("🧭 الطريق للفندق") { _, _ -> openRoute(lat, lng) }
        }
        d.show()
    }

    private fun openRoute(lat: Double, lng: Double) {
        try {
            // خرائط Google: طريق للمشي
            val i = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng&mode=w"))
            i.setPackage("com.google.android.apps.maps")
            startActivity(i)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=walking")))
            } catch (e2: Exception) {
                toast("تعذّر فتح الخرائط")
            }
        }
    }

    // ---------------- طمّن عائلتي ----------------
    private fun familyShare() {
        if (Store.get(this, Store.FAMILY) == "1") {
            AlertDialog.Builder(this)
                .setTitle("👪 عائلتي تتابعني")
                .setItems(arrayOf("إرسال الرابط من جديد", "إيقاف المشاركة", "إلغاء")) { _, which ->
                    when (which) {
                        0 -> sendFamilyLink()
                        1 -> Sync.family(this, "family_stop") { _, err ->
                            toast(err ?: "تم إيقاف المشاركة. الرابط القديم لم يعد يعمل.")
                            render()
                        }
                    }
                }
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("👪 طمّن عائلتي")
            .setMessage("سنرسل لعائلتك رابطاً يرون منه: هل أنت بخير، آخر ظهور لك، المكان التقريبي واسم الفندق.\n\nلا يرون موقعك بالضبط، وتستطيع إيقاف المشاركة متى شئت.")
            .setPositiveButton("إرسال الرابط") { _, _ -> sendFamilyLink() }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun sendFamilyLink() {
        Sync.family(this, "family_link") { token, err ->
            render()
            if (token.isNullOrEmpty()) { toast(err ?: "تعذّر إنشاء الرابط"); return@family }
            val url = Config.PANEL_URL + "family.html?t=" + token
            val text = "السلام عليكم، تابعوا حالتي في العمرة من هذا الرابط:\n" + url
            try {
                val i = Intent(Intent.ACTION_SEND)
                i.type = "text/plain"
                i.putExtra(Intent.EXTRA_TEXT, text)
                startActivity(Intent.createChooser(i, "أرسل الرابط لعائلتك"))
            } catch (e: Exception) {
                toast("تعذّر فتح المشاركة")
            }
        }
    }

    private fun guidePhone(): String = Store.get(this, Store.GUIDE_PHONE).filter { it.isDigit() || it == '+' }

    private fun callGuide() {
        val phone = guidePhone()
        if (phone.isEmpty()) { toast("رقم المرافق غير مسجّل"); return }
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
        } catch (e: Exception) {
            toast("تعذّر فتح الاتصال")
        }
    }

    private fun whatsappGuide() {
        val phone = guidePhone().replace("+", "")
        if (phone.isEmpty()) { toast("رقم المرافق غير مسجّل"); return }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone")))
        } catch (e: Exception) {
            toast("تعذّر فتح واتساب")
        }
    }

    private fun confirmLeave() {
        AlertDialog.Builder(this)
            .setTitle("الخروج من الرحلة؟")
            .setMessage("سيتوقف إرسال موقعك للمرافق.")
            .setPositiveButton("خروج") { _, _ ->
                Store.clear(this)
                goJoin()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
