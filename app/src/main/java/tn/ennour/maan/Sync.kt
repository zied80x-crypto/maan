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
                Store.setLastSent(app, System.currentTimeMillis())
                Store.setLastOk(app, System.currentTimeMillis())
                when (action) {
                    "sos" -> { Store.setSosPending(app, false); Store.set(app, Store.STATUS, "sos") }
                    "ok" -> { Store.setSosPending(app, false); Store.set(app, Store.STATUS, "ok") }
                    else -> applyServerStatus(app, r.str("status"))
                }
            } catch (e: Api.ApiException) {
                err = e.message
                if (e.code == 401) Store.clear(app)
            } catch (e: Exception) {
                err = "لا يوجد اتصال بالإنترنت"
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
                r.optJSONObject("pilgrim")?.let { applyServerStatus(app, it.str("status")) }
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

    fun saveTrip(app: Context, t: JSONObject) {
        Store.set(app, Store.TRIP, t.str("name"))
        Store.set(app, Store.HOTEL, t.str("current_hotel"))
        Store.set(app, Store.CITY, t.str("current_city"))
        Store.set(app, Store.GUIDE_NAME, t.str("guide_name"))
        Store.set(app, Store.GUIDE_PHONE, t.str("guide_phone"))
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
                Store.set(app, Store.NAME, p.str("full_name"))
                Store.set(app, Store.ROOM, p.str("room"))
                Store.set(app, Store.STATUS, "ok")
                r.optJSONObject("trip")?.let { saveTrip(app, it) }
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
