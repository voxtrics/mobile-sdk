package io.voxtrics.sdk.internal

import io.voxtrics.sdk.CloseReason
import io.voxtrics.sdk.SurveyErrorCode
import org.json.JSONException
import org.json.JSONObject

/**
 * One message from the survey page, `{"v":1,"type":…,"payload":{…}}`. A type this version does not know is [Unknown]
 * and ignored, as each side does with the other's unknowns - so a newer page still works with an older SDK.
 */
internal sealed class BridgeMessage {
    data class Ready(val surveyId: String, val locale: String) : BridgeMessage()
    data class Resize(val height: Double) : BridgeMessage()
    data class Submitted(val mainEvaluation: Double?) : BridgeMessage()
    data class Closed(val reason: CloseReason) : BridgeMessage()
    data class Error(val code: SurveyErrorCode, val message: String) : BridgeMessage()
    data class OpenUrl(val url: String) : BridgeMessage()
    data class Unknown(val type: String) : BridgeMessage()

    companion object {
        /** Null for something that is not a bridge message at all. */
        fun parse(json: String): BridgeMessage? = try {
            val message = JSONObject(json)
            val payload = message.optJSONObject("payload") ?: JSONObject()
            when (val type = message.optString("type")) {
                "ready" -> Ready(payload.optString("surveyId"), payload.optString("locale"))
                "resize" -> payload.optDouble("height").takeIf { it.isFinite() && it >= 0 }?.let(::Resize)
                "submitted" -> Submitted(payload.optNumber("mainEvaluation"))
                "closed" -> Closed(if (payload.optString("reason") == "completed") CloseReason.COMPLETED else CloseReason.USER)
                "error" -> Error(errorCodeOf(payload.optString("code")), payload.optString("message"))
                "openUrl" -> payload.optString("url").takeIf { it.isNotBlank() }?.let(::OpenUrl)
                "" -> null
                else -> Unknown(type)
            }
        } catch (e: JSONException) {
            null
        }

        // A code a later page sends that this version does not know is still an error.
        private fun errorCodeOf(code: String): SurveyErrorCode =
            SurveyErrorCode.entries.firstOrNull { it.name == code } ?: SurveyErrorCode.UNKNOWN

        private fun JSONObject.optNumber(name: String): Double? =
            if (isNull(name)) null else optDouble(name).takeIf { it.isFinite() }
    }
}
