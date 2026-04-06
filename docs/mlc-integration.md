# MLC LLM Android Integration Guide

## Current State of the Project

The application currently relies on the `FakeInferenceEngine` as its default and active backend for the `InferenceEngine` contract. This ensures the app is stable, testable, and functional (for UI and database testing) without requiring large external Native (C++) dependencies. 

The UI chat flows, database persistence, model management, and all critical logic are built out and tested against the fake backend. This allows developers to work safely on the app without dealing with the overhead of Android NDK toolchains.

### Why is MLC not activated yet?

MLC LLM is a complex project that leverages Apache TVM, Rust, Vulkan, and OpenCL to run optimized LLM inferences directly on mobile GPUs. It does **not** provide a pre-compiled `.aar` on Maven Central. 

Building the required native libraries (`libtvm4j_runtime_packed.so`) and Java bindings (`mlc4j`) requires a hefty toolchain (Android NDK, CMake, Rust, Python, etc.) that is ideally run on a PC/Mac. Because this environment currently runs on Android (via Termux), attempting a full local build of MLC LLM would likely fail due to memory limits and toolchain mismatches.

Therefore, the integration is deliberately kept at the **Scaffold** stage.

---

## Integration Phases Explained

To clarify the terminology used in this repository:

1. **Scaffold Integration (Current Status)**
   - The Kotlin interface (`MLCInferenceEngine.kt`) exists.
   - It correctly implements the `InferenceEngine` contract.
   - It contains `TODO` blocks showing exactly where the native MLC calls (e.g., `MLCEngine` initialization, `chat.completions.create()`) will be placed.
   - It is entirely safe to compile (`Compile-Ready`), but executing it currently returns placeholder text since there is no C++ engine beneath it.

2. **Compile-Ready Integration**
   - The application code can build cleanly without errors.
   - All dependencies (like Coroutines, Flow, Room, and Hilt) are structurally sound.

3. **Runtime-Ready Integration (Pending)**
   - The `mlc4j` module is physically present in the project.
   - The Application can load the native libraries without crashing.
   - The `Hilt` binding in `di/AppModule.kt` is switched to use `MLCInferenceEngine`.
   - The App streams real tokens from an actual language model running on the device GPU.

---

## Preparing to Activate MLC LLM

When you are ready to bring in the real MLC Engine, follow these steps using a PC/Mac:

### 1. Build `mlc4j` Externally
1. Clone the official MLC LLM repository:
   ```bash
   git clone --recursive https://github.com/mlc-ai/mlc-llm.git
   cd mlc-llm/android
   ```
2. Run the preparation script (make sure you have NDK and CMake installed):
   ```bash
   ./prepare_libs.sh
   ```
   This will generate the TVM runtime and Java bindings within the `mlc-llm/android/mlc4j` folder.

### 2. Import into this Project
1. Copy the generated `mlc4j` folder into the root directory of this Android project.
2. Open `settings.gradle.kts` and uncomment the lines:
   ```kotlin
   include(":mlc4j")
   project(":mlc4j").projectDir = file("mlc4j")
   ```
3. Open `app/build.gradle.kts` and uncomment the dependency:
   ```kotlin
   implementation(project(":mlc4j"))
   ```

### 3. Update the Source Code
1. Open `app/src/main/java/com/example/localllm/engine/MLCInferenceEngine.kt`.
2. Remove the `Any` placeholders and replace them with the actual `ai.mlc.mlcllm.MLCEngine` initialization.
3. Update the `generate` function to hook into the `mlc4j` async token generation stream.
4. Finally, open `app/src/main/java/com/example/localllm/di/AppModule.kt` and change the Hilt binding from `FakeInferenceEngine` to `MLCInferenceEngine`.

```kotlin
// di/AppModule.kt
@Binds
@Singleton
abstract fun bindInferenceEngine(engine: MLCInferenceEngine): InferenceEngine
```

Once these steps are completed, your application will transition from **Scaffold** to **Runtime-Ready**, utilizing the full power of MLC LLM on-device inference.
