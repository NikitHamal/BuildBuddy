package com.build.buddyai.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AgentAutonomyMode(val displayName: String, val description: String) {
    SUGGEST_ONLY(
        "Suggest only",
        "The agent can plan and propose edits, but nothing is applied until you review and approve it."
    ),
    PATCH_REVIEW(
        "Patch and review",
        "The agent prepares concrete edits and a staged review, then waits for your approval before applying them."
    ),
    AUTONOMOUS_SAFE(
        "Autonomous safe",
        "The agent applies ordinary code changes automatically but requires review for sensitive actions such as manifest, Gradle, dependency, signing, or delete operations."
    ),
    AUTONOMOUS_FULL(
        "Autonomous full",
        "The agent can plan, apply, validate, and repair changes automatically, including sensitive project edits."
    )
}
