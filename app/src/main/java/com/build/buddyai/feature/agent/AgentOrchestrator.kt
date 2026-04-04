package com.build.buddyai.feature.agent

import com.build.buddyai.core.data.repository.ConversationRepository
import com.build.buddyai.core.data.repository.PreferencesRepository
import com.build.buddyai.core.data.repository.ProjectRepository
import com.build.buddyai.core.data.repository.ProviderSettingsRepository
import com.build.buddyai.core.model.AgentAttachment
import com.build.buddyai.core.model.AgentMode
import com.build.buddyai.core.model.AgentRequest
import com.build.buddyai.core.model.AgentTimelineEvent
import com.build.buddyai.core.model.AiStreamEvent
import com.build.buddyai.core.model.BuildRecord
import com.build.buddyai.core.model.ChatMessage
import com.build.buddyai.core.model.ChangeOperation
import com.build.buddyai.core.model.MessageRole
import com.build.buddyai.core.model.MessageStatus
import com.build.buddyai.core.model.Project
import com.build.buddyai.core.model.TimelineStage
import com.build.buddyai.core.model.WorkspaceChange
import com.build.buddyai.core.network.ProviderRegistry
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull

@Singleton
class AgentOrchestrator @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val providerSettingsRepository: ProviderSettingsRepository,
    private val conversationRepository: ConversationRepository,
    private val providerRegistry: ProviderRegistry,
    private val projectRepository: ProjectRepository,
) {
    suspend fun sendMessage(
        project: Project,
        prompt: String,
        mode: AgentMode,
        selectedFiles: List<String>,
        buildContext: BuildRecord? = null,
    ) {
        val preferences = preferencesRepository.preferences.firstOrNull() ?: com.build.buddyai.core.model.AppPreferences()
        val providerSettings = providerSettingsRepository.settings.firstOrNull().orEmpty()
        val providerId = project.defaultProvider ?: preferences.defaultProvider
        val providerConfig = providerSettings.firstOrNull { it.providerId == providerId }
        val modelId = project.defaultModel ?: providerConfig?.selectedModel ?: preferences.defaultModel
        val apiKey = providerRegistry.apiKey(providerId)
            ?: throw IllegalStateException("Missing API key for ${providerId.name}.")

        val attachments = selectedFiles.map { path ->
            AgentAttachment(path = path, label = path.substringAfterLast('/'))
        }
        val contextBlock = selectedFiles.joinToString("\n\n") { path ->
            "File: $path\n${projectRepository.readFile(project, path)}"
        }
        val enrichedPrompt = buildString {
            append(prompt.trim())
            if (contextBlock.isNotBlank()) {
                append("\n\nRelevant workspace files:\n")
                append(contextBlock)
            }
            buildContext?.let {
                append("\n\nBuild summary:\n${it.summary}\n\nRaw build log:\n${it.rawLog}")
            }
        }

        val conversation = conversationRepository.ensureConversation(project.id, providerId, modelId, mode)
        conversationRepository.upsertMessage(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                conversationId = conversation.id,
                role = MessageRole.USER,
                content = prompt,
                status = MessageStatus.COMPLETE,
                attachments = attachments,
                createdAt = System.currentTimeMillis(),
            ),
        )

        val assistantMessageId = UUID.randomUUID().toString()
        var assistantMessage = ChatMessage(
            id = assistantMessageId,
            conversationId = conversation.id,
            role = MessageRole.ASSISTANT,
            content = "",
            status = MessageStatus.STREAMING,
            timeline = listOf(
                AgentTimelineEvent(TimelineStage.READING_FILES, "Context gathered", System.currentTimeMillis()),
                AgentTimelineEvent(TimelineStage.PLANNING, "Planning response", System.currentTimeMillis()),
            ),
            createdAt = System.currentTimeMillis(),
        )
        conversationRepository.upsertMessage(assistantMessage)

        val request = AgentRequest(
            project = project,
            mode = mode,
            provider = providerId,
            model = modelId,
            temperature = providerConfig?.temperature ?: 0.2,
            maxTokens = providerConfig?.maxTokens ?: 4096,
            topP = providerConfig?.topP ?: 0.95,
            prompt = enrichedPrompt,
            selectedFiles = selectedFiles,
            buildContext = buildContext,
        )

        providerRegistry.client(providerId).stream(request, apiKey).collect { event ->
            assistantMessage = when (event) {
                is AiStreamEvent.Delta -> assistantMessage.copy(content = assistantMessage.content + event.chunk)
                is AiStreamEvent.Completed -> assistantMessage.copy(content = event.finalMessage, status = MessageStatus.COMPLETE)
                is AiStreamEvent.Failed -> assistantMessage.copy(content = event.reason, status = MessageStatus.ERROR)
                is AiStreamEvent.ProposedPatch -> assistantMessage.copy(
                    content = event.summary,
                    status = MessageStatus.PROPOSED,
                    proposedChanges = event.changes,
                    timeline = assistantMessage.timeline + AgentTimelineEvent(
                        stage = TimelineStage.EDITING,
                        label = "Generated patch proposal",
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
            conversationRepository.upsertMessage(assistantMessage)
        }
    }

    suspend fun applyChanges(project: Project, changes: List<WorkspaceChange>) {
        if (changes.isEmpty()) return
        projectRepository.snapshotProject(project, "Before AI apply")
        changes.forEach { change ->
            when (change.operation) {
                ChangeOperation.CREATE, ChangeOperation.UPDATE -> {
                    projectRepository.writeFile(project, change.path, change.content.orEmpty())
                }
                ChangeOperation.DELETE -> {
                    projectRepository.deletePath(project, change.path)
                }
            }
        }
        projectRepository.updateProject(project.copy(updatedAt = System.currentTimeMillis()))
    }
}
