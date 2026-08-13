package com.kego.simplifiedfit.domain

import kotlin.math.abs
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
    val sleepScore: Int? = null,
    val recentSleepScores: List<Int> = emptyList(),
)

object ScoreCalculator {
    fun sleep(signals: SleepSignals): Int = sleepBreakdown(signals).total

    fun sleepBreakdown(signals: SleepSignals): SleepScoreBreakdown {
        if (signals.asleepMinutes <= 0 || signals.targetMinutes <= 0) return SleepScoreBreakdown()

        val duration = ratio(signals.asleepMinutes.toDouble(), signals.targetMinutes.toDouble())
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
            add(duration to .40)
            add(continuity to .20)
            restlessness?.let { add(it to .20) }
            rem?.let { add(it to .10) }
            deep?.let { add(it to .10) }
        }

        return SleepScoreBreakdown(
            duration = points(duration),
            continuity = points(continuity),
            restlessness = restlessness?.let(::points),
            rem = rem?.let(::points),
            deep = deep?.let(::points),
            total = weighted(values),
        )
    }

    fun readiness(signals: ReadinessSignals): Int {
        val sleepScores = signals.recentSleepScores.takeLast(7).ifEmpty {
            listOfNotNull(signals.sleepScore)
        }
        val values = listOfNotNull(
            baselineScore(signals.hrv, signals.hrvBaseline, higherIsBetter = true)?.let { it to 1.0 },
            baselineScore(signals.restingHeartRate, signals.restingHeartRateBaseline, higherIsBetter = false)?.let { it to 1.0 },
            sleepScores.takeIf { it.isNotEmpty() }?.let { scores ->
                (scores.map { it.coerceIn(0, 100) }.average() / 100.0) to 1.0
            },
        )
        if (values.isEmpty()) return 0
        val totalWeight = values.sumOf { it.second }
        return (values.sumOf { it.first * it.second } / totalWeight * 100).roundToInt().coerceIn(0, 100)
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

    private fun baselineScore(value: Double?, baseline: List<Double>, higherIsBetter: Boolean): Double? {
        if (value == null || baseline.isEmpty()) return null
        val mean = baseline.average()
        val standardDeviation = sqrt(baseline.map { (it - mean) * (it - mean) }.average())
        val spread = standardDeviation.coerceAtLeast(maxOf(abs(mean) * MINIMUM_BASELINE_SPREAD, 1.0))
        val direction = if (higherIsBetter) 1.0 else -1.0
        val deviation = (value - mean) / spread * direction
        return (BASELINE_SCORE + deviation * POINTS_PER_STANDARD_DEVIATION).coerceIn(0.0, 1.0)
    }

    private fun points(value: Double): Int = (value * 100).roundToInt().coerceIn(0, 100)

    private fun weighted(values: List<Pair<Double, Double>>): Int {
        val totalWeight = values.sumOf { it.second }
        if (totalWeight == 0.0) return 0
        return (values.sumOf { it.first * it.second } / totalWeight * 100).roundToInt().coerceIn(0, 100)
    }

    private const val RESTLESSNESS_LIMIT_MINUTES = 22.0
    private const val BASELINE_SCORE = .50
    private const val POINTS_PER_STANDARD_DEVIATION = .13
    private const val MINIMUM_BASELINE_SPREAD = .05
}
