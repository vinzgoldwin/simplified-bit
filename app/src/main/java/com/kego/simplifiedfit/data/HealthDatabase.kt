package com.kego.simplifiedfit.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

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
    val remMinutes: Int? = null,
    val lightMinutes: Int? = null,
    val deepMinutes: Int? = null,
    val sleepMidpointMinute: Int? = null,
    val sleepScore: Int? = null,
    val readinessScore: Int? = null,
)

class HealthDatabase(context: Context) : SQLiteOpenHelper(context, "health.db", null, 1) {
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

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
        put("rem_minutes", remMinutes)
        put("light_minutes", lightMinutes)
        put("deep_minutes", deepMinutes)
        put("sleep_midpoint_minute", sleepMidpointMinute)
        put("sleep_score", sleepScore)
        put("readiness_score", readinessScore)
        put("synced_at", System.currentTimeMillis())
    }
}
