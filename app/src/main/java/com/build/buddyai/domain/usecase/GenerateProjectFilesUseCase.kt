package com.build.buddyai.domain.usecase

import android.content.Context
import com.build.buddyai.core.common.FileUtils
import com.build.buddyai.core.model.Project
import com.build.buddyai.core.model.ProjectTemplate
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenerateProjectFilesUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    operator fun invoke(project: Project) {
        val projectDir = File(project.projectPath)
        projectDir.mkdirs()

        extractGradleWrapper(projectDir)
        copyBuildToolsStubs(projectDir)

        when (project.template) {
            ProjectTemplate.BLANK_COMPOSE -> generateComposeProject(projectDir, project)
            ProjectTemplate.BLANK_VIEWS -> generateViewsProject(projectDir, project)
            ProjectTemplate.SINGLE_ACTIVITY_COMPOSE -> generateSingleActivityCompose(projectDir, project)
            ProjectTemplate.JAVA_ACTIVITY -> generateJavaProject(projectDir, project)
            ProjectTemplate.BASIC_UTILITY -> generateBasicUtility(projectDir, project)
        }
    }

    private fun generateComposeProject(dir: File, project: Project) {
        val pkgPath = project.packageName.replace(".", "/")

        generateGradleFiles(dir, project)
        generateManifest(dir, project, ".MainActivity", isCompose = true)

        // Main Activity
        val srcDir = File(dir, "app/src/main/java/$pkgPath")
        srcDir.mkdirs()
        File(srcDir, "MainActivity.kt").writeText("""
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
import ${project.packageName}.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun MainScreen() {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("${project.name}") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Hello, ${project.name}!",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Built with BuildBuddy",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
""".trimIndent())

        // Theme
        val themeDir = File(dir, "app/src/main/java/$pkgPath/ui/theme")
        themeDir.mkdirs()
        File(themeDir, "Theme.kt").writeText("""
package ${project.packageName}.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme()
private val DarkColorScheme = darkColorScheme()

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
""".trimIndent())

        // Strings and theme for Compose projects
        val resDir = File(dir, "app/src/main/res/values")
        resDir.mkdirs()
        File(resDir, "strings.xml").writeText("""
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">${project.name}</string>
</resources>
""".trimIndent())
        // Generate a minimal themes.xml for on-device build compatibility
        File(resDir, "themes.xml").writeText("""
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.App" parent="android:Theme.Material.Light.NoActionBar"/>
</resources>
""".trimIndent())
    }

    private fun generateViewsProject(dir: File, project: Project) {
        val pkgPath = project.packageName.replace(".", "/")
        generateGradleFiles(dir, project)
        generateManifest(dir, project, ".MainActivity", isCompose = false)

        val srcDir = File(dir, "app/src/main/java/$pkgPath")
        srcDir.mkdirs()
        File(srcDir, "MainActivity.kt").writeText("""
package ${project.packageName}

import android.app.Activity
import android.os.Bundle

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
""".trimIndent())

        val layoutDir = File(dir, "app/src/main/res/layout")
        layoutDir.mkdirs()
        File(layoutDir, "activity_main.xml").writeText("""
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Hello, ${project.name}!"
        android:textSize="24sp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
""".trimIndent())

        val resDir = File(dir, "app/src/main/res/values")
        resDir.mkdirs()
        File(resDir, "strings.xml").writeText("""
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">${project.name}</string>
</resources>
""".trimIndent())
        // Generate themes.xml with PLATFORM theme for on-device build compatibility
        // MaterialComponents theme won't work during AAPT2 linking since it's not in android.jar
        // We use platform theme here and apply MaterialComponents styling programmatically if needed
        File(resDir, "themes.xml").writeText("""
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.App" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:colorPrimary">@android:color/holo_blue_dark</item>
        <item name="android:colorPrimaryDark">@android:color/holo_blue_dark</item>
        <item name="android:colorAccent">@android:color/holo_blue_light</item>
    </style>
</resources>
""".trimIndent())
    }

    private fun generateSingleActivityCompose(dir: File, project: Project) {
        generateComposeProject(dir, project)
    }

    private fun generateJavaProject(dir: File, project: Project) {
        val pkgPath = project.packageName.replace(".", "/")
        generateGradleFiles(dir, project)
        generateManifest(dir, project, ".MainActivity", isCompose = false)

        val srcDir = File(dir, "app/src/main/java/$pkgPath")
        srcDir.mkdirs()
        File(srcDir, "MainActivity.java").writeText("""
package ${project.packageName};

import android.app.Activity;
import android.os.Bundle;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}
""".trimIndent())

        val layoutDir = File(dir, "app/src/main/res/layout")
        layoutDir.mkdirs()
        File(layoutDir, "activity_main.xml").writeText("""
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Hello, ${project.name}!"
        android:textSize="24sp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
""".trimIndent())

        val resDir = File(dir, "app/src/main/res/values")
        resDir.mkdirs()
        File(resDir, "strings.xml").writeText("""
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">${project.name}</string>
</resources>
""".trimIndent())
        // Generate themes.xml with PLATFORM theme for on-device build compatibility
        File(resDir, "themes.xml").writeText("""
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.App" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:colorPrimary">@android:color/holo_blue_dark</item>
        <item name="android:colorPrimaryDark">@android:color/holo_blue_dark</item>
        <item name="android:colorAccent">@android:color/holo_blue_light</item>
    </style>
</resources>
""".trimIndent())
    }

    private fun generateBasicUtility(dir: File, project: Project) {
        generateComposeProject(dir, project)
    }

    private fun generateGradleFiles(dir: File, project: Project) {
        File(dir, "settings.gradle.kts").writeText("""
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "${project.name.replace("\"", "\\\"")}"
include(":app")
""".trimIndent())

        val isCompose = project.uiFramework == com.build.buddyai.core.model.UiFramework.COMPOSE
        File(dir, "build.gradle.kts").writeText("""
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
${if (isCompose) "    id(\"org.jetbrains.kotlin.plugin.compose\") version \"2.1.0\" apply false" else ""}
}
""".trimIndent())

        File(dir, "gradle.properties").writeText("""
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
""".trimIndent())

        val appBuildGradle = buildString {
            appendLine("plugins {")
            appendLine("    id(\"com.android.application\")")
            if (project.language == com.build.buddyai.core.model.ProjectLanguage.KOTLIN) {
                appendLine("    id(\"org.jetbrains.kotlin.android\")")
            }
            if (isCompose) {
                appendLine("    id(\"org.jetbrains.kotlin.plugin.compose\")")
            }
            appendLine("}")
            appendLine()
            appendLine("android {")
            appendLine("    namespace = \"${project.packageName}\"")
            appendLine("    compileSdk = ${project.targetSdk}")
            appendLine()
            appendLine("    defaultConfig {")
            appendLine("        applicationId = \"${project.packageName}\"")
            appendLine("        minSdk = ${project.minSdk}")
            appendLine("        targetSdk = ${project.targetSdk}")
            appendLine("        versionCode = 1")
            appendLine("        versionName = \"1.0.0\"")
            appendLine("    }")
            appendLine()
            appendLine("    buildTypes {")
            appendLine("        release {")
            appendLine("            isMinifyEnabled = false")
            appendLine("        }")
            appendLine("    }")
            appendLine()
            appendLine("    compileOptions {")
            appendLine("        sourceCompatibility = JavaVersion.VERSION_17")
            appendLine("        targetCompatibility = JavaVersion.VERSION_17")
            appendLine("    }")
            if (project.language == com.build.buddyai.core.model.ProjectLanguage.KOTLIN) {
                appendLine()
                appendLine("    kotlinOptions {")
                appendLine("        jvmTarget = \"17\"")
                appendLine("    }")
            }
            if (isCompose) {
                appendLine()
                appendLine("    buildFeatures {")
                appendLine("        compose = true")
                appendLine("    }")
            }
            appendLine("}")
            appendLine()
            appendLine("dependencies {")
            appendLine("    implementation(\"androidx.core:core-ktx:1.15.0\")")
            if (isCompose) {
                appendLine("    implementation(platform(\"androidx.compose:compose-bom:2024.12.01\"))")
                appendLine("    implementation(\"androidx.compose.ui:ui\")")
                appendLine("    implementation(\"androidx.compose.material3:material3\")")
                appendLine("    implementation(\"androidx.compose.ui:ui-tooling-preview\")")
                appendLine("    implementation(\"androidx.activity:activity-compose:1.9.3\")")
                appendLine("    debugImplementation(\"androidx.compose.ui:ui-tooling\")")
            } else {
                appendLine("    implementation(\"androidx.appcompat:appcompat:1.7.0\")")
                appendLine("    implementation(\"com.google.android.material:material:1.12.0\")")
                appendLine("    implementation(\"androidx.constraintlayout:constraintlayout:2.2.0\")")
            }
            appendLine("}")
        }
        File(dir, "app/build.gradle.kts").apply {
            parentFile?.mkdirs()
            writeText(appBuildGradle)
        }
    }

    private fun generateManifest(dir: File, project: Project, activityName: String, isCompose: Boolean = false) {
        val manifestDir = File(dir, "app/src/main")
        manifestDir.mkdirs()
        // For on-device builds, we must use platform themes that exist in android.jar
        // Compose projects don't need manifest-level theming (Compose handles it)
        // Views projects need a valid platform theme
        val themeRef = "@style/Theme.App"
        File(manifestDir, "AndroidManifest.xml").writeText("""
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="${project.packageName}">

    <application
        android:allowBackup="false"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:usesCleartextTraffic="false"
        android:theme="$themeRef">

        <activity
            android:name="$activityName"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>

</manifest>
""".trimIndent())
    }

    private fun copyAssetFile(assetPath: String, destFile: File) {
        destFile.parentFile?.mkdirs()
        try {
            context.assets.open(assetPath).use { input ->
                java.io.FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun extractGradleWrapper(dir: File) {
        copyAssetFile("gradle_wrapper/gradlew", File(dir, "gradlew"))
        File(dir, "gradlew").setExecutable(true)
        copyAssetFile("gradle_wrapper/gradlew.bat", File(dir, "gradlew.bat"))
        copyAssetFile("gradle_wrapper/gradle/libs.versions.toml", File(dir, "gradle/libs.versions.toml"))
        copyAssetFile("gradle_wrapper/gradle/wrapper/gradle-wrapper.jar", File(dir, "gradle/wrapper/gradle-wrapper.jar"))
        copyAssetFile("gradle_wrapper/gradle/wrapper/gradle-wrapper.properties", File(dir, "gradle/wrapper/gradle-wrapper.properties"))
    }

    /**
     * Copies build tools stubs needed for on-device compilation.
     */
    fun copyBuildToolsStubs(dir: File) {
        val buildToolsDir = File(dir, "build_tools")
        buildToolsDir.mkdirs()
        copyAssetFile("build_tools/javax-lang-model-stubs.jar", File(buildToolsDir, "javax-lang-model-stubs.jar"))
    }
}
