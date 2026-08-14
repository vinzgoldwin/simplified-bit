package com.kego.simplifiedfit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoachClientTest {
    @Test
    fun `parses coach evidence and follow up suggestions`() {
        val answer = parseCoachAnswer(
            """
            {
              "response": "Prioritize recovery today.",
              "evidence": {
                "signals": ["HRV is below baseline", "Resting heart rate is elevated"],
                "interpretation": "The combined pattern suggests reduced recovery."
              },
              "suggestions": ["What should I monitor?", "Can I walk today?", "How was my sleep?"]
            }
            """.trimIndent(),
        )

        assertEquals("Prioritize recovery today.", answer.response)
        assertEquals(listOf("HRV is below baseline", "Resting heart rate is elevated"), answer.evidence?.signals)
        assertEquals("The combined pattern suggests reduced recovery.", answer.evidence?.interpretation)
        assertEquals(3, answer.suggestions.size)
    }

    @Test
    fun `parses older codex responses without evidence`() {
        val answer = parseCoachAnswer(
            """{"response":"Take it easy.","suggestions":[]}""",
        )

        assertEquals("Take it easy.", answer.response)
        assertNull(answer.evidence)
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
}
