package com.kego.simplifiedfit.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleHealthClientTest {
    @Test
    fun `accepts either a raw authorization code or redirected url`() {
        assertEquals("4/raw-code", GoogleHealthClient.normalizeAuthorizationCode("4/raw-code"))
        assertEquals(
            "4/url-code",
            GoogleHealthClient.normalizeAuthorizationCode("https://www.google.com/?code=4%2Furl-code&scope=health"),
        )
    }
}
