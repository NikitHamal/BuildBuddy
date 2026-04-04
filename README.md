# BuildBuddy

**Vibe Coding for Android** — A production-grade native Android app for creating, coding, building, and installing Android apps directly on-device.

## Overview

BuildBuddy is a serious on-device "vibe coding" platform for Android app development. Users can create Android app projects, manually code them using a real in-app editor, use an agentic AI assistant to generate and modify code, build installable APKs on-device, and install them through proper Android install flows.

## Architecture

### Tech Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Design System**: Neo Vedic — warm earth tones, sacred geometry-inspired radii, hairline borders
- **Architecture**: MVVM with unidirectional state flow
- **DI**: Hilt
- **Persistence**: Room + DataStore
- **Network**: OkHttp + Retrofit + Kotlin Serialization
- **Async**: Coroutines + Flow
- **Build**: Gradle Kotlin DSL with version catalog

### Project Structure
```
com.build.buddyai/
├── core/
│   ├── designsystem/    # Neo Vedic theme, tokens, components
│   │   ├── theme/       # Color, Typography, Shape, Theme
│   │   └── component/   # NvButton, NvCard, NvTextField, etc.
│   ├── data/
│   │   ├── local/       # Room database, DAOs, entities
│   │   ├── datastore/   # DataStore preferences
│   │   └── repository/  # Data repositories
│   ├── model/           # Domain models
│   ├── network/         # AI API services, streaming, model catalog
│   ├── di/              # Hilt modules
│   └── common/          # Utilities, secure storage, file management
├── domain/
│   └── usecase/         # Build engine, project generation
├── feature/
│   ├── splash/          # Splash screen
│   ├── onboarding/      # One-time onboarding flow
│   ├── home/            # Dashboard with projects list
│   ├── project/
│   │   ├── creation/    # Project creation wizard
│   │   ├── playground/  # Main project workspace
│   │   └── overview/    # Project overview tab
│   ├── agent/           # AI chat/agent workspace
│   ├── editor/          # Code editor with syntax support
│   ├── files/           # File explorer/manager
│   ├── build/           # Build panel with logs
│   ├── artifacts/       # APK artifacts management
│   ├── settings/        # App settings
│   └── models/          # AI provider/model management
└── navigation/          # Navigation graph
```

## Features

### Project Management
- Create projects from 5 templates (Blank Compose, Blank Views, Single Activity Compose, Java Activity, Basic Utility)
- Full project configuration (name, package, language, UI framework, SDK versions)
- Search, sort, and filter projects
- Duplicate, export (zip), and import projects
- Snapshot/version history with restore

### AI Agent
- Bring-your-own-key model integration
- Supported providers: **NVIDIA**, **OpenRouter**, **Gemini**
- Streaming responses with SSE
- Agent modes: Ask, Plan, Apply, Auto
- File context attachment
- Diff generation and review before applying changes
- Auto-snapshot before AI modifications
- Cancel, retry, and regenerate support
- Action timeline (reading files, planning, editing, building, analyzing)

### Code Editor
- Syntax-aware editing for Kotlin, Java, XML, Gradle, JSON, Markdown
- Line numbers with gutter
- Search and replace
- Undo/redo stack
- File tabs with modified indicators
- Auto-save with configurable interval
- Monospace font with configurable size
- Hardware keyboard friendly

### On-Device Build System
- Real build pipeline with validation, source processing, dexing, packaging, signing
- Structured build logs with severity levels
- Build progress with phase indicators
- Foreground service for long-running builds
- Error parsing and diagnostics
- Build history tracking
- Compatibility warnings for unsupported features
- Clean and rebuild support
- "Ask AI to Fix" for build failures

### APK Management
- View generated APK artifacts with metadata
- Install via Android PackageInstaller
- Share/export APK files
- Version and package details

### Settings
- Theme: Light / Dark / System
- Editor: font size, tab width, soft wrap, line numbers, autosave
- AI: provider defaults, model selection
- Build: cache management, notifications
- Privacy: data clearing, log export
- About: version, licenses

## Design System

BuildBuddy uses the **Neo Vedic** design system:

- **Color**: Warm saffron/amber primary, temple stone secondary, parchment surfaces
- **Typography**: Clean sans-serif body, monospace for code
- **Shape**: 2dp architectural radii system (2, 4, 8, 12, 16dp)
- **Border**: Hairline (0.5dp) depth borders
- **Spacing**: 8-point grid (2, 4, 8, 12, 16, 20, 24, 32, 40, 48dp)
- **Elevation**: Minimal, preferring border-based depth
- **Extended colors**: Success, warning, info + full editor/syntax palette
- **Dark theme**: Full dark mode with inverted semantic mapping

## AI Provider Setup

1. Open **Settings → AI Providers** or the **Models** screen
2. Expand a provider (NVIDIA, OpenRouter, or Gemini)
3. Enter your API key
4. Test the connection
5. Select a model
6. Set as default provider

### Supported Models

| Provider | Models |
|----------|--------|
| NVIDIA | Llama 3.1 (8B/70B/405B), Nemotron-4 340B, Mixtral 8x22B, Gemma 2 27B, Phi-3, DeepSeek Coder |
| OpenRouter | Claude 3.5 Sonnet, GPT-4o, Gemini 1.5 Pro, Llama 3.1, DeepSeek, Mistral Large |
| Gemini | Gemini 1.5 Pro, 1.5 Flash, 1.5 Flash-8B, 1.0 Pro |

## Build System

BuildBuddy includes an on-device build engine for supported project templates:

**Supported scope:**
- BuildBuddy-generated project templates (Compose, Views, Java)
- Basic syntax validation
- Project structure verification
- APK assembly with metadata
- Debug signing

**Limitations (transparent in UI):**
- Full Gradle compilation parity requires expansion of the on-device toolchain
- Complex multi-module projects may show compatibility warnings
- Java compilation has limited on-device support

The architecture is designed for future expansion with proper abstractions for compilation, dexing, and signing phases.

## CI/CD

GitHub Actions workflow triggers on every push to any branch:

1. Builds a signed release APK
2. Renames it to `BuildBuddy-<shortsha>-release.apk`
3. Uploads as a GitHub Actions artifact

### Build Optimizations
- Gradle dependency caching
- Wrapper caching
- Configuration cache enabled
- Parallel builds
- Stable JDK 17 setup

## Setup

### Prerequisites
- Android Studio Ladybug or newer
- JDK 17
- Android SDK 35

### Local Development
```bash
git clone <repo-url>
cd BuildBuddy
./gradlew assembleDebug
```

### Signing
The repository includes a development keystore at `keystore/buildbuddy-release.jks` for CI builds.

- **Alias**: buildbuddy
- **Store password**: buildbuddy123
- **Key password**: buildbuddy123

For production releases, replace with your own signing configuration.

## Security

- API keys stored via Android EncryptedSharedPreferences (AES-256-GCM)
- Keys never logged or included in build artifacts
- Scoped file storage for projects and builds
- Proper Android permission handling
- FileProvider for safe APK sharing/installation
- No hidden telemetry

## License

Private repository. All rights reserved.
