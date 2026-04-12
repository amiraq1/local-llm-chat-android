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

## Notes

- The `InferenceEngine` bound via Hilt is `MLCInferenceEngine` (not `FakeInferenceEngine`)
- `FakeInferenceEngine` remains available for isolated testing/development
- Native libraries are in `mlc4j/output/` and are required for actual inference
- Full compilation (`assembleDebug`) requires a locally configured Android SDK
