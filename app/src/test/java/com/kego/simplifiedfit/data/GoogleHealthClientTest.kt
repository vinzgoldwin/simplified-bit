package com.kego.simplifiedfit.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.json.JSONObject
import java.time.Instant

class GoogleHealthClientTest {
    @Test
    fun `accepts either a raw authorization code or redirected url`() {
        assertEquals("4/raw-code", GoogleHealthClient.normalizeAuthorizationCode("4/raw-code"))
        assertEquals(
            "4/url-code",
            GoogleHealthClient.normalizeAuthorizationCode("https://www.google.com/?code=4%2Furl-code&scope=health"),
        )
    }

    @Test
    fun `parses an exercise session summary`() {
        val point = JSONObject(
            """
            {
              "dataPointName": "users/me/dataTypes/exercise/dataPoints/run-1",
              "exercise": {
                "interval": {
                  "startTime": "2026-08-22T06:00:00Z",
                  "endTime": "2026-08-22T06:42:18Z"
                },
                "exerciseType": "RUNNING",
                "displayName": "Morning Run",
                "activeDuration": "2538s",
                "metricsSummary": {
                  "caloriesKcal": 312.0,
                  "distanceMillimeters": 7400000.0,
                  "steps": "8214",
                  "averageHeartRateBeatsPerMinute": "142",
                  "activeZoneMinutes": "28",
                  "averagePaceSecondsPerMeter": 0.343
                }
              }
            }
            """.trimIndent(),
        )

        val activity = GoogleHealthClient(GoogleCredentials("id", "secret", "token")).parseExercise(point)!!

        assertEquals("users/me/dataTypes/exercise/dataPoints/run-1", activity.id)
        assertEquals(Instant.parse("2026-08-22T06:00:00Z"), activity.startTime)
        assertEquals("RUNNING", activity.type)
        assertEquals("Morning Run", activity.displayName)
        assertEquals(2_538L, activity.activeDurationSeconds)
        assertEquals(7_400.0, activity.distanceMeters!!, 0.01)
        assertEquals(312.0, activity.caloriesKcal!!, 0.01)
        assertEquals(142, activity.averageHeartRate)
        assertEquals(28, activity.activeZoneMinutes)
        assertEquals(343.0, activity.averagePaceSeconds!!, 0.01)
    }

    @Test
    fun `uses the exercise type when google returns a generic activity name`() {
        val point = JSONObject(
            """
            {
              "dataPointName": "users/me/dataTypes/exercise/dataPoints/cardio-1",
              "exercise": {
                "interval": {
                  "startTime": "2026-08-22T06:00:00Z",
                  "endTime": "2026-08-22T06:30:00Z"
                },
                "exerciseType": "CARDIO_WORKOUT",
                "displayName": "Activity",
                "activeDuration": "1800s",
                "metricsSummary": {}
              }
            }
            """.trimIndent(),
        )

        val activity = GoogleHealthClient(GoogleCredentials("id", "secret", "token")).parseExercise(point)!!

        assertEquals("Cardio workout", activity.displayName)
    }
}
