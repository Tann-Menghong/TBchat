# TBchat

TBchat is an Android 10+ offline AI studio for `arm64-v8a` flagship phones. It has local chat and text-to-image workflows, a signed remote model catalog, resumable verified downloads, and local-only history.

## What is included

- Kotlin/Jetpack Compose app with a setup-first model library, private chat, image roadmap, and settings screens.
- A built-in Qwen3 4B Q4_K_M starter download (2.50 GB) with the source's published SHA-256 verification; the model list never depends on a remote catalog to render.
- Room-backed local conversations and image metadata, plus a user-controlled clear-history action.
- Ed25519 verification for detached catalog signatures, catalog expiration checks, device compatibility checks, license acknowledgement, and SHA-256-verified resumable model downloads.
- Stable interfaces for a llama.cpp GGUF chat bridge and an ONNX Runtime diffusion pipeline.
- GitHub Actions debug APK build and Gradle wrapper files.

## Before releasing

1. Create `Tann-Menghong/TBchat-catalog` as a GitHub Pages repository and publish a signed `catalog.json` plus `catalog.json.sig` as described in [catalog/README.md](catalog/README.md).
2. Vendor a pinned llama.cpp release under `app/src/main/cpp` and implement the JNI bridge, then complete the ONNX diffusion pipeline for the exact tested Stable Diffusion package. Image generation is intentionally not advertised as ready until that work is complete.
4. Add a release keystore outside this repository and configure Android signing before producing a release APK.

The app does not include model weights. Models must be hosted as public, immutable HTTPS files with verified size/checksum and license metadata; never include license-gated artifacts in the catalog.
