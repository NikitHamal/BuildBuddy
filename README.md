# BuildBuddy AI

An on-device "vibe coding" platform for Android app development. Create projects, code with syntax highlighting, use AI agents to generate and modify code, build APKs on-device, and install them — all from your phone.

## Architecture

Multi-module MVVM architecture with Jetpack Compose and Hilt DI.

```
app/                          # Application shell, navigation, DI
core/
  designsystem/               # Neo Vedic design system (theme, components)
  model/                      # Domain models (Project, ChatMessage, BuildInfo, etc.)
  data/                       # Room database, DataStore, repositories
  network/                    # Retrofit + OkHttp SSE for AI providers
  ui/                         # Shared composables (ProjectCard, DiffView, etc.)
feature/
  onboarding/                 # Splash + onboarding flow
  home/                       # Project dashboard
  project/                    # Project creation wizard + playground
  editor/                     # Code editor with syntax highlighting
  agent/                      # AI chat with streaming (Ask/Plan/Apply/Auto modes)
  build/                      # On-device build engine + APK installer
  settings/                   # AI providers, editor, theme preferences
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| Database | Room |
| Preferences | DataStore + EncryptedSharedPreferences |
| Networking | Retrofit + OkHttp SSE |
| Serialization | Moshi + KSP codegen |
| Navigation | Navigation Compose |
| Build | Gradle 8.11.1 + AGP 8.7.3 + Kotlin 2.1.0 |

## AI Providers

Bring-your-own-key model. Configure API keys in Settings for:

- **NVIDIA NIM** — Llama 3.1 70B/405B, Mixtral 8x22B, DeepSeek Coder V2
- **OpenRouter** — Claude 3.5, GPT-4o, DeepSeek V3, Qwen 2.5 Coder
- **Google Gemini** — Gemini 2.0 Flash, Gemini 1.5 Pro

## Setup

1. Clone the repo
2. Open in Android Studio (Hedgehog or later)
3. Sync Gradle — all dependencies are managed via the version catalog at `gradle/libs.versions.toml`
4. Run on device or emulator (minSdk 26 / Android 8.0)

## Building

```bash
# Debug
./gradlew assembleDebug

# Signed release
./gradlew assembleRelease
```

The release keystore is at `keystore/buildbuddy-release.p12` (PKCS12 format). This is a development keystore committed for convenience in this private repo. **Do not use it for production distribution.**

## CI

GitHub Actions workflow at `.github/workflows/build.yml`:
- Triggers on push/PR to `main`
- Builds a signed release APK
- Uploads artifact as `buildbuddy-release-{short-sha}`
- Uses Gradle build caching for faster builds

## Design System

Neo Vedic design language:
- **Primary**: Deep Indigo (#1A237E)
- **Secondary**: Warm Amber (#FFA000)
- **Tertiary**: Sage Green (#4CAF50)
- 2dp architectural corner radii, hairline borders, coding-optimized syntax colors
