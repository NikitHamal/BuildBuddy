package com.build.buddyai.core.agent

data class ProjectGraphEdge(
    val fromPath: String,
    val toPath: String,
    val kind: String,
    val symbol: String? = null
)

data class ProjectGraph(
    val edges: List<ProjectGraphEdge>
) {
    private val byPath: Map<String, List<ProjectGraphEdge>> = edges
        .flatMap { edge -> listOf(edge, edge.copy(fromPath = edge.toPath, toPath = edge.fromPath)) }
        .groupBy { it.fromPath }

    fun neighbors(path: String): List<ProjectGraphEdge> = byPath[path].orEmpty()

    fun summary(maxEdges: Int = 24): String = buildString {
        appendLine("Project graph edges: ${edges.size}")
        edges.take(maxEdges).forEach { edge ->
            appendLine("- ${edge.kind}: ${edge.fromPath} -> ${edge.toPath}${edge.symbol?.let { " [$it]" } ?: ""}")
        }
    }.trim()
}
