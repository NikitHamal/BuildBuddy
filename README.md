# BuildBuddy

BuildBuddy is a native Android app for creating, editing, AI-modifying, building, and installing Android app projects directly on-device.

This repository contains:

- A full Kotlin + Jetpack Compose Android application with Hilt, Room, DataStore, WorkManager, secure API-key storage, adaptive navigation, and a coding-centric Neo Vedic UI system.
- A BuildBuddy-compatible workspace format generated inside app storage for on-device project creation and editing.
- A real AI provider subsystem for OpenRouter, Gemini, and NVIDIA with persisted conversations, streaming responses, context attachment, and patch proposal/apply flows.
- An honest on-device build pipeline that validates compatibility and hands supported projects to an installed BuildBuddy Android toolchain bundle instead of faking Gradle parity on-device.
- APK installation wiring through Android `PackageInstaller`.

## Product Scope

BuildBuddy supports:

- Splash and one-time onboarding
- Workspace dashboard with search, sort, filters, quick actions, recent builds
- Project creation wizard
- Adaptive project playground with Overview, Agent, Editor, Files, Build, and Artifacts sections
- Local project persistence, snapshots, zip export/import, artifacts, build history
- Bring-your-own-key model configuration
- Native code editing with syntax coloring, line numbers, search/replace, undo/redo, tabs, and autosave

BuildBuddy does not pretend to support arbitrary Gradle projects on-device. Instead it:

- Generates and manages BuildBuddy-compatible project workspaces
- Detects unsupported or incomplete build states honestly
- Requires an installed BuildBuddy Android toolchain bundle for actual on-device compilation
- Surfaces compatibility and toolchain diagnostics directly in the UI

## Stack

- Kotlin
- Jetpack Compose + Material 3
- Hilt
- Room
- DataStore
- WorkManager
- OkHttp + SSE
- AndroidX Security Crypto

## Repository Layout

- `app/`: Android application module
- `docs/ARCHITECTURE.md`: subsystem and package overview
- `docs/PROVIDERS_AND_BUILDING.md`: provider setup, toolchain contract, build/install flow
- `.github/workflows/android-release.yml`: signed release APK CI workflow

## Local Setup

1. Install Android Studio with Android SDK 35 and JDK 17.
2. Provide release signing secrets through environment variables or `keystore/signing.properties`.
3. Optionally configure a BuildBuddy Android toolchain bundle for real on-device compilation.
4. Open the project and run the app from Android Studio.

Release signing variables:

- `BUILD_BUDDY_UPLOAD_STORE_FILE`
- `BUILD_BUDDY_UPLOAD_STORE_PASSWORD`
- `BUILD_BUDDY_UPLOAD_KEY_ALIAS`
- `BUILD_BUDDY_UPLOAD_KEY_PASSWORD`

This repository intentionally does not commit a production keystore. CI is designed for secret-backed signing.

## Toolchain Bundle

For actual on-device builds, BuildBuddy expects a toolchain bundle containing:

- `manifest.json`
- the executable declared by `buildExecutable`

Example manifest:

```json
{
  "version": "1.0.0",
  "buildExecutable": "bin/buildbuddy-toolchain",
  "supportsProjectSchema": 1,
  "abi": "arm64-v8a"
}
```

The worker invokes:

```bash
<buildExecutable> build --project <workspace> --mode <debug|release> --out <artifact_dir>
```

## CI

The workflow:

- runs on every push
- skips pull request triggers
- builds a signed release APK
- renames it to `BuildBuddy-<shortsha>-release.apk`
- uploads it as a GitHub Actions artifact

## Notes

- No Gradle commands or tests were executed in this environment.
- The prompt referenced an authoritative Neo Vedic design spec, but that spec was not present in the repository. The implementation follows the explicit constraints from the prompt: 2dp-based geometry, hairline borders, calm premium surfaces, light/dark mapping, and Devanagari-capable typography resources.

