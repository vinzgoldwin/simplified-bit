package com.kego.simplifiedfit.domain

import kotlin.math.roundToInt
import kotlin.math.sqrt

data class SleepSignals(
    val asleepMinutes: Int,
    val targetMinutes: Int,
    val inBedMinutes: Int,
    val remMinutes: Int? = null,
    val deepMinutes: Int? = null,
    val awakeMinutes: Int? = null,
    val restlessnessMinutes: Int? = null,
)

data class SleepScoreBreakdown(
    val duration: Int = 0,
    val continuity: Int = 0,
    val restlessness: Int? = null,
    val rem: Int? = null,
    val deep: Int? = null,
    val total: Int = 0,
)

data class ReadinessSignals(
    val hrv: Double?,
    val hrvBaseline: List<Double>,
    val restingHeartRate: Double?,
    val restingHeartRateBaseline: List<Double>,
    val recentSleep: List<ReadinessSleep>,
)

data class ReadinessSleep(
    val asleepMinutes: Int,
    val midpointMinute: Int,
)

object ScoreCalculator {
    fun sleep(signals: SleepSignals): Int = sleepBreakdown(signals).total

    fun sleepBreakdown(signals: SleepSignals): SleepScoreBreakdown {
        if (signals.asleepMinutes <= 0 || signals.targetMinutes <= 0) return SleepScoreBreakdown()

        // Fitbit scores sleep duration against its healthy range, independently of the
        // user's shorter goal shown in the UI.
        val duration = ratio(
            signals.asleepMinutes.toDouble(),
            (signals.targetMinutes + SLEEP_DURATION_RANGE_MINUTES).toDouble(),
        )
        val inBed = maxOf(signals.inBedMinutes, signals.asleepMinutes).coerceAtLeast(1)
        val awakeMinutes = maxOf(
            signals.awakeMinutes ?: 0,
            (inBed - signals.asleepMinutes).coerceAtLeast(0),
        )
        val efficiency = ratio(signals.asleepMinutes.toDouble(), inBed.toDouble())
        val awakeQuality = (1.0 - awakeMinutes / 60.0).coerceIn(0.0, 1.0)
        val continuity = (efficiency * .60 + awakeQuality * .40).coerceIn(0.0, 1.0)
        val restlessness = signals.restlessnessMinutes?.let { restlessnessScore(it) }
        val rem = signals.remMinutes?.let { stageScore(it, signals.asleepMinutes, .20, .30) }
        val deep = signals.deepMinutes?.let { stageScore(it, signals.asleepMinutes, .13, .23) }

        val values = buildList {
            add(duration to .45)
            add(continuity to .20)
            restlessness?.let { add(it to .10) }
            rem?.let { add(it to .125) }
            deep?.let { add(it to .125) }
        }

        return SleepScoreBreakdown(
            duration = points(duration),
            continuity = points(continuity),
            restlessness = restlessness?.let(::points),
            rem = rem?.let(::points),
            deep = deep?.let(::points),
            total = points(weighted(values)),
        )
    }

    fun readiness(signals: ReadinessSignals): Int {
        val recentSleep = signals.recentSleep.takeLast(7)
        if (recentSleep.isEmpty()) return 0

        val hrvDeviation = baselineDeviation(signals.hrv, signals.hrvBaseline, minimumSpread = 1.0)
        val heartRateDeviation = baselineDeviation(
            signals.restingHeartRate,
            signals.restingHeartRateBaseline,
            minimumSpread = .5,
        )
        val factors = buildList {
            add(sleepRecovery(recentSleep) to SLEEP_RECOVERY_WEIGHT)
            hrvDeviation?.let { add(baselineScore(it, higherIsBetter = true) to HRV_WEIGHT) }
            heartRateDeviation?.let { add(baselineScore(it, higherIsBetter = false) to HEART_RATE_WEIGHT) }
        }
        // Large simultaneous deviations are recovery warnings, not values that good sleep
        // should average back toward the middle of the scale.
        val severePenalty = maxOf(-(hrvDeviation ?: 0.0) - SEVERE_DEVIATION, 0.0) +
            maxOf((heartRateDeviation ?: 0.0) - SEVERE_DEVIATION, 0.0)

        return (weighted(factors) - severePenalty * SEVERE_DEVIATION_PENALTY)
            .roundToInt()
            .coerceIn(0, 100)
    }

    private fun ratio(value: Double, target: Double): Double = (value / target).coerceIn(0.0, 1.0)

    private fun restlessnessScore(minutes: Int): Double =
        (1.0 - minutes.coerceAtLeast(0) / RESTLESSNESS_LIMIT_MINUTES).coerceIn(0.0, 1.0)

    private fun stageScore(minutes: Int, totalMinutes: Int, lower: Double, upper: Double): Double {
        val share = minutes.coerceAtLeast(0).toDouble() / totalMinutes.coerceAtLeast(1)
        return when {
            share < lower -> (share / lower).coerceIn(0.0, 1.0)
            share > upper -> (1.0 - (share - upper) / (1.0 - upper)).coerceIn(0.0, 1.0)
            else -> 1.0
        }
    }

    private fun baselineDeviation(value: Double?, baseline: List<Double>, minimumSpread: Double): Double? {
        if (value == null || baseline.isEmpty()) return null
        val mean = baseline.average()
        val standardDeviation = sqrt(baseline.map { (it - mean) * (it - mean) }.average())
        return (value - mean) / standardDeviation.coerceAtLeast(minimumSpread)
    }

    private fun baselineScore(deviation: Double, higherIsBetter: Boolean): Double {
        val direction = if (higherIsBetter) 1.0 else -1.0
        return (BASELINE_SCORE + deviation * direction * BASELINE_DEVIATION_POINTS).coerceIn(0.0, 100.0)
    }

    private fun sleepRecovery(sleeps: List<ReadinessSleep>): Double {
        val duration = sleeps.map { ratio(it.asleepMinutes.toDouble(), READINESS_SLEEP_TARGET_MINUTES) }.average()
        val anchor = sleeps.first().midpointMinute
        val unwrappedMidpoints = sleeps.map { sleep ->
            anchor + circularDifference(sleep.midpointMinute, anchor)
        }
        val mean = unwrappedMidpoints.average()
        val standardDeviation = sqrt(unwrappedMidpoints.map { (it - mean) * (it - mean) }.average())
        val consistency = (1.0 - standardDeviation / SLEEP_CONSISTENCY_LIMIT_MINUTES).coerceIn(0.0, 1.0)
        return (duration * SLEEP_DURATION_WEIGHT + consistency * SLEEP_CONSISTENCY_WEIGHT) * 100.0
    }

    private fun circularDifference(value: Int, anchor: Int): Int =
        ((value - anchor + MINUTES_PER_HALF_DAY) % MINUTES_PER_DAY + MINUTES_PER_DAY) % MINUTES_PER_DAY -
            MINUTES_PER_HALF_DAY

    private fun points(value: Double): Int = (value * 100).roundToInt().coerceIn(0, 100)

    private fun weighted(values: List<Pair<Double, Double>>): Double {
        val totalWeight = values.sumOf { it.second }
        if (totalWeight == 0.0) return 0.0
        return values.sumOf { it.first * it.second } / totalWeight
    }

    private const val RESTLESSNESS_LIMIT_MINUTES = 22.0
    private const val SLEEP_DURATION_RANGE_MINUTES = 85
    private const val BASELINE_SCORE = 90.0
    private const val BASELINE_DEVIATION_POINTS = 10.0
    private const val SLEEP_RECOVERY_WEIGHT = .30
    private const val HRV_WEIGHT = .50
    private const val HEART_RATE_WEIGHT = .20
    private const val SLEEP_DURATION_WEIGHT = .80
    private const val SLEEP_CONSISTENCY_WEIGHT = .20
    private const val SEVERE_DEVIATION = 1.5
    private const val SEVERE_DEVIATION_PENALTY = 39.0
    private const val READINESS_SLEEP_TARGET_MINUTES = 480.0
    private const val SLEEP_CONSISTENCY_LIMIT_MINUTES = 180.0
    private const val MINUTES_PER_HALF_DAY = 720
    private const val MINUTES_PER_DAY = 1_440
}
