package tn.ennour.maan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
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
    private lateinit var spLandmark: Spinner
    private lateinit var spFloor: Spinner
    private lateinit var btnOk: MaterialButton
    private lateinit var btnSos: MaterialButton

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

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        startTracking()
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
        if (Store.token(this) == null) { goJoin(); return }

        setContentView(R.layout.activity_main)
        tvName = findViewById(R.id.tvName)
        tvRoom = findViewById(R.id.tvRoom)
        tvHotel = findViewById(R.id.tvHotel)
        tvStatus = findViewById(R.id.tvStatus)
        tvBroadcast = findViewById(R.id.tvBroadcast)
        tvSos = findViewById(R.id.tvSos)
        spLandmark = findViewById(R.id.spLandmark)
        spFloor = findViewById(R.id.spFloor)
        btnOk = findViewById(R.id.btnOk)
        btnSos = findViewById(R.id.btnSos)

        btnOk.setOnClickListener { onOk() }
        btnSos.setOnClickListener { confirmSos() }
        findViewById<MaterialButton>(R.id.btnCall).setOnClickListener { callGuide() }
        findViewById<MaterialButton>(R.id.btnWhatsapp).setOnClickListener { whatsappGuide() }
        findViewById<TextView>(R.id.tvLeave).setOnClickListener { confirmLeave() }

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
        if (Config.SEND_SOS_SMS) list.add(Manifest.permission.SEND_SMS)
        val missing = list.filter { !granted(it) }
        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
    }

    private fun startTracking() {
        if (Store.token(this) == null || !Loc.allowed(this)) return
        try {
            ContextCompat.startForegroundService(this, Intent(this, LocationService::class.java))
        } catch (e: Exception) {
        }
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

        val last = Store.lastSent(this)
        val mins = (System.currentTimeMillis() - last) / 60000
        val gps = if (Loc.allowed(this)) "" else " · ⚠️ الموقع غير مفعّل"
        tvStatus.text = when {
            last == 0L -> "لم يُرسل الموقع بعد$gps"
            mins < 1 -> "🟢 آخر تحديث: الآن$gps"
            else -> "آخر تحديث: منذ $mins دقيقة$gps"
        }

        val msg = Store.get(this, Store.MSG)
        tvBroadcast.text = "📢 رسالة من المرافق:\n$msg"
        tvBroadcast.visibility = if (msg.isEmpty()) View.GONE else View.VISIBLE

        val sos = Store.isSos(this)
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
    private fun onOk() {
        btnOk.isEnabled = false
        toast("جاري تحديد موقعك…")
        Loc.current(this, 8000) { loc ->
            Sync.send(this, "ok", loc) { ok, err ->
                btnOk.isEnabled = true
                toast(if (ok) "✅ تم إرسال موقعك للمرافق" else (err ?: "تعذّر الإرسال"))
                startTracking()
                render()
            }
        }
    }

    private fun confirmSos() {
        AlertDialog.Builder(this)
            .setTitle("هل تحتاج مساعدة؟")
            .setMessage("سيصل نداؤك وموقعك إلى المرافق فوراً.")
            .setPositiveButton("نعم، أحتاج مساعدة") { _, _ -> sendSos() }
            .setNegativeButton("لا", null)
            .show()
    }

    private fun sendSos() {
        Store.set(this, Store.STATUS, "sos")
        Store.setSosPending(this, true)
        render()
        startTracking()
        Loc.last(this) { loc ->
            sendSms(loc)
            Sync.send(this, "sos", loc) { ok, _ ->
                if (!ok) toast("لا يوجد إنترنت. سنعيد المحاولة تلقائياً. اتصل بالمرافق.")
                startTracking()
                render()
            }
        }
    }

    /** رسالة SMS احتياطية للمرافق، تصل حتى بدون إنترنت. */
    private fun sendSms(loc: Location?) {
        if (!Config.SEND_SOS_SMS || !granted(Manifest.permission.SEND_SMS)) return
        val phone = Store.get(this, Store.GUIDE_PHONE).filter { it.isDigit() || it == '+' }
        if (phone.isEmpty()) return
        try {
            val sb = StringBuilder("SOS معاً: ")
            sb.append(Store.get(this, Store.NAME))
            val room = Store.get(this, Store.ROOM)
            if (room.isNotEmpty()) sb.append(" غرفة ").append(room)
            val lm = Store.get(this, Store.LANDMARK)
            if (lm.isNotEmpty()) sb.append(" - ").append(lm)
            if (loc != null) sb.append(" https://maps.google.com/?q=").append(loc.latitude).append(",").append(loc.longitude)
            @Suppress("DEPRECATION")
            val sms: SmsManager = if (Build.VERSION.SDK_INT >= 31) getSystemService(SmsManager::class.java) else SmsManager.getDefault()
            sms.sendMultipartTextMessage(phone, null, sms.divideMessage(sb.toString()), null, null)
        } catch (e: Exception) {
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
