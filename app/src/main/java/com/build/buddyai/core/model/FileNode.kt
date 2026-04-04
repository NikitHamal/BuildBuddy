package com.build.buddyai.core.model

import kotlinx.serialization.Serializable

@Serializable
data class FileNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val children: List<FileNode> = emptyList(),
    val size: Long = 0,
    val lastModified: Long = 0,
    val extension: String = name.substringAfterLast('.', ""),
    val isExpanded: Boolean = false
) {
    val fileType: FileType get() = FileType.fromExtension(extension)
}

@Serializable
enum class FileType(val displayName: String, val highlightLanguage: String) {
    KOTLIN("Kotlin", "kotlin"),
    JAVA("Java", "java"),
    XML("XML", "xml"),
    GRADLE("Gradle", "groovy"),
    GRADLE_KTS("Gradle KTS", "kotlin"),
    JSON("JSON", "json"),
    MARKDOWN("Markdown", "markdown"),
    PROPERTIES("Properties", "properties"),
    TEXT("Text", "text"),
    UNKNOWN("Unknown", "text");

    companion object {
        fun fromExtension(ext: String): FileType = when (ext.lowercase()) {
            "kt", "kts" -> if (ext == "kts") GRADLE_KTS else KOTLIN
            "java" -> JAVA
            "xml" -> XML
            "gradle" -> GRADLE
            "json" -> JSON
            "md" -> MARKDOWN
            "properties" -> PROPERTIES
            "txt" -> TEXT
            else -> UNKNOWN
        }
    }
}

@Serializable
data class OpenFile(
    val path: String,
    val name: String,
    val content: String,
    val isModified: Boolean = false,
    val cursorPosition: Int = 0,
    val scrollOffset: Int = 0,
    val fileType: FileType = FileType.UNKNOWN
)
