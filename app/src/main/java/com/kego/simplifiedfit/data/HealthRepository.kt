package com.kego.simplifiedfit.data

import com.kego.simplifiedfit.domain.ReadinessSignals
import com.kego.simplifiedfit.domain.ScoreCalculator
import com.kego.simplifiedfit.domain.SleepSignals
import java.time.LocalDate

class HealthRepository(
    private val database: HealthDatabase,
    private val secureStore: SecureStore,
) {
    fun recent(): List<DailyHealth> = database.recent()

    fun sync(): List<DailyHealth> {
        val credentials = secureStore.googleCredentials() ?: return recent()
        val batch = GoogleHealthClient(credentials).fetch()
        val existing = recent().associateBy { it.date }
        val dates = buildSet {
            addAll(existing.keys)
            addAll(batch.steps.keys)
            addAll(batch.totalCalories.keys)
            addAll(batch.activeCalories.keys)
            addAll(batch.restingHeartRate.keys)
            addAll(batch.hrv.keys)
            addAll(batch.sleeps.keys)
        }.sorted()

        val merged = dates.map { date ->
            val old = existing[date]
            val sleep = batch.sleeps[date]
            DailyHealth(
                date = date,
                steps = batch.steps[date] ?: old?.steps,
                latestHeartRate = if (date == LocalDate.now()) batch.latestHeartRate ?: old?.latestHeartRate else old?.latestHeartRate,
                restingHeartRate = batch.restingHeartRate[date] ?: old?.restingHeartRate,
                hrv = batch.hrv[date] ?: old?.hrv,
                totalCalories = batch.totalCalories[date] ?: old?.totalCalories,
                activeCalories = batch.activeCalories[date] ?: old?.activeCalories,
                asleepMinutes = sleep?.asleepMinutes ?: old?.asleepMinutes,
                inBedMinutes = sleep?.inBedMinutes ?: old?.inBedMinutes,
                awakeMinutes = sleep?.awakeMinutes ?: old?.awakeMinutes,
                remMinutes = sleep?.remMinutes ?: old?.remMinutes,
                lightMinutes = sleep?.lightMinutes ?: old?.lightMinutes,
                deepMinutes = sleep?.deepMinutes ?: old?.deepMinutes,
                sleepMidpointMinute = sleep?.midpointMinute ?: old?.sleepMidpointMinute,
            )
        }

        val scored = merged.mapIndexed { index, day -> score(day, merged.take(index)) }
        scored.forEach(database::upsert)
        return database.recent()
    }

    private fun score(day: DailyHealth, earlier: List<DailyHealth>): DailyHealth {
        val midpoints = earlier.mapNotNull { it.sleepMidpointMinute }.takeLast(28)
        val midpointDeviation = ScoreCalculator.midpointDeviation(day.sleepMidpointMinute, midpoints)
        val sleepScore = if (day.asleepMinutes != null && day.inBedMinutes != null) {
            ScoreCalculator.sleep(
                SleepSignals(
                    asleepMinutes = day.asleepMinutes,
                    targetMinutes = 480,
                    inBedMinutes = day.inBedMinutes,
                    remMinutes = day.remMinutes,
                    deepMinutes = day.deepMinutes,
                    midpointDeviationMinutes = midpointDeviation,
                    awakeMinutes = day.awakeMinutes,
                ),
            )
        } else null
        val readiness = ScoreCalculator.readiness(
            ReadinessSignals(
                hrv = day.hrv,
                hrvBaseline = earlier.mapNotNull { it.hrv }.takeLast(28),
                restingHeartRate = day.restingHeartRate,
                restingHeartRateBaseline = earlier.mapNotNull { it.restingHeartRate }.takeLast(28),
                sleepScore = sleepScore,
                priorActiveCalories = earlier.lastOrNull()?.activeCalories,
                activeCaloriesBaseline = earlier.mapNotNull { it.activeCalories }.takeLast(28),
            ),
        ).takeIf { it > 0 }
        return day.copy(sleepScore = sleepScore, readinessScore = readiness)
    }
}
