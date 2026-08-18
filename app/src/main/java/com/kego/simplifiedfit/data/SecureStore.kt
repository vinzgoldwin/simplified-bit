package com.kego.simplifiedfit.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class GoogleCredentials(
    val clientId: String,
    val clientSecret: String,
    val refreshToken: String,
)

data class GoogleSetupCredentials(
    val clientId: String,
    val clientSecret: String,
)

class SecureStore(context: Context) {
    private val preferences = context.getSharedPreferences("secure_credentials", Context.MODE_PRIVATE)

    fun saveGoogleCredentials(credentials: GoogleCredentials) {
        put("google_client_id", credentials.clientId)
        put("google_client_secret", credentials.clientSecret)
        put("google_refresh_token", credentials.refreshToken)
    }

    fun googleCredentials(): GoogleCredentials? {
        val clientId = get("google_client_id") ?: return null
        val clientSecret = get("google_client_secret") ?: return null
        val refreshToken = get("google_refresh_token") ?: return null
        return GoogleCredentials(clientId, clientSecret, refreshToken)
    }

    fun clearGoogleCredentials() {
        preferences.edit()
            .remove("google_client_id")
            .remove("google_client_secret")
            .remove("google_refresh_token")
            .apply()
    }

    fun saveGoogleSetupCredentials(credentials: GoogleSetupCredentials) {
        put("google_setup_client_id", credentials.clientId)
        put("google_setup_client_secret", credentials.clientSecret)
    }

    fun googleSetupCredentials(): GoogleSetupCredentials? {
        val clientId = get("google_setup_client_id") ?: return null
        val clientSecret = get("google_setup_client_secret") ?: return null
        return GoogleSetupCredentials(clientId, clientSecret)
    }

    fun clearGoogleSetupCredentials() {
        preferences.edit()
            .remove("google_setup_client_id")
            .remove("google_setup_client_secret")
            .apply()
    }

    fun saveCoachConnection(connection: CoachConnection) {
        put("coach_url", connection.baseUrl)
        put("coach_token", connection.token)
    }

    fun coachConnection(): CoachConnection? {
        val url = get("coach_url") ?: return null
        val token = get("coach_token") ?: return null
        return CoachConnection(url, token)
    }

    fun clearCoachConnection() {
        preferences.edit().remove("coach_url").remove("coach_token").apply()
    }

    fun saveOpenRouterApiKey(apiKey: String) {
        put("openrouter_api_key", apiKey)
    }

    fun openRouterApiKey(): String? = get("openrouter_api_key")

    fun clearOpenRouterApiKey() {
        preferences.edit().remove("openrouter_api_key").apply()
    }

    fun coachProvider(): CoachProvider = preferences.getString("coach_provider", null)
        ?.let { value -> CoachProvider.entries.firstOrNull { it.name == value } }
        ?: CoachProvider.CODEX

    fun saveCoachProvider(provider: CoachProvider) {
        preferences.edit().putString("coach_provider", provider.name).apply()
    }

    fun saveCoachJob(id: String, provider: CoachProvider, request: CoachRequest) {
        val previousId = currentCoachJobId()
        val payload = JSONObject()
            .put("provider", provider.name)
            .put("message", request.message)
            .put("healthContext", request.healthContext)
            .put("previousQuestion", request.previousQuestion)
            .put("previousAnswer", request.previousAnswer)
        put(coachJobKey(id), payload.toString())
        preferences.edit().putString(CURRENT_COACH_JOB_ID, id).apply()
        if (previousId != null && previousId != id) clearCoachJob(previousId)
    }

    fun coachJob(id: String): Pair<CoachProvider, CoachRequest>? = get(coachJobKey(id))?.let { text ->
        runCatching {
            val json = JSONObject(text)
            CoachProvider.valueOf(json.getString("provider")) to CoachRequest(
                message = json.getString("message"),
                healthContext = json.getString("healthContext"),
                previousQuestion = json.optString("previousQuestion").takeIf(String::isNotBlank),
                previousAnswer = json.optString("previousAnswer").takeIf(String::isNotBlank),
            )
        }.getOrNull()
    }

    fun currentCoachJobId(): String? = preferences.getString(CURRENT_COACH_JOB_ID, null)

    fun saveCoachJobResult(id: String, answer: CoachAnswer, durationMs: Long) {
        val payload = JSONObject()
            .put("response", answer.response)
            .put("reasoning", JSONArray(answer.reasoning))
            .put("suggestions", JSONArray(answer.suggestions))
            .put("durationMs", durationMs)
        put(coachJobResultKey(id), payload.toString())
    }

    fun coachJobResult(id: String): Pair<CoachAnswer, Long>? = get(coachJobResultKey(id))?.let { text ->
        runCatching {
            val json = JSONObject(text)
            fun strings(name: String): List<String> = json.getJSONArray(name).let { array ->
                (0 until array.length()).map(array::getString)
            }
            CoachAnswer(
                response = json.getString("response"),
                reasoning = strings("reasoning"),
                suggestions = strings("suggestions"),
            ) to json.getLong("durationMs")
        }.getOrNull()
    }

    private fun clearCoachJob(id: String) {
        preferences.edit().remove(coachJobKey(id)).remove(coachJobResultKey(id)).apply()
    }

    private fun coachJobKey(id: String) = "coach_job_$id"

    private fun coachJobResultKey(id: String) = "coach_job_result_$id"

    private fun put(name: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val payload = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        preferences.edit().putString(name, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
    }

    private fun get(name: String): String? = runCatching {
        val payload = Base64.decode(preferences.getString(name, null), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
        String(cipher.doFinal(payload.copyOfRange(12, payload.size)), StandardCharsets.UTF_8)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val CURRENT_COACH_JOB_ID = "current_coach_job_id"
        const val KEY_ALIAS = "simplified_fit_credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
