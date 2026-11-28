package tn.ennour.maan

import android.content.Context
import android.location.Location
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.util.concurrent.Executors

/** إرسال الموقع والحالة للخادم، وجلب رسائل المرافق. */
object Sync {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private fun battery(ctx: Context): Int? {
        return try {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val v = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (v in 0..100) v else null
        } catch (e: Exception) {
            null
        }
    }

    /** يطبّق الحالة القادمة من الخادم (مثلاً: المرافق أغلق النداء). */
    private fun applyServerStatus(app: Context, status: String) {
        if (Store.sosPending(app) || status.isEmpty()) return
        if (Store.isSos(app) && status == "ok") {
            Notifier.message(app, "في عيني", "المرافق أغلق نداء المساعدة ✅")
        }
        Store.set(app, Store.STATUS, status)
    }

    /** action: update | sos | ok */
    fun send(ctx: Context, action: String, loc: Location?, done: ((Boolean, String?) -> Unit)? = null) {
        val app = ctx.applicationContext
        val token = Store.token(app)
        if (token == null) {
            done?.invoke(false, null)
            return
        }
        io.execute {
            var ok = false
            var err: String? = null
            try {
                val b = JSONObject()
                if (loc != null) {
                    b.put("lat", loc.latitude)
                    b.put("lng", loc.longitude)
                    if (loc.hasAccuracy()) b.put("accuracy", loc.accuracy.toDouble())
                }
                val bat = battery(app)
                if (bat != null) b.put("battery", bat)
                val lm = Store.get(app, Store.LANDMARK)
                if (lm.isNotEmpty()) b.put("landmark", lm)

                val r = Api.call(action, b, token)
                ok = true
                Store.setNetFail(app, false)
                Store.setLastSent(app, System.currentTimeMillis())
                Store.setLastOk(app, System.currentTimeMillis())
                when (action) {
                    "sos" -> { Store.setSosPending(app, false); Store.set(app, Store.STATUS, "sos") }
                    "ok" -> { Store.setSosPending(app, false); Store.set(app, Store.STATUS, "ok") }
                    else -> applyServerStatus(app, r.str("status"))
                }
                if (r.has("walk")) Store.setWalk(app, r.optBoolean("walk", false))
                flushTrail(app, token)
            } catch (e: Api.ApiException) {
                err = e.message
                if (e.code == 401) Store.clear(app)
            } catch (e: Exception) {
                err = "لا يوجد اتصال بالإنترنت"
                Store.setNetFail(app, true)
                if (loc != null && action != "ok") Store.addTrail(app, loc.latitude, loc.longitude, loc.time / 1000)
                // بلا إنترنت: نبثّ موقعنا بالبلوتوث لعل معتمراً قريباً يرحّله
                try { Relay.advertise(app, loc, battery(app), action == "sos" || Store.sosPending(app) || Store.isSos(app)) } catch (e2: Exception) { }
            }
            main.post { done?.invoke(ok, err) }
        }
    }

    /** يجلب بيانات الرحلة (الفندق اليوم) ورسائل المرافق. */
    fun refresh(ctx: Context, done: (() -> Unit)? = null) {
        val app = ctx.applicationContext
        val token = Store.token(app)
        if (token == null) {
            done?.invoke()
            return
        }
        io.execute {
            try {
                val r = Api.call("me", JSONObject(), token)
                r.optJSONObject("trip")?.let { saveTrip(app, it) }
                r.str("relay_key").let { if (it.length == 32) Store.set(app, "relay_key", it) }
                r.optJSONObject("pilgrim")?.let {
                    savePilgrim(app, it)
                    applyServerStatus(app, it.str("status"))
                }
                handleHelper(app, r)
                val list = r.optJSONArray("broadcasts")
                if (list != null && list.length() > 0) {
                    val m = list.getJSONObject(0)   // الأحدث أولاً
                    val id = m.str("id").toLongOrNull() ?: 0L
                    val text = m.str("message")
                    val last = Store.lastMsgId(app)
                    if (id > last) {
                        Store.setLastMsgId(app, id)
                        Store.set(app, Store.MSG, text)
                        if (last >= 0) Notifier.message(app, "📢 رسالة من المرافق", text)
                    }
                } else if (Store.lastMsgId(app) < 0) {
                    Store.setLastMsgId(app, 0)
                }
            } catch (e: Api.ApiException) {
                if (e.code == 401) Store.clear(app)
            } catch (e: Exception) {
            }
            main.post { done?.invoke() }
        }
    }

    /** بعد رجوع الإنترنت: نبعثو النقاط اللي جمعناها وقت الانقطاع (المرافق يشوف المسار الحقيقي). */
    private fun flushTrail(app: Context, token: String) {
        val q = Store.trailQueue(app)
        if (q.length() == 0) return
        try {
            val pts = org.json.JSONArray()
            for (i in 0 until q.length()) {
                val x = q.getJSONArray(i)
                pts.put(JSONObject().put("lat", x.getDouble(0)).put("lng", x.getDouble(1)).put("t", x.getLong(2)))
            }
            Api.call("trail", JSONObject().put("points", pts), token)
            Store.clearTrail(app)
        } catch (e: Api.ApiException) {
            if (e.code in 400..499) Store.clearTrail(app)   // خادم قديم أو بيانات مرفوضة
        } catch (e: Exception) {
        }
    }

    /** يرحّل للخادم مواقع معتمرين قريبين التقطناها بالبلوتوث. */
    fun relay(ctx: Context, items: List<String>, done: (Boolean) -> Unit) {
        val app = ctx.applicationContext
        val token = Store.token(app)
        if (token == null || items.isEmpty()) { done(false); return }
        io.execute {
            var ok = false
            try {
                val arr = org.json.JSONArray()
                items.forEach { arr.put(it) }
                Api.call("relay", JSONObject().put("items", arr), token)
                ok = true
            } catch (e: Api.ApiException) {
                if (e.code == 401) Store.clear(app)
                if (e.code == 501) ok = true   // الخادم ما يدعمش الميزة: نترك الرسائل
            } catch (e: Exception) {
            }
            main.post { done(ok) }
        }
    }

    /** مساعد المرافق: إنذار لكل نداء نجدة جديد، ويتسكّر وحدو كي يتحلّ النداء. */
    private fun handleHelper(app: Context, r: JSONObject) {
        val helper = r.optBoolean("helper", false)
        Store.setHelper(app, helper)
        val arr = if (helper) r.optJSONArray("helper_sos") else null
        val open = HashSet<String>()
        var fresh: JSONObject? = null
        val seen = Store.helperAlerted(app)
        if (arr != null) for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            val id = s.str("id")
            open.add(id)
            if (id !in seen && fresh == null) fresh = s
        }
        if (fresh != null) {
            val room = fresh.str("room")
            val where = listOf(fresh.str("landmark"), if (room.isNotEmpty()) "غرفة $room" else "")
                .filter { it.isNotEmpty() }.joinToString(" · ")
            Notifier.helperAlarm(app, fresh.str("full_name"), where, Store.get(app, Store.GUIDE_PHONE))
        }
        if (open.isEmpty()) Notifier.cancelHelperAlarm(app)
        Store.setHelperAlerted(app, open)
    }

    fun saveTrip(app: Context, t: JSONObject) {
        Store.set(app, Store.TRIP, t.str("name"))
        Store.set(app, Store.HOTEL, t.str("current_hotel"))
        Store.set(app, Store.CITY, t.str("current_city"))
        Store.set(app, Store.GUIDE_NAME, t.str("guide_name"))
        Store.set(app, Store.GUIDE_PHONE, t.str("guide_phone"))
        Store.setWalk(app, t.optBoolean("walk", false))
    }

    /** بيانات المعتمر كما يراها الخادم: المرافق قد يعدّل الاسم والغرفة والفندق. */
    fun savePilgrim(app: Context, p: JSONObject) {
        Store.set(app, Store.NAME, p.str("full_name"))
        Store.set(app, Store.ROOM, p.str("current_room"))
        val hotel = p.str("current_hotel")
        if (hotel.isNotEmpty()) Store.set(app, Store.HOTEL, hotel)
        Store.set(app, Store.HOTEL_LAT, if (p.isNull("hotel_lat")) "" else p.optDouble("hotel_lat").toString())
        Store.set(app, Store.HOTEL_LNG, if (p.isNull("hotel_lng")) "" else p.optDouble("hotel_lng").toString())
        Store.set(app, Store.FAMILY, if (p.optBoolean("family", false)) "1" else "")
    }

    /** رابط العائلة: ينشئه المعتمر نفسه (action = family_link) أو يوقفه (family_stop). */
    fun family(ctx: Context, action: String, done: (String?, String?) -> Unit) {
        val app = ctx.applicationContext
        val token = Store.token(app)
        if (token == null) { done(null, null); return }
        io.execute {
            var tok: String? = null
            var err: String? = null
            try {
                val r = Api.call(action, JSONObject(), token)
                tok = r.str("token")
                Store.set(app, Store.FAMILY, if (action == "family_link") "1" else "")
            } catch (e: Api.ApiException) {
                err = e.message
            } catch (e: Exception) {
                err = "لا يوجد اتصال بالإنترنت"
            }
            main.post { done(tok, err) }
        }
    }

    /** الانضمام للرحلة. */
    fun join(ctx: Context, body: JSONObject, done: (Boolean, String?) -> Unit) {
        val app = ctx.applicationContext
        io.execute {
            var ok = false
            var err: String? = null
            try {
                val r = Api.call("join", body, null)
                val p = r.getJSONObject("pilgrim")
                Store.clear(app)
                Store.set(app, Store.TOKEN, r.getString("token"))
                Store.set(app, Store.STATUS, "ok")
                r.optJSONObject("trip")?.let { saveTrip(app, it) }
                savePilgrim(app, p)
                ok = true
            } catch (e: Api.ApiException) {
                err = e.message
            } catch (e: Exception) {
                err = "لا يوجد اتصال بالإنترنت"
            }
            main.post { done(ok, err) }
        }
    }
}
