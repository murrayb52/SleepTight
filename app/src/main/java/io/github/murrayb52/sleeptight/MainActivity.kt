package io.github.murrayb52.sleeptight

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.murrayb52.sleeptight.ui.SleepTightApp
import io.github.murrayb52.sleeptight.ui.theme.SleepTightTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always light status-bar icons: the app is dark.
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val openNight = intent.getLongExtra(EXTRA_OPEN_NIGHT, 0).takeIf { it > 0 }
        setContent {
            SleepTightTheme {
                SleepTightApp(openNightId = openNight)
            }
        }
    }

    companion object {
        private const val EXTRA_OPEN_NIGHT = "openNight"

        /** Opens the app on one night's page. */
        fun openNight(context: Context, nightId: Long) = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_NIGHT, nightId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
