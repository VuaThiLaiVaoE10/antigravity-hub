package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.firebase.configureAppCheck
import com.example.ui.navigation.AntigravityHubApp
import com.example.ui.theme.AntigravityHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before Compose UI or ViewModels.
        configureAppCheck(this, intent)

        // STEP 2: Enable edge-to-edge with dark system bars matching the Obsidian Mission Control deck.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContent {
            AntigravityHubTheme {
                AntigravityHubApp()
            }
        }
    }
}
