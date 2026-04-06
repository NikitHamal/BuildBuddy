package com.build.buddyai.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.build.buddyai.core.model.AgentAutonomyMode
import com.build.buddyai.core.model.AppSettings
import com.build.buddyai.core.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "buildbuddy_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val EDITOR_FONT_SIZE = intPreferencesKey("editor_font_size")
        val EDITOR_TAB_WIDTH = intPreferencesKey("editor_tab_width")
        val EDITOR_SOFT_WRAP = booleanPreferencesKey("editor_soft_wrap")
        val EDITOR_LINE_NUMBERS = booleanPreferencesKey("editor_line_numbers")
        val EDITOR_AUTOSAVE = booleanPreferencesKey("editor_autosave")
        val BUILD_NOTIFICATIONS = booleanPreferencesKey("build_notifications")
        val BUILD_CACHE_ENABLED = booleanPreferencesKey("build_cache_enabled")
        val AUTONOMY_MODE = stringPreferencesKey("autonomy_mode")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val DEFAULT_PROVIDER_ID = stringPreferencesKey("default_provider_id")
        val DEFAULT_MODEL_ID = stringPreferencesKey("default_model_id")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs ->
        AppSettings(
            theme = prefs[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            editorFontSize = prefs[Keys.EDITOR_FONT_SIZE] ?: 14,
            editorTabWidth = prefs[Keys.EDITOR_TAB_WIDTH] ?: 4,
            editorSoftWrap = prefs[Keys.EDITOR_SOFT_WRAP] ?: true,
            editorLineNumbers = prefs[Keys.EDITOR_LINE_NUMBERS] ?: true,
            editorAutosave = prefs[Keys.EDITOR_AUTOSAVE] ?: true,
            buildNotifications = prefs[Keys.BUILD_NOTIFICATIONS] ?: true,
            buildCacheEnabled = prefs[Keys.BUILD_CACHE_ENABLED] ?: true,
            autonomyMode = prefs[Keys.AUTONOMY_MODE]?.let { runCatching { AgentAutonomyMode.valueOf(it) }.getOrNull() } ?: AgentAutonomyMode.AUTONOMOUS_SAFE,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            defaultProviderId = prefs[Keys.DEFAULT_PROVIDER_ID],
            defaultModelId = prefs[Keys.DEFAULT_MODEL_ID]
        )
    }

    suspend fun updateTheme(theme: ThemeMode) = context.dataStore.edit { it[Keys.THEME] = theme.name }
    suspend fun updateEditorFontSize(size: Int) = context.dataStore.edit { it[Keys.EDITOR_FONT_SIZE] = size }
    suspend fun updateEditorTabWidth(width: Int) = context.dataStore.edit { it[Keys.EDITOR_TAB_WIDTH] = width }
    suspend fun updateEditorSoftWrap(enabled: Boolean) = context.dataStore.edit { it[Keys.EDITOR_SOFT_WRAP] = enabled }
    suspend fun updateEditorLineNumbers(enabled: Boolean) = context.dataStore.edit { it[Keys.EDITOR_LINE_NUMBERS] = enabled }
    suspend fun updateEditorAutosave(enabled: Boolean) = context.dataStore.edit { it[Keys.EDITOR_AUTOSAVE] = enabled }
    suspend fun updateBuildNotifications(enabled: Boolean) = context.dataStore.edit { it[Keys.BUILD_NOTIFICATIONS] = enabled }
    suspend fun updateBuildCacheEnabled(enabled: Boolean) = context.dataStore.edit { it[Keys.BUILD_CACHE_ENABLED] = enabled }
    suspend fun updateAutonomyMode(mode: AgentAutonomyMode) = context.dataStore.edit { it[Keys.AUTONOMY_MODE] = mode.name }
    suspend fun setOnboardingCompleted() = context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = true }
    suspend fun updateDefaultProvider(providerId: String?) = context.dataStore.edit {
        if (providerId != null) it[Keys.DEFAULT_PROVIDER_ID] = providerId
        else it.remove(Keys.DEFAULT_PROVIDER_ID)
    }
    suspend fun updateDefaultModel(modelId: String?) = context.dataStore.edit {
        if (modelId != null) it[Keys.DEFAULT_MODEL_ID] = modelId
        else it.remove(Keys.DEFAULT_MODEL_ID)
    }
    suspend fun clearAll() = context.dataStore.edit { it.clear() }
}
