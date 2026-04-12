import os
import sys
import subprocess
import urllib.request
import tarfile
from pathlib import Path

# Project structure
ROOT_DIR = Path(__file__).parent.parent
OUTPUT_DIR = ROOT_DIR / "mlc4j" / "output" / "arm64-v8a"

# GitHub source for compiled binaries
GH_BINARY_REPO = "https://github.com/mlc-ai/binary-mlc-llm-libs/raw/main"

# Models and their expected library names in the project
# Map project model_lib name to GitHub path and tar name
MODELS = {
    "qwen3_q0f16_e709b04052d95e24b38d40e4259e1f14": {
        "gh_path": "Qwen2-0.5B-Instruct",
        "tar_name": "Qwen2-0.5B-Instruct-q0f16-android.tar",
        "so_name_in_tar": "Qwen2-0.5B-Instruct-q0f16-android.so"
    },
    "gemma_2_2b_q4": {
        "gh_path": "gemma-2b-it",
        "tar_name": "gemma-2b-it-q4f16_1-android.tar",
        "so_name_in_tar": "gemma-2b-it-q4f16_1-android.so"
    }
}

def download_and_extract(model_lib_name, info):
    print(f"\n--- Processing {model_lib_name} ---")
    
    # Check if already exists
    target_so = OUTPUT_DIR / f"lib{model_lib_name}.so"
    if target_so.exists():
        print(f"  [OK] Library already exists: {target_so.name}")
        return

    # Download URL
    url = f"{GH_BINARY_REPO}/{info['gh_path']}/{info['tar_name']}"
    temp_tar = OUTPUT_DIR / info['tar_name']
    
    print(f"  [..] Downloading tar from GitHub...")
    try:
        urllib.request.urlretrieve(url, str(temp_tar))
        print(f"  [OK] Downloaded {info['tar_name']}")
    except Exception as e:
        print(f"  [X] Failed to download from {url}: {e}")
        return

    # Extract
    print(f"  [..] Extracting .so file...")
    try:
        with tarfile.open(temp_tar) as tar:
            # Find the so file in the tar
            so_member = None
            for member in tar.getmembers():
                if member.name.endswith(".so"):
                    so_member = member
                    break
            
            if not so_member:
                print(f"  [X] No .so file found in tarball!")
                return
                
            tar.extract(so_member, path=OUTPUT_DIR)
            extracted_path = OUTPUT_DIR / so_member.name
            
            # Move and rename to the target name
            if extracted_path.exists():
                if target_so.exists(): target_so.unlink()
                extracted_path.rename(target_so)
                print(f"  [OK] Extracted and installed as lib{model_lib_name}.so")
            
    except Exception as e:
        print(f"  [X] Extraction failed: {e}")
    finally:
        if temp_tar.exists():
            temp_tar.unlink()

def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    
    print(f"Fetching MLC model libraries for Android (arm64-v8a)")
    print(f"Target directory: {OUTPUT_DIR}\n")

    for lib_name, info in MODELS.items():
        download_and_extract(lib_name, info)

    print("\nDone. You can now build the project in Android Studio.")

if __name__ == "__main__":
    main()
