package com.kego.simplifiedfit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ScoreCalculatorTest {
    @Test
    fun `excellent sleep stays near the top of the scale`() {
        val score = ScoreCalculator.sleep(
            SleepSignals(
                asleepMinutes = 480,
                targetMinutes = 480,
                inBedMinutes = 490,
                remMinutes = 110,
                deepMinutes = 110,
                awakeMinutes = 10,
                restlessnessMinutes = 4,
            ),
        )
        assertTrue(score in 90..95)
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
                awakeMinutes = 6,
                restlessnessMinutes = 4,
            ),
        )
        val fragmented = ScoreCalculator.sleep(
            SleepSignals(
                asleepMinutes = 480,
                targetMinutes = 480,
                inBedMinutes = 540,
                remMinutes = 60,
                deepMinutes = 30,
                awakeMinutes = 60,
                restlessnessMinutes = 30,
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
        assertEquals(null, breakdown.restlessness)
        assertTrue(breakdown.total >= 85)
    }

    @Test
    fun `reference sleep card scores 88`() {
        val breakdown = ScoreCalculator.sleepBreakdown(
            SleepSignals(
                asleepMinutes = 499,
                targetMinutes = 480,
                inBedMinutes = 505,
                remMinutes = 121,
                deepMinutes = 97,
                awakeMinutes = 6,
                restlessnessMinutes = 12,
            ),
        )

        assertEquals(88, breakdown.total)
        assertEquals(45, breakdown.restlessness)
    }

    @Test
    fun `readiness waits for a physiological baseline`() {
        val score = ScoreCalculator.readiness(
            ReadinessSignals(
                hrv = null,
                hrvBaseline = emptyList(),
                restingHeartRate = null,
                restingHeartRateBaseline = emptyList(),
                recentSleep = List(7) { ReadinessSleep(480, 300) },
            ),
        )
        assertEquals(0, score)
    }

    @Test
    fun `lower resting heart rate and higher hrv improve readiness`() {
        val score = ScoreCalculator.readiness(
            ReadinessSignals(
                hrv = 70.0,
                hrvBaseline = listOf(38.0, 40.0, 45.0, 50.0, 55.0, 60.0, 62.0),
                restingHeartRate = 52.0,
                restingHeartRateBaseline = listOf(52.0, 54.0, 56.0, 58.0, 60.0, 62.0, 64.0),
                recentSleep = List(7) { ReadinessSleep(480, 300) },
            ),
        )
        assertTrue(score >= 70)
    }

    @Test
    fun `poor autonomic signals are not averaged away by good sleep`() {
        val score = ScoreCalculator.readiness(
            ReadinessSignals(
                hrv = 77.6,
                hrvBaseline = listOf(105.7, 86.9, 83.7, 98.2, 79.4, 94.199, 95.1, 100.25, 89.449, 97.55),
                restingHeartRate = 57.0,
                restingHeartRateBaseline = listOf(53.0, 54.0, 56.0, 55.0, 55.0, 55.0, 55.0, 55.0, 56.0, 55.0),
                recentSleep = listOf(
                    ReadinessSleep(404, 354),
                    ReadinessSleep(428, 314),
                    ReadinessSleep(541, 259),
                    ReadinessSleep(496, 283),
                    ReadinessSleep(405, 339),
                    ReadinessSleep(499, 248),
                    ReadinessSleep(548, 310),
                ),
            ),
        )

        assertTrue(score < 30)
    }

    @Test
    fun `strong hrv with elevated resting heart rate stays moderate`() {
        val score = ScoreCalculator.readiness(
            ReadinessSignals(
                hrv = 117.0,
                hrvBaseline = listOf(105.7, 86.9, 83.7, 98.2, 79.4, 94.199, 95.1, 100.25, 89.449, 97.55, 77.6, 78.15, 91.95, 68.8),
                restingHeartRate = 58.0,
                restingHeartRateBaseline = listOf(53.0, 54.0, 56.0, 55.0, 55.0, 55.0, 55.0, 55.0, 56.0, 55.0, 57.0, 59.0, 59.0, 60.0),
                recentSleep = listOf(
                    ReadinessSleep(405, 339),
                    ReadinessSleep(499, 248),
                    ReadinessSleep(548, 310),
                    ReadinessSleep(203, 61),
                    ReadinessSleep(426, 145),
                    ReadinessSleep(564, 161),
                    ReadinessSleep(503, 256),
                ),
            ),
        )

        assertTrue(score in 30..64)
    }

    @Test
    fun `sleep scores track the Fitbit device history`() {
        val nights = listOf(
            SleepSignals(452, 480, 460, 113, 114, 8, 14) to 81,
            SleepSignals(471, 480, 476, 104, 131, 5, 13) to 87,
            SleepSignals(428, 480, 438, 113, 110, 10, 12) to 84,
            SleepSignals(523, 480, 532, 158, 123, 9, 8) to 91,
            SleepSignals(404, 480, 410, 99, 91, 6, 15) to 79,
            SleepSignals(428, 480, 438, 94, 108, 10, 9) to 85,
            SleepSignals(541, 480, 546, 136, 130, 5, 13) to 91,
            SleepSignals(496, 480, 501, 125, 126, 5, 18) to 87,
            SleepSignals(405, 480, 411, 117, 100, 6, 10) to 80,
            SleepSignals(499, 480, 505, 121, 97, 6, 12) to 88,
            SleepSignals(548, 480, 555, 140, 127, 7, 19) to 89,
        )

        nights.forEach { (signals, fitbitScore) ->
            assertTrue(abs(ScoreCalculator.sleep(signals) - fitbitScore) <= 2)
        }
    }
}
