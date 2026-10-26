package io.voxtrics.sdk

/**
 * How one survey is shown.
 *
 * @property screen A name for the screen the survey is opened from ("checkout"). It is stored with the response,
 *   and the response links to `app://{applicationId}/{screen}`.
 * @property theme Light or dark. [Theme.SYSTEM] follows the app's own night mode, which is what the screen under the
 *   sheet shows.
 * @property locale A language code ("de"). Unset is the device's language; a survey without that language opens in
 *   its first one.
 */
public data class SurveyOptions(
    val screen: String? = null,
    val theme: Theme = Theme.SYSTEM,
    val locale: String? = null,
)

public enum class Theme { SYSTEM, LIGHT, DARK }
