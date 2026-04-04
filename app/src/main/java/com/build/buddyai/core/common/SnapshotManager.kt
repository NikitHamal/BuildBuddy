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

        projectDir.deleteRecursively()
        projectDir.mkdirs()
        FileUtils.unzipToDirectory(snapshotFile, projectDir)
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
