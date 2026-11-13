package tn.ennour.maan

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import org.json.JSONObject

/** شاشة الانضمام للرحلة: مسح QR أو كتابة الكود، ثم الاسم والغرفة. */
class JoinActivity : AppCompatActivity() {
    private lateinit var etCode: EditText
    private lateinit var etName: EditText
    private lateinit var etRoom: EditText
    private lateinit var etPhone: EditText
    private lateinit var etHealth: EditText
    private lateinit var tvError: TextView
    private lateinit var btnJoin: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_join)
        etCode = findViewById(R.id.etCode)
        etName = findViewById(R.id.etName)
        etRoom = findViewById(R.id.etRoom)
        etPhone = findViewById(R.id.etPhone)
        etHealth = findViewById(R.id.etHealth)
        tvError = findViewById(R.id.tvError)
        btnJoin = findViewById(R.id.btnJoin)

        findViewById<MaterialButton>(R.id.btnScan).setOnClickListener { scan() }
        btnJoin.setOnClickListener { join() }
    }

    private fun scan() {
        try {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            GmsBarcodeScanning.getClient(this, options).startScan()
                .addOnSuccessListener { code ->
                    val raw = code.rawValue ?: ""
                    etCode.setText(raw.trim().uppercase().removePrefix("MAAN:"))
                    etName.requestFocus()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "تعذّر فتح الكاميرا. اكتب الكود باليد.", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            Toast.makeText(this, "تعذّر فتح الكاميرا. اكتب الكود باليد.", Toast.LENGTH_LONG).show()
        }
    }

    private fun join() {
        val code = etCode.text.toString().trim().uppercase().removePrefix("MAAN:")
        val name = etName.text.toString().trim()
        if (code.isEmpty()) { tvError.text = "اكتب كود الرحلة أو امسحه"; return }
        if (name.length < 3) { tvError.text = "اكتب اسمك الكامل"; return }

        tvError.text = ""
        btnJoin.isEnabled = false
        btnJoin.text = "جاري الدخول…"
        val body = JSONObject()
            .put("code", code)
            .put("full_name", name)
            .put("room", etRoom.text.toString().trim())
            .put("phone", etPhone.text.toString().trim())
            .put("health_notes", etHealth.text.toString().trim())
        Sync.join(this, body) { ok, err ->
            if (ok) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                btnJoin.isEnabled = true
                btnJoin.text = "دخول الرحلة"
                tvError.text = err ?: "حدث خطأ"
            }
        }
    }
}
