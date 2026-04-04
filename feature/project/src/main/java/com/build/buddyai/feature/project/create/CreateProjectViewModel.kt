package com.build.buddyai.feature.project.create

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.model.*
import com.build.buddyai.core.ui.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateProjectState(
    val step: Int = 0,
    val totalSteps: Int = 3,
    val appName: String = "",
    val packageName: String = "",
    val description: String = "",
    val language: ProjectLanguage = ProjectLanguage.KOTLIN,
    val uiFramework: UiFramework = UiFramework.COMPOSE,
    val template: ProjectTemplate = ProjectTemplate.BLANK_COMPOSE,
    val minSdk: Int = 26,
    val targetSdk: Int = 35,
    val appNameError: String? = null,
    val packageNameError: String? = null,
    val isCreating: Boolean = false,
    val createdProjectId: String? = null
)

@HiltViewModel
class CreateProjectViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(CreateProjectState())
    val state: StateFlow<CreateProjectState> = _state.asStateFlow()

    fun updateAppName(name: String) {
        _state.update {
            val error = validateAppName(name)
            val pkg = if (it.packageName.isEmpty() || it.packageName == generatePackageName(it.appName)) {
                generatePackageName(name)
            } else it.packageName
            it.copy(appName = name, appNameError = error, packageName = pkg)
        }
    }

    fun updatePackageName(name: String) {
        _state.update { it.copy(packageName = name, packageNameError = validatePackageName(name)) }
    }

    fun updateDescription(desc: String) { _state.update { it.copy(description = desc) } }
    fun updateLanguage(lang: ProjectLanguage) {
        _state.update {
            val newTemplate = when {
                lang == ProjectLanguage.JAVA -> ProjectTemplate.JAVA_ACTIVITY
                it.template.language != lang -> ProjectTemplate.BLANK_COMPOSE
                else -> it.template
            }
            val newUi = if (lang == ProjectLanguage.JAVA) UiFramework.XML_VIEWS else it.uiFramework
            it.copy(language = lang, uiFramework = newUi, template = newTemplate)
        }
    }
    fun updateUiFramework(ui: UiFramework) {
        _state.update {
            val newTemplate = ProjectTemplate.entries.first { t -> t.uiFramework == ui && t.language == it.language }
            it.copy(uiFramework = ui, template = newTemplate)
        }
    }
    fun updateTemplate(template: ProjectTemplate) {
        _state.update { it.copy(template = template, language = template.language, uiFramework = template.uiFramework) }
    }
    fun updateMinSdk(sdk: Int) { _state.update { it.copy(minSdk = sdk.coerceIn(21, 34)) } }
    fun updateTargetSdk(sdk: Int) { _state.update { it.copy(targetSdk = sdk.coerceIn(26, 35)) } }

    fun nextStep() {
        val s = _state.value
        if (s.step == 0) {
            val nameErr = validateAppName(s.appName)
            val pkgErr = validatePackageName(s.packageName)
            if (nameErr != null || pkgErr != null) {
                _state.update { it.copy(appNameError = nameErr, packageNameError = pkgErr) }
                return
            }
        }
        _state.update { it.copy(step = (it.step + 1).coerceAtMost(it.totalSteps - 1)) }
    }

    fun previousStep() { _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) } }

    fun createProject() {
        viewModelScope.launch {
            _state.update { it.copy(isCreating = true) }
            val s = _state.value
            val project = Project(
                name = s.appName.trim(),
                packageName = s.packageName.trim(),
                description = s.description.trim(),
                language = s.language,
                uiFramework = s.uiFramework,
                template = s.template,
                minSdk = s.minSdk,
                targetSdk = s.targetSdk
            )
            val projectDir = FileUtils.getProjectDir(context, project.id)
            generateProjectFiles(projectDir, project)
            val updatedProject = project.copy(projectPath = projectDir.absolutePath)
            projectRepository.create(updatedProject)
            _state.update { it.copy(isCreating = false, createdProjectId = updatedProject.id) }
        }
    }

    private fun generateProjectFiles(projectDir: java.io.File, project: Project) {
        val pkgPath = project.packageName.replace('.', '/')
        val srcDir = java.io.File(projectDir, "app/src/main/java/$pkgPath")
        srcDir.mkdirs()
        val resDir = java.io.File(projectDir, "app/src/main/res/values")
        resDir.mkdirs()
        java.io.File(projectDir, "app/src/main/res/layout").mkdirs()

        // Root build.gradle.kts
        java.io.File(projectDir, "build.gradle.kts").writeText("""
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}
""".trimIndent())

        // settings.gradle.kts
        java.io.File(projectDir, "settings.gradle.kts").writeText("""
rootProject.name = "${project.name}"
include(":app")
""".trimIndent())

        // App build.gradle.kts
        java.io.File(projectDir, "app").mkdirs()
        java.io.File(projectDir, "app/build.gradle.kts").writeText("""
plugins {
    id("com.android.application")
    ${if (project.language == ProjectLanguage.KOTLIN) "id(\"org.jetbrains.kotlin.android\")" else ""}
    ${if (project.uiFramework == UiFramework.COMPOSE) "id(\"org.jetbrains.kotlin.plugin.compose\")" else ""}
}

android {
    namespace = "${project.packageName}"
    compileSdk = ${project.targetSdk}
    defaultConfig {
        applicationId = "${project.packageName}"
        minSdk = ${project.minSdk}
        targetSdk = ${project.targetSdk}
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    ${if (project.language == ProjectLanguage.KOTLIN) "kotlinOptions { jvmTarget = \"17\" }" else ""}
    ${if (project.uiFramework == UiFramework.COMPOSE) """
    buildFeatures { compose = true }
    """.trimIndent() else "buildFeatures { viewBinding = true }"}
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    ${if (project.uiFramework == UiFramework.COMPOSE) """
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    """.trimIndent() else """
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    """.trimIndent()}
}
""".trimIndent())

        // AndroidManifest.xml
        java.io.File(projectDir, "app/src/main/AndroidManifest.xml").writeText("""
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="true"
        android:label="${project.name}"
        android:supportsRtl="true"
        android:theme="@style/Theme.${project.name.replace(" ", "")}">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
""".trimIndent())

        // Main Activity
        when {
            project.uiFramework == UiFramework.COMPOSE && project.language == ProjectLanguage.KOTLIN -> {
                java.io.File(srcDir, "MainActivity.kt").writeText("""
package ${project.packageName}

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Greeting()
                }
            }
        }
    }
}

@Composable
fun Greeting() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "${project.name}",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Built with BuildBuddy",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
""".trimIndent())
            }
            project.language == ProjectLanguage.JAVA -> {
                java.io.File(srcDir, "MainActivity.java").writeText("""
package ${project.packageName};

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}
""".trimIndent())
                java.io.File(projectDir, "app/src/main/res/layout/activity_main.xml").writeText("""
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="${project.name}"
        android:textSize="24sp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />
</androidx.constraintlayout.widget.ConstraintLayout>
""".trimIndent())
            }
            else -> {
                java.io.File(srcDir, "MainActivity.kt").writeText("""
package ${project.packageName}

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
""".trimIndent())
                java.io.File(projectDir, "app/src/main/res/layout/activity_main.xml").writeText("""
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="${project.name}"
        android:textSize="24sp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />
</androidx.constraintlayout.widget.ConstraintLayout>
""".trimIndent())
            }
        }

        // strings.xml
        java.io.File(resDir, "strings.xml").writeText("""
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">${project.name}</string>
</resources>
""".trimIndent())

        // themes.xml
        java.io.File(resDir, "themes.xml").writeText("""
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.${project.name.replace(" ", "")}" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <item name="colorPrimary">@color/purple_500</item>
        <item name="colorPrimaryVariant">@color/purple_700</item>
        <item name="colorOnPrimary">@color/white</item>
    </style>
</resources>
""".trimIndent())

        // colors.xml
        java.io.File(resDir, "colors.xml").writeText("""
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="purple_500">#FF6200EE</color>
    <color name="purple_700">#FF3700B3</color>
    <color name="white">#FFFFFFFF</color>
</resources>
""".trimIndent())

        // gradle.properties
        java.io.File(projectDir, "gradle.properties").writeText("""
android.useAndroidX=true
org.gradle.jvmargs=-Xmx2048m
kotlin.code.style=official
""".trimIndent())
    }

    private fun validateAppName(name: String): String? {
        return when {
            name.isBlank() -> "App name is required"
            name.length < 2 -> "App name must be at least 2 characters"
            name.length > 50 -> "App name must be under 50 characters"
            else -> null
        }
    }

    private fun validatePackageName(name: String): String? {
        val pattern = Regex("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*){1,}\$")
        return when {
            name.isBlank() -> "Package name is required"
            !pattern.matches(name) -> "Invalid package name (e.g. com.example.app)"
            name.split('.').any { it.length < 2 } -> "Each segment must be at least 2 characters"
            else -> null
        }
    }

    private fun generatePackageName(appName: String): String {
        val sanitized = appName.lowercase().replace(Regex("[^a-z0-9]"), "")
        return if (sanitized.isNotEmpty()) "com.app.$sanitized" else ""
    }
}