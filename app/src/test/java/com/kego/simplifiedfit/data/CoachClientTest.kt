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

        assertEquals("medium", payload.getJSONObject("reasoning").getString("effort"))
        assertFalse(payload.has("max_tokens"))
        assertFalse(payload.has("max_completion_tokens"))
    }

    @Test
    fun `asks for short scannable markdown responses`() {
        val prompt = coachPrompt(
            CoachRequest(
                message = "How am I doing?",
                healthContext = "Readiness: 90/100",
            ),
        )

        assertTrue(prompt.contains("Lead with the real point in one short sentence"))
        assertTrue(prompt.contains("Default to no more than 120 words"))
        assertTrue(prompt.contains("use a Markdown bullet list"))
        assertTrue(prompt.contains("Bold only important numbers and recommended actions"))
    }

    @Test
    fun `asks for a natural human coaching voice`() {
        val prompt = coachPrompt(
            CoachRequest(
                message = "What should I focus on today?",
                healthContext = "Readiness: 90/100",
            ),
        )

        assertTrue(prompt.contains("thoughtful person, not a customer-support bot"))
        assertTrue(prompt.contains("Have an opinion when the evidence supports one"))
        assertTrue(prompt.contains("supportive, practical choices rather than commands"))
        assertTrue(prompt.contains("Avoid canned praise, promotional language, inflated claims"))
        assertTrue(prompt.contains("silently remove anything that sounds obviously AI-generated"))
    }
}
