package com.kego.simplifiedfit.ui

import com.kego.simplifiedfit.data.DailyHealth
import com.kego.simplifiedfit.domain.SleepScoreBreakdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class AppModelTest {
    @Test
    fun `new snapshot never contains sample health data`() {
        val snapshot = HealthSnapshot()

        assertEquals(0, snapshot.steps)
        assertEquals(0, snapshot.sleepScore)
        assertEquals(0, snapshot.readiness)
        assertEquals("Never", snapshot.lastSync)
        assertTrue(snapshot.stepTrend.isEmpty())
    }

    @Test
    fun `coach context includes recovery baselines sleep details and trends`() {
        val currentDate = LocalDate.of(2026, 8, 14)
        val prior = DailyHealth(
            date = currentDate.minusDays(1),
            steps = 8_000,
            restingHeartRate = 60.0,
            hrv = 50.0,
            asleepMinutes = 450,
            sleepMidpointMinute = 180,
        )
        val current = DailyHealth(
            date = currentDate,
            steps = 6_400,
            latestHeartRate = 78,
            restingHeartRate = 64.0,
            hrv = 42.0,
            totalCalories = 1_850.0,
            activeCalories = 520.0,
            asleepMinutes = 430,
            awakeMinutes = 38,
            restlessnessMinutes = 24,
            remMinutes = 96,
            lightMinutes = 262,
            deepMinutes = 72,
            sleepMidpointMinute = 210,
            readinessScore = 72,
        )

        val context = buildCoachContext(
            listOf(current, prior),
            current,
            SleepScoreBreakdown(total = 81),
            LocalDateTime.of(2026, 8, 14, 14, 35),
        )

        assertTrue(context.contains("Readiness: 72/100 (high)"))
        assertTrue(context.contains("Overall recovery supports a normal or challenging workout"))
        assertTrue(context.contains("Use the components below to explain it, not score recovery again"))
        assertTrue(context.contains("HRV: 42.0 ms"))
        assertTrue(context.contains("HRV 28-day baseline: 50.0 ms from 1 prior day"))
        assertTrue(context.contains("Deep: 72 min"))
        assertTrue(context.contains("Average steps: 7,200 steps across 2 days"))
        assertTrue(context.contains("HRV trend: 50.0 ms to 42.0 ms (-16.0%, 2 readings)"))
        assertTrue(context.contains("Missing latest-day fields: none"))
    }

    @Test
    fun `coach context labels unavailable data instead of using zero`() {
        val current = DailyHealth(date = LocalDate.of(2026, 8, 14))

        val context = buildCoachContext(
            listOf(current),
            current,
            null,
            LocalDateTime.of(2026, 8, 14, 14, 35),
        )

        assertTrue(context.contains("Readiness: unavailable"))
        assertTrue(context.contains("Do not infer workout readiness from one metric alone"))
        assertTrue(context.contains("HRV: unavailable"))
        assertTrue(context.contains("Average sleep: unavailable"))
        assertTrue(context.contains("Missing latest-day fields: readiness, sleep score"))
    }

    @Test
    fun `coach context anchors training advice to overall readiness`() {
        val date = LocalDate.of(2026, 8, 14)
        val cases = listOf(
            80 to "supports a normal or challenging workout",
            50 to "A normal workout is reasonable",
            20 to "Favor rest or light activity",
        )

        cases.forEach { (score, guidance) ->
            val current = DailyHealth(date = date, readinessScore = score)
            val context = buildCoachContext(listOf(current), current, null)

            assertTrue(context.contains(guidance))
        }
    }
}
