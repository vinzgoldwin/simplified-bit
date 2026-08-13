package com.kego.simplifiedfit.ui

import com.kego.simplifiedfit.domain.SleepScoreBreakdown

data class DayPoint(
    val label: String,
    val value: Float,
)

data class HealthSnapshot(
    val readiness: Int = 78,
    val sleepScore: Int = 82,
    val steps: Int = 8_426,
    val latestHeartRate: Int = 72,
    val restingHeartRate: Int = 61,
    val hrv: Double = 98.0,
    val lowHeartRate: Int = 54,
    val highHeartRate: Int = 132,
    val totalCalories: Int = 2_184,
    val activeCalories: Int = 612,
    val restingCalories: Int = 1_572,
    val sleepMinutes: Int = 462,
    val sleepTargetMinutes: Int = 480,
    val awakeMinutes: Int = 34,
    val restlessnessMinutes: Int = 12,
    val remMinutes: Int = 132,
    val lightMinutes: Int = 242,
    val deepMinutes: Int = 88,
    val sleepBreakdown: SleepScoreBreakdown = SleepScoreBreakdown(
        duration = 96,
        continuity = 73,
        restlessness = 45,
        rem = 100,
        deep = 100,
        total = 82,
    ),
    val lastSync: String = "8:42",
    val validNights: Int = 7,
    val stepTrend: List<DayPoint> = listOf(
        DayPoint("Friday", 6_210f), DayPoint("Saturday", 9_140f), DayPoint("Sunday", 7_860f),
        DayPoint("Monday", 10_240f), DayPoint("Tuesday", 7_430f), DayPoint("Wednesday", 8_920f),
        DayPoint("Thursday", 8_426f),
    ),
    val sleepTrend: List<DayPoint> = listOf(
        DayPoint("Friday", 78f), DayPoint("Saturday", 82f), DayPoint("Sunday", 75f),
        DayPoint("Monday", 86f), DayPoint("Tuesday", 84f), DayPoint("Wednesday", 83f),
        DayPoint("Thursday", 82f),
    ),
    val hrvTrend: List<DayPoint> = listOf(
        DayPoint("Friday", 102f), DayPoint("Saturday", 80f), DayPoint("Sunday", 97f),
        DayPoint("Monday", 98f), DayPoint("Tuesday", 104f), DayPoint("Wednesday", 91f),
        DayPoint("Thursday", 98f),
    ),
    val restingHeartRateTrend: List<DayPoint> = listOf(
        DayPoint("Friday", 63f), DayPoint("Saturday", 60f), DayPoint("Sunday", 62f),
        DayPoint("Monday", 61f), DayPoint("Tuesday", 60f), DayPoint("Wednesday", 62f),
        DayPoint("Thursday", 61f),
    ),
    val calorieTrend: List<DayPoint> = listOf(
        DayPoint("Friday", 2_040f), DayPoint("Saturday", 2_380f), DayPoint("Sunday", 2_190f),
        DayPoint("Monday", 2_470f), DayPoint("Tuesday", 2_060f), DayPoint("Wednesday", 2_365f),
        DayPoint("Thursday", 2_184f),
    ),
) {
    companion object {
        fun empty() = HealthSnapshot(
            readiness = 0,
            sleepScore = 0,
            steps = 0,
            latestHeartRate = 0,
            restingHeartRate = 0,
            hrv = 0.0,
            lowHeartRate = 0,
            highHeartRate = 0,
            totalCalories = 0,
            activeCalories = 0,
            restingCalories = 0,
            sleepMinutes = 0,
            sleepTargetMinutes = 480,
            awakeMinutes = 0,
            restlessnessMinutes = 0,
            remMinutes = 0,
            lightMinutes = 0,
            deepMinutes = 0,
            sleepBreakdown = SleepScoreBreakdown(),
            lastSync = "Never",
            validNights = 0,
            stepTrend = emptyList(),
            sleepTrend = emptyList(),
            hrvTrend = emptyList(),
            restingHeartRateTrend = emptyList(),
            calorieTrend = emptyList(),
        )
    }
}

enum class Destination { TODAY, COACH }

enum class Detail { READINESS, SLEEP, STEPS, HEART, CALORIES, HRV, RESTING_HEART_RATE }
