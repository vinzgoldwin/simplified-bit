package com.kego.simplifiedfit.ui

import com.kego.simplifiedfit.domain.SleepScoreBreakdown

data class DayPoint(
    val label: String,
    val value: Float,
)

data class HealthSnapshot(
    val readiness: Int = 0,
    val sleepScore: Int = 0,
    val steps: Int = 0,
    val latestHeartRate: Int = 0,
    val restingHeartRate: Int = 0,
    val hrv: Double = 0.0,
    val lowHeartRate: Int = 0,
    val highHeartRate: Int = 0,
    val totalCalories: Int = 0,
    val activeCalories: Int = 0,
    val restingCalories: Int = 0,
    val sleepMinutes: Int = 0,
    val sleepTargetMinutes: Int = 480,
    val awakeMinutes: Int = 0,
    val restlessnessMinutes: Int = 0,
    val remMinutes: Int = 0,
    val lightMinutes: Int = 0,
    val deepMinutes: Int = 0,
    val sleepBreakdown: SleepScoreBreakdown = SleepScoreBreakdown(),
    val lastSync: String = "Never",
    val validNights: Int = 0,
    val stepTrend: List<DayPoint> = emptyList(),
    val sleepTrend: List<DayPoint> = emptyList(),
    val hrvTrend: List<DayPoint> = emptyList(),
    val restingHeartRateTrend: List<DayPoint> = emptyList(),
    val calorieTrend: List<DayPoint> = emptyList(),
) {
    companion object {
        fun empty() = HealthSnapshot()
    }
}

enum class Destination { TODAY, COACH }

enum class Detail { READINESS, SLEEP, STEPS, HEART, CALORIES, HRV, RESTING_HEART_RATE }
