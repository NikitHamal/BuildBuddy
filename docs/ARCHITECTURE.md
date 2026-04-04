# BuildBuddy Architecture

## Layers

- `feature/*`: Compose screens + viewmodels
- `core/designsystem`: Neo Vedic theme, tokens, shared UI components
- `core/model`: domain enums/models
- `core/data/local`: Room DB, entities, DAOs, converters
- `core/data/repository`: project/build/AI repositories
- `core/data/prefs`: DataStore + encrypted key vault
- `core/data/files`: workspace scaffold/import/export/snapshot
- `core/agent`: orchestrator + provider clients
- `core/build`: build engine, compatibility checks, work scheduling
- `core/installer`: PackageInstaller APK install flow
- `app/navigation`: route model and launch state

## State and Flow

- Unidirectional state updates with `StateFlow`
- UI observes repository/viewmodel state via Compose collectors
- Build execution runs through WorkManager (`BuildWorker`)
- Agent responses stream token-by-token from provider clients

## Persistence

Room persists:

- projects metadata
- build history and diagnostics
- artifacts metadata
- chat messages and tool timelines
- provider configurations
- snapshots

DataStore persists:

- onboarding completion
- theme/editor preferences
- install/notification toggles

EncryptedSharedPreferences persists:

- provider API keys

## AI Subsystem

- provider abstraction: `AiProviderClient`
- registry-backed provider resolution
- OpenAI-compatible SSE streaming for OpenRouter/NVIDIA
- Gemini generate-content client
- `AgentOrchestrator` handles context collection, snapshots, diff extraction, and optional auto-apply

## Build Subsystem

- `BuildEngine` streams progress/log/final events
- compatibility analyzer enforces BuildBuddy project marker and manifest checks
- toolchain detection checks SDK path readiness
- build worker records history and artifacts
- explicit unsupported/failure diagnostics for non-buildable states

## Install Subsystem

- `ApkInstaller` uses PackageInstaller sessions
- emits pending/success/error events
- handles pending user action and confirmation intent
