package tn.ennour.maan

import android.content.Context
import android.content.SharedPreferences

/** تخزين بيانات المعتمر على الهاتف. */
object Store {
    const val TOKEN = "token"
    const val NAME = "name"
    const val ROOM = "room"
    const val TRIP = "trip"
    const val HOTEL = "hotel"
    const val CITY = "city"
    const val GUIDE_NAME = "guide_name"
    const val GUIDE_PHONE = "guide_phone"
    const val STATUS = "status"            // ok | sos
    const val LANDMARK = "landmark"
    const val MSG = "msg"
    const val HOTEL_LAT = "hotel_lat"
    const val HOTEL_LNG = "hotel_lng"
    const val FAMILY = "family"          // "1" إذا كان المعتمر يشارك حالته مع عائلته

    private fun p(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences("maan", Context.MODE_PRIVATE)

    fun get(ctx: Context, key: String): String = p(ctx).getString(key, "") ?: ""
    fun set(ctx: Context, key: String, value: String) { p(ctx).edit().putString(key, value).apply() }

    fun token(ctx: Context): String? = get(ctx, TOKEN).ifEmpty { null }
    fun isSos(ctx: Context): Boolean = get(ctx, STATUS) == "sos"

    fun sosPending(ctx: Context): Boolean = p(ctx).getBoolean("sos_pending", false)
    fun setSosPending(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("sos_pending", v).apply() }

    fun lastSent(ctx: Context): Long = p(ctx).getLong("last_sent", 0L)
    fun setLastSent(ctx: Context, v: Long) { p(ctx).edit().putLong("last_sent", v).apply() }

    fun lastOk(ctx: Context): Long = p(ctx).getLong("last_ok", 0L)
    fun setLastOk(ctx: Context, v: Long) { p(ctx).edit().putLong("last_ok", v).apply() }

    fun lastMsgId(ctx: Context): Long = p(ctx).getLong("last_msg_id", -1L)
    fun setLastMsgId(ctx: Context, v: Long) { p(ctx).edit().putLong("last_msg_id", v).apply() }

    // وضع المرافق: التطبيق يفتح لوحة المرافق مباشرة
    fun guideMode(ctx: Context): Boolean = p(ctx).getBoolean("guide_mode", false)
    fun setGuideMode(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("guide_mode", v).apply() }

    // مساعد المرافق: يصله إنذار كل نداء نجدة في الرحلة
    fun isHelper(ctx: Context): Boolean = p(ctx).getBoolean("helper", false)
    fun setHelper(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("helper", v).apply() }
    fun helperAlerted(ctx: Context): Set<String> = p(ctx).getStringSet("helper_alerted", emptySet()) ?: emptySet()
    fun setHelperAlerted(ctx: Context, v: Set<String>) { p(ctx).edit().putStringSet("helper_alerted", HashSet(v)).apply() }

    // «المشي مع المجموعة» مشعول عند المرافق
    fun isWalk(ctx: Context): Boolean = p(ctx).getBoolean("walk", false)
    fun setWalk(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("walk", v).apply() }

    // آخر محاولة إرسال فشلت لأن ما فماش إنترنت
    fun netFail(ctx: Context): Boolean = p(ctx).getBoolean("net_fail", false)
    fun setNetFail(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("net_fail", v).apply() }

    // نقاط الموقع اللي ما تبعثتش بسبب انقطاع الإنترنت: [[lat,lng,tSec],...]
    @Synchronized
    fun addTrail(ctx: Context, lat: Double, lng: Double, tSec: Long) {
        val arr = try { org.json.JSONArray(p(ctx).getString("trail_q", "[]")) } catch (e: Exception) { org.json.JSONArray() }
        if (arr.length() > 0) {
            val last = arr.getJSONArray(arr.length() - 1)
            if (last.getLong(2) == tSec) return
        }
        arr.put(org.json.JSONArray().put(lat).put(lng).put(tSec))
        val out = org.json.JSONArray()
        for (i in maxOf(0, arr.length() - 120) until arr.length()) out.put(arr.get(i))
        p(ctx).edit().putString("trail_q", out.toString()).apply()
    }
    @Synchronized
    fun trailQueue(ctx: Context): org.json.JSONArray =
        try { org.json.JSONArray(p(ctx).getString("trail_q", "[]")) } catch (e: Exception) { org.json.JSONArray() }
    @Synchronized
    fun clearTrail(ctx: Context) { p(ctx).edit().remove("trail_q").apply() }

    fun clear(ctx: Context) { p(ctx).edit().clear().apply() }
}
