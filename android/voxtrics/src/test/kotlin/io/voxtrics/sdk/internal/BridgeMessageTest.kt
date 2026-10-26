package io.voxtrics.sdk.internal

import io.voxtrics.sdk.CloseReason
import io.voxtrics.sdk.SurveyErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// The messages exactly as the page sends them (utils-ui, features/inAppSurvey/nativeBridge.ts).
class BridgeMessageTest {
    @Test
    fun `every message of the contract parses`() {
        assertEquals(
            BridgeMessage.Ready("eb7e05ef", "de"),
            BridgeMessage.parse("""{"v":1,"type":"ready","payload":{"surveyId":"eb7e05ef","locale":"de"}}"""),
        )
        assertEquals(BridgeMessage.Resize(601.0), BridgeMessage.parse("""{"v":1,"type":"resize","payload":{"height":601}}"""))
        assertEquals(
            BridgeMessage.Submitted(4.6),
            BridgeMessage.parse("""{"v":1,"type":"submitted","payload":{"mainEvaluation":4.6}}"""),
        )
        assertEquals(
            BridgeMessage.Closed(CloseReason.COMPLETED),
            BridgeMessage.parse("""{"v":1,"type":"closed","payload":{"reason":"completed"}}"""),
        )
        assertEquals(
            BridgeMessage.Closed(CloseReason.USER),
            BridgeMessage.parse("""{"v":1,"type":"closed","payload":{"reason":"user"}}"""),
        )
        assertEquals(
            BridgeMessage.Error(SurveyErrorCode.SURVEY_NOT_FOUND, "Not found"),
            BridgeMessage.parse("""{"v":1,"type":"error","payload":{"code":"SURVEY_NOT_FOUND","message":"Not found"}}"""),
        )
        assertEquals(
            BridgeMessage.OpenUrl("https://feedback.voxtrics.io/"),
            BridgeMessage.parse("""{"v":1,"type":"openUrl","payload":{"url":"https://feedback.voxtrics.io/"}}"""),
        )
    }

    @Test
    fun `a survey without a score submits null`() {
        assertEquals(
            BridgeMessage.Submitted(null),
            BridgeMessage.parse("""{"v":1,"type":"submitted","payload":{"mainEvaluation":null}}"""),
        )
    }

    @Test
    fun `what this version does not know is ignored, never a crash`() {
        assertEquals(BridgeMessage.Unknown("progress"), BridgeMessage.parse("""{"v":2,"type":"progress","payload":{}}"""))
        assertEquals(
            BridgeMessage.Error(SurveyErrorCode.UNKNOWN, "?"),
            BridgeMessage.parse("""{"v":1,"type":"error","payload":{"code":"QUOTA","message":"?"}}"""),
        )
        assertNull(BridgeMessage.parse("not json"))
        assertNull(BridgeMessage.parse("""{"v":1}"""))
        assertNull(BridgeMessage.parse("""{"v":1,"type":"resize","payload":{"height":"tall"}}"""))
    }
}
