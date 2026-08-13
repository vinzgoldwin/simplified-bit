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
            ),
        )
        assertTrue(score in 94..100)
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
                priorActiveCalories = null,
                activeCaloriesBaseline = emptyList(),
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
                priorActiveCalories = 500.0,
                activeCaloriesBaseline = listOf(400.0, 500.0, 600.0, 700.0),
            ),
        )
        assertTrue(score >= 80)
    }
}
