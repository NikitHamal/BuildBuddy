package com.build.buddyai.core.model

data class ProjectFile(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val extension: String = name.substringAfterLast('.', ""),
    val sizeBytes: Long = 0,
    val lastModified: Long = System.currentTimeMillis(),
    val children: List<ProjectFile> = emptyList(),
    val content: String? = null
)

enum class FileType(val extensions: List<String>, val displayName: String) {
    KOTLIN(listOf("kt", "kts"), "Kotlin"),
    JAVA(listOf("java"), "Java"),
    XML(listOf("xml"), "XML"),
    GRADLE(listOf("gradle"), "Gradle"),
    GRADLE_KTS(listOf("gradle.kts"), "Gradle KTS"),
    JSON(listOf("json"), "JSON"),
    MARKDOWN(listOf("md"), "Markdown"),
    PROPERTIES(listOf("properties"), "Properties"),
    YAML(listOf("yml", "yaml"), "YAML"),
    TEXT(listOf("txt"), "Text"),
    UNKNOWN(emptyList(), "Unknown");

    companion object {
        fun fromExtension(ext: String): FileType =
            entries.firstOrNull { ext.lowercase() in it.extensions } ?: UNKNOWN
    }
}

data class FileOperation(
    val type: FileOperationType,
    val path: String,
    val newPath: String? = null,
    val content: String? = null
)

enum class FileOperationType {
    CREATE_FILE,
    CREATE_DIRECTORY,
    RENAME,
    DELETE,
    MOVE,
    UPDATE_CONTENT
}

data class Snapshot(
    val id: String = java.util.UUID.randomUUID().toString(),
    val projectId: String,
    val label: String,
    val createdAt: Long = System.currentTimeMillis(),
    val description: String = "",
    val fileCount: Int = 0,
    val isAutoSnapshot: Boolean = false
)
