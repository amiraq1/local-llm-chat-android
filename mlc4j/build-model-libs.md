# MLC Model Library Bundling Guide

## Directory Structure

```
mlc4j/output/arm64-v8a/
├── libtvm4j_runtime_packed.so      ← TVM runtime (already present)
├── libqwen3_q0f16_*.so             ← Qwen3-0.6B compiled model lib
├── libgemma_2_2b_q4.so             ← Gemma-2-2B compiled model lib
└── lib<model_lib>.so               ← Any additional model libs
```

## How to add a model library

1. Compile the model library using `mlc_llm` CLI for Android/OpenCL target
2. Place the resulting `.so` in `mlc4j/output/arm64-v8a/`
3. Ensure the filename matches `lib<model_lib>.so` where `<model_lib>` matches
   the `mlcModelLib` / `model_lib` value in your config

## Build command example (from MLC-LLM docs)

```bash
mlc_llm compile \
  --model HF_MODEL_PATH \
  --quantization q4f16_1 \
  --device android \
  --output mlc4j/output/arm64-v8a/lib<model_lib>.so
```
