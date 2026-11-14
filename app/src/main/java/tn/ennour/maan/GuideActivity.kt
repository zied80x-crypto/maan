package tn.ennour.maan

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** وضع المرافق: يعرض لوحة المرافق (نفس لوحة الويب) داخل التطبيق. */
class GuideActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var geoOrigin: String? = null
    private var geoCallback: GeolocationPermissions.Callback? = null
    private val locPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        geoCallback?.invoke(geoOrigin, Loc.allowed(this), false)
        geoCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // الشاشة تبقى مضيئة حتى يبقى إنذار الاستغاثة يعمل
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false   // صوت الإنذار بدون لمس
        web.settings.setGeolocationEnabled(true)
        web.webChromeClient = object : WebChromeClient() {       // نوافذ التأكيد + إذن الموقع
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                if (Loc.allowed(this@GuideActivity)) {
                    callback.invoke(origin, true, false)
                } else {
                    geoOrigin = origin
                    geoCallback = callback
                    locPermission.launch(arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                }
            }
        }
        val panelHost = Uri.parse(Config.PANEL_URL).host

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                val scheme = url.scheme ?: ""
                // اللوحة نفسها تُفتح هنا، وكل شيء آخر (اتصال، واتساب، خرائط) يُفتح في تطبيقه
                if ((scheme == "https" || scheme == "http") && url.host == panelHost) return false
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                } catch (e: Exception) {
                }
                return true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (!request.isForMainFrame) return
                val html = "<html dir='rtl'><body style='font-family:sans-serif;text-align:center;padding:60px 20px;background:#F4F1EA'>" +
                    "<h2>⚠️ لا يوجد اتصال بالإنترنت</h2><p>تأكد من الإنترنت ثم أعد المحاولة.</p>" +
                    "<p><a href='" + Config.PANEL_URL + "' style='display:inline-block;background:#0F5C4D;color:#fff;" +
                    "padding:14px 28px;border-radius:12px;text-decoration:none;font-size:18px'>إعادة المحاولة</a></p></body></html>"
                view.loadDataWithBaseURL(Config.PANEL_URL, html, "text/html", "utf-8", null)
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else confirmExit()
            }
        })

        if (savedInstanceState == null) web.loadUrl(Config.PANEL_URL) else web.restoreState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    private fun confirmExit() {
        AlertDialog.Builder(this)
            .setTitle("وضع المرافق")
            .setItems(arrayOf("إغلاق التطبيق", "الخروج من وضع المرافق (الرجوع لشاشة المعتمر)", "إلغاء")) { _, which ->
                when (which) {
                    0 -> finish()
                    1 -> {
                        Store.setGuideMode(this, false)
                        startActivity(Intent(this, JoinActivity::class.java))
                        finish()
                    }
                }
            }
            .show()
    }
}
