package com.build.buddyai.domain.usecase

import android.content.Context
import com.build.buddyai.core.model.Project
import com.build.buddyai.core.model.ProjectLanguage
import com.build.buddyai.core.model.ProjectTemplate
import com.build.buddyai.core.model.UiFramework
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
        generateGradleFiles(projectDir, project)

        when (project.template) {
            ProjectTemplate.BLANK_COMPOSE,
            ProjectTemplate.SINGLE_ACTIVITY_COMPOSE -> generateComposeProject(projectDir, project)

            ProjectTemplate.BLANK_JAVA_VIEWS,
            ProjectTemplate.BLANK_VIEWS,
            ProjectTemplate.JAVA_ACTIVITY,
            ProjectTemplate.BASIC_UTILITY -> generateBlankViewsProject(projectDir, project, ProjectLanguage.JAVA)

            ProjectTemplate.BLANK_KOTLIN_VIEWS -> generateBlankViewsProject(projectDir, project, ProjectLanguage.KOTLIN)
            ProjectTemplate.JAVA_DASHBOARD -> generateJavaDashboardProject(projectDir, project)
            ProjectTemplate.JAVA_FORM -> generateJavaFormProject(projectDir, project)
            ProjectTemplate.JAVA_MASTER_DETAIL -> generateJavaMasterDetailProject(projectDir, project)
        }
    }

    private fun generateComposeProject(dir: File, project: Project) {
        val pkgPath = project.packageName.replace('.', '/')
        generateManifest(dir, project, ".MainActivity")

        val srcDir = File(dir, "app/src/main/java/$pkgPath").apply { mkdirs() }
        File(srcDir, "MainActivity.kt").writeText(
            """
            package ${project.packageName}

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.activity.enableEdgeToEdge
            import androidx.compose.foundation.layout.Arrangement
            import androidx.compose.foundation.layout.Column
            import androidx.compose.foundation.layout.Spacer
            import androidx.compose.foundation.layout.fillMaxSize
            import androidx.compose.foundation.layout.height
            import androidx.compose.foundation.layout.padding
            import androidx.compose.material3.CenterAlignedTopAppBar
            import androidx.compose.material3.MaterialTheme
            import androidx.compose.material3.Scaffold
            import androidx.compose.material3.Surface
            import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable
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
                            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                                MainScreen()
                            }
                        }
                    }
                }
            }

            @Composable
            private fun MainScreen() {
                Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("${escape(project.name)}") }) }) { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "${escape(project.name)}", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Everything is ready for your product direction.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            """.trimIndent()
        )

        val themeDir = File(dir, "app/src/main/java/$pkgPath/ui/theme").apply { mkdirs() }
        File(themeDir, "Theme.kt").writeText(
            """
            package ${project.packageName}.ui.theme

            import androidx.compose.foundation.isSystemInDarkTheme
            import androidx.compose.material3.MaterialTheme
            import androidx.compose.material3.darkColorScheme
            import androidx.compose.material3.lightColorScheme
            import androidx.compose.runtime.Composable

            private val LightColors = lightColorScheme()
            private val DarkColors = darkColorScheme()

            @Composable
            fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
                MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
            }
            """.trimIndent()
        )

        writeCommonValues(dir, project)
    }

    private fun generateBlankViewsProject(dir: File, project: Project, language: ProjectLanguage) {
        generateManifest(dir, project, ".MainActivity")
        writeCommonValues(dir, project)
        val layoutDir = File(dir, "app/src/main/res/layout").apply { mkdirs() }
        File(layoutDir, "activity_main.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:fillViewport="true"
                android:background="#F4F1EC">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="24dp">

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="vertical"
                        android:background="@drawable/bg_surface_card"
                        android:padding="20dp">

                        <TextView
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:text="@string/app_name"
                            android:textStyle="bold"
                            android:textSize="28sp"
                            android:textColor="#17130F" />

                        <TextView
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:layout_marginTop="8dp"
                            android:text="A polished starting point for your app experience."
                            android:textColor="#6C665F"
                            android:textSize="15sp" />

                        <Button
                            android:id="@+id/primaryActionButton"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:layout_marginTop="20dp"
                            android:background="@drawable/bg_primary_button"
                            android:text="Get started"
                            android:textAllCaps="false"
                            android:textColor="@android:color/white" />
                    </LinearLayout>

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="16dp"
                        android:orientation="vertical"
                        android:background="@drawable/bg_surface_card"
                        android:padding="16dp">

                        <TextView
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:text="Status"
                            android:textStyle="bold"
                            android:textColor="#17130F" />

                        <TextView
                            android:id="@+id/statusText"
                            android:layout_width="match_parent"
                            android:layout_height="wrap_content"
                            android:layout_marginTop="6dp"
                            android:text="Ready to customize"
                            android:textColor="#6C665F"
                            android:textSize="16sp" />
                    </LinearLayout>
                </LinearLayout>
            </ScrollView>
            """.trimIndent()
        )
        writeActivitySource(dir, project, language, blankActivityBody(project, language))
    }

    private fun generateJavaDashboardProject(dir: File, project: Project) {
        generateManifest(dir, project, ".MainActivity")
        writeCommonValues(dir, project)
        val layoutDir = File(dir, "app/src/main/res/layout").apply { mkdirs() }
        File(layoutDir, "activity_main.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:fillViewport="true">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="20dp">

                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="@string/app_name"
                        android:textSize="28sp"
                        android:textStyle="bold" />

                    <TextView
                        android:id="@+id/summaryText"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="6dp"
                        android:text="Live project summary"
                        android:textColor="@android:color/darker_gray" />

                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="20dp"
                        android:orientation="horizontal"
                        android:weightSum="2">

                        <TextView
                            android:id="@+id/kpiOne"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:background="@android:color/white"
                            android:padding="18dp"
                            android:text="Builds&#10;24"
                            android:textStyle="bold" />

                        <Space
                            android:layout_width="12dp"
                            android:layout_height="1dp" />

                        <TextView
                            android:id="@+id/kpiTwo"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:background="@android:color/white"
                            android:padding="18dp"
                            android:text="Success&#10;99%"
                            android:textStyle="bold" />
                    </LinearLayout>

                    <Button
                        android:id="@+id/refreshButton"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="20dp"
                        android:text="Refresh metrics" />
                </LinearLayout>
            </ScrollView>
            """.trimIndent()
        )
        writeActivitySource(dir, project, ProjectLanguage.JAVA, javaDashboardBody(project))
    }

    private fun generateJavaFormProject(dir: File, project: Project) {
        generateManifest(dir, project, ".MainActivity")
        writeCommonValues(dir, project)
        val layoutDir = File(dir, "app/src/main/res/layout").apply { mkdirs() }
        File(layoutDir, "activity_main.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="20dp">

                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="Create account"
                        android:textSize="26sp"
                        android:textStyle="bold" />

                    <EditText
                        android:id="@+id/nameInput"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="16dp"
                        android:hint="Full name" />

                    <EditText
                        android:id="@+id/emailInput"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="12dp"
                        android:hint="Email" />

                    <Button
                        android:id="@+id/submitButton"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="20dp"
                        android:text="Submit" />

                    <TextView
                        android:id="@+id/resultText"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="16dp"
                        android:text="Waiting for input" />
                </LinearLayout>
            </ScrollView>
            """.trimIndent()
        )
        writeActivitySource(dir, project, ProjectLanguage.JAVA, javaFormBody(project))
    }

    private fun generateJavaMasterDetailProject(dir: File, project: Project) {
        generateManifest(dir, project, ".MainActivity")
        writeCommonValues(dir, project)
        val layoutDir = File(dir, "app/src/main/res/layout").apply { mkdirs() }
        File(layoutDir, "activity_main.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="horizontal"
                android:padding="12dp">

                <ListView
                    android:id="@+id/itemsList"
                    android:layout_width="0dp"
                    android:layout_height="match_parent"
                    android:layout_weight="1"
                    android:dividerHeight="8dp" />

                <Space
                    android:layout_width="12dp"
                    android:layout_height="1dp" />

                <TextView
                    android:id="@+id/detailText"
                    android:layout_width="0dp"
                    android:layout_height="match_parent"
                    android:layout_weight="1"
                    android:gravity="center"
                    android:padding="20dp"
                    android:text="Select an item"
                    android:textSize="18sp" />
            </LinearLayout>
            """.trimIndent()
        )
        writeActivitySource(dir, project, ProjectLanguage.JAVA, javaMasterDetailBody(project))
    }

    private fun writeActivitySource(dir: File, project: Project, language: ProjectLanguage, body: String) {
        val pkgPath = project.packageName.replace('.', '/')
        val srcDir = File(dir, "app/src/main/java/$pkgPath").apply { mkdirs() }
        File(srcDir, if (language == ProjectLanguage.JAVA) "MainActivity.java" else "MainActivity.kt").writeText(body)
    }

    private fun blankActivityBody(project: Project, language: ProjectLanguage): String = if (language == ProjectLanguage.JAVA) {
        """
        package ${project.packageName};

        import android.app.Activity;
        import android.os.Bundle;
        import android.widget.Button;
        import android.widget.TextView;
        import android.widget.Toast;

        public class MainActivity extends Activity {
            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                setContentView(R.layout.activity_main);

                Button action = findViewById(R.id.primaryActionButton);
                TextView status = findViewById(R.id.statusText);
                action.setOnClickListener(view -> {
                    status.setText("Your first interaction is wired up.");
                    Toast.makeText(this, "${escape(project.name)} is ready", Toast.LENGTH_SHORT).show();
                });
            }
        }
        """.trimIndent()
    } else {
        """
        package ${project.packageName}

        import android.app.Activity
        import android.os.Bundle
        import android.widget.Button
        import android.widget.TextView
        import android.widget.Toast

        class MainActivity : Activity() {
            override fun onCreate(savedInstanceState: Bundle?) {
                super.onCreate(savedInstanceState)
                setContentView(R.layout.activity_main)

                val action = findViewById<Button>(R.id.primaryActionButton)
                val status = findViewById<TextView>(R.id.statusText)
                action.setOnClickListener {
                    status.text = "Your first interaction is wired up."
                    Toast.makeText(this, "${escape(project.name)} is ready", Toast.LENGTH_SHORT).show()
                }
            }
        }
        """.trimIndent()
    }

    private fun javaDashboardBody(project: Project) =
        """
        package ${project.packageName};

        import android.app.Activity;
        import android.os.Bundle;
        import android.widget.Button;
        import android.widget.TextView;

        public class MainActivity extends Activity {
            private int refreshCount = 0;

            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                setContentView(R.layout.activity_main);

                TextView summary = findViewById(R.id.summaryText);
                TextView kpiOne = findViewById(R.id.kpiOne);
                TextView kpiTwo = findViewById(R.id.kpiTwo);
                Button refreshButton = findViewById(R.id.refreshButton);

                refreshButton.setOnClickListener(view -> {
                    refreshCount++;
                    summary.setText("Refreshed " + refreshCount + " time(s)");
                    kpiOne.setText("Builds\n" + (24 + refreshCount));
                    kpiTwo.setText("Success\n" + (99 - Math.min(refreshCount, 4)) + "%");
                });
            }
        }
        """.trimIndent()

    private fun javaFormBody(project: Project) =
        """
        package ${project.packageName};

        import android.app.Activity;
        import android.os.Bundle;
        import android.text.TextUtils;
        import android.widget.Button;
        import android.widget.EditText;
        import android.widget.TextView;

        public class MainActivity extends Activity {
            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                setContentView(R.layout.activity_main);

                EditText nameInput = findViewById(R.id.nameInput);
                EditText emailInput = findViewById(R.id.emailInput);
                TextView resultText = findViewById(R.id.resultText);
                Button submit = findViewById(R.id.submitButton);

                submit.setOnClickListener(view -> {
                    String name = nameInput.getText().toString().trim();
                    String email = emailInput.getText().toString().trim();
                    if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email)) {
                        resultText.setText("Please complete every field.");
                    } else {
                        resultText.setText("Saved profile for " + name + " (" + email + ")");
                    }
                });
            }
        }
        """.trimIndent()

    private fun javaMasterDetailBody(project: Project) =
        """
        package ${project.packageName};

        import android.app.Activity;
        import android.os.Bundle;
        import android.widget.ArrayAdapter;
        import android.widget.ListView;
        import android.widget.TextView;

        public class MainActivity extends Activity {
            @Override
            protected void onCreate(Bundle savedInstanceState) {
                super.onCreate(savedInstanceState);
                setContentView(R.layout.activity_main);

                ListView itemsList = findViewById(R.id.itemsList);
                TextView detailText = findViewById(R.id.detailText);
                String[] items = new String[] {"Overview", "Tasks", "Release", "Settings"};
                itemsList.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items));
                itemsList.setOnItemClickListener((parent, view, position, id) -> detailText.setText(items[position] + " details for ${escape(project.name)}"));
            }
        }
        """.trimIndent()

    private fun generateGradleFiles(dir: File, project: Project) {
        File(dir, "settings.gradle.kts").writeText(
            """
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
            rootProject.name = "${escape(project.name)}"
            include(":app")
            """.trimIndent()
        )

        val isCompose = project.uiFramework == UiFramework.COMPOSE
        File(dir, "build.gradle.kts").writeText(
            buildString {
                appendLine("plugins {")
                appendLine("    id(\"com.android.application\") version \"8.7.3\" apply false")
                appendLine("    id(\"org.jetbrains.kotlin.android\") version \"2.1.0\" apply false")
                if (isCompose) appendLine("    id(\"org.jetbrains.kotlin.plugin.compose\") version \"2.1.0\" apply false")
                appendLine("}")
            }
        )

        File(dir, "gradle.properties").writeText(
            """
            org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
            android.useAndroidX=true
            kotlin.code.style=official
            android.nonTransitiveRClass=true
            """.trimIndent()
        )

        val appBuildGradle = buildString {
            appendLine("plugins {")
            appendLine("    id(\"com.android.application\")")
            if (project.language == ProjectLanguage.KOTLIN) appendLine("    id(\"org.jetbrains.kotlin.android\")")
            if (isCompose) appendLine("    id(\"org.jetbrains.kotlin.plugin.compose\")")
            appendLine("}")
            appendLine()
            appendLine("android {")
            appendLine("    namespace = \"${project.packageName}\"")
            appendLine("    compileSdk = ${project.targetSdk}")
            appendLine("    defaultConfig {")
            appendLine("        applicationId = \"${project.packageName}\"")
            appendLine("        minSdk = ${project.minSdk}")
            appendLine("        targetSdk = ${project.targetSdk}")
            appendLine("        versionCode = 1")
            appendLine("        versionName = \"1.0.0\"")
            appendLine("    }")
            appendLine("    buildTypes {")
            appendLine("        release {")
            appendLine("            isMinifyEnabled = false")
            appendLine("        }")
            appendLine("    }")
            appendLine("    compileOptions {")
            appendLine("        sourceCompatibility = JavaVersion.VERSION_17")
            appendLine("        targetCompatibility = JavaVersion.VERSION_17")
            appendLine("    }")
            if (project.language == ProjectLanguage.KOTLIN) {
                appendLine("    kotlinOptions {")
                appendLine("        jvmTarget = \"17\"")
                appendLine("    }")
            }
            if (isCompose) {
                appendLine("    buildFeatures {")
                appendLine("        compose = true")
                appendLine("    }")
            }
            appendLine("}")
            appendLine()
            appendLine("dependencies {")
            if (isCompose) {
                appendLine("    implementation(platform(\"androidx.compose:compose-bom:2024.12.01\"))")
                appendLine("    implementation(\"androidx.compose.ui:ui\")")
                appendLine("    implementation(\"androidx.compose.material3:material3\")")
                appendLine("    implementation(\"androidx.activity:activity-compose:1.9.3\")")
                appendLine("    implementation(\"androidx.compose.ui:ui-tooling-preview\")")
            }
            appendLine("}")
        }
        File(dir, "app/build.gradle.kts").apply {
            parentFile?.mkdirs()
            writeText(appBuildGradle)
        }
    }

    private fun generateManifest(dir: File, project: Project, activityName: String) {
        val manifestDir = File(dir, "app/src/main").apply { mkdirs() }
        File(manifestDir, "AndroidManifest.xml").writeText(
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

    private fun writeCommonValues(dir: File, project: Project) {
        val valuesDir = File(dir, "app/src/main/res/values").apply { mkdirs() }
        File(valuesDir, "strings.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="app_name">${escape(project.name)}</string>
            </resources>
            """.trimIndent()
        )
        File(valuesDir, "themes.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <style name="Theme.App" parent="android:Theme.Material.Light.NoActionBar">
                    <item name="android:windowBackground">#F4F1EC</item>
                </style>
            </resources>
            """.trimIndent()
        )
        val drawableDir = File(dir, "app/src/main/res/drawable").apply { mkdirs() }
        File(drawableDir, "bg_surface_card.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
                <solid android:color="#FFFDFC" />
                <corners android:radius="22dp" />
                <stroke android:width="1dp" android:color="#E3D8CB" />
            </shape>
            """.trimIndent()
        )
        File(drawableDir, "bg_primary_button.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
                <solid android:color="#17130F" />
                <corners android:radius="16dp" />
            </shape>
            """.trimIndent()
        )
    }

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun copyAssetFile(assetPath: String, destFile: File) {
        destFile.parentFile?.mkdirs()
        try {
            context.assets.open(assetPath).use { input ->
                destFile.outputStream().use { output ->
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

    fun copyBuildToolsStubs(dir: File) {
        val buildToolsDir = File(dir, "build_tools").apply { mkdirs() }
        copyAssetFile("build_tools/javax-lang-model-stubs.jar", File(buildToolsDir, "javax-lang-model-stubs.jar"))
    }
}
