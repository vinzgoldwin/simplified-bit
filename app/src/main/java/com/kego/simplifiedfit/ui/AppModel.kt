package com.kego.simplifiedfit.ui

data class DayPoint(
    val label: String,
    val value: Float,
)

data class HealthSnapshot(
    val readiness: Int = 78,
    val sleepScore: Int = 84,
    val steps: Int = 8_426,
    val latestHeartRate: Int = 72,
    val restingHeartRate: Int = 61,
    val lowHeartRate: Int = 54,
    val highHeartRate: Int = 132,
    val totalCalories: Int = 2_184,
    val activeCalories: Int = 612,
    val restingCalories: Int = 1_572,
    val sleepMinutes: Int = 462,
    val sleepTargetMinutes: Int = 480,
    val awakeMinutes: Int = 34,
    val remMinutes: Int = 132,
    val lightMinutes: Int = 242,
    val deepMinutes: Int = 88,
    val lastSync: String = "8:42",
    val validNights: Int = 7,
    val stepTrend: List<DayPoint> = listOf(
        DayPoint("F", 6_210f), DayPoint("S", 9_140f), DayPoint("S", 7_860f),
        DayPoint("M", 10_240f), DayPoint("T", 7_430f), DayPoint("W", 8_920f),
        DayPoint("T", 8_426f),
    ),
    val sleepTrend: List<DayPoint> = listOf(
        DayPoint("Fri", 78f), DayPoint("Sat", 82f), DayPoint("Sun", 75f),
        DayPoint("Mon", 86f), DayPoint("Tue", 84f), DayPoint("Wed", 83f),
        DayPoint("Thu", 84f),
    ),
    val calorieTrend: List<DayPoint> = listOf(
        DayPoint("F", 2_040f), DayPoint("S", 2_380f), DayPoint("S", 2_190f),
        DayPoint("M", 2_470f), DayPoint("T", 2_060f), DayPoint("W", 2_365f),
        DayPoint("T", 2_184f),
    ),
) {
    companion object {
        fun empty() = HealthSnapshot(
            readiness = 0,
            sleepScore = 0,
            steps = 0,
            latestHeartRate = 0,
            restingHeartRate = 0,
            lowHeartRate = 0,
            highHeartRate = 0,
            totalCalories = 0,
            activeCalories = 0,
            restingCalories = 0,
            sleepMinutes = 0,
            sleepTargetMinutes = 480,
            awakeMinutes = 0,
            remMinutes = 0,
            lightMinutes = 0,
            deepMinutes = 0,
            lastSync = "Never",
            validNights = 0,
            stepTrend = emptyList(),
            sleepTrend = emptyList(),
            calorieTrend = emptyList(),
        )
    }
}

enum class Destination { TODAY, COACH }

enum class Detail { READINESS, SLEEP, STEPS, HEART, CALORIES }
