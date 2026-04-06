package com.build.buddyai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.build.buddyai.core.data.datastore.SettingsDataStore
import com.build.buddyai.core.designsystem.theme.BuildBuddyTheme
import com.build.buddyai.core.model.ThemeMode
import com.build.buddyai.navigation.BuildBuddyNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by settingsDataStore.settings.collectAsState(
                initial = com.build.buddyai.core.model.AppSettings()
            )

            val darkTheme = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            BuildBuddyTheme(darkTheme = darkTheme) {
                BuildBuddyNavHost(
                    onboardingCompleted = settings.onboardingCompleted
                )
            }
        }
    }
}
