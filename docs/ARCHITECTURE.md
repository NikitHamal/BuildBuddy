# Architecture

## Overview

BuildBuddy is implemented as a native Android application with a pragmatic single-module layout and production-oriented package boundaries.

Primary layers:

- `app`: activity, navigation shell, app theme wiring
- `core/designsystem`: Neo Vedic color, type, spacing, shape, and reusable cards/badges
- `core/model`: shared product models, enums, workspace/build/provider contracts
- `core/data/db`: Room entities, DAOs, database mappings
- `core/data/repository`: preferences, providers, projects, builds, conversations
- `core/data/workspace`: file-system workspace creation, snapshots, zip import/export
- `core/data/secure`: encrypted local secret storage
- `core/network`: AI provider clients and streaming
- `core/build`: compatibility assessment, toolchain probing, background build worker
- `core/install`: APK install/share integration
- `feature/*`: screen/viewmodel/orchestration packages

## State Model

The app uses coroutine/Flow driven state with Compose collection in the UI.

- Preferences live in DataStore and drive theme/editor/build defaults.
- Structured entities live in Room.
- Workspace files live in app-specific storage.
- Feature viewmodels combine repository flows into screen state.

## Workspace Format

User projects are BuildBuddy-native workspaces, not full arbitrary Gradle repos.

Each workspace contains:

- `buildbuddy.json` manifest
- `README.md`
- `app/src/main/...` source/resources generated from the selected template

This gives BuildBuddy control over:

- project validation
- file editing
- AI change application
- snapshotting
- build compatibility decisions

## AI System

The AI system is provider-agnostic.

Core flow:

1. Resolve provider and model from project override or app defaults.
2. Read selected workspace files and optional build context.
3. Persist user and assistant messages.
4. Stream provider output into the conversation.
5. Persist proposed file changes when the model returns them.
6. Snapshot and apply approved changes to the workspace.

## Build System

BuildBuddy intentionally avoids pretending to be Gradle-on-phone.

Instead:

- compatibility is checked against the BuildBuddy workspace contract
- the app probes for an installed toolchain bundle
- a foreground `WorkManager` worker invokes the toolchain executable
- logs, diagnostics, and artifacts are persisted
- failures feed back into the AI repair loop

## Security

- API keys are stored in encrypted shared preferences.
- Signing material is expected from local ignored files or CI secrets.
- The repository does not commit a production keystore.
- APK installs go through Android package installation APIs.

