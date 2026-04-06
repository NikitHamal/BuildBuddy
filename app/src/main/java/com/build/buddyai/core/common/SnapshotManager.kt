package com.build.buddyai.core.common

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnapshotManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun createSnapshot(projectId: String, projectDir: File, label: String = "auto"): File {
        val snapshotDir = File(FileUtils.getSnapshotsDir(context), projectId).apply { mkdirs() }
        val timestamp = dateFormat.format(Date())
        val snapshotFile = File(snapshotDir, "${label}_${timestamp}.zip")
        FileUtils.zipDirectory(projectDir, snapshotFile)
        return snapshotFile
    }

    fun listSnapshots(projectId: String): List<SnapshotInfo> {
        val snapshotDir = File(FileUtils.getSnapshotsDir(context), projectId)
        return snapshotDir.listFiles()
            ?.filter { it.extension == "zip" }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                SnapshotInfo(
                    fileName = file.name,
                    path = file.absolutePath,
                    sizeBytes = file.length(),
                    createdAt = file.lastModified(),
                    label = file.nameWithoutExtension.substringBefore("_")
                )
            } ?: emptyList()
    }

    fun restoreSnapshot(projectId: String, snapshotPath: String, projectDir: File) {
        val snapshotRoot = File(FileUtils.getSnapshotsDir(context), projectId).canonicalFile
        val snapshotFile = File(snapshotPath).canonicalFile
        require(snapshotFile.path.startsWith(snapshotRoot.path + File.separator)) {
            "Snapshot path is outside the project snapshot directory"
        }
        require(snapshotFile.exists() && snapshotFile.isFile) { "Snapshot file not found" }
        val parentDir = projectDir.parentFile?.canonicalFile
            ?: throw IllegalStateException("Project directory has no parent: ${projectDir.absolutePath}")
        parentDir.mkdirs()

        val restoreId = System.currentTimeMillis()
        val tempDir = File(parentDir, "${projectDir.name}.restore_$restoreId").canonicalFile
        val backupDir = File(parentDir, "${projectDir.name}.backup_$restoreId").canonicalFile

        tempDir.deleteRecursively()
        backupDir.deleteRecursively()
        FileUtils.unzipToDirectory(snapshotFile, tempDir)

        val hadProject = projectDir.exists()
        if (hadProject && !projectDir.renameTo(backupDir)) {
            tempDir.deleteRecursively()
            throw IllegalStateException("Unable to create project backup before restore")
        }
        if (projectDir.exists()) projectDir.deleteRecursively()
        if (!tempDir.renameTo(projectDir)) {
            if (projectDir.exists()) projectDir.deleteRecursively()
            if (hadProject) backupDir.renameTo(projectDir)
            throw IllegalStateException("Failed to activate restored snapshot")
        }
        backupDir.deleteRecursively()
    }

    fun deleteSnapshot(snapshotPath: String) {
        File(snapshotPath).delete()
    }

    data class SnapshotInfo(
        val fileName: String,
        val path: String,
        val sizeBytes: Long,
        val createdAt: Long,
        val label: String
    )
}
