package com.build.buddyai.core.common

import android.content.Context
import com.build.buddyai.core.model.FileNode
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FileUtils {

    fun getProjectsDir(context: Context): File {
        return File(context.filesDir, "projects").apply { mkdirs() }
    }

    fun getBuildsDir(context: Context): File {
        return File(context.filesDir, "builds").apply { mkdirs() }
    }

    fun getArtifactsDir(context: Context): File {
        return File(context.filesDir, "artifacts").apply { mkdirs() }
    }

    fun getSnapshotsDir(context: Context): File {
        return File(context.filesDir, "snapshots").apply { mkdirs() }
    }

    fun getCacheDir(context: Context): File {
        return File(context.cacheDir, "build_cache").apply { mkdirs() }
    }

    fun buildFileTree(rootDir: File, relativeTo: File = rootDir): FileNode {
        val children = rootDir.listFiles()
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            ?.map { file ->
                if (file.isDirectory) {
                    buildFileTree(file, relativeTo)
                } else {
                    FileNode(
                        name = file.name,
                        path = file.toRelativeString(relativeTo),
                        isDirectory = false,
                        size = file.length(),
                        lastModified = file.lastModified()
                    )
                }
            } ?: emptyList()

        return FileNode(
            name = rootDir.name,
            path = rootDir.toRelativeString(relativeTo),
            isDirectory = true,
            children = children,
            lastModified = rootDir.lastModified()
        )
    }

    fun readFileContent(projectDir: File, relativePath: String): String? {
        val file = File(projectDir, relativePath)
        return if (file.exists() && file.isFile) file.readText() else null
    }

    fun writeFileContent(projectDir: File, relativePath: String, content: String) {
        val file = File(projectDir, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    fun createFile(projectDir: File, relativePath: String, content: String = "") {
        val file = File(projectDir, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    fun createDirectory(projectDir: File, relativePath: String) {
        File(projectDir, relativePath).mkdirs()
    }

    fun deleteFileOrDir(projectDir: File, relativePath: String): Boolean {
        val file = File(projectDir, relativePath)
        return file.deleteRecursively()
    }

    fun renameFile(projectDir: File, oldPath: String, newName: String): Boolean {
        val old = File(projectDir, oldPath)
        val newFile = File(old.parentFile, newName)
        return old.renameTo(newFile)
    }

    fun zipDirectory(sourceDir: File, outputZip: File) {
        ZipOutputStream(FileOutputStream(outputZip)).use { zos ->
            sourceDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val entry = ZipEntry(file.toRelativeString(sourceDir))
                    zos.putNextEntry(entry)
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }

    fun unzipToDirectory(zipFile: File, targetDir: File) {
        targetDir.mkdirs()
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos -> zis.copyTo(fos) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }

    fun calculateDirectorySize(dir: File): Long {
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
