package com.build.buddyai.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.build.buddyai.core.model.BuildArtifact

@Entity(tableName = "artifacts")
data class ArtifactEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val projectName: String,
    val buildRecordId: String,
    val filePath: String,
    val fileName: String,
    val sizeBytes: Long,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val createdAt: Long,
    val minSdk: Int,
    val targetSdk: Int
) {
    fun toArtifact() = BuildArtifact(
        id = id, projectId = projectId, projectName = projectName,
        buildRecordId = buildRecordId, filePath = filePath,
        fileName = fileName, sizeBytes = sizeBytes,
        packageName = packageName, versionName = versionName,
        versionCode = versionCode, createdAt = createdAt,
        minSdk = minSdk, targetSdk = targetSdk
    )

    companion object {
        fun fromArtifact(artifact: BuildArtifact) = ArtifactEntity(
            id = artifact.id, projectId = artifact.projectId,
            projectName = artifact.projectName, buildRecordId = artifact.buildRecordId,
            filePath = artifact.filePath, fileName = artifact.fileName,
            sizeBytes = artifact.sizeBytes, packageName = artifact.packageName,
            versionName = artifact.versionName, versionCode = artifact.versionCode,
            createdAt = artifact.createdAt, minSdk = artifact.minSdk,
            targetSdk = artifact.targetSdk
        )
    }
}
