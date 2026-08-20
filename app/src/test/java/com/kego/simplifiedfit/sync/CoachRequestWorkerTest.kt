package com.kego.simplifiedfit.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoachRequestWorkerTest {
    @Test
    fun `publishes a large response as growing prefixes`() {
        assertEquals(12, nextCoachPartialLength(130, 0))
        assertEquals(60, nextCoachPartialLength(130, 12))
        assertEquals(108, nextCoachPartialLength(130, 60))
        assertNull(nextCoachPartialLength(130, 108))
    }

    @Test
    fun `publishes the first short delta immediately`() {
        assertEquals(4, nextCoachPartialLength(4, 0))
        assertNull(nextCoachPartialLength(4, 4))
    }
}
