package com.kego.simplifiedfit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreCalculatorTest {
    @Test
    fun `excellent sleep stays near the top of the scale`() {
        val score = ScoreCalculator.sleep(
            SleepSignals(
                asleepMinutes = 480,
                targetMinutes = 480,
                inBedMinutes = 500,
                remMinutes = 110,
                deepMinutes = 110,
                midpointDeviationMinutes = 10,
                awakeMinutes = 20,
            ),
        )
        assertTrue(score in 94..100)
    }

    @Test
    fun `awake time and unbalanced stages lower sleep score`() {
        val healthy = ScoreCalculator.sleep(
            SleepSignals(
                asleepMinutes = 480,
                targetMinutes = 480,
                inBedMinutes = 486,
                remMinutes = 120,
                deepMinutes = 90,
                midpointDeviationMinutes = 5,
                awakeMinutes = 6,
            ),
        )
        val fragmented = ScoreCalculator.sleep(
            SleepSignals(
                asleepMinutes = 480,
                targetMinutes = 480,
                inBedMinutes = 540,
                remMinutes = 60,
                deepMinutes = 30,
                midpointDeviationMinutes = 90,
                awakeMinutes = 60,
            ),
        )

        assertTrue(healthy > fragmented)
        assertTrue(fragmented < 80)
    }

    @Test
    fun `missing optional sleep signals are reweighted`() {
        val breakdown = ScoreCalculator.sleepBreakdown(
            SleepSignals(
                asleepMinutes = 480,
                targetMinutes = 480,
                inBedMinutes = 490,
                awakeMinutes = 10,
            ),
        )

        assertEquals(null, breakdown.rem)
        assertEquals(null, breakdown.deep)
        assertEquals(null, breakdown.consistency)
        assertTrue(breakdown.total >= 90)
    }

    @Test
    fun `midpoint consistency handles nights around midnight`() {
        assertTrue(ScoreCalculator.midpointDeviation(5, listOf(1_430, 1_420))!! <= 20)
    }

    @Test
    fun `readiness reweights signals when data is missing`() {
        val score = ScoreCalculator.readiness(
            ReadinessSignals(
                hrv = null,
                hrvBaseline = emptyList(),
                restingHeartRate = null,
                restingHeartRateBaseline = emptyList(),
                sleepScore = 80,
            ),
        )
        assertEquals(80, score)
    }

    @Test
    fun `lower resting heart rate and higher hrv improve readiness`() {
        val score = ScoreCalculator.readiness(
            ReadinessSignals(
                hrv = 70.0,
                hrvBaseline = listOf(40.0, 45.0, 50.0, 55.0, 60.0),
                restingHeartRate = 52.0,
                restingHeartRateBaseline = listOf(52.0, 56.0, 58.0, 60.0, 62.0),
                sleepScore = 88,
            ),
        )
        assertTrue(score >= 80)
    }

    @Test
    fun `readiness uses the past seven sleep scores`() {
        val score = ScoreCalculator.readiness(
            ReadinessSignals(
                hrv = null,
                hrvBaseline = emptyList(),
                restingHeartRate = null,
                restingHeartRateBaseline = emptyList(),
                sleepScore = 100,
                recentSleepScores = listOf(40, 40, 40, 40, 40, 40, 100),
            ),
        )

        assertEquals(49, score)
    }

    @Test
    fun `readiness gives the three Google Health signals equal weight`() {
        val score = ScoreCalculator.readiness(
            ReadinessSignals(
                hrv = 50.0,
                hrvBaseline = listOf(50.0),
                restingHeartRate = 60.0,
                restingHeartRateBaseline = listOf(60.0),
                recentSleepScores = listOf(90),
            ),
        )

        assertEquals(63, score)
    }
}
