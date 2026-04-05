package com.build.buddyai.core.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AgentTaskEnvelope(
    val mode: String = MODE_REPLY,
    val summary: String = "",
    val shouldBuild: Boolean = false,
    val deleteFiles: List<String> = emptyList(),
    val plan: List<String> = emptyList()
) {
    companion object {
        const val MODE_REPLY = "reply"
        const val MODE_TASK = "task"
    }
}

@Serializable
data class AgentExecutionPlan(
    val goal: String = "",
    val steps: List<String> = emptyList(),
    val readFiles: List<String> = emptyList(),
    val risks: List<String> = emptyList()
)

data class AgentFileWrite(
    val path: String,
    val content: String
)

@Serializable
data class AgentEditOperation(
    val kind: String,
    val path: String,
    val target: String = "",
    val payload: String = "",
    val attributes: Map<String, String> = emptyMap()
)

data class ParsedAgentResponse(
    val envelope: AgentTaskEnvelope?,
    val plan: AgentExecutionPlan?,
    val writes: List<AgentFileWrite>,
    val deletes: List<String>,
    val operations: List<AgentEditOperation>,
    val displayMessage: String,
    val rawContent: String
) {
    val isTask: Boolean get() = envelope?.mode == AgentTaskEnvelope.MODE_TASK || writes.isNotEmpty() || deletes.isNotEmpty() || operations.isNotEmpty()
    val shouldBuild: Boolean get() = envelope?.shouldBuild == true
}

object AgentTaskProtocol {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val envelopeRegex = Regex("""```buildbuddy\s*(\{[\s\S]*?\})\s*```""")
    private val planRegex = Regex("""```buildbuddy-plan\s*(\{[\s\S]*?\})\s*```""")
    private val editRegex = Regex("""```buildbuddy-edit\s*(\[[\s\S]*?\])\s*```""")
    private val filepathRegex = Regex("""```filepath:(.*?)\n([\s\S]*?)```""")

    fun parse(rawContent: String): ParsedAgentResponse {
        val envelope = envelopeRegex.find(rawContent)
            ?.groupValues?.getOrNull(1)
            ?.let { runCatching { json.decodeFromString<AgentTaskEnvelope>(it) }.getOrNull() }

        val plan = planRegex.find(rawContent)
            ?.groupValues?.getOrNull(1)
            ?.let { runCatching { json.decodeFromString<AgentExecutionPlan>(it) }.getOrNull() }

        val operations = editRegex.find(rawContent)
            ?.groupValues?.getOrNull(1)
            ?.let { runCatching { json.decodeFromString<List<AgentEditOperation>>(it) }.getOrElse { emptyList() } }
            .orEmpty()

        val writes = filepathRegex.findAll(rawContent).mapNotNull { match ->
            val path = match.groupValues.getOrNull(1)?.trim().orEmpty()
            val content = match.groupValues.getOrNull(2)?.trimEnd().orEmpty()
            if (path.isBlank()) null else AgentFileWrite(path = path, content = content)
        }.toList()

        val deletes = envelope?.deleteFiles.orEmpty().distinct()
        val stripped = rawContent
            .replace(envelopeRegex, "")
            .replace(planRegex, "")
            .replace(editRegex, "")
            .replace(filepathRegex, "")
            .trim()

        val displayMessage = envelope?.summary?.takeIf { it.isNotBlank() }
            ?: stripped.ifBlank {
                when {
                    writes.isNotEmpty() || deletes.isNotEmpty() || operations.isNotEmpty() -> "Task completed."
                    else -> rawContent.trim()
                }
            }

        return ParsedAgentResponse(
            envelope = envelope,
            plan = plan,
            writes = writes,
            deletes = deletes,
            operations = operations,
            displayMessage = displayMessage,
            rawContent = rawContent.trim()
        )
    }

    fun parsePlan(rawContent: String): AgentExecutionPlan? = planRegex.find(rawContent)
        ?.groupValues?.getOrNull(1)
        ?.let { runCatching { json.decodeFromString<AgentExecutionPlan>(it) }.getOrNull() }

    fun planningInstructions(): String = """
Return exactly one planning block:
```buildbuddy-plan
{"goal":"what will be achieved","steps":["step 1","step 2"],"readFiles":["relative/path"],"risks":["key risk"]}
```

Rules:
- Think like a planner/executor for a real Android product.
- Keep the plan short, concrete, and file-aware.
- Prefer surgical edits over full-file rewrites when possible.
""".trimIndent()

    fun protocolInstructions(): String = """
Always begin your response with a buildbuddy JSON block:
```buildbuddy
{"mode":"reply|task","summary":"what you did or what the answer is","shouldBuild":false,"deleteFiles":[],"plan":["step 1","step 2"]}
```

If you can make a precise structured edit, prefer a buildbuddy-edit block before any full-file blocks:
```buildbuddy-edit
[{"kind":"java_add_import|java_remove_import|java_replace_method|java_upsert_method|java_replace_call|xml_set_attribute|xml_remove_attribute|xml_replace_text|xml_append_under|xml_remove_nodes|text_replace|text_replace_regex","path":"relative/path","target":"selector","payload":"content","attributes":{"name":"value"}}]
```

If files must be created or fully replaced, emit one full-file block per file:
```filepath:relative/path/from/project/root
<complete file content>
```

Rules:
- Use mode="reply" for pure questions or advice with no project edits.
- Use mode="task" for implementation, fixes, refactors, audits that change files, or validation work.
- Prefer AST/XML edit operations for Java and XML when a surgical patch is enough.
- text_replace uses a literal string match. Use text_replace_regex only when you intentionally need regex semantics.
- Never output partial patches, ellipses, or placeholders.
- Never write outside the project root.
- Put every file change in filepath blocks and every deletion in deleteFiles.
- Set shouldBuild=true when the changed project should be validated immediately.
""".trimIndent()
}
