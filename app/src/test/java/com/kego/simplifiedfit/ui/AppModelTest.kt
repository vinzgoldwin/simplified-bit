package com.kego.simplifiedfit.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppModelTest {
    @Test
    fun `new snapshot never contains sample health data`() {
        val snapshot = HealthSnapshot()

        assertEquals(0, snapshot.steps)
        assertEquals(0, snapshot.sleepScore)
        assertEquals(0, snapshot.readiness)
        assertEquals("Never", snapshot.lastSync)
        assertTrue(snapshot.stepTrend.isEmpty())
    }
}
