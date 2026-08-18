package com.kego.simplifiedfit.ui

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Test

class CoachTextTest {
    @Test
    fun `renders double asterisks as bold instead of visible markers`() {
        val rendered = coachMarkdown("Prioritize **rest** and **recovery**.")

        assertEquals("Prioritize rest and recovery.", rendered.text)
        assertEquals(2, rendered.spanStyles.size)
        assertEquals(FontWeight.Bold, rendered.spanStyles.first().item.fontWeight)
        assertEquals("rest", rendered.text.substring(rendered.spanStyles.first().start, rendered.spanStyles.first().end))
    }

    @Test
    fun `hides an unfinished bold marker while streaming`() {
        val rendered = coachMarkdown("Start **gentle")

        assertEquals("Start gentle", rendered.text)
        assertEquals(FontWeight.Bold, rendered.spanStyles.single().item.fontWeight)
    }

    @Test
    fun `formats coach loading time as seconds then minutes`() {
        assertEquals("0s", formatCoachElapsed(0))
        assertEquals("59s", formatCoachElapsed(59))
        assertEquals("1m 0s", formatCoachElapsed(60))
        assertEquals("2m 5s", formatCoachElapsed(125))
    }
}
