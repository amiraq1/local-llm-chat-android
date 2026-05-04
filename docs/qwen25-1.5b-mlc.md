# Qwen 2.5 1.5B Instruct MLC Android

`Qwen2.5-1.5B-Instruct-q4f16_1-MLC` is registered in the app catalog as a
pending MLC target. It is intentionally not exposed as a runnable model until
the Android native runtime is repackaged with the matching `model_lib`.

Official MLC model:

```text
https://huggingface.co/mlc-ai/Qwen2.5-1.5B-Instruct-q4f16_1-MLC
```

The upstream `mlc-chat-config.json` reports `q4f16_1` quantization and a
32,768-token context window.

## Package

Use the package template at:

```text
scripts/mlc-package-qwen25-1.5b.json
```

With an MLC LLM Android packaging environment, copy or use that file as the
active `mlc-package-config.json`, then run:

```bash
mlc_llm package
```

When packaging finishes, copy the generated `model_lib` for
`Qwen2.5-1.5B-Instruct-q4f16_1-MLC` into:

```text
app/src/main/assets/model_catalog.json
mlc4j/src/main/assets/mlc-app-config.json
```

Also replace `mlc4j/output` with the generated Android runtime artifacts.

## Validation

Before enabling the model in a release build:

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug --console=plain
```

The current bundled runtime does not contain this Qwen2.5 model library, so
adding only the catalog entry is not enough to run the model.
