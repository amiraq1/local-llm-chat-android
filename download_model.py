from huggingface_hub import snapshot_download
import os

model_id = "mlc-ai/Qwen2.5-0.5B-Instruct-q4f16_1-MLC"
local_dir = r"d:\Qwen3-Model"

print(f"Downloading {model_id} to {local_dir}...")
snapshot_download(repo_id=model_id, local_dir=local_dir, local_dir_use_symlinks=False)
print("Download complete.")
