package dev.russell.audiolimit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.russell.audiolimit.ui.AppNav
import dev.russell.audiolimit.ui.theme.AudioLimitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AudioLimitTheme {
                AppNav()
            }
        }
    }
}
