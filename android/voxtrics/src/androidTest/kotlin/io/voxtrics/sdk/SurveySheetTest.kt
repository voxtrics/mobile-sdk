package io.voxtrics.sdk

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.voxtrics.sdk.internal.SurveySession
import io.voxtrics.sdk.internal.SurveySheetFragment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The SDK on a device, the way an app uses it - Voxtrics.showSurvey over an activity, and the events its listener
 * gets - against FakeSurveyPage instead of a backend. What the page does is driven through its bridge; what a person
 * does (Back, a tap outside, a rotation) through the system.
 */
@RunWith(AndroidJUnit4::class)
class SurveySheetTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val page = FakeSurveyPage()
    private val listener = RecordingListener()
    private lateinit var screen: ActivityScenario<TestHostActivity>

    @Before
    fun openTheScreen() {
        screen = ActivityScenario.launch(TestHostActivity::class.java)
        screen.onActivity { Voxtrics.configure(it, VoxtricsConfig(baseUrl = page.baseUrl, logging = true)) }
    }

    @After
    fun closeEverything() {
        // Closing the screen ends a survey still open, so the next test starts with none.
        closeTheScreen()
        waitFor { !surveyIsOpen() }
        page.close()
    }

    @Test
    fun aReadySurveyShowsInTheSheet() {
        show(FakeSurveyPage.READY)

        listener.expect("shown")
        waitFor { sheetIsShowing() }
    }

    @Test
    fun theScoreAndTheCloseAfterTheThankYouScreenReachTheApp() {
        showAndWaitForTheSheet(FakeSurveyPage.READY)

        onPage("fake.post('submitted', {mainEvaluation: 4.6})")
        listener.expect("submitted 4.6")
        onPage("fake.post('closed', {reason: 'completed'})")
        listener.expect("closed COMPLETED")
        waitFor { !surveyIsOpen() && !sheetIsShowing() }
    }

    @Test
    fun backClosesItAsTheUser() {
        showAndWaitForTheSheet(FakeSurveyPage.READY)

        device.pressBack()

        listener.expect("closed USER")
        waitFor { !sheetIsShowing() }
    }

    @Test
    fun aTapOutsideTheSheetClosesItAsTheUser() {
        showAndWaitForTheSheet(FakeSurveyPage.READY)

        // The top of the screen: the dim above a sheet 420dp tall.
        device.click(device.displayWidth / 2, device.displayHeight / 10)

        listener.expect("closed USER")
    }

    @Test
    fun aSurveyThePageCannotShowReportsWhyAndShowsNothing() {
        show(FakeSurveyPage.NOT_FOUND)

        listener.expect("error SURVEY_NOT_FOUND")
        assertEquals(false, sheetIsShowing())
        waitFor { !surveyIsOpen() }
    }

    @Test
    fun aPageThatFailsToLoadIsReportedAtOnce() {
        show(FakeSurveyPage.SERVER_ERROR)

        listener.expect("error UNKNOWN")
    }

    @Test
    fun aPageThatNeverGetsReadyTimesOut() {
        show(FakeSurveyPage.SILENT)

        listener.expectNothing(SurveySession.READY_TIMEOUT_MS - 1_000)
        listener.expect("error TIMEOUT", timeoutMs = 3_000)
    }

    @Test
    fun aRotationKeepsTheOpenSurvey() {
        showAndWaitForTheSheet(FakeSurveyPage.READY)

        screen.recreate()

        waitFor { sheetIsShowing() }
        listener.expectNothing(1_000)
        // Still the same survey, still reporting to the same listener.
        onPage("fake.post('closed', {reason: 'completed'})")
        listener.expect("closed COMPLETED")
    }

    @Test
    fun aRotationWhileLoadingShowsTheSurveyOverTheNewScreen() {
        show(FakeSurveyPage.READY_ON_CUE)
        waitFor { surveyIsOpen() }

        screen.recreate()
        listener.expectNothing(500)
        onPage("fake.ready()")

        listener.expect("shown")
        waitFor { sheetIsShowing() }
    }

    @Test
    fun aFrameInsideThePageCannotTalkToTheApp() {
        show(FakeSurveyPage.FRAMED)

        // The frame says ready; only the page itself may.
        listener.expectNothing(3_000)
        assertEquals(false, sheetIsShowing())
    }

    @Test
    fun aLinkInsideAFrameOpensNothingOutsideTheApp() {
        withOpenedLinks { opened ->
            showAndWaitForTheSheet(FakeSurveyPage.FRAME_LINK)

            device.wait(Until.findObject(By.text("Write to us")), 5_000)!!.click()

            assertNull(opened.poll(2, TimeUnit.SECONDS))
            assertEquals(true, sheetIsShowing())
        }
    }

    @Test
    fun aLinkOpensOutsideAndTheSurveyStays() {
        withOpenedLinks { opened ->
            showAndWaitForTheSheet(FakeSurveyPage.READY)

            onPage("fake.post('openUrl', {url: 'https://example.com/terms'})")

            assertEquals("https://example.com/terms", opened.poll(5, TimeUnit.SECONDS)?.dataString)
            assertEquals(true, sheetIsShowing())
        }
    }

    @Test
    fun aSecondSurveyWhileOneIsOpenIsRefused() {
        showAndWaitForTheSheet(FakeSurveyPage.READY)
        val second = RecordingListener()

        screen.onActivity { Voxtrics.showSurvey(it, FakeSurveyPage.READY, listener = second) }

        second.expect("error UNKNOWN")
        listener.expectNothing(500)
    }

    @Test
    fun theScreenGoingAwayClosesTheSurveyAsTheUser() {
        showAndWaitForTheSheet(FakeSurveyPage.READY)

        closeTheScreen()

        listener.expect("closed USER")
    }

    private fun show(surveyId: String) {
        screen.onActivity { Voxtrics.showSurvey(it, surveyId, SurveyOptions(screen = "checkout"), listener) }
    }

    private fun showAndWaitForTheSheet(surveyId: String) {
        show(surveyId)
        listener.expect("shown")
        waitFor { sheetIsShowing() }
    }

    // As the page would - through its own bridge, in the survey's WebView.
    private fun onPage(script: String) {
        instrumentation.runOnMainSync { SurveySession.active!!.webView.evaluateJavascript(script, null) }
    }

    private var screenIsOpen = true

    private fun closeTheScreen() {
        if (!screenIsOpen) return
        screenIsOpen = false
        screen.close()
    }

    private fun surveyIsOpen(): Boolean {
        var open = false
        instrumentation.runOnMainSync { open = SurveySession.active != null }
        return open
    }

    // On the activity on screen now - after recreate() a new one.
    private fun sheetIsShowing(): Boolean {
        var showing = false
        screen.onActivity {
            val sheet = it.supportFragmentManager.findFragmentByTag(SurveySheetFragment.TAG) as? SurveySheetFragment
            showing = sheet?.dialog?.isShowing == true
        }
        return showing
    }

    // Each check hops onto the main thread itself; the condition runs on the test's.
    private fun waitFor(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (true) {
            if (condition()) return
            check(SystemClock.uptimeMillis() < deadline) { "Timed out after $timeoutMs ms" }
            SystemClock.sleep(50)
        }
    }

    // Every ACTION_VIEW the app starts while [block] runs, stopped before it leaves the app - no browser opens.
    private fun withOpenedLinks(block: (LinkedBlockingQueue<Intent>) -> Unit) {
        val opened = LinkedBlockingQueue<Intent>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? =
                if (intent.action == Intent.ACTION_VIEW) {
                    opened.add(intent)
                    Instrumentation.ActivityResult(Activity.RESULT_OK, null)
                } else {
                    null
                }
        }
        instrumentation.addMonitor(monitor)
        try {
            block(opened)
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }
}

/** Every event a survey reports, in order, as one line each ("shown", "submitted 4.6", "closed USER"). */
internal class RecordingListener : SurveyListener {
    private val events = LinkedBlockingQueue<String>()

    override fun onShown() {
        events.add("shown")
    }

    override fun onSubmitted(result: SurveyResult) {
        events.add("submitted ${result.mainEvaluation}")
    }

    override fun onClosed(reason: CloseReason) {
        events.add("closed $reason")
    }

    override fun onError(error: SurveyError) {
        events.add("error ${error.code}")
    }

    fun expect(event: String, timeoutMs: Long = 5_000) {
        assertEquals(event, events.poll(timeoutMs, TimeUnit.MILLISECONDS))
    }

    fun expectNothing(forMs: Long) {
        assertNull(events.poll(forMs, TimeUnit.MILLISECONDS))
    }
}
