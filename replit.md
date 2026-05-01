# LocalLLM — Android On-device LLM Chat App

## Project Overview

An Android native application designed to run Large Language Models (LLMs) locally on-device using the MLC (Machine Learning Compilation) stack for high-performance inference on mobile hardware.

## Architecture

This is a **pure Android application** — it cannot run as a web server or in a browser. It requires:
- Android SDK (API 35 target, API 28 minimum)
- JDK 17+ (configured for JVM 21)
- Android device or emulator

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material3 |
| State | ViewModel + StateFlow |
| DI | Hilt 2.52 |
| Database | Room 2.6.1 |
| Settings | DataStore Preferences |
| Navigation | Navigation Compose |
| Logging | Timber |
| Inference Engine | MLC LLM / TVM (via `mlc4j` module) |
| Build System | Gradle 8.7 (Kotlin DSL) |

## Project Structure

```
app/                          Main Android application module
  src/main/java/com/example/localllm/
    engine/                   Inference abstraction layer
    ui/                       Compose screens + ViewModels (Chat, History, Settings, Benchmark)
    data/                     Room DB + DataStore + repositories
    di/                       Hilt DI modules
    mlc/                      MLC runtime configuration
  schemas/                    Room database schema exports

mlc4j/                        Native bridge library module
  src/main/java/ai/mlc/mlcllm/
    MLCEngine.kt              Request/streaming management over JSON FFI
    OpenAIProtocol.kt         OpenAI-compatible protocol definitions
    JSONFFIEngine.java        Native Java FFI bridge
  output/                     Native libraries (tvm4j_core.jar, arm64-v8a .so files)
  src/cpp/                    TVM runtime headers

docs/                         Documentation
download_model.py             Utility script for fetching model weights
```

## Gradle Commands

```bash
# List available tasks
./gradlew --no-daemon tasks --console=plain

# Build debug APK (requires Android SDK)
./gradlew --no-daemon assembleDebug --console=plain

# Run unit tests (requires Android SDK)
./gradlew --no-daemon test --console=plain

# Run lint checks
./gradlew --no-daemon lint --console=plain
```

## Replit Workflow

The configured workflow runs `./gradlew --no-daemon tasks --console=plain` to display available build tasks. This is a console-only project — no web preview is available since it's an Android application.

## Tool Calling Layer

A clean, extensible tool-calling architecture lives above the `InferenceEngine`:

```
domain/tools/
  Tool.kt                 Interface: name, description, keywords, sensitivity, execute()
  ToolResult.kt           Typed result: success, resultText, payload, errorMessage, refusalReason
  ToolRegistry.kt         Central registry populated via Hilt multibindings
  ToolConsentStore.kt     ToolConsentGate interface + DataStore-backed implementation
  ActionOrchestrator.kt   Tool dispatch + two-layer consent gating for SENSITIVE tools
  ToolCallClassifier.kt   Deterministic keyword classifier with sensitivity thresholds

data/tools/
  GetDeviceInfoTool.kt    PUBLIC    — Reads Build.*
  GetBatteryStatusTool.kt PUBLIC    — Reads ACTION_BATTERY_CHANGED sticky broadcast
  GetClipboardTool.kt     SENSITIVE — Reads ClipboardManager (PII-safe logging)
  ReadScreenTool.kt       SENSITIVE — Reads accessibility tree of foreground app

di/
  ToolsModule.kt          @IntoSet multibindings; add new tools here only

ui/tasks/
  TasksViewModel.kt       @HiltViewModel — delegates to ActionOrchestrator
  TasksScreen.kt          Compose screen exposing the available tools
```

### Privacy & consent model
SENSITIVE tools (clipboard, screen) require **two independent approvals**:
1. A persistent enable flag (DataStore `tool_consent`) the user toggles in the **Privacy** section of `SettingsScreen` via `SettingsViewModel.setSensitiveToolEnabled`. The list of toggles is built dynamically by enumerating tools whose `sensitivity == SENSITIVE` from `ToolRegistry`.
2. A per-process session approval held only in memory (`grantSessionApproval`), to be wired into the chat UI's refusal handler in a follow-up.

A `ToolResult.refusalReason` of `DISABLED_BY_USER` or `NEEDS_USER_APPROVAL` lets the UI distinguish a permission prompt from a real error.

## Inference Engine

- The `InferenceEngine` bound via Hilt is `FallbackInferenceEngine`, which tries the MLC native engine first (`@MlcEngine`) and automatically falls back to a deterministic fake (`@FakeEngine`) when native libs are missing or generation fails at runtime. Both delegates are injected by qualifier and held as the `InferenceEngine` interface — the fallback can therefore be unit-tested without touching real native code.
- **Cancellation is never swallowed** — `CancellationException` is re-thrown in both the `loadModel` and `generate` paths. Programmer errors (`IllegalStateException`, `IllegalArgumentException`) surface as `GenerationResponse.Error` instead of triggering a silent fallback.
- Native libraries live in `mlc4j/output/` and are required for actual inference.
- Full compilation (`assembleDebug`) requires a locally configured Android SDK.

## Database

Room schemas are exported (`exportSchema = true`). When bumping `@Database.version`:
- Prefer `autoMigrations` for purely additive changes.
- Use `AppDatabase.MIGRATIONS` for non-trivial changes (renames, type changes, splits).
- `fallbackToDestructiveMigration()` is intentionally **not** used — it would silently wipe user conversations and downloaded model metadata.
