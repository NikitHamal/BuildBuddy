# Providers And On-Device Building

## AI Providers

Supported providers:

- OpenRouter
- Gemini
- NVIDIA

Each provider has:

- encrypted API-key storage
- persisted model defaults
- temperature / max tokens / top-p controls
- connection test support
- streamed response handling

## Provider Selection Logic

Resolution order:

1. project-level provider override
2. app default provider
3. provider-specific default model if no stored model exists

## Agent Patch Contract

The current patch-oriented prompt contract asks models to return a JSON envelope containing:

- `message`
- `changes[]`

Each change contains:

- `operation`
- `path`
- `content`
- `reason`

This keeps patch application reviewable and deterministic.

## Build Compatibility

BuildBuddy currently targets BuildBuddy-native workspaces first.

A workspace is considered build-compatible when:

- `buildbuddy.json` exists
- the workspace schema matches what the app/toolchain supports
- a toolchain bundle is installed and complete

## Toolchain Bundle Contract

The bundle root defaults to:

```text
<app files>/toolchains/android
```

The bundle must include:

- `manifest.json`
- the executable declared by `buildExecutable`

The build worker executes:

```text
<executable> build --project <workspace> --mode <debug|release> --out <artifact_dir>
```

Expected behavior from the toolchain:

- validate project schema
- compile/package supported templates
- emit machine-readable or human-readable logs
- place APK outputs in the provided output directory

## APK Install Flow

Artifacts are installed through `PackageInstaller` sessions. BuildBuddy does not attempt silent installs.

Required user/platform conditions:

- install permission allowed for the app on supported Android versions
- artifact must be a real APK emitted by the toolchain

## CI Signing

GitHub Actions expects these secrets:

- `BUILD_BUDDY_UPLOAD_STORE_B64`
- `BUILD_BUDDY_UPLOAD_STORE_PASSWORD`
- `BUILD_BUDDY_UPLOAD_KEY_ALIAS`
- `BUILD_BUDDY_UPLOAD_KEY_PASSWORD`

The workflow decodes the keystore at build time and passes the path/passwords through environment variables consumed by `app/build.gradle.kts`.

