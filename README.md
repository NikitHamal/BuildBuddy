# BuildBuddy

**Vibe Coding for Android** — a native Android workspace for creating, editing, and orchestrating Android app projects with an AI agent.

## What this repository now does

BuildBuddy provides:
- project creation, import/export, duplication, and snapshot restore
- a real in-app code editor and file workspace
- an agentic AI assistant that can propose complete file diffs inside the project sandbox
- artifact management for APKs that are actually produced by Gradle
- real provider integrations for NVIDIA, OpenRouter, and Gemini, including true Gemini SSE streaming

## Build behavior

BuildBuddy no longer fabricates APK files.

The build flow now:
1. validates that a real Gradle wrapper is present
2. launches `:app:assembleDebug`
3. streams real Gradle logs into the app
4. captures the newest APK from `app/build/outputs/apk`
5. stores that APK as a build artifact only if Gradle actually produced it

If the wrapper script or wrapper JAR is missing, the build fails transparently instead of generating a fake artifact.

## Security posture improvements in this version

- project file access is sandboxed against path traversal
- zip extraction rejects Zip Slip payloads
- Gemini API keys are sent via header instead of URL query params
- unsafe global HTTP logging is removed
- foreground build cancel now cancels the active build process
- artifact deletion removes disk files as well as database rows
- log export uses `FileProvider` and launches a proper share flow
- destructive Room fallback migration is removed
- release protection rules keep Hilt/ViewModel generated classes

## Current limitation

This upload does not include a complete tested Android toolchain or build verification in this environment, so changes were applied by source review only. You should run a full device and release validation pass before shipping.
