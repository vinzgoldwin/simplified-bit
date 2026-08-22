package com.kego.simplifiedfit.ui

import com.kego.simplifiedfit.data.DailyHealth
import com.kego.simplifiedfit.domain.SleepScoreBreakdown
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal fun buildCoachContext(
    days: List<DailyHealth>,
    current: DailyHealth,
    sleepBreakdown: SleepScoreBreakdown?,
    preparedAt: LocalDateTime = LocalDateTime.now(),
): String {
    val priorDays = days.filter {
        it.date >= current.date.minusDays(28) && it.date.isBefore(current.date)
    }
    val recentDays = days.filter {
        it.date >= current.date.minusDays(6) && !it.date.isAfter(current.date)
    }.sortedBy { it.date }
    val hrvBaseline = priorDays.mapNotNull { it.hrv }.averageMetricOrNull()
    val restingHeartRateBaseline = priorDays.mapNotNull { it.restingHeartRate }.averageMetricOrNull()
    val recentSleep = recentDays.mapNotNull { it.asleepMinutes }
    val recentSteps = recentDays.mapNotNull { it.steps }
    val sleepTiming = sleepTimingVariation(recentDays)
    val validSleepNights = days.count { (it.asleepMinutes ?: 0) >= 180 }
    val ageInDays = java.time.LocalDate.now().toEpochDay() - current.date.toEpochDay()
    val readiness = when (val score = current.readinessScore) {
        null -> "Readiness: unavailable\nTraining guidance: Do not infer workout readiness from one metric alone. Ask how the user feels and give conditional advice."
        in 65..100 -> "Readiness: $score/100 (high)\nTraining guidance: Overall recovery supports a normal or challenging workout if the user feels well. A component below baseline does not override this assessment."
        in 30..64 -> "Readiness: $score/100 (moderate)\nTraining guidance: A normal workout is reasonable if the user feels well. Adjust intensity based on energy, soreness, symptoms, and the warm-up."
        else -> "Readiness: $score/100 (low)\nTraining guidance: Favor rest or light activity and reassess based on how the user feels."
    }

    val missing = buildList {
        if (current.readinessScore == null) add("readiness")
        if (sleepBreakdown == null) add("sleep score")
        if (current.asleepMinutes == null) add("sleep duration")
        if (current.steps == null) add("steps")
        if (current.latestHeartRate == null) add("latest heart rate")
        if (current.restingHeartRate == null) add("resting heart rate")
        if (current.hrv == null) add("HRV")
        if (current.totalCalories == null) add("total calories")
        if (current.activeCalories == null) add("active calories")
        if (current.awakeMinutes == null) add("awake time")
        if (current.restlessnessMinutes == null) add("restlessness")
        if (current.remMinutes == null) add("REM sleep")
        if (current.lightMinutes == null) add("light sleep")
        if (current.deepMinutes == null) add("deep sleep")
    }

    return """
        LATEST DAY (${current.date})
        Data age: ${when (ageInDays) {
            0L -> "today"
            1L -> "1 day old"
            else -> "$ageInDays days old"
        }}
        Sleep score: ${sleepBreakdown?.total.score()}
        Sleep: ${current.asleepMinutes.minutes()} of 480 min target
        Steps: ${current.steps.number()}
        Latest heart rate: ${current.latestHeartRate.metric("bpm")}
        Total calories: ${current.totalCalories.metric("kcal", 0)}
        Active calories: ${current.activeCalories.metric("kcal", 0)}

        OVERALL TRAINING READINESS
        $readiness
        Readiness already combines recent sleep, HRV, and resting heart rate. Use the components below to explain it, not score recovery again.

        READINESS COMPONENTS
        HRV: ${current.hrv.metric("ms")}
        HRV 28-day baseline: ${hrvBaseline.baseline("ms", priorDays.count { it.hrv != null })}
        HRV vs baseline: ${percentDifference(current.hrv, hrvBaseline)}
        Resting heart rate: ${current.restingHeartRate.metric("bpm")}
        Resting heart rate 28-day baseline: ${restingHeartRateBaseline.baseline("bpm", priorDays.count { it.restingHeartRate != null })}
        Resting heart rate vs baseline: ${absoluteDifference(current.restingHeartRate, restingHeartRateBaseline, "bpm")}

        LAST SLEEP
        Duration: ${current.asleepMinutes.minutes()}
        Awake: ${current.awakeMinutes.minutes()}
        Restless: ${current.restlessnessMinutes.minutes()}
        REM: ${current.remMinutes.minutes()}
        Light: ${current.lightMinutes.minutes()}
        Deep: ${current.deepMinutes.minutes()}

        7-DAY PATTERNS
        Average sleep: ${recentSleep.averageCountOrNull().averageMinutes(recentSleep.size)}
        Sleep timing variation: ${sleepTiming?.let { "${it.roundToInt()} min across ${recentDays.count { day -> day.sleepMidpointMinute != null }.counted("night")}; lower is more consistent" } ?: "unavailable; need at least 2 nights"}
        Average steps: ${recentSteps.averageCountOrNull().averageNumber("steps", recentSteps.size)}
        HRV trend: ${trend(recentDays.mapNotNull { day -> day.hrv?.let { day.date to it } }, "ms", percent = true)}
        Resting heart rate trend: ${trend(recentDays.mapNotNull { day -> day.restingHeartRate?.let { day.date to it } }, "bpm")}

        DATA QUALITY
        Valid sleep nights in stored history: $validSleepNights of ${days.size}
        Snapshot prepared: ${preparedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))} local time
        Missing latest-day fields: ${missing.ifEmpty { listOf("none") }.joinToString()}
    """.trimIndent()
}

private fun List<Double>.averageMetricOrNull(): Double? = takeIf { it.isNotEmpty() }?.average()

private fun List<Int>.averageCountOrNull(): Double? = takeIf { it.isNotEmpty() }?.average()

private fun Int?.score(): String = this?.let { "$it/100" } ?: "unavailable"

private fun Int?.minutes(): String = this?.let { "$it min" } ?: "unavailable"

private fun Int?.number(): String = this?.let { String.format(Locale.US, "%,d", it) } ?: "unavailable"

private fun Number?.metric(unit: String, decimals: Int = 1): String =
    this?.let { String.format(Locale.US, "%.${decimals}f %s", it.toDouble(), unit) } ?: "unavailable"

private fun Double?.baseline(unit: String, samples: Int): String =
    this?.let { "${it.metric(unit)} from ${samples.counted("prior day")}" } ?: "unavailable; no prior readings"

private fun Double?.averageMinutes(samples: Int): String =
    this?.let { "${it.roundToInt()} min across ${samples.counted("night")}" } ?: "unavailable"

private fun Double?.averageNumber(unit: String, samples: Int): String =
    this?.let { "${String.format(Locale.US, "%,d", it.roundToInt())} $unit across ${samples.counted("day")}" } ?: "unavailable"

private fun Int.counted(noun: String): String = "$this $noun${if (this == 1) "" else "s"}"

private fun percentDifference(value: Double?, baseline: Double?): String {
    if (value == null || baseline == null || baseline == 0.0) return "unavailable"
    return String.format(Locale.US, "%+.1f%%", (value - baseline) / baseline * 100.0)
}

private fun absoluteDifference(value: Double?, baseline: Double?, unit: String): String {
    if (value == null || baseline == null) return "unavailable"
    return String.format(Locale.US, "%+.1f %s", value - baseline, unit)
}

private fun trend(readings: List<Pair<java.time.LocalDate, Double>>, unit: String, percent: Boolean = false): String {
    if (readings.size < 2) return "unavailable; need at least 2 readings"
    val first = readings.first().second
    val last = readings.last().second
    val change = if (percent && first != 0.0) {
        String.format(Locale.US, "%+.1f%%", (last - first) / first * 100.0)
    } else {
        String.format(Locale.US, "%+.1f %s", last - first, unit)
    }
    return "${first.metric(unit)} to ${last.metric(unit)} ($change, ${readings.size} readings)"
}

private fun sleepTimingVariation(days: List<DailyHealth>): Double? {
    val midpoints = days.mapNotNull { it.sleepMidpointMinute }
    if (midpoints.size < 2) return null
    val anchor = midpoints.first()
    val unwrapped = midpoints.map { anchor + circularDifference(it, anchor) }
    val mean = unwrapped.average()
    return sqrt(unwrapped.map { (it - mean) * (it - mean) }.average())
}

private fun circularDifference(value: Int, anchor: Int): Int =
    ((value - anchor + 720) % 1_440 + 1_440) % 1_440 - 720
