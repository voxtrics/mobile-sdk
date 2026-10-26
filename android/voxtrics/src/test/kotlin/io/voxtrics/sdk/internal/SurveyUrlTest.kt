package io.voxtrics.sdk.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyUrlTest {
    private val id = "eb7e05ef-8ca2-45ad-b1ac-175d2501dd82"

    @Test
    fun `the page is the base URL's m path with the query the contract names`() {
        val url = SurveyUrl.build(
            "https://feedback.voxtrics.io/",
            id,
            listOf("platform" to "ANDROID", "appId" to "com.example.shop", "screen" to "checkout"),
        )
        assertEquals("https://feedback.voxtrics.io/m/$id?platform=ANDROID&appId=com.example.shop&screen=checkout", url)
    }

    @Test
    fun `blank values are left out and every value is encoded`() {
        val url = SurveyUrl.build(
            "http://localhost:3000",
            id,
            listOf("screen" to "Cart & checkout", "appVersion" to null, "appBuild" to "  ", "deviceModel" to "Pixel 8"),
        )
        assertEquals("http://localhost:3000/m/$id?screen=Cart%20%26%20checkout&deviceModel=Pixel%208", url)
    }

    @Test
    fun `only a survey id becomes part of the path`() {
        assertTrue(SurveyUrl.isValidSurveyId(id))
        assertFalse(SurveyUrl.isValidSurveyId("../admin"))
        assertFalse(SurveyUrl.isValidSurveyId("abc?x=1"))
        assertFalse(SurveyUrl.isValidSurveyId(""))
    }

    @Test
    fun `the origin is scheme, host and port`() {
        assertEquals("https://feedback.voxtrics.io", SurveyUrl.originOf("https://feedback.voxtrics.io/m/$id?x=1"))
        assertEquals("http://localhost:3000", SurveyUrl.originOf("http://LOCALHOST:3000"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a base URL that is not http is refused`() {
        SurveyUrl.originOf("file:///sdcard/page.html")
    }
}
