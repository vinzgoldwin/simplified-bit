package com.kego.simplifiedfit.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class CoachConnection(val baseUrl: String, val token: String)

class CoachClient(private val connection: CoachConnection) {
    fun ask(message: String, healthContext: String): String {
        val payload = JSONObject().put("message", message).put("healthContext", healthContext).toString()
        val http = URL("${connection.baseUrl.trimEnd('/')}/chat").openConnection() as HttpURLConnection
        http.requestMethod = "POST"
        http.connectTimeout = 8_000
        http.readTimeout = 120_000
        http.doOutput = true
        http.setRequestProperty("Authorization", "Bearer ${connection.token}")
        http.setRequestProperty("Content-Type", "application/json")
        http.outputStream.use { it.write(payload.toByteArray(StandardCharsets.UTF_8)) }
        val text = (if (http.responseCode in 200..299) http.inputStream else http.errorStream).bufferedReader().use { it.readText() }
        if (http.responseCode !in 200..299) error(JSONObject(text).optString("error", "Coach unavailable"))
        return JSONObject(text).getString("response")
    }

    fun isHealthy(): Boolean = runCatching {
        val http = URL("${connection.baseUrl.trimEnd('/')}/health").openConnection() as HttpURLConnection
        http.connectTimeout = 2_000
        http.readTimeout = 2_000
        http.setRequestProperty("Authorization", "Bearer ${connection.token}")
        http.responseCode == 200
    }.getOrDefault(false)
}
