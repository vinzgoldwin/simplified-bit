package com.kego.simplifiedfit.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

enum class CoachProvider { CODEX, OPENROUTER }

data class CoachConnection(val baseUrl: String, val token: String)

data class CoachEvidence(
    val signals: List<String>,
    val interpretation: String,
)

data class CoachAnswer(
    val response: String,
    val evidence: CoachEvidence? = null,
    val suggestions: List<String>,
)

data class CoachRequest(
    val message: String,
    val healthContext: String,
    val previousQuestion: String? = null,
    val previousAnswer: String? = null,
)

enum class CoachProgress { CONTEXT_READY, ANALYZING, WRITING }

sealed interface CoachEvent {
    data class Progress(val stage: CoachProgress) : CoachEvent
    data class ResponseDelta(val text: String) : CoachEvent
    data class Complete(val answer: CoachAnswer, val durationMs: Long) : CoachEvent
}

interface CoachBackend {
    fun ask(request: CoachRequest): Flow<CoachEvent>
}

internal fun parseCoachAnswer(text: String): CoachAnswer {
    val json = JSONObject(text)
    val suggestions = json.optJSONArray("suggestions")?.let { array ->
        (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf(String::isNotBlank) }
    }.orEmpty()
    val evidence = json.optJSONObject("evidence")?.let { value ->
        CoachEvidence(
            signals = value.optJSONArray("signals")?.let { array ->
                (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf(String::isNotBlank) }
            }.orEmpty(),
            interpretation = value.optString("interpretation"),
        )
    }
    return CoachAnswer(json.getString("response"), evidence, suggestions)
}

class CoachClient(private val connection: CoachConnection) : CoachBackend {
    override fun ask(request: CoachRequest): Flow<CoachEvent> = flow {
        val startedAt = System.currentTimeMillis()
        emit(CoachEvent.Progress(CoachProgress.CONTEXT_READY))
        emit(CoachEvent.Progress(CoachProgress.ANALYZING))

        val payload = JSONObject()
            .put("message", request.message)
            .put("healthContext", request.healthContext)
            .put("previousQuestion", request.previousQuestion)
            .put("previousAnswer", request.previousAnswer)
            .toString()
        val http = URL("${connection.baseUrl.trimEnd('/')}/chat").openConnection() as HttpURLConnection
        http.requestMethod = "POST"
        http.connectTimeout = 8_000
        http.readTimeout = 120_000
        http.doOutput = true
        http.setRequestProperty("Authorization", "Bearer ${connection.token}")
        http.setRequestProperty("Content-Type", "application/json")
        http.outputStream.use { it.write(payload.toByteArray(StandardCharsets.UTF_8)) }
        val text = (if (http.responseCode in 200..299) http.inputStream else http.errorStream)
            .bufferedReader()
            .use { it.readText() }
        if (http.responseCode !in 200..299) error(JSONObject(text).optString("error", "Coach unavailable"))

        val answer = parseCoachAnswer(text)
        emit(CoachEvent.Progress(CoachProgress.WRITING))
        emit(CoachEvent.ResponseDelta(answer.response))
        emit(CoachEvent.Complete(answer, System.currentTimeMillis() - startedAt))
    }.flowOn(Dispatchers.IO)

    fun isHealthy(): Boolean = runCatching {
        val http = URL("${connection.baseUrl.trimEnd('/')}/health").openConnection() as HttpURLConnection
        http.connectTimeout = 2_000
        http.readTimeout = 2_000
        http.setRequestProperty("Authorization", "Bearer ${connection.token}")
        http.responseCode == 200
    }.getOrDefault(false)
}

class OpenRouterCoachClient(private val apiKey: String) : CoachBackend {
    override fun ask(request: CoachRequest): Flow<CoachEvent> = flow {
        val startedAt = System.currentTimeMillis()
        emit(CoachEvent.Progress(CoachProgress.CONTEXT_READY))
        emit(CoachEvent.Progress(CoachProgress.ANALYZING))

        val http = URL(OPENROUTER_URL).openConnection() as HttpURLConnection
        http.requestMethod = "POST"
        http.connectTimeout = 15_000
        http.readTimeout = 120_000
        http.doOutput = true
        http.setRequestProperty("Authorization", "Bearer $apiKey")
        http.setRequestProperty("Content-Type", "application/json")
        http.setRequestProperty("Accept", "text/event-stream")
        http.setRequestProperty("X-Title", "Simplified Fit")
        http.outputStream.use { output ->
            output.write(openRouterPayload(request).toString().toByteArray(StandardCharsets.UTF_8))
        }

        if (http.responseCode !in 200..299) {
            val errorText = http.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val message = runCatching {
                JSONObject(errorText).optJSONObject("error")?.optString("message")
            }.getOrNull()
            error(message?.takeIf(String::isNotBlank) ?: "OpenRouter request failed (${http.responseCode})")
        }

        val structured = StringBuilder()
        var emittedResponse = ""
        var pendingDelta = ""
        var writingStarted = false

        http.inputStream.bufferedReader().use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                val data = line.takeIf { it.startsWith("data:") }
                    ?.removePrefix("data:")
                    ?.trimStart()
                    ?: continue
                if (data == "[DONE]") break

                val chunk = JSONObject(data)
                chunk.optJSONObject("error")?.let { error ->
                    throw IllegalStateException(error.optString("message", "OpenRouter stream failed"))
                }
                val content = chunk.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("delta")
                    ?.optString("content")
                    .orEmpty()
                if (content.isEmpty()) continue

                structured.append(content)
                val decoded = decodedResponsePrefix(structured.toString()) ?: continue
                if (!decoded.startsWith(emittedResponse)) continue
                pendingDelta += decoded.removePrefix(emittedResponse)
                emittedResponse = decoded

                if (pendingDelta.length >= 48 || pendingDelta.endsAtPhraseBoundary()) {
                    if (!writingStarted) {
                        emit(CoachEvent.Progress(CoachProgress.WRITING))
                        writingStarted = true
                    }
                    emit(CoachEvent.ResponseDelta(pendingDelta))
                    pendingDelta = ""
                }
            }
        }

        val answer = parseCoachAnswer(structured.toString())
        val remaining = when {
            answer.response.startsWith(emittedResponse) -> pendingDelta + answer.response.removePrefix(emittedResponse)
            else -> answer.response
        }
        if (!writingStarted) emit(CoachEvent.Progress(CoachProgress.WRITING))
        if (remaining.isNotEmpty()) emit(CoachEvent.ResponseDelta(remaining))
        emit(CoachEvent.Complete(answer, System.currentTimeMillis() - startedAt))
    }.flowOn(Dispatchers.IO)

    private fun openRouterPayload(request: CoachRequest): JSONObject = JSONObject()
        .put("model", MODEL)
        .put("stream", true)
        .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", coachPrompt(request))))
        .put(
            "response_format",
            JSONObject()
                .put("type", "json_schema")
                .put(
                    "json_schema",
                    JSONObject()
                        .put("name", "coach_answer")
                        .put("strict", true)
                        .put("schema", OUTPUT_SCHEMA),
                ),
        )
        .put("provider", JSONObject().put("require_parameters", true))

    private companion object {
        const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
        const val MODEL = "deepseek/deepseek-v4-flash"

        val OUTPUT_SCHEMA = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put("response", JSONObject().put("type", "string"))
                    .put(
                        "evidence",
                        JSONObject()
                            .put("type", "object")
                            .put(
                                "properties",
                                JSONObject()
                                    .put(
                                        "signals",
                                        JSONObject()
                                            .put("type", "array")
                                            .put("items", JSONObject().put("type", "string"))
                                            .put("minItems", 1)
                                            .put("maxItems", 4),
                                    )
                                    .put("interpretation", JSONObject().put("type", "string")),
                            )
                            .put("required", JSONArray().put("signals").put("interpretation"))
                            .put("additionalProperties", false),
                    )
                    .put(
                        "suggestions",
                        JSONObject()
                            .put("type", "array")
                            .put("items", JSONObject().put("type", "string"))
                            .put("minItems", 3)
                            .put("maxItems", 3),
                    ),
            )
            .put("required", JSONArray().put("response").put("evidence").put("suggestions"))
            .put("additionalProperties", false)
    }
}

internal fun coachPrompt(request: CoachRequest): String {
    val previousExchange = if (request.previousQuestion != null && request.previousAnswer != null) {
        """
        PREVIOUS EXCHANGE
        Question: ${request.previousQuestion}
        Answer: ${request.previousAnswer}
        """.trimIndent()
    } else {
        "PREVIOUS EXCHANGE\nNone"
    }
    return """
        You are the personal wellness coach inside Simplified Fit. The supplied health summary is the sole source of personal facts. You may apply general wellness knowledge, but never invent measurements, history, symptoms, or causes. Treat unavailable fields as unknown. Do not use tools or seek external data.

        Answer the current question directly. Use the previous exchange only when relevant. Ground conclusions in supplied signals and favor personal baselines and multi-day trends. Separate observation from inference and acknowledge stale, sparse, conflicting, or missing data.

        When a recommendation would help, give one or two low-risk actions for today. Make actions specific and realistic, cite the signals that motivate them, and say what to monitor next. Avoid generic filler, alarmist interpretations, and pretending correlation proves a cause.

        This is general wellness guidance, not medical diagnosis or treatment. Do not prescribe medication or claim medical certainty. For urgent or severe symptoms, advise seeking appropriate local medical or emergency care. Keep the response calm, compact, and easy to scan.

        Provide evidence as one to four short signal summaries and one concise interpretation written for the user. This is an evidence summary, not private chain-of-thought. Also provide exactly three distinct follow-up questions, each under 60 characters.

        HEALTH SUMMARY
        ${request.healthContext}

        $previousExchange

        CURRENT QUESTION
        ${request.message}
    """.trimIndent()
}

internal fun decodedResponsePrefix(structured: String): String? {
    val key = structured.indexOf("\"response\"")
    if (key < 0) return null
    val colon = structured.indexOf(':', key + 10)
    if (colon < 0) return null
    val quote = structured.indexOf('"', colon + 1)
    if (quote < 0) return null

    var escaped = false
    var end = structured.length
    for (index in quote + 1 until structured.length) {
        val character = structured[index]
        if (escaped) {
            escaped = false
        } else if (character == '\\') {
            escaped = true
        } else if (character == '"') {
            end = index
            break
        }
    }

    var raw = structured.substring(quote + 1, end)
    val incompleteUnicode = Regex("\\\\u[0-9a-fA-F]{0,3}$").find(raw)
    if (incompleteUnicode != null) raw = raw.substring(0, incompleteUnicode.range.first)
    if (raw.takeLastWhile { it == '\\' }.length % 2 == 1) raw = raw.dropLast(1)
    return runCatching { JSONObject("{\"value\":\"$raw\"}").getString("value") }.getOrNull()
}

private fun String.endsAtPhraseBoundary(): Boolean {
    val trimmed = trimEnd()
    return trimmed.endsWith('.') || trimmed.endsWith('!') || trimmed.endsWith('?') || trimmed.endsWith('\n')
}
