package com.kego.simplifiedfit.domain

import kotlin.math.abs
import kotlin.math.roundToInt

data class SleepSignals(
    val asleepMinutes: Int,
    val targetMinutes: Int,
    val inBedMinutes: Int,
    val remMinutes: Int,
    val deepMinutes: Int,
    val midpointDeviationMinutes: Int,
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
    fun sleep(signals: SleepSignals): Int {
        if (signals.asleepMinutes <= 0 || signals.targetMinutes <= 0) return 0

        val duration = ratio(signals.asleepMinutes.toDouble(), signals.targetMinutes.toDouble())
        val efficiency = ratio(signals.asleepMinutes.toDouble(), signals.inBedMinutes.coerceAtLeast(1).toDouble())
        val restorative = ratio(
            (signals.remMinutes + signals.deepMinutes).toDouble(),
            signals.asleepMinutes * .45,
        )
        val consistency = (1.0 - abs(signals.midpointDeviationMinutes) / 120.0).coerceIn(0.0, 1.0)

        return weighted(
            duration to .45,
            efficiency to .25,
            restorative to .20,
            consistency to .10,
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

    private fun ratio(value: Double, target: Double): Double = (value / target).coerceIn(0.0, 1.0)

    private fun percentile(value: Double?, baseline: List<Double>): Double? {
        if (value == null || baseline.isEmpty()) return null
        return baseline.count { it <= value }.toDouble() / baseline.size
    }

    private fun weighted(vararg values: Pair<Double, Double>): Int =
        (values.sumOf { it.first * it.second } * 100).roundToInt().coerceIn(0, 100)
}
