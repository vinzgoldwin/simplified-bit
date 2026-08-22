package com.kego.simplifiedfit.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId

data class DailyHealth(
    val date: LocalDate,
    val steps: Int? = null,
    val latestHeartRate: Int? = null,
    val restingHeartRate: Double? = null,
    val hrv: Double? = null,
    val totalCalories: Double? = null,
    val activeCalories: Double? = null,
    val asleepMinutes: Int? = null,
    val inBedMinutes: Int? = null,
    val awakeMinutes: Int? = null,
    val restlessnessMinutes: Int? = null,
    val remMinutes: Int? = null,
    val lightMinutes: Int? = null,
    val deepMinutes: Int? = null,
    val sleepMidpointMinute: Int? = null,
    val sleepScore: Int? = null,
    val readinessScore: Int? = null,
)

class HealthDatabase(context: Context) : SQLiteOpenHelper(context, "health.db", null, 4) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE daily_health (
                date TEXT PRIMARY KEY,
                steps INTEGER,
                latest_heart_rate INTEGER,
                resting_heart_rate REAL,
                hrv REAL,
                total_calories REAL,
                active_calories REAL,
                asleep_minutes INTEGER,
                in_bed_minutes INTEGER,
                awake_minutes INTEGER,
                restlessness_minutes INTEGER,
                rem_minutes INTEGER,
                light_minutes INTEGER,
                deep_minutes INTEGER,
                sleep_midpoint_minute INTEGER,
                sleep_score INTEGER,
                readiness_score INTEGER,
                synced_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        createActivitiesTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE daily_health ADD COLUMN restlessness_minutes INTEGER")
        if (oldVersion < 4) createActivitiesTable(db)
    }

    fun upsert(day: DailyHealth) {
        writableDatabase.insertWithOnConflict(
            "daily_health",
            null,
            day.toValues(),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        writableDatabase.delete("daily_health", "date < ?", arrayOf(LocalDate.now().minusDays(30).toString()))
    }

    fun recent(days: Int = 30): List<DailyHealth> = readableDatabase.query(
        "daily_health",
        null,
        "date >= ?",
        arrayOf(LocalDate.now().minusDays(days.toLong()).toString()),
        null,
        null,
        "date DESC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                fun int(name: String) = cursor.getColumnIndexOrThrow(name).let { if (cursor.isNull(it)) null else cursor.getInt(it) }
                fun double(name: String) = cursor.getColumnIndexOrThrow(name).let { if (cursor.isNull(it)) null else cursor.getDouble(it) }
                add(
                    DailyHealth(
                        date = LocalDate.parse(cursor.getString(cursor.getColumnIndexOrThrow("date"))),
                        steps = int("steps"),
                        latestHeartRate = int("latest_heart_rate"),
                        restingHeartRate = double("resting_heart_rate"),
                        hrv = double("hrv"),
                        totalCalories = double("total_calories"),
                        activeCalories = double("active_calories"),
                        asleepMinutes = int("asleep_minutes"),
                        inBedMinutes = int("in_bed_minutes"),
                        awakeMinutes = int("awake_minutes"),
                        restlessnessMinutes = int("restlessness_minutes"),
                        remMinutes = int("rem_minutes"),
                        lightMinutes = int("light_minutes"),
                        deepMinutes = int("deep_minutes"),
                        sleepMidpointMinute = int("sleep_midpoint_minute"),
                        sleepScore = int("sleep_score"),
                        readinessScore = int("readiness_score"),
                    ),
                )
            }
        }
    }

    fun replaceActivities(activities: List<ExerciseSession>) {
        writableDatabase.apply {
            beginTransaction()
            try {
                delete("activities", null, null)
                activities.forEach { activity ->
                    insertWithOnConflict(
                        "activities",
                        null,
                        activity.toValues(),
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
    }

    fun recentActivities(days: Int = 30): List<ExerciseSession> {
        val start = LocalDate.now().minusDays((days - 1).coerceAtLeast(0).toLong())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return readableDatabase.query(
            "activities",
            null,
            "start_time >= ?",
            arrayOf(start.toString()),
            null,
            null,
            "start_time DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    fun long(name: String) = cursor.getLong(cursor.getColumnIndexOrThrow(name))
                    fun int(name: String) = cursor.getColumnIndexOrThrow(name).let { if (cursor.isNull(it)) null else cursor.getInt(it) }
                    fun double(name: String) = cursor.getColumnIndexOrThrow(name).let { if (cursor.isNull(it)) null else cursor.getDouble(it) }
                    add(
                        ExerciseSession(
                            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                            startTime = Instant.ofEpochMilli(long("start_time")),
                            endTime = Instant.ofEpochMilli(long("end_time")),
                            type = cursor.getString(cursor.getColumnIndexOrThrow("type")),
                            displayName = cursor.getString(cursor.getColumnIndexOrThrow("display_name")),
                            activeDurationSeconds = long("active_duration_seconds"),
                            caloriesKcal = double("calories_kcal"),
                            distanceMeters = double("distance_meters"),
                            steps = int("steps"),
                            averageHeartRate = int("average_heart_rate"),
                            activeZoneMinutes = int("active_zone_minutes"),
                            averageSpeedMetersPerSecond = double("average_speed_meters_per_second"),
                            averagePaceSeconds = double("average_pace_seconds"),
                            elevationGainMeters = double("elevation_gain_meters"),
                        ),
                    )
                }
            }
        }
    }

    private fun DailyHealth.toValues() = ContentValues().apply {
        put("date", date.toString())
        put("steps", steps)
        put("latest_heart_rate", latestHeartRate)
        put("resting_heart_rate", restingHeartRate)
        put("hrv", hrv)
        put("total_calories", totalCalories)
        put("active_calories", activeCalories)
        put("asleep_minutes", asleepMinutes)
        put("in_bed_minutes", inBedMinutes)
        put("awake_minutes", awakeMinutes)
        put("restlessness_minutes", restlessnessMinutes)
        put("rem_minutes", remMinutes)
        put("light_minutes", lightMinutes)
        put("deep_minutes", deepMinutes)
        put("sleep_midpoint_minute", sleepMidpointMinute)
        put("sleep_score", sleepScore)
        put("readiness_score", readinessScore)
        put("synced_at", System.currentTimeMillis())
    }

    private fun ExerciseSession.toValues() = ContentValues().apply {
        put("id", id)
        put("start_time", startTime.toEpochMilli())
        put("end_time", endTime.toEpochMilli())
        put("type", type)
        put("display_name", displayName)
        put("active_duration_seconds", activeDurationSeconds)
        put("calories_kcal", caloriesKcal)
        put("distance_meters", distanceMeters)
        put("steps", steps)
        put("average_heart_rate", averageHeartRate)
        put("active_zone_minutes", activeZoneMinutes)
        put("average_speed_meters_per_second", averageSpeedMetersPerSecond)
        put("average_pace_seconds", averagePaceSeconds)
        put("elevation_gain_meters", elevationGainMeters)
    }

    private fun createActivitiesTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS activities (
                id TEXT PRIMARY KEY,
                start_time INTEGER NOT NULL,
                end_time INTEGER NOT NULL,
                type TEXT NOT NULL,
                display_name TEXT NOT NULL,
                active_duration_seconds INTEGER NOT NULL,
                calories_kcal REAL,
                distance_meters REAL,
                steps INTEGER,
                average_heart_rate INTEGER,
                active_zone_minutes INTEGER,
                average_speed_meters_per_second REAL,
                average_pace_seconds REAL,
                elevation_gain_meters REAL
            )
            """.trimIndent(),
        )
    }
}
