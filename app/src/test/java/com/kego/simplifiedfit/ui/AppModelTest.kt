package com.kego.simplifiedfit.ui

import com.kego.simplifiedfit.data.DailyHealth
import com.kego.simplifiedfit.domain.SleepScoreBreakdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
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
    fun `activity duration uses compact history formatting`() {
        assertEquals("42:18", formatActivityDuration(2_538))
        assertEquals("4h 28m", formatActivityDuration(16_080))
        assertEquals("1:04:12", formatActivityDuration(3_852))
        assertEquals("3h 13m", formatActivityTotalDuration(11_634))
        assertEquals("1:05:00", formatActivityDetailDuration(3_900))
    }

    @Test
    fun `pace is limited to walking and running activities`() {
        assertTrue(supportsActivityPace("WALKING"))
        assertTrue(supportsActivityPace("TRAIL_RUN"))
        assertTrue(supportsActivityPace("TREADMILL"))
        assertFalse(supportsActivityPace("MOTORCYCLE"))
        assertFalse(supportsActivityPace("BIKING"))
        assertFalse(supportsActivityPace("STRENGTH_TRAINING"))
        assertFalse(supportsActivityDistance("MOTORCYCLE"))
        assertFalse(supportsActivityDistance("WORKOUT"))
        assertFalse(supportsActivityDistance("CARDIO_WORKOUT"))
        assertFalse(supportsActivityDistance("STRENGTH_TRAINING"))
        assertTrue(supportsActivityDistance("WALKING"))
        assertTrue(supportsActivityDistance("ROWING"))
    }

    @Test
    fun `motorcycle details exclude movement sensor artifacts`() {
        val metrics = activityDetailMetrics(
            activity(
                type = "MOTORCYCLE",
                distanceMeters = 10.0,
                averageSpeedMetersPerSecond = 0.005,
                averagePaceSeconds = 199_278.0,
                elevationGainMeters = 12.0,
            ),
        )

        assertEquals(
            listOf("Active time", "Active energy", "Average heart rate", "Active zone minutes"),
            metrics.map(ActivityMetric::label),
        )
    }

    @Test
    fun `motorcycle cards never fall back to sensor distance`() {
        val motorcycle = activity(
            type = "MOTORCYCLE",
            caloriesKcal = null,
            distanceMeters = 10.0,
            averageHeartRate = null,
            activeZoneMinutes = null,
        )

        assertEquals("", activityListMetric(motorcycle))
    }

    @Test
    fun `walking details prefer pace and include walking metrics`() {
        val metrics = activityDetailMetrics(
            activity(
                type = "WALKING",
                distanceMeters = 3_700.0,
                steps = 5_441,
                averageSpeedMetersPerSecond = 1.15,
                averagePaceSeconds = 875.0,
                elevationGainMeters = 42.0,
            ),
        )

        assertEquals(
            listOf(
                "Distance",
                "Average pace",
                "Active time",
                "Elevation gain",
                "Active energy",
                "Average heart rate",
                "Steps",
                "Active zone minutes",
            ),
            metrics.map(ActivityMetric::label),
        )
        assertEquals(ActivityMetric("Average pace", "14:35", "/km"), metrics[1])
    }

    @Test
    fun `walking details reject an implausible derived pace`() {
        val metrics = activityDetailMetrics(
            activity(
                type = "WALKING",
                distanceMeters = 10.0,
                averagePaceSeconds = null,
            ),
        )

        assertFalse(metrics.any { it.label == "Average pace" })
    }

    @Test
    fun `cycling details use speed instead of pace`() {
        val metrics = activityDetailMetrics(
            activity(
                type = "BIKING",
                distanceMeters = 12_400.0,
                averageSpeedMetersPerSecond = 5.0,
                averagePaceSeconds = 200.0,
                elevationGainMeters = 85.0,
            ),
        )

        assertEquals(
            listOf(
                "Distance",
                "Average speed",
                "Active time",
                "Elevation gain",
                "Active energy",
                "Average heart rate",
                "Active zone minutes",
            ),
            metrics.map(ActivityMetric::label),
        )
        assertFalse(metrics.any { it.label == "Average pace" })
    }

    @Test
    fun `strength and cardio details only show relevant available data`() {
        val strength = activityDetailMetrics(
            activity(
                type = "STRENGTH_TRAINING",
                distanceMeters = 250.0,
                steps = 40,
                averageSpeedMetersPerSecond = 1.0,
                elevationGainMeters = 5.0,
            ),
        )
        val cardio = activityDetailMetrics(activity(type = "CARDIO_WORKOUT"))

        assertEquals(
            listOf("Active time", "Active energy", "Average heart rate", "Active zone minutes", "Steps"),
            strength.map(ActivityMetric::label),
        )
        assertEquals(
            listOf("Active time", "Active energy", "Average heart rate", "Active zone minutes"),
            cardio.map(ActivityMetric::label),
        )
    }

    @Test
    fun `other distance activities use speed without inventing pace`() {
        val metrics = activityDetailMetrics(
            activity(
                type = "ROWING",
                distanceMeters = 2_000.0,
                steps = 300,
                averageSpeedMetersPerSecond = 2.5,
                averagePaceSeconds = 400.0,
                elevationGainMeters = 3.0,
            ),
        )

        assertEquals(
            listOf(
                "Active time",
                "Active energy",
                "Average heart rate",
                "Active zone minutes",
                "Distance",
                "Average speed",
                "Steps",
                "Elevation gain",
            ),
            metrics.map(ActivityMetric::label),
        )
        assertFalse(metrics.any { it.label == "Average pace" })
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

        assertTrue(context.contains("Recovery estimate: 72/100 (high)"))
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

        assertTrue(context.contains("Recovery estimate: unavailable"))
        assertTrue(context.contains("Do not infer workout readiness from one metric alone"))
        assertTrue(context.contains("HRV: unavailable"))
        assertTrue(context.contains("Average sleep: unavailable"))
        assertTrue(context.contains("Missing latest-day fields: recovery estimate, sleep score"))
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

    private fun activity(
        type: String,
        caloriesKcal: Int? = 150,
        distanceMeters: Double? = null,
        steps: Int? = null,
        averageHeartRate: Int? = 120,
        activeZoneMinutes: Int? = 12,
        averageSpeedMetersPerSecond: Double? = null,
        averagePaceSeconds: Double? = null,
        elevationGainMeters: Double? = null,
    ) = ActivitySummary(
        id = "activity-$type",
        startTime = Instant.parse("2026-08-22T06:00:00Z"),
        type = type,
        name = type.lowercase(),
        activeDurationSeconds = 1_800,
        caloriesKcal = caloriesKcal,
        distanceMeters = distanceMeters,
        steps = steps,
        averageHeartRate = averageHeartRate,
        activeZoneMinutes = activeZoneMinutes,
        averageSpeedMetersPerSecond = averageSpeedMetersPerSecond,
        averagePaceSeconds = averagePaceSeconds,
        elevationGainMeters = elevationGainMeters,
    )
}
