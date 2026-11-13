معاً - تطبيق المعتمر (أندرويد)
==============================

الفتح والبناء
-------------
1) Android Studio ← File ← Open ← اختر مجلد Maan، وانتظر انتهاء Gradle Sync.
2) افتح الملف:  app/src/main/java/tn/ennour/maan/Config.kt
   وتأكد أن API_URL هو رابط موقعك الصحيح وينتهي بـ api/index.php
3) Build ← Build Bundle(s) / APK(s) ← Build APK(s)
   الملف الناتج: app/build/outputs/apk/debug/app-debug.apk

الإعدادات (Config.kt)
---------------------
API_URL              رابط الـ API
NORMAL_INTERVAL_MS   تحديث الموقع في الوضع العادي (3 دقائق)
SOS_INTERVAL_MS      تحديث الموقع أثناء الاستغاثة (15 ثانية)
SEND_SOS_SMS         رسالة SMS احتياطية للمرافق عند الاستغاثة (true / false)

عند تثبيت التطبيق على هاتف المعتمر
----------------------------------
- اقبل كل الأذونات: الموقع، الإشعارات، الرسائل.
- الإعدادات ← البطارية ← التطبيق «معاً» ← «بدون قيود»، حتى لا يوقفه الهاتف في الخلفية
  (مهم خاصة على Xiaomi و Huawei و Oppo و Samsung).
- شغّل GPS (الموقع) في الهاتف.
