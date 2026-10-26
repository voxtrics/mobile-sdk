package io.voxtrics.sdk.internal

import java.net.URLEncoder

/**
 * The page a survey opens on, `{baseUrl}/m/{surveyId}?…`. Plain Kotlin - android.net.Uri does nothing in a JVM test.
 */
internal object SurveyUrl {
    // A survey id is a UUID. Anything else is not one of ours, and must not become part of a path.
    private val SURVEY_ID = Regex("^[A-Za-z0-9-]{1,64}$")
    private val ORIGIN = Regex("^(https?://[^/?#]+)", RegexOption.IGNORE_CASE)

    fun isValidSurveyId(surveyId: String): Boolean = SURVEY_ID.matches(surveyId)

    /** Blank values are left out, so the page reads them as not sent. */
    fun build(baseUrl: String, surveyId: String, query: List<Pair<String, String?>>): String {
        require(isValidSurveyId(surveyId)) { "Not a survey id: $surveyId" }
        val params = query
            .filter { (_, value) -> !value.isNullOrBlank() }
            .joinToString("&") { (name, value) -> "${encode(name)}=${encode(value!!.trim())}" }
        val page = "${baseUrl.trimEnd('/')}/m/$surveyId"
        return if (params.isEmpty()) page else "$page?$params"
    }

    /** `scheme://host[:port]` of a base URL - what the bridge accepts messages from, and navigation may stay on. */
    fun originOf(baseUrl: String): String =
        ORIGIN.find(baseUrl.trim())?.value?.lowercase() ?: throw IllegalArgumentException("Not an http(s) URL: $baseUrl")

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
