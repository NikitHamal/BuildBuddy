package com.build.buddyai.core.data.workspace

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.build.buddyai.core.model.Project
import com.build.buddyai.core.model.ProjectDraft
import com.build.buddyai.core.model.ProjectLanguage
import com.build.buddyai.core.model.ProjectTemplate
import com.build.buddyai.core.model.Snapshot
import com.build.buddyai.core.model.UiToolkit
import com.build.buddyai.core.model.WorkspaceFile
import com.build.buddyai.core.model.WorkspaceManifest
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class WorkspaceManager @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val json: Json,
) {
    private val projectsRoot: File by lazy {
        File(context.filesDir, "projects").apply { mkdirs() }
    }

    private val snapshotRoot: File by lazy {
        File(context.filesDir, "snapshots").apply { mkdirs() }
    }

    private val exportsRoot: File by lazy {
        File(context.filesDir, "exports").apply { mkdirs() }
    }

    fun projectRoot(projectId: String): File = File(projectsRoot, projectId)

    suspend fun createProject(draft: ProjectDraft): Project {
        val projectId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val root = projectRoot(projectId).apply { mkdirs() }

        val project = Project(
            id = projectId,
            name = draft.name.trim(),
            packageName = draft.packageName.trim(),
            description = draft.description.trim(),
            language = draft.language,
            uiToolkit = draft.uiToolkit,
            minSdk = draft.minSdk,
            targetSdk = draft.targetSdk,
            accentColor = draft.accentColor,
            template = draft.template,
            workspacePath = root.absolutePath,
            createdAt = now,
            updatedAt = now,
            lastOpenedAt = now,
            archived = false,
        )

        scaffoldProject(project)
        return project
    }

    fun listFiles(project: Project): List<WorkspaceFile> {
        val root = File(project.workspacePath)
        if (!root.exists()) return emptyList()
        val basePath = root.absolutePath
        return root.walkTopDown()
            .filter { it.absolutePath != basePath }
            .sortedBy { it.absolutePath }
            .map {
                val relative = it.relativeTo(root).invariantSeparatorsPath
                WorkspaceFile(
                    path = relative,
                    name = it.name,
                    isDirectory = it.isDirectory,
                    depth = relative.count { char -> char == '/' },
                    modifiedAt = it.lastModified(),
                )
            }
            .toList()
    }

    fun readText(project: Project, relativePath: String): String {
        val file = File(project.workspacePath, relativePath)
        return if (file.exists() && file.isFile) file.readText() else ""
    }

    fun writeText(project: Project, relativePath: String, content: String) {
        val file = File(project.workspacePath, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    fun createFile(project: Project, relativePath: String, content: String = "") {
        writeText(project, relativePath, content)
    }

    fun createFolder(project: Project, relativePath: String) {
        File(project.workspacePath, relativePath).mkdirs()
    }

    fun rename(project: Project, from: String, to: String): Boolean {
        val source = File(project.workspacePath, from)
        val target = File(project.workspacePath, to)
        target.parentFile?.mkdirs()
        return source.renameTo(target)
    }

    fun delete(project: Project, relativePath: String): Boolean {
        val target = File(project.workspacePath, relativePath)
        return if (target.isDirectory) target.deleteRecursively() else target.delete()
    }

    fun duplicate(project: Project): Project {
        val duplicateId = UUID.randomUUID().toString()
        val copy = project.copy(
            id = duplicateId,
            name = "${project.name} Copy",
            workspacePath = projectRoot(duplicateId).absolutePath,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            lastOpenedAt = System.currentTimeMillis(),
        )
        File(project.workspacePath).copyRecursively(File(copy.workspacePath), overwrite = true)
        return copy
    }

    fun exportProjectZip(project: Project): File {
        val destination = File(exportsRoot, "${project.name}-${project.id.take(8)}.zip")
        ZipOutputStream(FileOutputStream(destination)).use { output ->
            File(project.workspacePath).walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val entryName = file.relativeTo(File(project.workspacePath)).invariantSeparatorsPath
                    output.putNextEntry(ZipEntry(entryName))
                    file.inputStream().copyTo(output)
                    output.closeEntry()
                }
        }
        return destination
    }

    fun importProjectZip(uri: Uri, contentResolver: ContentResolver = context.contentResolver): File {
        val projectId = UUID.randomUUID().toString()
        val root = projectRoot(projectId).apply { mkdirs() }
        contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    val destination = File(root, entry.name)
                    if (entry.isDirectory) {
                        destination.mkdirs()
                    } else {
                        destination.parentFile?.mkdirs()
                        destination.outputStream().use(zip::copyTo)
                    }
                    zip.closeEntry()
                }
            }
        }
        return root
    }

    fun createSnapshot(project: Project, reason: String): Snapshot {
        val snapshotId = UUID.randomUUID().toString()
        val file = File(snapshotRoot, "$snapshotId.zip")
        ZipOutputStream(FileOutputStream(file)).use { output ->
            File(project.workspacePath).walkTopDown()
                .filter { it.isFile }
                .forEach { source ->
                    val entryName = source.relativeTo(File(project.workspacePath)).invariantSeparatorsPath
                    output.putNextEntry(ZipEntry(entryName))
                    source.inputStream().copyTo(output)
                    output.closeEntry()
                }
        }
        return Snapshot(
            id = snapshotId,
            projectId = project.id,
            label = "Snapshot ${snapshotId.take(6)}",
            filePath = file.absolutePath,
            createdAt = System.currentTimeMillis(),
            reason = reason,
        )
    }

    fun restoreSnapshot(project: Project, snapshot: Snapshot) {
        val root = File(project.workspacePath).apply {
            deleteRecursively()
            mkdirs()
        }
        ZipInputStream(File(snapshot.filePath).inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val destination = File(root, entry.name)
                if (entry.isDirectory) {
                    destination.mkdirs()
                } else {
                    destination.parentFile?.mkdirs()
                    destination.outputStream().use(zip::copyTo)
                }
                zip.closeEntry()
            }
        }
    }

    fun listExportDocuments(): List<DocumentFile> {
        val exportDir = DocumentFile.fromFile(exportsRoot)
        return exportDir.listFiles().toList()
    }

    private fun scaffoldProject(project: Project) {
        val root = File(project.workspacePath)
        val mainActivityPath = when (project.language) {
            ProjectLanguage.KOTLIN -> "app/src/main/java/${project.packageName.replace('.', '/')}/MainActivity.kt"
            ProjectLanguage.JAVA -> "app/src/main/java/${project.packageName.replace('.', '/')}/MainActivity.java"
        }
        val manifestPath = "app/src/main/AndroidManifest.xml"
        val resourcePath = when (project.uiToolkit) {
            UiToolkit.COMPOSE -> null
            UiToolkit.XML -> "app/src/main/res/layout/activity_main.xml"
        }
        val manifest = WorkspaceManifest(
            projectId = project.id,
            packageName = project.packageName,
            appName = project.name,
            description = project.description,
            language = project.language,
            uiToolkit = project.uiToolkit,
            template = project.template,
            minSdk = project.minSdk,
            targetSdk = project.targetSdk,
            mainActivityPath = mainActivityPath,
            manifestPath = manifestPath,
            entryResourcePath = resourcePath,
        )

        writeProjectFile(root, "buildbuddy.json", json.encodeToString(manifest))
        writeProjectFile(root, "README.md", projectReadme(project))
        writeProjectFile(root, manifestPath, androidManifest(project))
        writeProjectFile(root, "app/src/main/res/values/strings.xml", appStrings(project))
        writeProjectFile(root, "app/src/main/res/values/colors.xml", appColors(project))
        writeProjectFile(root, "app/src/main/res/values/themes.xml", appTheme(project))

        when (project.language) {
            ProjectLanguage.KOTLIN -> writeProjectFile(root, mainActivityPath, kotlinActivity(project))
            ProjectLanguage.JAVA -> writeProjectFile(root, mainActivityPath, javaActivity(project))
        }

        if (project.uiToolkit == UiToolkit.XML) {
            writeProjectFile(root, resourcePath.orEmpty(), xmlLayout(project))
        }

        if (project.uiToolkit == UiToolkit.COMPOSE) {
            writeProjectFile(
                root,
                "app/src/main/java/${project.packageName.replace('.', '/')}/ui/theme/Theme.kt",
                composeTheme(project),
            )
        }
    }

    private fun writeProjectFile(root: File, path: String, content: String) {
        val file = File(root, path)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    private fun projectReadme(project: Project): String = """
        # ${project.name}

        Generated by BuildBuddy as a BuildBuddy-compatible Android project.

        - Package: ${project.packageName}
        - Language: ${project.language.name}
        - UI: ${project.uiToolkit.name}
        - Template: ${project.template.name}
        - Min SDK: ${project.minSdk}
        - Target SDK: ${project.targetSdk}
    """.trimIndent()

    private fun androidManifest(project: Project): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="${project.packageName}">
            <application
                android:allowBackup="true"
                android:label="@string/app_name"
                android:supportsRtl="true"
                android:theme="@style/Theme.${project.name.filter { it.isLetterOrDigit() }}">
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
    """.trimIndent()

    private fun appStrings(project: Project): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <resources>
            <string name="app_name">${xmlEscape(project.name)}</string>
            <string name="welcome_headline">${xmlEscape(project.description.ifBlank { "Built with BuildBuddy." })}</string>
        </resources>
    """.trimIndent()

    private fun appColors(project: Project): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <resources>
            <color name="seed_accent">${project.accentColor}</color>
            <color name="surface_tone">#FFF8F1</color>
            <color name="ink_tone">#14181D</color>
        </resources>
    """.trimIndent()

    private fun appTheme(project: Project): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <resources>
            <style name="Theme.${project.name.filter { it.isLetterOrDigit() }}" parent="Theme.Material3.DayNight.NoActionBar" />
        </resources>
    """.trimIndent()

    private fun kotlinActivity(project: Project): String {
        val packageLine = "package ${project.packageName}"
        return when (project.uiToolkit) {
            UiToolkit.COMPOSE -> """
                $packageLine

                import android.os.Bundle
                import androidx.activity.ComponentActivity
                import androidx.activity.compose.setContent
                import androidx.compose.foundation.layout.Arrangement
                import androidx.compose.foundation.layout.Column
                import androidx.compose.foundation.layout.fillMaxSize
                import androidx.compose.foundation.layout.padding
                import androidx.compose.material3.MaterialTheme
                import androidx.compose.material3.Surface
                import androidx.compose.material3.Text
                import androidx.compose.ui.Alignment
                import androidx.compose.ui.Modifier
                import androidx.compose.ui.unit.dp
                import ${project.packageName}.ui.theme.AppTheme

                class MainActivity : ComponentActivity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        setContent {
                            AppTheme {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background,
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(24.dp),
                                        horizontalAlignment = Alignment.Start,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(text = "${project.name}", style = MaterialTheme.typography.headlineMedium)
                                        Text(text = "${project.description.ifBlank { "Built with BuildBuddy." }}")
                                    }
                                }
                            }
                        }
                    }
                }
            """.trimIndent()

            UiToolkit.XML -> """
                $packageLine

                import android.os.Bundle
                import androidx.appcompat.app.AppCompatActivity

                class MainActivity : AppCompatActivity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        setContentView(R.layout.activity_main)
                    }
                }
            """.trimIndent()
        }
    }

    private fun javaActivity(project: Project): String = when (project.uiToolkit) {
        UiToolkit.XML -> """
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
        """.trimIndent()

        UiToolkit.COMPOSE -> """
            package ${project.packageName};

            import android.os.Bundle;
            import androidx.activity.ComponentActivity;

            public class MainActivity extends ComponentActivity {
                @Override
                protected void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                }
            }
        """.trimIndent()
    }

    private fun xmlLayout(project: Project): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:gravity="center"
            android:orientation="vertical"
            android:padding="24dp">

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="${xmlEscape(project.name)}"
                android:textSize="28sp" />

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:text="@string/welcome_headline" />
        </LinearLayout>
    """.trimIndent()

    private fun composeTheme(project: Project): String = """
        package ${project.packageName}.ui.theme

        import androidx.compose.material3.MaterialTheme
        import androidx.compose.material3.darkColorScheme
        import androidx.compose.material3.lightColorScheme
        import androidx.compose.runtime.Composable

        private val LightColors = lightColorScheme()
        private val DarkColors = darkColorScheme()

        @Composable
        fun AppTheme(content: @Composable () -> Unit) {
            MaterialTheme(
                colorScheme = LightColors,
                content = content,
            )
        }
    """.trimIndent()

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
