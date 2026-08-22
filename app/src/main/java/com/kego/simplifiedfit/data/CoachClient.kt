package com.kego.simplifiedfit.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

data class CoachAnswer(
    val response: String,
    val reasoning: List<String>,
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
    }.orEmpty().validFollowUpQuestions()
    val reasoning = (json.optJSONArray("reasoning")?.let { array ->
        (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf(String::isNotBlank) }
    } ?: json.optJSONObject("evidence")?.let { evidence ->
        buildList {
            evidence.optJSONArray("signals")?.let { signals ->
                repeat(signals.length()) { index -> signals.optString(index).takeIf(String::isNotBlank)?.let(::add) }
            }
            evidence.optString("interpretation").takeIf(String::isNotBlank)?.let(::add)
        }
    }).orEmpty()
    return CoachAnswer(json.getString("response"), reasoning, suggestions)
}

internal fun List<String>.validFollowUpQuestions(): List<String> {
    val fallback = listOf(
        "What stands out most for me?",
        "Is this normal for me?",
        "What should I keep an eye on?",
    )
    val valid = map(String::trim).filter { question ->
        question.length in 8..60 &&
            question.endsWith('?') &&
            !question.contains('_') &&
            Regex("\\b(i|me|my)\\b", RegexOption.IGNORE_CASE).containsMatchIn(question) &&
            !Regex("\\b(you|your|eat|ate|food|meal|drink|drank|hydrat\\w*)\\b", RegexOption.IGNORE_CASE).containsMatchIn(question)
    }
    return (valid + fallback).distinctBy(String::lowercase).take(3)
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
        http.connectTimeout = CONNECT_TIMEOUT_MS
        http.readTimeout = READ_TIMEOUT_MS
        http.doOutput = true
        http.setRequestProperty("Authorization", "Bearer $apiKey")
        http.setRequestProperty("Content-Type", "application/json")
        http.setRequestProperty("Accept", "text/event-stream")
        http.setRequestProperty("X-Title", "Simplified Fit")
        http.outputStream.use { output ->
            output.write(openRouterPayload(request, stream = true).toString().toByteArray(StandardCharsets.UTF_8))
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
                val delta = decoded.removePrefix(emittedResponse)
                emittedResponse = decoded
                if (delta.isEmpty()) continue
                if (!writingStarted) {
                    emit(CoachEvent.Progress(CoachProgress.WRITING))
                    writingStarted = true
                }
                emit(CoachEvent.ResponseDelta(delta))
            }
        }

        val answer = parseCoachAnswer(structured.toString())
        val remaining = when {
            answer.response.startsWith(emittedResponse) -> answer.response.removePrefix(emittedResponse)
            else -> answer.response
        }
        if (!writingStarted) emit(CoachEvent.Progress(CoachProgress.WRITING))
        remaining.chunked(6).forEach { chunk ->
            emit(CoachEvent.ResponseDelta(chunk))
            delay(12)
        }
        emit(CoachEvent.Complete(answer, System.currentTimeMillis() - startedAt))
    }.flowOn(Dispatchers.IO)

    internal fun openRouterPayload(request: CoachRequest, stream: Boolean): JSONObject = JSONObject()
        .put("model", MODEL)
        .put("stream", stream)
        .put("reasoning", JSONObject().put("effort", "none"))
        .put(
            "messages",
            JSONArray()
                .put(JSONObject().put("role", "system").put("content", "Return only JSON matching the provided schema. Never wrap or label the JSON with Markdown."))
                .put(JSONObject().put("role", "user").put("content", coachPrompt(request))),
        )
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
        const val MODEL = "~deepseek/deepseek-v4-flash-latest"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 120_000

        val OUTPUT_SCHEMA = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put(
                        "response",
                        JSONObject()
                            .put("type", "string")
                            .put("description", "A warm coaching answer in plain-language Markdown, usually no more than 120 words. Lead with the takeaway. Interpret measurements into meaning and action instead of reciting them. Include an exact value only when the user asks for it or it makes an action more useful. Use bullets for three or more related facts and bold only useful actions or targets. Do not repeat reasoning or suggestions."),
                    )
                    .put(
                        "reasoning",
                        JSONObject()
                            .put("type", "array")
                            .put("description", "Two to four short, plain-language reasons supporting the answer. Explain what the signals mean without listing measurements unless asked.")
                            .put("items", JSONObject().put("type", "string"))
                            .put("minItems", 2)
                            .put("maxItems", 4),
                    )
                    .put(
                        "suggestions",
                        JSONObject()
                            .put("type", "array")
                            .put("description", "Three natural first-person questions grounded in the supplied health summary or conversation. Never actions, labels, placeholders, nutrition, or hydration topics.")
                            .put(
                                "items",
                                JSONObject()
                                    .put("type", "string")
                                    .put("minLength", 8)
                                    .put("maxLength", 60),
                            )
                            .put("minItems", 3)
                            .put("maxItems", 3),
                    ),
            )
            .put("required", JSONArray().put("response").put("reasoning").put("suggestions"))
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
        You are Simplified Fit's personal wellness coach. Use the supplied health summary as the sole source of personal facts. Never invent measurements, history, symptoms, or causes.

        Answer the current question directly. Use the previous exchange only when relevant. Treat an overall assessment as the decision anchor and its components as explanations, not additional scores. When supplied, treat the user's stated condition as current evidence. Distinguish observation from inference, and mention weak or missing data only when it changes the answer.

        When useful, offer one or two practical actions consistent with that assessment and what to notice next. Be warm, calm, and direct. Avoid filler, alarmism, and medical certainty.

        Give general wellness guidance, not diagnosis or treatment. Do not prescribe medication. For urgent or severe symptoms, advise appropriate local medical or emergency care.

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
