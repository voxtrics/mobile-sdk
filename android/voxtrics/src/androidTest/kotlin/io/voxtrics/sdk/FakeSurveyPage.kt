package io.voxtrics.sdk

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/**
 * A stand-in for the survey page the SDK opens (`{baseUrl}/m/{surveyId}`), served from the test process, so the SDK is
 * tested without a backend. It speaks the same bridge as the real page - `{"v":1,"type":…,"payload":{…}}` through
 * `window.VoxtricsAndroid` - and the survey id names what it does. `window.fake` on the page lets a test send any
 * further message (`fake.post('closed', {reason: 'completed'})`).
 */
internal class FakeSurveyPage : AutoCloseable {
    private val server = MockWebServer()

    /** The SDK's base URL: `http://localhost:{port}`, the origin the bridge takes messages from. */
    val baseUrl: String

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = respond(request.requestUrl!!.encodedPath)
        }
        server.start()
        baseUrl = server.url("/").toString().trimEnd('/')
    }

    private fun respond(path: String): MockResponse = when (path) {
        "/m/$READY" -> page(onLoad = "fake.ready();")
        "/m/$READY_ON_CUE" -> page(onLoad = "")
        "/m/$NOT_FOUND" -> page(onLoad = "fake.post('error', {code: 'SURVEY_NOT_FOUND', message: 'No such survey'});")
        "/m/$SILENT" -> page(onLoad = "")
        "/m/$SERVER_ERROR" -> MockResponse().setResponseCode(500).setBody("Server error")
        // The survey page itself says nothing; a frame inside it, from its own origin, tries to talk for it.
        "/m/$FRAMED" -> page(onLoad = "", top = """<iframe src="/talking-frame" style="width:100%;height:120px;border:0"></iframe>""")
        "/talking-frame" -> page(onLoad = "fake.ready();")
        // A ready survey with a frame at its top holding a mail link, for a person to tap. Not an http(s) one: WebView
        // asks the app about a frame's navigation only for other schemes.
        "/m/$FRAME_LINK" -> page(onLoad = "fake.ready();", top = """<iframe src="/linking-frame" style="width:100%;height:120px;border:0"></iframe>""")
        "/linking-frame" -> MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(
            """<!doctype html><body style="margin:0"><a href="mailto:support@example.com" style="display:block;padding:24px;font-size:24px">Write to us</a></body>""",
        )
        else -> MockResponse().setResponseCode(404)
    }

    private fun page(onLoad: String, top: String = ""): MockResponse = MockResponse()
        .setHeader("Content-Type", "text/html; charset=utf-8")
        .setBody(
            """
            <!doctype html>
            <html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
            <body style="margin:0;background:#fff;font-family:sans-serif">
              $top
              <div style="height:280px;padding:16px">A fake survey</div>
              <script>
                window.fake = {
                  post: function (type, payload) {
                    window.VoxtricsAndroid.postMessage(JSON.stringify({v: 1, type: type, payload: payload || {}}));
                  },
                  ready: function () {
                    fake.post('resize', {height: 420});
                    fake.post('ready', {surveyId: location.pathname.split('/').pop(), locale: 'en'});
                  }
                };
                $onLoad
              </script>
            </body></html>
            """.trimIndent(),
        )

    override fun close() = server.shutdown()

    companion object {
        /** Reports its height and is ready at once. */
        const val READY = "ready"

        /** Says nothing until the test calls `fake.ready()`. */
        const val READY_ON_CUE = "ready-on-cue"

        /** Answers SURVEY_NOT_FOUND, as the real page does for a missing survey. */
        const val NOT_FOUND = "not-found"

        /** Never gets ready. */
        const val SILENT = "silent"

        /** The page itself fails: HTTP 500. */
        const val SERVER_ERROR = "server-error"

        /** Only a frame inside the page sends messages - which the bridge must not take. */
        const val FRAMED = "framed"

        /** Ready, with a frame at the top holding a mail link. */
        const val FRAME_LINK = "frame-link"
    }
}
