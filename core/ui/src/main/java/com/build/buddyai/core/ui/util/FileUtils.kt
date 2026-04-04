package com.build.buddyai.core.ui.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FileUtils {
    fun getProjectsDir(context: Context): File {
        val dir = File(context.filesDir, "projects")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getProjectDir(context: Context, projectId: String): File {
        val dir = File(getProjectsDir(context), projectId)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getBuildOutputDir(context: Context, projectId: String): File {
        val dir = File(getProjectDir(context, projectId), "build/output")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getSnapshotsDir(context: Context, projectId: String): File {
        val dir = File(getProjectDir(context, projectId), ".snapshots")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun readFileContent(file: File): String? {
        return try { file.readText() } catch (_: Exception) { null }
    }

    fun writeFileContent(file: File, content: String): Boolean {
        return try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (_: Exception) { false }
    }

    fun deleteRecursive(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursive(it) }
        }
        return file.delete()
    }

    fun copyDirectory(source: File, destination: File) {
        if (source.isDirectory) {
            destination.mkdirs()
            source.listFiles()?.forEach { child ->
                copyDirectory(child, File(destination, child.name))
            }
        } else {
            source.copyTo(destination, overwrite = true)
        }
    }

    fun zipDirectory(sourceDir: File, outputZip: File) {
        ZipOutputStream(FileOutputStream(outputZip)).use { zos ->
            sourceDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val entryName = file.relativeTo(sourceDir).path
                    zos.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }

    fun unzipToDirectory(zipFile: File, targetDir: File) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry: ZipEntry?
            while (zis.nextEntry.also { entry = it } != null) {
                val outFile = File(targetDir, entry!!.name)
                if (entry!!.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                }
                zis.closeEntry()
            }
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
            else -> "${"%.2f".format(bytes.toDouble() / (1024 * 1024 * 1024))} GB"
        }
    }

    fun countFiles(dir: File): Int {
        return dir.walkTopDown().count { it.isFile }
    }
}
