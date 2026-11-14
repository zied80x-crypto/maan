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

    fun clear(ctx: Context) { p(ctx).edit().clear().apply() }
}
