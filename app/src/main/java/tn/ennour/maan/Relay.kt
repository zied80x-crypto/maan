package tn.ennour.maan

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * الترحيل عبر البلوتوث (BLE):
 *  - معتمر بلا إنترنت يبثّ موقعه (رسالة 24 بايت مشفّرة وموقّعة بمفتاحه الخاص).
 *  - معتمر قريب عندو إنترنت يلتقطها ويرسلها للخادم (action = relay)، والخادم هو اللي يفكّها ويتحقق منها.
 * الرسالة: [0]=0xA7 | [1..6]=معرّف متغيّر كل ساعة | [7..10]=الوقت (ثواني) | [11..19]=موقع+بطارية مشفّرين | [20..23]=توقيع.
 */
object Relay {
    private const val COMPANY_ID = 0xFFFF
    private const val MAGIC: Byte = 0xA7.toByte()
    private const val ADV_MAX_AGE_MS = 12 * 3600 * 1000L

    private val main = Handler(Looper.getMainLooper())
    private var advCb: AdvertiseCallback? = null
    private var advertising = false
    private var scanCb: ScanCallback? = null
    private var scanning = false
    private var lastScan = 0L
    private val found = LinkedHashMap<String, Pair<String, Long>>()   // مفتاح الرسالة → (base64، وقت الالتقاط)

    private fun adapter(ctx: Context): BluetoothAdapter? =
        try { (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter } catch (e: Exception) { null }

    fun hasPerms(ctx: Context): Boolean {
        if (!ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) return false
        if (Build.VERSION.SDK_INT >= 31) {
            return ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        }
        return true   // أندرويد 11 وأقل: أذونات البلوتوث عادية، والموقع مفعّل أصلاً
    }

    private fun on(ctx: Context): BluetoothAdapter? {
        val a = adapter(ctx) ?: return null
        return if (a.isEnabled && hasPerms(ctx)) a else null
    }

    private fun hmac(key: ByteArray, msg: ByteArray): ByteArray {
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec(key, "HmacSHA256"))
        return m.doFinal(msg)
    }

    private fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun u32(v: Long): ByteArray = ByteBuffer.allocate(4).putInt(v.toInt()).array()

    /** يبني رسالة 24 بايت من الموقع الحالي. */
    fun encode(keyHex: String, lat: Double, lng: Double, battery: Int, sos: Boolean, tsSec: Long): ByteArray {
        val key = hex(keyHex)
        val tsb = u32(tsSec)
        val rid = hmac(key, byteArrayOf('i'.code.toByte()) + u32(tsSec / 3600)).copyOfRange(0, 6)
        val fb = ((if (sos) 0x80 else 0) or battery.coerceIn(0, 100)).toByte()
        val pl = ByteBuffer.allocate(9)
            .putInt(Math.round(lat * 1e6).toInt()).putInt(Math.round(lng * 1e6).toInt()).put(fb).array()
        val ks = hmac(key, byteArrayOf('k'.code.toByte()) + tsb).copyOfRange(0, 9)
        val ct = ByteArray(9) { (pl[it].toInt() xor ks[it].toInt()).toByte() }
        val head = byteArrayOf(MAGIC) + rid + tsb + ct
        val mac = hmac(key, byteArrayOf('m'.code.toByte()) + head).copyOfRange(0, 4)
        return head + mac
    }

    // ---------------- الإرسال (المعتمر بلا إنترنت) ----------------

    /** يبدأ/يحدّث البثّ بآخر موقع. */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun advertise(ctx: Context, loc: Location?, battery: Int?, sos: Boolean) {
        val key = Store.get(ctx, "relay_key")
        if (key.length != 32 || loc == null) return
        val a = on(ctx) ?: return
        val adv = try { a.bluetoothLeAdvertiser } catch (e: Exception) { null } ?: return
        val data = encode(key, loc.latitude, loc.longitude, battery ?: 0, sos, System.currentTimeMillis() / 1000)
        stopAdvertise(ctx)
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()
        val ad = AdvertiseData.Builder().setIncludeDeviceName(false).setIncludeTxPowerLevel(false)
            .addManufacturerData(COMPANY_ID, data).build()
        val cb = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) { advertising = true }
            override fun onStartFailure(errorCode: Int) { advertising = false }
        }
        advCb = cb
        try { adv.startAdvertising(settings, ad, cb) } catch (e: Exception) { advCb = null }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun stopAdvertise(ctx: Context) {
        val cb = advCb ?: return
        advCb = null
        advertising = false
        try { adapter(ctx)?.bluetoothLeAdvertiser?.stopAdvertising(cb) } catch (e: Exception) { }
    }

    // ---------------- الاستقبال (المعتمر عندو إنترنت) ----------------

    /** يسمع 8 ثواني كل ~30 ثانية، ثم يرسل ما التقطه للخادم. */
    @SuppressLint("MissingPermission")
    fun scanOnce(ctx: Context) {
        if (scanning) return
        val now = System.currentTimeMillis()
        if (now - lastScan < 30_000L) return
        if (Store.get(ctx, "relay_key").length != 32) return
        val a = on(ctx) ?: return
        val sc = try { a.bluetoothLeScanner } catch (e: Exception) { null } ?: return
        lastScan = now
        val filter = ScanFilter.Builder().setManufacturerData(COMPANY_ID, byteArrayOf(MAGIC), byteArrayOf(0xFF.toByte())).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
        val app = ctx.applicationContext
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val raw = result.scanRecord?.getManufacturerSpecificData(COMPANY_ID) ?: return
                if (raw.size != 24 || raw[0] != MAGIC) return
                val b64 = Base64.encodeToString(raw, Base64.NO_WRAP)
                synchronized(found) {
                    // نفس المعرّف + نفس الوقت = نفس الرسالة
                    found[Base64.encodeToString(raw.copyOfRange(1, 11), Base64.NO_WRAP)] = b64 to System.currentTimeMillis()
                    while (found.size > 30) found.remove(found.keys.first())
                }
            }
        }
        scanCb = cb
        scanning = true
        try { sc.startScan(listOf(filter), settings, cb) } catch (e: Exception) { scanning = false; scanCb = null; return }
        main.postDelayed({
            try { sc.stopScan(cb) } catch (e: Exception) { }
            scanning = false
            scanCb = null
            flush(app)
        }, 8_000L)
    }

    /** يرسل الرسائل الملتقطة للخادم. إذا فشل (ما عندوش إنترنت) تبقى في الانتظار. */
    fun flush(ctx: Context) {
        val items: List<String>
        synchronized(found) {
            val now = System.currentTimeMillis()
            found.entries.removeAll { now - it.value.second > ADV_MAX_AGE_MS }
            items = found.values.map { it.first }
        }
        if (items.isEmpty()) return
        Sync.relay(ctx, items) { ok ->
            if (ok) synchronized(found) { found.clear() }
        }
    }
}
