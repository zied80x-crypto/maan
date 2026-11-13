package tn.ennour.maan

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** الاتصال بالخادم. يُستدعى دائماً من خيط في الخلفية. */
object Api {
    class ApiException(message: String, val code: Int) : Exception(message)

    fun call(action: String, body: JSONObject, token: String?): JSONObject {
        val conn = URL(Config.API_URL + "?action=" + action).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (!token.isNullOrEmpty()) conn.setRequestProperty("X-Auth-Token", token)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val json = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
            if (code !in 200..299) {
                throw ApiException(json.optString("error", "خطأ في الخادم ($code)"), code)
            }
            return json
        } finally {
            conn.disconnect()
        }
    }
}

/** نص من JSON، مع تحويل null إلى نص فارغ. */
fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")
