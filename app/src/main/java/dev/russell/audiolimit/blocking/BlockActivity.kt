package dev.russell.audiolimit.blocking

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.russell.audiolimit.appGraph
import dev.russell.audiolimit.ui.components.AppIcon
import dev.russell.audiolimit.ui.theme.AudioLimitTheme
import dev.russell.audiolimit.util.formatDuration
import dev.russell.audiolimit.util.formatUntil

/** Full-screen lock shown over a blocked app (or a guarded settings page, when no package is given). */
class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        setContent {
            AudioLimitTheme {
                BlockScreen(pkg, onHome = ::goHome)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    @Composable
    private fun BlockScreen(pkg: String?, onHome: () -> Unit) {
        val evaluation by appGraph.enforcer.evaluation.collectAsState()
        val block = if (pkg != null) evaluation.screenBlockFor(pkg) else null
        val until = (if (pkg != null) block?.blockedUntil else evaluation.lockedUntil)
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (pkg != null) AppIcon(pkg, Modifier.size(72.dp))
                else Icon(Icons.Default.Lock, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(24.dp))
                Text(
                    if (pkg != null) "${appGraph.apps.label(pkg)} is locked" else "Settings are locked",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                val reason = when {
                    pkg == null -> "Audio Limit's system settings can't be changed while a lock is active."
                    block == null -> "The lock has lifted."
                    block.limitReached ->
                        "${block.rule.name}: daily limit of ${formatDuration(block.limitMillis!!)} reached."
                    else -> "${block.rule.name}: blocked during this time."
                }
                Text(reason, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                if (until != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Unlocks at ${formatUntil(until)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(32.dp))
                Button(onClick = onHome) { Text("Go home") }
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
    }
}
