package com.build.buddyai.core.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AgentExecutionPlan(
    val summary: String = "",
    val goals: List<String> = emptyList(),
    val focusFiles: List<String> = emptyList(),
    val shouldBuild: Boolean = false,
    val riskChecks: List<String> = emptyList()
)

object AgentPlannerProtocol {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val planRegex = Regex("""```buildbuddy-plan\s*(\{[\s\S]*?\})\s*```""")

    fun instructions(): String = """
Return only one JSON block using this exact fence:
```buildbuddy-plan
{"summary":"","goals":[],"focusFiles":[],"shouldBuild":false,"riskChecks":[]}
```

Rules:
- focusFiles must be project-relative paths from the provided index when possible.
- Keep goals and riskChecks concise and concrete.
- Set shouldBuild=true when the task changes code that should be validated now.
- Do not include prose outside the JSON fence.
""".trimIndent()

    fun parse(raw: String): AgentExecutionPlan? {
        val payload = planRegex.find(raw)?.groupValues?.getOrNull(1) ?: raw.trim()
        return runCatching { json.decodeFromString<AgentExecutionPlan>(payload) }.getOrNull()
    }
}
