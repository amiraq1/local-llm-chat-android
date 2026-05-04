# Phi 3 Mini 4K Instruct MLC Android

`Phi-3-mini-4k-instruct-q4f16_1-MLC` is registered in the app catalog as a
pending MLC target. It is intentionally not exposed as a runnable model until
the Android native runtime is repackaged with the matching `model_lib`.

Official MLC model:

```text
https://huggingface.co/mlc-ai/Phi-3-mini-4k-instruct-q4f16_1-MLC
```

The upstream `mlc-chat-config.json` reports `q4f16_1` quantization and a
4,096-token context window. The package template lowers `prefill_chunk_size`
for mobile packaging.

## Package

Use the package template at:

```text
scripts/mlc-package-phi3-mini-4k-instruct.json
```

With an MLC LLM Android packaging environment, copy or use that file as the
active `mlc-package-config.json`, then run:

```bash
mlc_llm package
```

When packaging finishes, copy the generated `model_lib` for
`Phi-3-mini-4k-instruct-q4f16_1-MLC` into:

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

The current bundled runtime does not contain this Phi 3 model library, so
adding only the catalog entry is not enough to run the model.
