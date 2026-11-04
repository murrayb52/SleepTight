package io.github.murrayb52.sleeptight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.murrayb52.sleeptight.ui.home.HomeRoute
import io.github.murrayb52.sleeptight.ui.theme.SleepTightTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SleepTightTheme {
                HomeRoute()
            }
        }
    }
}
