package com.kego.simplifiedfit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachClientTest {
    @Test
    fun `parses coach reasoning and follow up suggestions`() {
        val answer = parseCoachAnswer(
            """
            {
              "response": "Prioritize recovery today.",
              "reasoning": ["HRV is below baseline", "Resting heart rate is elevated"],
              "suggestions": ["What should I monitor?", "Can I walk today?", "How was my sleep?"]
            }
            """.trimIndent(),
        )

        assertEquals("Prioritize recovery today.", answer.response)
        assertEquals(listOf("HRV is below baseline", "Resting heart rate is elevated"), answer.reasoning)
        assertEquals(3, answer.suggestions.size)
    }

    @Test
    fun `parses older codex evidence as reasoning`() {
        val answer = parseCoachAnswer(
            """{"response":"Take it easy.","evidence":{"signals":["HRV is low"],"interpretation":"Recovery is reduced."},"suggestions":[]}""",
        )

        assertEquals("Take it easy.", answer.response)
        assertEquals(listOf("HRV is low", "Recovery is reduced."), answer.reasoning)
    }

    @Test
    fun `replaces malformed and ungrounded follow ups`() {
        val questions = listOf(
            "Did you eat or hydrate differently today?",
            "Action: Drink water now",
            "followUpQuestions__3_items_under_60_chars_each)",
        ).validFollowUpQuestions()

        assertEquals(3, questions.size)
        assertFalse(questions.any { it.contains('_') || it.contains("you", ignoreCase = true) })
        assertEquals("What stands out most for me?", questions.first())
    }

    @Test
    fun `decodes a partial streamed response string`() {
        assertEquals(
            "Recovery first.\nTake a walk",
            decodedResponsePrefix("""{"response":"Recovery first.\nTake a walk"""),
        )
    }

    @Test
    fun `holds incomplete unicode escapes until the next chunk`() {
        val prefix = "{\"response\":\"HRV " + "\\" + "u"
        assertEquals("HRV ", decodedResponsePrefix(prefix))
        assertEquals("HRV ↓", decodedResponsePrefix(prefix + "2193"))
    }

    @Test
    fun `does not cap the structured answer`() {
        val payload = OpenRouterCoachClient("test-key").openRouterPayload(
            CoachRequest(
                message = "How am I doing?",
                healthContext = "Readiness: 90/100",
            ),
            stream = true,
        )

        assertEquals("none", payload.getJSONObject("reasoning").getString("effort"))
        assertFalse(payload.has("max_tokens"))
        assertFalse(payload.has("max_completion_tokens"))
    }

    @Test
    fun `keeps coach instructions concise and grounded`() {
        val prompt = coachPrompt(
            CoachRequest(
                message = "How am I doing?",
                healthContext = "Readiness: 90/100",
            ),
        )
        val instructions = prompt.substringBefore("HEALTH SUMMARY")

        assertTrue(instructions.length < 1_000)
        assertTrue(instructions.contains("sole source of personal facts"))
        assertTrue(instructions.contains("overall assessment as the decision anchor"))
        assertTrue(instructions.contains("components as explanations, not additional scores"))
        assertTrue(instructions.contains("general wellness guidance, not diagnosis or treatment"))
    }

    @Test
    fun `defines an interpretation first response contract`() {
        val payload = OpenRouterCoachClient("test-key").openRouterPayload(
            CoachRequest(
                message = "What should I focus on today?",
                healthContext = "Readiness: 90/100",
            ),
            stream = true,
        )
        val schema = payload.getJSONObject("response_format")
            .getJSONObject("json_schema")
            .getJSONObject("schema")
        val properties = schema.getJSONObject("properties")
        val responseDescription = properties.getJSONObject("response").getString("description")
        val suggestionItems = properties.getJSONObject("suggestions").getJSONObject("items")

        assertTrue(responseDescription.contains("Interpret measurements into meaning and action"))
        assertTrue(responseDescription.contains("exact value only when the user asks"))
        assertEquals(60, suggestionItems.getInt("maxLength"))
    }
}
