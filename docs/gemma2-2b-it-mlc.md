# Gemma 2 2B It MLC Android

`gemma-2-2b-it-q4f16_1-MLC` is already bundled in the Android runtime as
`gemma_2_2b_q4`. The app catalog keeps the existing local model slug for
install compatibility, but displays the model by its real upstream name.

Official MLC model:

```text
https://huggingface.co/mlc-ai/gemma-2-2b-it-q4f16_1-MLC
```

The upstream `mlc-chat-config.json` reports `q4f16_1` quantization and a
4,096-token context window.

## Repackage

Use the package template at:

```text
scripts/mlc-package-gemma2-2b-it.json
```

With an MLC LLM Android packaging environment, copy or use that file as the
active `mlc-package-config.json`, then run:

```bash
mlc_llm package
```

When packaging finishes, preserve the existing app catalog `slug` unless you
also migrate installed model records on device.

## Validation

```bash
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug --console=plain
```
