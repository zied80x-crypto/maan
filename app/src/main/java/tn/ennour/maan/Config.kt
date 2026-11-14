package tn.ennour.maan

object Config {
    // =================================================================
    //  رابط الـ API على موقعك: غيّره إذا رفعت الملفات في مجلد آخر.
    //  يجب أن يبدأ بـ https وينتهي بـ api/index.php
    // =================================================================
    const val API_URL = "https://ennourhna.com/maan/api/index.php"

    // رابط لوحة المرافق (يُستخرج من رابط الـ API)
    val PANEL_URL: String get() = API_URL.removeSuffix("api/index.php")

    const val NORMAL_INTERVAL_MS = 3 * 60 * 1000L   // تحديث الموقع كل 3 دقائق
    const val SOS_INTERVAL_MS = 15 * 1000L          // كل 15 ثانية أثناء الاستغاثة
}
