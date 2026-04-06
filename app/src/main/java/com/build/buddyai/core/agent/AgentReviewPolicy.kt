package com.build.buddyai.core.agent

import com.build.buddyai.core.model.AgentAutonomyMode

object AgentReviewPolicy {
    data class Decision(
        val requiresReview: Boolean,
        val reasons: List<String>
    )

    fun decide(
        mode: AgentAutonomyMode,
        writes: List<AgentFileWrite>,
        deletes: List<String>,
        operations: List<AgentEditOperation>
    ): Decision {
        if (mode == AgentAutonomyMode.SUGGEST_ONLY || mode == AgentAutonomyMode.PATCH_REVIEW) {
            return Decision(true, listOf("Current autonomy mode requires review before applying any edits."))
        }
        if (mode == AgentAutonomyMode.AUTONOMOUS_FULL) {
            return Decision(false, emptyList())
        }

        val affectedPaths = (writes.map { it.path } + deletes + operations.map { it.path }).distinct()
        val reasons = buildList {
            val manifestTouched = affectedPaths.any { it.contains("AndroidManifest.xml", ignoreCase = true) } ||
                operations.any { op ->
                    op.kind.startsWith("xml_") &&
                        (op.path.contains("manifest", ignoreCase = true) ||
                            op.target.contains("manifest", ignoreCase = true) ||
                            op.target.contains("/application", ignoreCase = true))
                }
            val gradleTouched = affectedPaths.any {
                it.endsWith("build.gradle") ||
                    it.endsWith("build.gradle.kts") ||
                    it.endsWith("settings.gradle.kts") ||
                    it.endsWith("gradle.properties")
            }
            val releaseSensitiveTouched = affectedPaths.any {
                it.contains("proguard", ignoreCase = true) ||
                    it.contains("keystore", ignoreCase = true) ||
                    it.contains("sign", ignoreCase = true)
            }

            if (deletes.isNotEmpty()) add("Delete operations require review in Autonomous safe mode.")
            if (releaseSensitiveTouched) add("Signing or release-sensitive configuration changes require review.")
            if (affectedPaths.size > 20) add("Large multi-file patch requires review.")
            if (manifestTouched && affectedPaths.size > 14) add("Large patch includes manifest changes and should be reviewed.")
            if (gradleTouched && affectedPaths.size > 14) add("Large patch includes Gradle/build configuration changes and should be reviewed.")
        }
        return Decision(reasons.isNotEmpty(), reasons)
    }
}
