package com.voicebox.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voicebox.voiceboxkit.VoiceboxEffect
import com.voicebox.voiceboxkit.VoiceboxKit
import com.voicebox.voiceboxkit.VoiceboxListener
import com.voicebox.voiceboxkit.VoiceboxPresentationMode
import com.voicebox.voiceboxkit.VoiceboxState
import com.voicebox.voiceboxkit.VoiceboxView
import com.voicebox.voiceboxkit.rememberVoiceboxState
import kotlinx.coroutines.delay

private const val HANDLE = "p-test"

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                ExampleScreen(activity = this)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExampleScreen(activity: AppCompatActivity) {

    // Status message — auto-clears after 3 seconds (mirrors iOS ContentView behaviour)
    var statusMessage by remember { mutableStateOf("") }
    LaunchedEffect(statusMessage) {
        if (statusMessage.isNotEmpty()) {
            delay(3_000)
            statusMessage = ""
        }
    }

    // Shared listener that feeds status updates back to the UI
    val listener = object : VoiceboxListener {
        override fun onRecordingComplete(voiceboxView: VoiceboxView) {
            statusMessage = "Recording complete ✓"
        }
        override fun onMessageSubmitted(voiceboxView: VoiceboxView) {
            statusMessage = "Message submitted ✓"
        }
        override fun onDismiss(voiceboxView: VoiceboxView) {
            statusMessage = "Voicebox dismissed"
        }
        override fun onFailure(voiceboxView: VoiceboxView, error: Exception) {
            statusMessage = "Error: ${error.message ?: "unknown"}"
        }
    }

    // Compose state for all non-fullscreen modes (shared — mode is switched before presenting)
    val sheetState = rememberVoiceboxState(handle = HANDLE)
    sheetState.listener = listener

    // Separate state for FullScreen (mirrors iOS two-.voicebox()-modifier pattern)
    val fullScreenState = remember {
        VoiceboxState(handle = HANDLE).also {
            it.presentationMode = VoiceboxPresentationMode.FullScreen
        }
    }
    fullScreenState.listener = listener

    // Wire both states to the fragment layer — renders nothing itself
    VoiceboxEffect(state = sheetState)
    VoiceboxEffect(state = fullScreenState)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("VoiceboxKit") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))

            Text(
                text = "Android SDK ${VoiceboxKit.VERSION}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )

            Spacer(Modifier.height(16.dp))

            // ── Compose API ──────────────────────────────────────────────────

            SectionLabel("Compose API")

            ModeButton("Bottom Sheet") {
                sheetState.presentationMode = VoiceboxPresentationMode.BottomSheet
                sheetState.isPresented = true
            }
            ModeButton("Sheet (Full Height)") {
                sheetState.presentationMode = VoiceboxPresentationMode.Sheet
                sheetState.isPresented = true
            }
            ModeButton("Full Screen") {
                fullScreenState.isPresented = true
            }
            ModeButton("Fit Content") {
                sheetState.presentationMode = VoiceboxPresentationMode.FitContent
                sheetState.isPresented = true
            }
            ModeButton("Custom Height (500 dp)") {
                sheetState.presentationMode = VoiceboxPresentationMode.Custom(height = 500f)
                sheetState.isPresented = true
            }
            ModeButton("Custom Fraction (70 %)") {
                sheetState.presentationMode = VoiceboxPresentationMode.CustomFraction(fraction = 0.7f)
                sheetState.isPresented = true
            }
            ModeButton("Overlay (floating)") {
                sheetState.presentationMode = VoiceboxPresentationMode.Overlay
                sheetState.isPresented = true
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // ── View API ─────────────────────────────────────────────────────

            SectionLabel("View API")

            OutlinedButton(
                onClick = {
                    VoiceboxView(handle = HANDLE).also { it.listener = listener }.present(activity)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Bottom Sheet (View API)")
            }

            OutlinedButton(
                onClick = {
                    VoiceboxView(handle = HANDLE)
                        .also { it.listener = listener }
                        .presentAsOverlay(activity)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Overlay (View API)")
            }

            Spacer(Modifier.height(16.dp))

            // ── Status ───────────────────────────────────────────────────────

            AnimatedVisibility(visible = statusMessage.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ModeButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label)
    }
}
