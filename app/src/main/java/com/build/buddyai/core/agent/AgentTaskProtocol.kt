package com.build.buddyai.core.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AgentTaskEnvelope(
    val mode: String = MODE_REPLY,
    val summary: String = "",
    val shouldBuild: Boolean = false,
    val deleteFiles: List<String> = emptyList()
) {
    companion object {
        const val MODE_REPLY = "reply"
        const val MODE_TASK = "task"
    }
}

data class AgentFileWrite(
    val path: String,
    val content: String
)

data class ParsedAgentResponse(
    val envelope: AgentTaskEnvelope?,
    val writes: List<AgentFileWrite>,
    val deletes: List<String>,
    val displayMessage: String,
    val rawContent: String
) {
    val isTask: Boolean get() = envelope?.mode == AgentTaskEnvelope.MODE_TASK || writes.isNotEmpty() || deletes.isNotEmpty()
    val shouldBuild: Boolean get() = envelope?.shouldBuild == true
}

object AgentTaskProtocol {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val envelopeRegex = Regex("""```buildbuddy\s*(\{[\s\S]*?\})\s*```""")
    private val filepathRegex = Regex("""```filepath:(.*?)\n([\s\S]*?)```""")

    fun parse(rawContent: String): ParsedAgentResponse {
        val envelope = envelopeRegex.find(rawContent)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { runCatching { json.decodeFromString<AgentTaskEnvelope>(it) }.getOrNull() }

        val writes = filepathRegex.findAll(rawContent).mapNotNull { match ->
            val path = match.groupValues.getOrNull(1)?.trim().orEmpty()
            val content = match.groupValues.getOrNull(2)?.trimEnd().orEmpty()
            if (path.isBlank()) {
                null
            } else {
                AgentFileWrite(path = path, content = content)
            }
        }.toList()

        val deletes = envelope?.deleteFiles.orEmpty().distinct()
        val stripped = rawContent
            .replace(envelopeRegex, "")
            .replace(filepathRegex, "")
            .trim()

        val displayMessage = envelope?.summary?.takeIf { it.isNotBlank() }
            ?: stripped.ifBlank {
                when {
                    writes.isNotEmpty() || deletes.isNotEmpty() -> "Task completed."
                    else -> rawContent.trim()
                }
            }

        return ParsedAgentResponse(
            envelope = envelope,
            writes = writes,
            deletes = deletes,
            displayMessage = displayMessage,
            rawContent = rawContent.trim()
        )
    }

    fun protocolInstructions(): String = """
Always begin your response with a buildbuddy JSON block:
```buildbuddy
{"mode":"reply|task","summary":"what you did or what the answer is","shouldBuild":false,"deleteFiles":[]}
```

If files must be created or updated, emit one full-file block per file:
```filepath:relative/path/from/project/root
<complete file content>
```

Rules:
- Use mode="reply" for pure questions or advice with no project edits.
- Use mode="task" for implementation, fixes, refactors, audits that change files, or validation work.
- Never output partial patches, ellipses, or placeholders.
- Never write outside the project root.
- Put every file change in filepath blocks and every deletion in deleteFiles.
- Set shouldBuild=true when the changed project should be validated immediately.
""".trimIndent()
}
