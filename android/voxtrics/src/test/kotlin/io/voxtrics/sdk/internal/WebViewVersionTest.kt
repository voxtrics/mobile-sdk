package io.voxtrics.sdk.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebViewVersionTest {
    @Test
    fun `the major version is the first part of the version name`() {
        assertEquals(66, WebViewVersion.parseMajor("66.0.3359.158"))
        assertEquals(101, WebViewVersion.parseMajor("101.0.4951.74"))
        assertNull(WebViewVersion.parseMajor("dev"))
        assertNull(WebViewVersion.parseMajor(null))
    }
}
