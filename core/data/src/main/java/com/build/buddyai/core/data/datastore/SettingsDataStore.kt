package com.build.buddyai.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.build.buddyai.core.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val THEME = stringPreferencesKey("theme")
        val EDITOR_FONT_SIZE = intPreferencesKey("editor_font_size")
        val EDITOR_TAB_WIDTH = intPreferencesKey("editor_tab_width")
        val EDITOR_SOFT_WRAP = booleanPreferencesKey("editor_soft_wrap")
        val EDITOR_LINE_NUMBERS = booleanPreferencesKey("editor_line_numbers")
        val EDITOR_AUTO_SAVE = booleanPreferencesKey("editor_auto_save")
        val EDITOR_HIGHLIGHT_LINE = booleanPreferencesKey("editor_highlight_line")
        val AI_DEFAULT_PROVIDER = stringPreferencesKey("ai_default_provider")
        val AI_DEFAULT_MODEL = stringPreferencesKey("ai_default_model")
        val AI_TEMPERATURE = floatPreferencesKey("ai_temperature")
        val AI_MAX_TOKENS = intPreferencesKey("ai_max_tokens")
        val AI_STREAM = booleanPreferencesKey("ai_stream")
        val AI_SHOW_TOKENS = booleanPreferencesKey("ai_show_tokens")
        val BUILD_AUTO_BUILD = booleanPreferencesKey("build_auto_build")
        val BUILD_NOTIFY_COMPLETE = booleanPreferencesKey("build_notify_complete")
        val BUILD_CLEAN_BEFORE = booleanPreferencesKey("build_clean_before")
        val NOTIFY_BUILD_COMPLETE = booleanPreferencesKey("notify_build_complete")
        val NOTIFY_BUILD_FAILED = booleanPreferencesKey("notify_build_failed")
        val PRIVACY_ANALYTICS = booleanPreferencesKey("privacy_analytics")
        val PRIVACY_CRASH = booleanPreferencesKey("privacy_crash")
    }

    val isOnboardingCompleted: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_COMPLETED] ?: false
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = completed
        }
    }

    val appSettings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            theme = prefs[Keys.THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.SYSTEM,
            editorSettings = EditorSettings(
                fontSize = prefs[Keys.EDITOR_FONT_SIZE] ?: 14,
                tabWidth = prefs[Keys.EDITOR_TAB_WIDTH] ?: 4,
                softWrap = prefs[Keys.EDITOR_SOFT_WRAP] ?: true,
                showLineNumbers = prefs[Keys.EDITOR_LINE_NUMBERS] ?: true,
                autoSave = prefs[Keys.EDITOR_AUTO_SAVE] ?: true,
                highlightCurrentLine = prefs[Keys.EDITOR_HIGHLIGHT_LINE] ?: true
            ),
            aiSettings = AiSettings(
                defaultProviderId = prefs[Keys.AI_DEFAULT_PROVIDER],
                defaultModelId = prefs[Keys.AI_DEFAULT_MODEL],
                parameters = ModelParameters(
                    temperature = prefs[Keys.AI_TEMPERATURE] ?: 0.7f,
                    maxTokens = prefs[Keys.AI_MAX_TOKENS] ?: 4096
                ),
                streamResponses = prefs[Keys.AI_STREAM] ?: true,
                showTokenCount = prefs[Keys.AI_SHOW_TOKENS] ?: true
            ),
            buildSettings = BuildSettings(
                autoBuildOnSave = prefs[Keys.BUILD_AUTO_BUILD] ?: false,
                showNotificationOnComplete = prefs[Keys.BUILD_NOTIFY_COMPLETE] ?: true,
                cleanBeforeBuild = prefs[Keys.BUILD_CLEAN_BEFORE] ?: false
            ),
            notificationSettings = NotificationSettings(
                buildComplete = prefs[Keys.NOTIFY_BUILD_COMPLETE] ?: true,
                buildFailed = prefs[Keys.NOTIFY_BUILD_FAILED] ?: true
            ),
            privacySettings = PrivacySettings(
                analyticsEnabled = prefs[Keys.PRIVACY_ANALYTICS] ?: false,
                crashReportingEnabled = prefs[Keys.PRIVACY_CRASH] ?: false
            )
        )
    }

    suspend fun updateTheme(theme: AppTheme) {
        context.settingsDataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun updateEditorSettings(settings: EditorSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.EDITOR_FONT_SIZE] = settings.fontSize
            prefs[Keys.EDITOR_TAB_WIDTH] = settings.tabWidth
            prefs[Keys.EDITOR_SOFT_WRAP] = settings.softWrap
            prefs[Keys.EDITOR_LINE_NUMBERS] = settings.showLineNumbers
            prefs[Keys.EDITOR_AUTO_SAVE] = settings.autoSave
            prefs[Keys.EDITOR_HIGHLIGHT_LINE] = settings.highlightCurrentLine
        }
    }

    suspend fun updateAiSettings(settings: AiSettings) {
        context.settingsDataStore.edit { prefs ->
            settings.defaultProviderId?.let { prefs[Keys.AI_DEFAULT_PROVIDER] = it } ?: prefs.remove(Keys.AI_DEFAULT_PROVIDER)
            settings.defaultModelId?.let { prefs[Keys.AI_DEFAULT_MODEL] = it } ?: prefs.remove(Keys.AI_DEFAULT_MODEL)
            prefs[Keys.AI_TEMPERATURE] = settings.parameters.temperature
            prefs[Keys.AI_MAX_TOKENS] = settings.parameters.maxTokens
            prefs[Keys.AI_STREAM] = settings.streamResponses
            prefs[Keys.AI_SHOW_TOKENS] = settings.showTokenCount
        }
    }

    suspend fun updateBuildSettings(settings: BuildSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.BUILD_AUTO_BUILD] = settings.autoBuildOnSave
            prefs[Keys.BUILD_NOTIFY_COMPLETE] = settings.showNotificationOnComplete
            prefs[Keys.BUILD_CLEAN_BEFORE] = settings.cleanBeforeBuild
        }
    }

    suspend fun updatePrivacySettings(settings: PrivacySettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.PRIVACY_ANALYTICS] = settings.analyticsEnabled
            prefs[Keys.PRIVACY_CRASH] = settings.crashReportingEnabled
        }
    }
}
