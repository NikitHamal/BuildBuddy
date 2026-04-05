# BuildBuddy

**Vibe Coding for Android** — a native Android workspace for creating, editing, and validating Android app projects with an autonomous AI agent.

## What this repository does now

BuildBuddy provides:
- project creation, import/export, duplication, and snapshot restore
- a real in-app code editor and file workspace
- an autonomous AI assistant that can inspect the project, write full files, apply changes automatically, and run one validation build pass by default
- artifact management for APKs produced by the on-device build pipeline
- provider integrations for NVIDIA, OpenRouter, Gemini, and PAXSENIX with SSE streaming

## Current build behavior

BuildBuddy currently ships an **on-device build pipeline**:
1. AAPT2 compiles and links Android resources
2. ECJ compiles Java sources
3. D8 produces `classes.dex`
4. the APK packager merges resources + dex, zipaligns, signs, and verifies the output
5. the artifact installer launches the platform package installer with a `FileProvider` URI

## Important capability boundary

The current validator is **Java-only** for source compilation. Kotlin and Compose files can still be edited, generated, and reviewed in the workspace, but on-device validation should fail transparently until a Kotlin-capable compiler pipeline is added.

## Notable hardening in this version

- APK packaging preserves resource entry metadata more safely
- APK signing now enables v1 + v2 + v3 and verifies the signed archive before reporting success
- generated APKs are checked with Android package parsing before being surfaced as installable artifacts
- install flow uses `ACTION_INSTALL_PACKAGE` instead of a generic viewer intent
- build clean removes both `build/` and `.build/` outputs
- the agent defaults to inspect → plan internally → edit → validate instead of exposing mode selectors

## Release note

This environment cannot run a full Android device validation pass, so the patch was produced from deep source review plus architectural tracing. You should still run a real device smoke test after applying it.
