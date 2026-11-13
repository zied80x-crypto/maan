package tn.ennour.maan

object Config {
    // =================================================================
    //  رابط الـ API على موقعك: غيّره إذا رفعت الملفات في مجلد آخر.
    //  يجب أن يبدأ بـ https وينتهي بـ api/index.php
    // =================================================================
    const val API_URL = "https://ennourhna.com/maan/api/index.php"

    const val NORMAL_INTERVAL_MS = 3 * 60 * 1000L   // تحديث الموقع كل 3 دقائق
    const val SOS_INTERVAL_MS = 15 * 1000L          // كل 15 ثانية أثناء الاستغاثة
    const val SEND_SOS_SMS = true                   // رسالة SMS احتياطية للمرافق عند الاستغاثة
}
