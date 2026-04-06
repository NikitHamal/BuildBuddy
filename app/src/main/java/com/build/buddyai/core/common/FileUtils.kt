package com.build.buddyai.core.common

import android.content.Context
import com.build.buddyai.core.model.FileNode
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FileUtils {

    fun getProjectsDir(context: Context): File = File(context.filesDir, "projects").apply { mkdirs() }
    fun getBuildsDir(context: Context): File = File(context.filesDir, "builds").apply { mkdirs() }
    fun getArtifactsDir(context: Context): File = File(context.filesDir, "artifacts").apply { mkdirs() }
    fun getSnapshotsDir(context: Context): File = File(context.filesDir, "snapshots").apply { mkdirs() }
    fun getCacheDir(context: Context): File = File(context.cacheDir, "build_cache").apply { mkdirs() }

    fun normalizeRelativePath(relativePath: String): String {
        val sanitized = relativePath
            .replace('\\', '/')
            .trim()
            .removePrefix("./")
            .trimStart('/')

        require(sanitized.isNotBlank()) { "Path cannot be blank" }
        require(!sanitized.contains("\u0000")) { "Path contains invalid characters" }

        val segments = sanitized.split('/').filter { it.isNotBlank() && it != "." }
        require(segments.isNotEmpty()) { "Path must point to a file or directory inside the project" }
        require(segments.none { it == ".." }) { "Parent path traversal is not allowed" }

        return segments.joinToString("/")
    }

    fun resolveProjectFile(projectDir: File, relativePath: String): File {
        val normalized = normalizeRelativePath(relativePath)
        val canonicalProjectDir = projectDir.canonicalFile
        val candidate = File(canonicalProjectDir, normalized).canonicalFile
        require(candidate.path == canonicalProjectDir.path || candidate.path.startsWith(canonicalProjectDir.path + File.separator)) {
            "Resolved path escapes the project sandbox"
        }
        return candidate
    }

    fun resolveProjectDirectory(projectDir: File, relativePath: String): File {
        val directory = resolveProjectFile(projectDir, relativePath)
        require(!directory.exists() || directory.isDirectory) { "Target exists and is not a directory" }
        return directory
    }

    fun buildFileTree(rootDir: File, relativeTo: File = rootDir): FileNode {
        val canonicalRoot = rootDir.canonicalFile
        val canonicalRelativeTo = relativeTo.canonicalFile
        val children = canonicalRoot.listFiles()
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            ?.map { file ->
                if (file.isDirectory) {
                    buildFileTree(file, canonicalRelativeTo)
                } else {
                    FileNode(
                        name = file.name,
                        path = file.relativeTo(canonicalRelativeTo).invariantSeparatorsPath,
                        isDirectory = false,
                        size = file.length(),
                        lastModified = file.lastModified()
                    )
                }
            } ?: emptyList()

        val relativePath = if (canonicalRoot == canonicalRelativeTo) "" else canonicalRoot.relativeTo(canonicalRelativeTo).invariantSeparatorsPath
        return FileNode(
            name = canonicalRoot.name,
            path = relativePath,
            isDirectory = true,
            children = children,
            lastModified = canonicalRoot.lastModified()
        )
    }

    fun readFileContent(projectDir: File, relativePath: String): String? {
        val file = resolveProjectFile(projectDir, relativePath)
        return if (file.exists() && file.isFile) file.readText() else null
    }

    fun writeFileContent(projectDir: File, relativePath: String, content: String) {
        val file = resolveProjectFile(projectDir, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    fun createFile(projectDir: File, relativePath: String, content: String = "") {
        val file = resolveProjectFile(projectDir, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    fun createDirectory(projectDir: File, relativePath: String) {
        resolveProjectDirectory(projectDir, relativePath).mkdirs()
    }

    fun deleteFileOrDir(projectDir: File, relativePath: String): Boolean {
        val file = resolveProjectFile(projectDir, relativePath)
        return if (file.exists()) file.deleteRecursively() else false
    }

    fun renameFile(projectDir: File, oldPath: String, newName: String): Boolean {
        require(newName.isNotBlank()) { "New file name cannot be blank" }
        require(!newName.contains('/') && !newName.contains('\\')) { "New file name must not contain path separators" }
        val old = resolveProjectFile(projectDir, oldPath)
        val parent = old.parentFile?.canonicalFile ?: return false
        val canonicalProjectDir = projectDir.canonicalFile
        require(parent.path == canonicalProjectDir.path || parent.path.startsWith(canonicalProjectDir.path + File.separator)) {
            "Resolved rename target escapes the project sandbox"
        }
        val newFile = File(parent, newName).canonicalFile
        require(newFile.path.startsWith(parent.path + File.separator) || newFile.path == parent.path + File.separator + newName) {
            "Invalid rename target"
        }
        return old.renameTo(newFile)
    }

    fun zipDirectory(sourceDir: File, outputZip: File) {
        val canonicalSourceDir = sourceDir.canonicalFile
        outputZip.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(outputZip)).use { zos ->
            canonicalSourceDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val entry = ZipEntry(file.relativeTo(canonicalSourceDir).invariantSeparatorsPath)
                    zos.putNextEntry(entry)
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }

    fun unzipToDirectory(zipFile: File, targetDir: File) {
        val canonicalTargetDir = targetDir.canonicalFile
        canonicalTargetDir.mkdirs()
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val output = resolveZipEntry(canonicalTargetDir, entry)
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    FileOutputStream(output).use { fos -> zis.copyTo(fos) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun resolveZipEntry(targetDir: File, entry: ZipEntry): File {
        val normalized = entry.name.replace('\\', '/').trim()
        require(normalized.isNotBlank()) { "Zip entry name cannot be blank" }
        require(!normalized.startsWith('/')) { "Absolute zip entries are not allowed" }
        require(!normalized.split('/').any { it == ".." }) { "Zip entry attempts path traversal" }

        val outFile = File(targetDir, normalized).canonicalFile
        require(outFile.path == targetDir.path || outFile.path.startsWith(targetDir.path + File.separator)) {
            throw IOException("Zip entry escapes target directory: ${entry.name}")
        }
        return outFile
    }

    fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    }

    fun calculateDirectorySize(dir: File): Long =
        if (!dir.exists()) 0L else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
