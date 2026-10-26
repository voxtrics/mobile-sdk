package io.voxtrics.demo

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.voxtrics.sdk.CloseReason
import io.voxtrics.sdk.SurveyError
import io.voxtrics.sdk.SurveyListener
import io.voxtrics.sdk.SurveyOptions
import io.voxtrics.sdk.SurveyResult
import io.voxtrics.sdk.Theme
import io.voxtrics.sdk.Voxtrics
import io.voxtrics.sdk.VoxtricsConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// One screen to try the SDK with: where Voxtrics is, which survey, from which screen, in which theme - and every
// callback the listener gets, newest first. The fields are kept between launches.
class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("demo", MODE_PRIVATE) }
    private lateinit var events: EventLog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val baseUrl = findViewById<EditText>(R.id.baseUrl)
        val surveyId = findViewById<EditText>(R.id.surveyId)
        val screen = findViewById<EditText>(R.id.screen)
        val theme = findViewById<RadioGroup>(R.id.theme)
        val log = findViewById<TextView>(R.id.log)
        events = ViewModelProvider(this)[EventLog::class.java]
        log.text = events.text
        events.onChange = { log.text = it }

        baseUrl.setText(prefs.getString(KEY_BASE_URL, LOCAL_STACK))
        surveyId.setText(prefs.getString(KEY_SURVEY_ID, ""))
        screen.setText(prefs.getString(KEY_SCREEN, "checkout"))

        findViewById<Button>(R.id.show).setOnClickListener {
            prefs.edit()
                .putString(KEY_BASE_URL, baseUrl.text.toString().trim())
                .putString(KEY_SURVEY_ID, surveyId.text.toString().trim())
                .putString(KEY_SCREEN, screen.text.toString().trim())
                .apply()
            Voxtrics.configure(this, VoxtricsConfig(baseUrl = baseUrl.text.toString().trim(), logging = true))
            // The listener outlives a rotation; it writes to the log, never to this activity.
            val events = events
            events.add("showSurvey")
            Voxtrics.showSurvey(
                this,
                surveyId = surveyId.text.toString().trim(),
                options = SurveyOptions(
                    screen = screen.text.toString().trim().ifEmpty { null },
                    theme = when (theme.checkedRadioButtonId) {
                        R.id.themeLight -> Theme.LIGHT
                        R.id.themeDark -> Theme.DARK
                        else -> Theme.SYSTEM
                    },
                ),
                listener = object : SurveyListener {
                    override fun onShown() = events.add("onShown")
                    override fun onSubmitted(result: SurveyResult) = events.add("onSubmitted mainEvaluation=${result.mainEvaluation}")
                    override fun onClosed(reason: CloseReason) = events.add("onClosed $reason")
                    override fun onError(error: SurveyError) = events.add("onError ${error.code}: ${error.message}")
                },
            )
        }
        findViewById<Button>(R.id.clearLog).setOnClickListener { events.clear() }
    }

    override fun onDestroy() {
        events.onChange = null
        super.onDestroy()
    }

    // Survives a rotation, unlike the activity: the survey's callbacks write here, and whichever activity is up shows it.
    class EventLog : ViewModel() {
        private val time = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
        var text = ""
            private set
        var onChange: ((String) -> Unit)? = null

        fun add(line: String) {
            text = "${time.format(Date())}  $line\n$text"
            onChange?.invoke(text)
        }

        fun clear() {
            text = ""
            onChange?.invoke(text)
        }
    }

    private companion object {
        const val LOCAL_STACK = "http://localhost:3000"
        const val KEY_BASE_URL = "baseUrl"
        const val KEY_SURVEY_ID = "surveyId"
        const val KEY_SCREEN = "screen"
    }
}
