package com.build.buddyai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.build.buddyai.core.data.datastore.SettingsDataStore
import com.build.buddyai.core.designsystem.theme.BuildBuddyTheme
import com.build.buddyai.navigation.AppNavigation
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        var isReady = false
        splashScreen.setKeepOnScreenCondition { !isReady }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isOnboarded by settingsDataStore.isOnboardingCompleted
                .collectAsState(initial = null)

            if (isOnboarded != null) {
                isReady = true
            }

            BuildBuddyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    isOnboarded?.let { onboarded ->
                        AppNavigation(isOnboarded = onboarded)
                    }
                }
            }
        }
    }
}
