package com.kego.simplifiedfit.domain

import kotlin.math.abs
import kotlin.math.roundToInt

data class SleepSignals(
    val asleepMinutes: Int,
    val targetMinutes: Int,
    val inBedMinutes: Int,
    val remMinutes: Int? = null,
    val deepMinutes: Int? = null,
    val midpointDeviationMinutes: Int? = null,
    val awakeMinutes: Int? = null,
)

data class SleepScoreBreakdown(
    val duration: Int = 0,
    val continuity: Int = 0,
    val rem: Int? = null,
    val deep: Int? = null,
    val consistency: Int? = null,
    val total: Int = 0,
)

data class ReadinessSignals(
    val hrv: Double?,
    val hrvBaseline: List<Double>,
    val restingHeartRate: Double?,
    val restingHeartRateBaseline: List<Double>,
    val sleepScore: Int?,
    val priorActiveCalories: Double?,
    val activeCaloriesBaseline: List<Double>,
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
        val rem = signals.remMinutes?.let { stageScore(it, signals.asleepMinutes, .20, .30) }
        val deep = signals.deepMinutes?.let { stageScore(it, signals.asleepMinutes, .13, .23) }
        val consistency = signals.midpointDeviationMinutes?.let {
            (1.0 - abs(it) / 120.0).coerceIn(0.0, 1.0)
        }

        val values = buildList {
            add(duration to .45)
            add(continuity to .25)
            rem?.let { add(it to .10) }
            deep?.let { add(it to .10) }
            consistency?.let { add(it to .10) }
        }

        return SleepScoreBreakdown(
            duration = points(duration),
            continuity = points(continuity),
            rem = rem?.let(::points),
            deep = deep?.let(::points),
            consistency = consistency?.let(::points),
            total = weighted(values),
        )
    }

    fun readiness(signals: ReadinessSignals): Int {
        val values = listOfNotNull(
            percentile(signals.hrv, signals.hrvBaseline)?.let { it to .35 },
            percentile(signals.restingHeartRate, signals.restingHeartRateBaseline)?.let { (1.0 - it) to .25 },
            signals.sleepScore?.let { (it.coerceIn(0, 100) / 100.0) to .30 },
            percentile(signals.priorActiveCalories, signals.activeCaloriesBaseline)?.let { (1.0 - it) to .10 },
        )
        if (values.isEmpty()) return 0
        val totalWeight = values.sumOf { it.second }
        return (values.sumOf { it.first * it.second } / totalWeight * 100).roundToInt().coerceIn(0, 100)
    }

    fun midpointDeviation(current: Int?, baseline: List<Int>): Int? {
        current ?: return null
        if (baseline.isEmpty()) return 0
        return circularMinuteDistance(current, circularMean(baseline))
    }

    private fun ratio(value: Double, target: Double): Double = (value / target).coerceIn(0.0, 1.0)

    private fun stageScore(minutes: Int, totalMinutes: Int, lower: Double, upper: Double): Double {
        val share = minutes.coerceAtLeast(0).toDouble() / totalMinutes.coerceAtLeast(1)
        return when {
            share < lower -> (share / lower).coerceIn(0.0, 1.0)
            share > upper -> (1.0 - (share - upper) / (1.0 - upper)).coerceIn(0.0, 1.0)
            else -> 1.0
        }
    }

    private fun percentile(value: Double?, baseline: List<Double>): Double? {
        if (value == null || baseline.isEmpty()) return null
        return baseline.count { it <= value }.toDouble() / baseline.size
    }

    private fun points(value: Double): Int = (value * 100).roundToInt().coerceIn(0, 100)

    private fun weighted(values: List<Pair<Double, Double>>): Int {
        val totalWeight = values.sumOf { it.second }
        if (totalWeight == 0.0) return 0
        return (values.sumOf { it.first * it.second } / totalWeight * 100).roundToInt().coerceIn(0, 100)
    }

    private fun circularMean(values: List<Int>): Int {
        val reference = values.first()
        val unwrapped = values.map { reference + signedMinuteDistance(reference, it) }
        return normalizeMinute(unwrapped.average().roundToInt())
    }

    private fun circularMinuteDistance(a: Int, b: Int): Int {
        val distance = abs(normalizeMinute(a) - normalizeMinute(b))
        return minOf(distance, 1_440 - distance)
    }

    private fun signedMinuteDistance(from: Int, to: Int): Int =
        (normalizeMinute(to) - normalizeMinute(from) + 720) % 1_440 - 720

    private fun normalizeMinute(value: Int): Int = ((value % 1_440) + 1_440) % 1_440
}
