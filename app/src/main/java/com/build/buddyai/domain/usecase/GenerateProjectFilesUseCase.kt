package com.build.buddyai.domain.usecase

import android.content.Context
import com.build.buddyai.core.model.Project
import com.build.buddyai.core.model.ProjectLanguage
import com.build.buddyai.core.model.ProjectTemplate
import com.build.buddyai.core.model.UiFramework
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Properties
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
        generateGradleFiles(projectDir, project)
        writeBuildBuddyProperties(projectDir, project)

        when (project.template) {
            ProjectTemplate.BLANK_COMPOSE -> generateComposeStarter(projectDir, project)
            ProjectTemplate.SINGLE_ACTIVITY_COMPOSE -> generateComposeDashboard(projectDir, project)
            ProjectTemplate.COMPOSE_SETTINGS -> generateComposeSettings(projectDir, project)
            ProjectTemplate.BLANK_VIEWS -> generateViewsStarter(projectDir, project)
            ProjectTemplate.JAVA_DASHBOARD -> generateJavaDashboard(projectDir, project)
            ProjectTemplate.BASIC_UTILITY -> generateJavaUtility(projectDir, project)
            ProjectTemplate.JAVA_FORM -> generateJavaForm(projectDir, project)
            ProjectTemplate.JAVA_LIST_DETAIL -> generateJavaListDetail(projectDir, project)
        }
    }

    private fun generateComposeStarter(dir: File, project: Project) {
        generateManifest(dir, project, ".MainActivity")
        val pkgPath = project.packageName.replace('.', '/')
        val srcDir = File(dir, "app/src/main/java/$pkgPath").apply { mkdirs() }
        File(srcDir, "MainActivity.kt").writeText(
            composeBlankActivity(project)
        )
        writeComposeTheme(dir, project)
        writeBaseStrings(dir, project)
    }

    private fun generateComposeDashboard(dir: File, project: Project) {
        generateManifest(dir, project, ".MainActivity")
        val pkgPath = project.packageName.replace('.', '/')
        val srcDir = File(dir, "app/src/main/java/$pkgPath").apply { mkdirs() }
        File(srcDir, "MainActivity.kt").writeText(
            composeMainActivity(project, "A dashboard shell with KPI cards, action lanes, and status surfaces ready for real data wiring.")
        )
        writeComposeTheme(dir, project)
        writeBaseStrings(dir, project)
    }

    private fun generateComposeSettings(dir: File, project: Project) {
        generateManifest(dir, project, ".MainActivity")
        val pkgPath = project.packageName.replace('.', '/')
        val srcDir = File(dir, "app/src/main/java/$pkgPath").apply { mkdirs() }
        File(srcDir, "MainActivity.kt").writeText(
            composeSettingsActivity(project)
        )
        writeComposeTheme(dir, project)
        writeBaseStrings(dir, project)
    }

    private fun generateViewsStarter(dir: File, project: Project) {
        generateManifest(dir, project, ".MainActivity")
        writeBaseStrings(dir, project)
        writeViewResources(dir)
        val pkgPath = project.packageName.replace('.', '/')
        val srcDir = File(dir, "app/src/main/java/$pkgPath").apply { mkdirs() }
        File(srcDir, "MainActivity.java").writeText(
            javaActivity(project, afterSetContent = """
        TextView title = findViewById(R.id.screenTitle);
        TextView subtitle = findViewById(R.id.screenSubtitle);
        TextView status = findViewById(R.id.statusValue);
        Button primary = findViewById(R.id.primaryActionButton);

        title.setText("${project.name}");
        subtitle.setText("Blank Java starter using only build-safe Android Views.");
        status.setText("Ready");

        primary.setText("Update status");
        primary.setOnClickListener(v -> status.setText("Updated at " + System.currentTimeMillis()));
            """.trimIndent())
        )
        File(dir, "app/src/main/res/layout/activity_main.xml").apply {
            parentFile?.mkdirs()
            writeText(blankViewsLayout(project.name))
        }
    }

    private fun generateJavaDashboard(dir: File, project: Project) {
        generateViewsStarter(dir, project)
        val pkgPath = project.packageName.replace('.', '/')
        File(dir, "app/src/main/java/$pkgPath/MainActivity.java").writeText(
            javaActivity(project, afterSetContent = """
        TextView title = findViewById(R.id.screenTitle);
        TextView subtitle = findViewById(R.id.screenSubtitle);
        TextView status = findViewById(R.id.statusValue);
        Button primary = findViewById(R.id.primaryActionButton);
        Button secondary = findViewById(R.id.secondaryActionButton);

        title.setText("${project.name} dashboard");
        subtitle.setText("A production-oriented Java starter with a clear hero section, action lane, and live status block.");
        status.setText("All systems ready");

        primary.setText("Run primary flow");
        secondary.setText("Open secondary lane");
        primary.setOnClickListener(v -> status.setText("Primary flow started at " + System.currentTimeMillis()));
        secondary.setOnClickListener(v -> status.setText("Secondary lane opened"));
            """.trimIndent())
        )
    }

    private fun generateJavaUtility(dir: File, project: Project) {
        generateManifest(dir, project, ".MainActivity")
        writeBaseStrings(dir, project)
        writeViewResources(dir)
        val pkgPath = project.packageName.replace('.', '/')
        val srcDir = File(dir, "app/src/main/java/$pkgPath").apply { mkdirs() }
        File(srcDir, "MainActivity.java").writeText(
            javaActivity(project, layoutName = "activity_main", afterSetContent = """
        EditText input = findViewById(R.id.inputField);
        TextView result = findViewById(R.id.resultValue);
        Button process = findViewById(R.id.processButton);

        process.setOnClickListener(v -> {
            String raw = input.getText().toString().trim();
            if (raw.isEmpty()) {
                result.setText("Enter text to process.");
                return;
            }
            String processed = "Length: " + raw.length() + "\nUppercase: " + raw.toUpperCase() + "\nSlug: " + raw.toLowerCase().replace(' ', '-');
            result.setText(processed);
        });
            """.trimIndent())
        )
        File(dir, "app/src/main/res/layout/activity_main.xml").apply {
            parentFile?.mkdirs()
            writeText(javaUtilityLayout(project.name))
        }
    }

    private fun generateJavaForm(dir: File, project: Project) {
        generateManifest(dir, project, ".MainActivity")
        writeBaseStrings(dir, project)
        writeViewResources(dir)
        val pkgPath = project.packageName.replace('.', '/')
        val srcDir = File(dir, "app/src/main/java/$pkgPath").apply { mkdirs() }
        File(srcDir, "MainActivity.java").writeText(
            javaActivity(project, layoutName = "activity_main", afterSetContent = """
        EditText fullName = findViewById(R.id.fullNameField);
        EditText email = findViewById(R.id.emailField);
        EditText company = findViewById(R.id.companyField);
        TextView summary = findViewById(R.id.summaryValue);
        Button submit = findViewById(R.id.submitButton);

        submit.setOnClickListener(v -> {
            String name = fullName.getText().toString().trim();
            String emailValue = email.getText().toString().trim();
            String companyValue = company.getText().toString().trim();
            if (name.isEmpty() || emailValue.isEmpty()) {
                summary.setText("Name and email are required before submission.");
                return;
            }
            summary.setText("Saved contact\nName: " + name + "\nEmail: " + emailValue + "\nCompany: " + companyValue);
        });
            """.trimIndent())
        )
        File(dir, "app/src/main/res/layout/activity_main.xml").apply {
            parentFile?.mkdirs()
            writeText(javaFormLayout(project.name))
        }
    }

    private fun generateJavaListDetail(dir: File, project: Project) {
        generateManifest(dir, project, ".MainActivity")
        writeBaseStrings(dir, project)
        writeViewResources(dir)
        val pkgPath = project.packageName.replace('.', '/')
        val srcDir = File(dir, "app/src/main/java/$pkgPath").apply { mkdirs() }
        File(srcDir, "MainActivity.java").writeText(
            javaActivity(project, layoutName = "activity_main", extraImports = listOf("android.widget.ArrayAdapter", "android.widget.ListView"), afterSetContent = """
        ListView listView = findViewById(R.id.itemList);
        TextView detailTitle = findViewById(R.id.detailTitle);
        TextView detailBody = findViewById(R.id.detailBody);

        String[] items = new String[] {"Planning", "Implementation", "Validation", "Release"};
        listView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_activated_1, items));
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            String item = items[position];
            detailTitle.setText(item);
            detailBody.setText("Selected lane: " + item + "\n\nReplace this sample detail text with your real domain data and navigation flow.");
        });
        listView.performItemClick(listView.getChildAt(0), 0, listView.getAdapter().getItemId(0));
            """.trimIndent())
        )
        File(dir, "app/src/main/res/layout/activity_main.xml").apply {
            parentFile?.mkdirs()
            writeText(javaListDetailLayout(project.name))
        }
    }

    private fun generateGradleFiles(dir: File, project: Project) {
        File(dir, "settings.gradle.kts").writeText(
            """
pluginManagement {
    repositories {
        maven { url = uri(rootDir.resolve(".buildbuddy/m2repo")) }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri(rootDir.resolve(".buildbuddy/m2repo")) }
        google()
        mavenCentral()
    }
}
rootProject.name = "${project.name.replace("\"", "\\\"")}"
include(":app")
            """.trimIndent()
        )

        val composeProject = project.uiFramework == UiFramework.COMPOSE
        File(dir, "build.gradle.kts").writeText(
            """
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
${if (composeProject) "    id(\"org.jetbrains.kotlin.plugin.compose\") version \"2.1.0\" apply false" else ""}
}
            """.trimIndent()
        )

        File(dir, "gradle.properties").writeText(
            """
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
            """.trimIndent()
        )

        File(dir, "app/build.gradle.kts").apply {
            parentFile?.mkdirs()
            writeText(buildString {
                appendLine("plugins {")
                appendLine("    id(\"com.android.application\")")
                if (project.language == ProjectLanguage.KOTLIN) appendLine("    id(\"org.jetbrains.kotlin.android\")")
                if (composeProject) appendLine("    id(\"org.jetbrains.kotlin.plugin.compose\")")
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
                if (project.language == ProjectLanguage.KOTLIN) {
                    appendLine("    kotlinOptions {")
                    appendLine("        jvmTarget = \"17\"")
                    appendLine("    }")
                }
                if (composeProject) {
                    appendLine("    buildFeatures {")
                    appendLine("        compose = true")
                    appendLine("    }")
                }
                appendLine("}")
                appendLine()
                appendLine("dependencies {")
                appendLine("    implementation(\"androidx.core:core-ktx:1.15.0\")")
                if (composeProject) {
                    appendLine("    implementation(platform(\"androidx.compose:compose-bom:2024.12.01\"))")
                    appendLine("    implementation(\"androidx.activity:activity-compose:1.9.3\")")
                    appendLine("    implementation(\"androidx.compose.ui:ui\")")
                    appendLine("    implementation(\"androidx.compose.ui:ui-tooling-preview\")")
                    appendLine("    implementation(\"androidx.compose.material3:material3\")")
                    appendLine("    debugImplementation(\"androidx.compose.ui:ui-tooling\")")
                }
                appendLine("}")
            })
        }
    }

    private fun writeBuildBuddyProperties(dir: File, project: Project) {
        val properties = Properties().apply {
            setProperty("template", project.template.name)
            setProperty("preferredBuildEngine", project.template.preferredBuildEngine.name.lowercase())
        }
        File(dir, "buildbuddy.properties").outputStream().use { properties.store(it, "BuildBuddy project metadata") }
    }

    private fun generateManifest(dir: File, project: Project, activityName: String) {
        File(dir, "app/src/main/AndroidManifest.xml").apply {
            parentFile?.mkdirs()
            writeText(
                """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="${project.packageName}">

    <application
        android:allowBackup="false"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:usesCleartextTraffic="false"
        android:theme="@style/Theme.App">

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
                """.trimIndent()
            )
        }
    }

    private fun writeBaseStrings(dir: File, project: Project) {
        File(dir, "app/src/main/res/values/strings.xml").apply {
            parentFile?.mkdirs()
            writeText(
                """
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">${project.name}</string>
</resources>
                """.trimIndent()
            )
        }
    }

    private fun writeViewResources(dir: File) {
        val valuesDir = File(dir, "app/src/main/res/values").apply { mkdirs() }
        File(valuesDir, "colors.xml").writeText(
            """
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="bb_screen">#FFF5F7FB</color>
    <color name="bb_surface">#FFFFFFFF</color>
    <color name="bb_surface_alt">#FFE9EEF8</color>
    <color name="bb_primary">#FF1E63FF</color>
    <color name="bb_primary_dark">#FF0F3D99</color>
    <color name="bb_text_primary">#FF111827</color>
    <color name="bb_text_secondary">#FF5F6B7A</color>
    <color name="bb_outline">#FFD7DEEA</color>
    <color name="bb_success">#FF0B8B5B</color>
</resources>
            """.trimIndent()
        )
        File(valuesDir, "themes.xml").writeText(
            """
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.App" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:windowBackground">@color/bb_screen</item>
        <item name="android:statusBarColor">@color/bb_primary_dark</item>
        <item name="android:navigationBarColor">@color/bb_screen</item>
    </style>
</resources>
            """.trimIndent()
        )
        val drawableDir = File(dir, "app/src/main/res/drawable").apply { mkdirs() }
        File(drawableDir, "bg_card.xml").writeText(shapeDrawable("@color/bb_surface", "@color/bb_outline", 18))
        File(drawableDir, "bg_card_alt.xml").writeText(shapeDrawable("@color/bb_surface_alt", "@color/bb_outline", 18))
        File(drawableDir, "bg_primary_button.xml").writeText(shapeDrawable("@color/bb_primary", null, 14))
        File(drawableDir, "bg_secondary_button.xml").writeText(shapeDrawable("@color/bb_surface", "@color/bb_outline", 14))
    }

    private fun writeComposeTheme(dir: File, project: Project) {
        val pkgPath = project.packageName.replace('.', '/')
        val themeDir = File(dir, "app/src/main/java/$pkgPath/ui/theme").apply { mkdirs() }
        File(themeDir, "Theme.kt").writeText(
            """
package ${project.packageName}.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E63FF),
    secondary = Color(0xFF5A6B89),
    tertiary = Color(0xFF006B5B),
    background = Color(0xFFF5F7FB),
    surface = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9AB7FF),
    secondary = Color(0xFFB9C5DA),
    tertiary = Color(0xFF7CD8BE),
    background = Color(0xFF0F172A),
    surface = Color(0xFF111827)
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
            """.trimIndent()
        )
    }

    private fun composeBlankActivity(project: Project): String =
        """
package ${project.packageName}

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ${project.packageName}.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
private fun MainScreen() {
    val status = remember { mutableStateOf("Ready") }
    Scaffold(topBar = { TopAppBar(title = { Text("${project.name}") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Blank Kotlin starter", style = MaterialTheme.typography.headlineSmall)
            Text("Start from a clean screen and replace this placeholder text with your real product UI.", style = MaterialTheme.typography.bodyLarge)
            Text("Status: ${'$'}{status.value}", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { status.value = "Updated" }, modifier = Modifier.fillMaxWidth()) {
                Text("Update status")
            }
        }
    }
}
        """.trimIndent()

    private fun composeMainActivity(project: Project, heroText: String): String =
        """
package ${project.packageName}

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ${project.packageName}.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
private fun MainScreen() {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("${project.name}") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Production Android starter", style = MaterialTheme.typography.labelLarge)
                    Text("$heroText", style = MaterialTheme.typography.bodyLarge)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickCard(title = "Project", value = "Ready", modifier = Modifier.weight(1f))
                QuickCard(title = "Build", value = "Gradle", modifier = Modifier.weight(1f))
            }
            QuickCard(
                title = "Next step",
                value = "Replace sample cards with real domain features and wire your data layer.",
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Primary action")
            }
        }
    }
}

@Composable
private fun QuickCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}
        """.trimIndent()

    private fun composeSettingsActivity(project: Project): String =
        """
package ${project.packageName}

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ${project.packageName}.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { SettingsScreen() } }
    }
}

@Composable
private fun SettingsScreen() {
    val notifications = remember { mutableStateOf(true) }
    val syncEnabled = remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("${project.name}") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingCard(title = "Notifications", subtitle = "Delivery status and build events") {
                Switch(checked = notifications.value, onCheckedChange = { notifications.value = it })
            }
            SettingCard(title = "Background sync", subtitle = "Keep project metadata aligned") {
                Switch(checked = syncEnabled.value, onCheckedChange = { syncEnabled.value = it })
            }
            SettingCard(title = "Workspace", subtitle = "Replace these sample groups with real settings modules") {}
        }
    }
}

@Composable
private fun SettingCard(title: String, subtitle: String, trailing: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(18.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing()
        }
    }
}
        """.trimIndent()

    private fun javaActivity(
        project: Project,
        layoutName: String = "activity_main",
        extraImports: List<String> = emptyList(),
        afterSetContent: String
    ): String =
        """
package ${project.packageName};

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
${extraImports.joinToString("\n") { "import $it;" }}

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.$layoutName);
$afterSetContent
    }
}
        """.trimIndent()

    private fun blankViewsLayout(appName: String): String =
        """
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bb_screen"
    android:fillViewport="true">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="20dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="@drawable/bg_card"
            android:orientation="vertical"
            android:padding="20dp">

            <TextView
                android:id="@+id/screenTitle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="$appName"
                android:textColor="@color/bb_text_primary"
                android:textSize="24sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/screenSubtitle"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="10dp"
                android:textColor="@color/bb_text_secondary"
                android:textSize="15sp" />

            <TextView
                android:id="@+id/statusValue"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="14dp"
                android:textColor="@color/bb_text_primary"
                android:textSize="16sp" />
        </LinearLayout>

        <Button
            android:id="@+id/primaryActionButton"
            android:layout_width="match_parent"
            android:layout_height="52dp"
            android:layout_marginTop="16dp"
            android:background="@drawable/bg_primary_button"
            android:text="Primary"
            android:textColor="@android:color/white" />
    </LinearLayout>
</ScrollView>
        """.trimIndent()

    private fun baseDashboardLayout(appName: String): String =
        """
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bb_screen"
    android:fillViewport="true">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="20dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:background="@drawable/bg_card"
            android:padding="20dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="$appName"
                android:textColor="@color/bb_text_secondary"
                android:textSize="13sp" />

            <TextView
                android:id="@+id/screenTitle"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="10dp"
                android:textColor="@color/bb_text_primary"
                android:textSize="26sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/screenSubtitle"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="10dp"
                android:textColor="@color/bb_text_secondary"
                android:textSize="15sp" />
        </LinearLayout>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:orientation="horizontal"
            android:weightSum="2">

            <Button
                android:id="@+id/primaryActionButton"
                android:layout_width="0dp"
                android:layout_height="52dp"
                android:layout_weight="1"
                android:background="@drawable/bg_primary_button"
                android:text="Primary"
                android:textColor="@android:color/white" />

            <Button
                android:id="@+id/secondaryActionButton"
                android:layout_width="0dp"
                android:layout_height="52dp"
                android:layout_marginStart="12dp"
                android:layout_weight="1"
                android:background="@drawable/bg_secondary_button"
                android:text="Secondary"
                android:textColor="@color/bb_text_primary" />
        </LinearLayout>

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:background="@drawable/bg_card_alt"
            android:orientation="vertical"
            android:padding="18dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Status"
                android:textColor="@color/bb_text_secondary"
                android:textSize="13sp" />

            <TextView
                android:id="@+id/statusValue"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:textColor="@color/bb_text_primary"
                android:textSize="18sp"
                android:textStyle="bold" />
        </LinearLayout>
    </LinearLayout>
</ScrollView>
        """.trimIndent()

    private fun javaUtilityLayout(appName: String): String =
        """
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bb_screen"
    android:fillViewport="true">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="20dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="$appName utility"
            android:textColor="@color/bb_text_primary"
            android:textSize="24sp"
            android:textStyle="bold" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="Process input and present a clean result block."
            android:textColor="@color/bb_text_secondary" />

        <EditText
            android:id="@+id/inputField"
            android:layout_width="match_parent"
            android:layout_height="140dp"
            android:layout_marginTop="16dp"
            android:background="@drawable/bg_card"
            android:gravity="top|start"
            android:hint="Paste or type text"
            android:padding="16dp" />

        <Button
            android:id="@+id/processButton"
            android:layout_width="match_parent"
            android:layout_height="52dp"
            android:layout_marginTop="14dp"
            android:background="@drawable/bg_primary_button"
            android:text="Process"
            android:textColor="@android:color/white" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:background="@drawable/bg_card"
            android:orientation="vertical"
            android:padding="18dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Result"
                android:textColor="@color/bb_text_secondary" />

            <TextView
                android:id="@+id/resultValue"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="10dp"
                android:text="No output yet"
                android:textColor="@color/bb_text_primary" />
        </LinearLayout>
    </LinearLayout>
</ScrollView>
        """.trimIndent()

    private fun javaFormLayout(appName: String): String =
        """
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bb_screen"
    android:fillViewport="true">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="20dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="$appName intake"
            android:textColor="@color/bb_text_primary"
            android:textSize="24sp"
            android:textStyle="bold" />

        <EditText
            android:id="@+id/fullNameField"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:background="@drawable/bg_card"
            android:hint="Full name"
            android:padding="16dp" />

        <EditText
            android:id="@+id/emailField"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:background="@drawable/bg_card"
            android:hint="Email"
            android:inputType="textEmailAddress"
            android:padding="16dp" />

        <EditText
            android:id="@+id/companyField"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="12dp"
            android:background="@drawable/bg_card"
            android:hint="Company"
            android:padding="16dp" />

        <Button
            android:id="@+id/submitButton"
            android:layout_width="match_parent"
            android:layout_height="52dp"
            android:layout_marginTop="16dp"
            android:background="@drawable/bg_primary_button"
            android:text="Submit"
            android:textColor="@android:color/white" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:background="@drawable/bg_card_alt"
            android:orientation="vertical"
            android:padding="18dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Submission summary"
                android:textColor="@color/bb_text_secondary" />

            <TextView
                android:id="@+id/summaryValue"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="10dp"
                android:text="Nothing submitted yet"
                android:textColor="@color/bb_text_primary" />
        </LinearLayout>
    </LinearLayout>
</ScrollView>
        """.trimIndent()

    private fun javaListDetailLayout(appName: String): String =
        """
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/bb_screen"
    android:orientation="horizontal"
    android:padding="16dp"
    android:weightSum="10">

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="4"
        android:background="@drawable/bg_card"
        android:orientation="vertical"
        android:padding="12dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="$appName lanes"
            android:textColor="@color/bb_text_primary"
            android:textSize="18sp"
            android:textStyle="bold" />

        <ListView
            android:id="@+id/itemList"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_marginTop="10dp"
            android:layout_weight="1"
            android:divider="@android:color/transparent"
            android:dividerHeight="8dp" />
    </LinearLayout>

    <ScrollView
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_marginStart="12dp"
        android:layout_weight="6"
        android:fillViewport="true">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:background="@drawable/bg_card_alt"
            android:orientation="vertical"
            android:padding="18dp">

            <TextView
                android:id="@+id/detailTitle"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Select an item"
                android:textColor="@color/bb_text_primary"
                android:textSize="22sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/detailBody"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:text="Choose a lane from the list to inspect details."
                android:textColor="@color/bb_text_secondary" />
        </LinearLayout>
    </ScrollView>
</LinearLayout>
        """.trimIndent()

    private fun shapeDrawable(fillColor: String, strokeColor: String?, radiusDp: Int): String =
        buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            appendLine("<shape xmlns:android=\"http://schemas.android.com/apk/res/android\" android:shape=\"rectangle\">")
            appendLine("    <corners android:radius=\"${radiusDp}dp\" />")
            appendLine("    <solid android:color=\"$fillColor\" />")
            if (strokeColor != null) appendLine("    <stroke android:width=\"1dp\" android:color=\"$strokeColor\" />")
            appendLine("</shape>")
        }.trimIndent()

    private fun copyAssetFile(assetPath: String, destFile: File) {
        destFile.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
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

    fun copyBuildToolsStubs(dir: File) {
        val buildToolsDir = File(dir, "build_tools").apply { mkdirs() }
        copyAssetFile("build_tools/javax-lang-model-stubs.jar", File(buildToolsDir, "javax-lang-model-stubs.jar"))
    }
}
