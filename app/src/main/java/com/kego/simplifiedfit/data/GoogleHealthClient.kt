package com.kego.simplifiedfit.data

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SleepRecord(
    val date: LocalDate,
    val asleepMinutes: Int,
    val inBedMinutes: Int,
    val awakeMinutes: Int,
    val restlessnessMinutes: Int?,
    val remMinutes: Int?,
    val lightMinutes: Int?,
    val deepMinutes: Int?,
    val midpointMinute: Int,
)

data class GoogleHealthBatch(
    val steps: Map<LocalDate, Int>,
    val totalCalories: Map<LocalDate, Double>,
    val activeCalories: Map<LocalDate, Double>,
    val restingHeartRate: Map<LocalDate, Double>,
    val hrv: Map<LocalDate, Double>,
    val sleeps: Map<LocalDate, SleepRecord>,
    val latestHeartRate: Int?,
)

class GoogleHealthClient(
    private val credentials: GoogleCredentials,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun authorizationUrl(): String = authorizationUrl(credentials.clientId)

    fun exchangeAuthorizationCode(code: String): GoogleCredentials {
        val body = form(
            "client_id" to credentials.clientId,
            "client_secret" to credentials.clientSecret,
            "code" to normalizeAuthorizationCode(code),
            "redirect_uri" to REDIRECT_URI,
            "grant_type" to "authorization_code",
        )
        val response = request(TOKEN_URL, "POST", body, "application/x-www-form-urlencoded", null)
        val token = JSONObject(response).optString("refresh_token")
        require(token.isNotBlank()) { "Google did not return a refresh token. Revoke consent and try again." }
        return credentials.copy(refreshToken = token)
    }

    fun fetch(): GoogleHealthBatch {
        val token = accessToken()
        val today = LocalDate.now(zoneId)
        val rollupStart = today.minusDays(7)
        val sleepStart = today.minusDays(30)

        return GoogleHealthBatch(
            steps = dailyRollup(token, "steps", "steps", "countSum", rollupStart, today.plusDays(1)).mapValues { it.value.toInt() },
            totalCalories = dailyRollup(token, "total-calories", "totalCalories", "kcalSum", rollupStart, today.plusDays(1)),
            activeCalories = dailyRollup(token, "active-energy-burned", "activeEnergyBurned", "kcalSum", rollupStart, today.plusDays(1)),
            restingHeartRate = dailyMetric(token, "daily-resting-heart-rate", "dailyRestingHeartRate", "beatsPerMinute"),
            hrv = dailyMetric(token, "daily-heart-rate-variability", "dailyHeartRateVariability", "averageHeartRateVariabilityMilliseconds"),
            sleeps = sleeps(token, sleepStart),
            latestHeartRate = latestHeartRate(token, today),
        )
    }

    private fun accessToken(): String {
        val body = form(
            "client_id" to credentials.clientId,
            "client_secret" to credentials.clientSecret,
            "refresh_token" to credentials.refreshToken,
            "grant_type" to "refresh_token",
        )
        val response = JSONObject(request(TOKEN_URL, "POST", body, "application/x-www-form-urlencoded", null))
        return response.getString("access_token")
    }

    private fun dailyRollup(
        token: String,
        dataType: String,
        valueObject: String,
        valueField: String,
        start: LocalDate,
        end: LocalDate,
    ): Map<LocalDate, Double> {
        val payload = JSONObject()
            .put("range", JSONObject().put("start", civilDate(start)).put("end", civilDate(end)))
            .put("dataSourceFamily", GOOGLE_WEARABLES)
            .put("windowSizeDays", 1)
        val response = JSONObject(
            request("$API_ROOT/users/me/dataTypes/$dataType/dataPoints:dailyRollUp", "POST", payload.toString(), "application/json", token),
        )
        return response.optJSONArray("rollupDataPoints").orEmpty().mapNotNull { point ->
            val date = point.optJSONObject("civilStartTime")?.optJSONObject("date")?.toLocalDate() ?: return@mapNotNull null
            val value = point.optJSONObject(valueObject)?.optDouble(valueField, Double.NaN) ?: return@mapNotNull null
            if (value.isNaN()) null else date to value
        }.toMap()
    }

    private fun dailyMetric(token: String, dataType: String, objectName: String, valueName: String): Map<LocalDate, Double> {
        val response = JSONObject(request("$API_ROOT/users/me/dataTypes/$dataType/dataPoints?pageSize=35", token = token))
        return response.optJSONArray("dataPoints").orEmpty().mapNotNull { point ->
            val data = point.optJSONObject(objectName) ?: return@mapNotNull null
            val date = data.optJSONObject("date")?.toLocalDate() ?: return@mapNotNull null
            val value = data.optDouble(valueName, Double.NaN)
            if (value.isNaN()) null else date to value
        }.toMap()
    }

    private fun sleeps(token: String, start: LocalDate): Map<LocalDate, SleepRecord> {
        val filter = "sleep.interval.civil_end_time >= \"$start\""
        val url = "$API_ROOT/users/me/dataTypes/sleep/dataPoints:reconcile?dataSourceFamily=users/me/dataSourceFamilies/google-wearables&pageSize=25&filter=${encode(filter)}"
        val response = JSONObject(request(url, token = token))
        return response.optJSONArray("dataPoints").orEmpty().mapNotNull { point -> parseSleep(point.optJSONObject("sleep")) }
            .groupBy { it.date }
            .mapValues { (_, records) -> records.maxBy { it.asleepMinutes } }
    }

    private fun parseSleep(sleep: JSONObject?): SleepRecord? {
        sleep ?: return null
        if (sleep.optJSONObject("metadata")?.optBoolean("nap", false) == true) return null
        val interval = sleep.optJSONObject("interval") ?: return null
        val start = runCatching { Instant.parse(interval.getString("startTime")) }.getOrNull() ?: return null
        val end = runCatching { Instant.parse(interval.getString("endTime")) }.getOrNull() ?: return null
        val summary = sleep.optJSONObject("summary") ?: return null
        val stages = summary.optJSONArray("stagesSummary").orEmpty().associate { stage ->
            stage.optString("type") to stage.optInt("minutes")
        }
        val restlessnessMinutes = summary.optIntOrNull("minutesRestless")
            ?: summary.optIntOrNull("restlessnessMinutes")
            ?: stages["RESTLESS"]
            ?: stageMinutes(sleep.optJSONArray("stages"), "RESTLESS")
            ?: stageMinutes(sleep.optJSONArray("shortAwakenings"))
        val localEnd = end.atZone(zoneId)
        val midpoint = start.plusMillis(Duration.between(start, end).toMillis() / 2).atZone(zoneId)
        return SleepRecord(
            date = localEnd.toLocalDate(),
            asleepMinutes = summary.optInt("minutesAsleep"),
            inBedMinutes = summary.optInt("minutesInSleepPeriod"),
            awakeMinutes = summary.optInt("minutesAwake"),
            restlessnessMinutes = restlessnessMinutes,
            remMinutes = stages["REM"],
            lightMinutes = stages["LIGHT"] ?: stages["ASLEEP"],
            deepMinutes = stages["DEEP"],
            midpointMinute = midpoint.hour * 60 + midpoint.minute,
        )
    }

    private fun latestHeartRate(token: String, today: LocalDate): Int? {
        val filter = "heart_rate.sample_time.civil_time >= \"${today}T00:00:00\""
        val url = "$API_ROOT/users/me/dataTypes/heart-rate/dataPoints?pageSize=1&filter=${encode(filter)}"
        val response = JSONObject(request(url, token = token))
        return response.optJSONArray("dataPoints")?.optJSONObject(0)?.optJSONObject("heartRate")?.optInt("beatsPerMinute")
    }

    private fun request(
        url: String,
        method: String = "GET",
        body: String? = null,
        contentType: String = "application/json",
        token: String? = null,
    ): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Content-Type", contentType)
        token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
        if (body != null) {
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val text = stream.bufferedReader().use(BufferedReader::readText)
        if (connection.responseCode !in 200..299) {
            val message = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull()
            error(message?.takeIf { it.isNotBlank() } ?: "Google Health request failed (${connection.responseCode})")
        }
        return text
    }

    private fun civilDate(date: LocalDate) = JSONObject()
        .put("date", JSONObject().put("year", date.year).put("month", date.monthValue).put("day", date.dayOfMonth))
        .put("time", JSONObject())

    private fun JSONObject.toLocalDate() = LocalDate.of(getInt("year"), getInt("month"), getInt("day"))

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (has(name) && !isNull(name)) optInt(name) else null

    private fun stageMinutes(segments: JSONArray?, type: String? = null): Int? {
        val matching = segments.orEmpty().filter { type == null || it.optString("type") == type }
        if (matching.isEmpty()) return null
        val millis = matching.mapNotNull { segment ->
            runCatching {
                Duration.between(
                    Instant.parse(segment.getString("startTime")),
                    Instant.parse(segment.getString("endTime")),
                ).toMillis()
            }.getOrNull()
        }.sum()
        return (millis / 60_000.0).toInt()
    }

    private fun JSONArray?.orEmpty(): List<JSONObject> = buildList {
        if (this@orEmpty != null) for (index in 0 until length()) add(getJSONObject(index))
    }

    private fun form(vararg values: Pair<String, String>): String = values.joinToString("&") { "${encode(it.first)}=${encode(it.second)}" }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    companion object {
        private const val API_ROOT = "https://health.googleapis.com/v4"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val GOOGLE_WEARABLES = "users/me/dataSourceFamilies/google-wearables"
        const val REDIRECT_URI = "https://www.google.com"
        val SCOPES = listOf(
            "https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly",
            "https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.readonly",
            "https://www.googleapis.com/auth/googlehealth.sleep.readonly",
        )

        fun authorizationUrl(clientId: String): String = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .appendQueryParameter("scope", SCOPES.joinToString(" "))
            .build()
            .toString()

        fun normalizeAuthorizationCode(input: String): String {
            val trimmed = input.trim()
            if (!trimmed.startsWith("http")) return trimmed
            val query = runCatching { URI(trimmed).rawQuery }.getOrNull() ?: return trimmed
            return query.split('&').firstOrNull { it.substringBefore('=') == "code" }
                ?.substringAfter('=', "")
                ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
                ?.takeIf { it.isNotBlank() }
                ?: trimmed
        }
    }
}
