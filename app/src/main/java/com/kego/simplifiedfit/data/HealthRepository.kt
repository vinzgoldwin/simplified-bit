package com.kego.simplifiedfit.data

import com.kego.simplifiedfit.domain.ReadinessSignals
import com.kego.simplifiedfit.domain.ReadinessSleep
import com.kego.simplifiedfit.domain.ScoreCalculator
import com.kego.simplifiedfit.domain.SleepSignals
import java.time.LocalDate

class HealthRepository(
    private val database: HealthDatabase,
    private val secureStore: SecureStore,
) {
    fun recent(): List<DailyHealth> = database.recent()

    fun recentActivities(): List<ExerciseSession> = database.recentActivities()

    fun sync(): List<DailyHealth> {
        val credentials = secureStore.googleCredentials() ?: return recent()
        val batch = GoogleHealthClient(credentials).fetch()
        database.replaceActivities(batch.activities)
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
                restlessnessMinutes = sleep?.restlessnessMinutes ?: old?.restlessnessMinutes,
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
        val sleepScore = calculateSleepScore(day, earlier)
        val recentSleep = recentSleep(day, earlier)
        val readiness = ScoreCalculator.readiness(
            ReadinessSignals(
                hrv = day.hrv,
                hrvBaseline = baseline(earlier, day.date) { it.hrv },
                restingHeartRate = day.restingHeartRate,
                restingHeartRateBaseline = baseline(earlier, day.date) { it.restingHeartRate },
                recentSleep = recentSleep,
            ),
        ).takeIf { recentSleep.size >= 7 && it > 0 }
        return day.copy(sleepScore = sleepScore, readinessScore = readiness)
    }

    private fun calculateSleepScore(day: DailyHealth, earlier: List<DailyHealth>): Int? {
        val asleep = day.asleepMinutes ?: return null
        val inBed = day.inBedMinutes ?: return null
        return ScoreCalculator.sleep(
            SleepSignals(
                asleepMinutes = asleep,
                targetMinutes = 480,
                inBedMinutes = inBed,
                remMinutes = day.remMinutes,
                deepMinutes = day.deepMinutes,
                awakeMinutes = day.awakeMinutes,
                restlessnessMinutes = day.restlessnessMinutes,
            ),
        )
    }

    private fun recentSleep(day: DailyHealth, earlier: List<DailyHealth>): List<ReadinessSleep> {
        val start = day.date.minusDays(6)
        return (earlier + day)
            .filter { it.date >= start && !it.date.isAfter(day.date) }
            .sortedBy { it.date }
            .mapNotNull { sleepDay ->
                val asleep = sleepDay.asleepMinutes ?: return@mapNotNull null
                val midpoint = sleepDay.sleepMidpointMinute ?: return@mapNotNull null
                if (asleep < 180) return@mapNotNull null
                ReadinessSleep(asleep, midpoint)
            }
    }

    private fun <T> baseline(
        earlier: List<DailyHealth>,
        date: LocalDate,
        value: (DailyHealth) -> T?,
    ): List<T> = earlier
        .filter { it.date >= date.minusDays(28) && it.date.isBefore(date) }
        .mapNotNull(value)
        .takeLast(28)
}
