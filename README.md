# TBchat

TBchat is an Android 10+ offline AI studio for `arm64-v8a` flagship phones. It has local chat and text-to-image workflows, a signed remote model catalog, resumable verified downloads, and local-only history.

## What is included

- Kotlin/Jetpack Compose app with Chat, Create Image, Models, and Settings screens.
- Room-backed local conversations and image metadata, plus a user-controlled clear-history action.
- Ed25519 verification for detached catalog signatures, catalog expiration checks, device compatibility checks, license acknowledgement, and SHA-256-verified resumable model downloads.
- Stable interfaces for a llama.cpp GGUF chat bridge and an ONNX Runtime diffusion pipeline.
- GitHub Actions debug APK build and Gradle wrapper files.

## Before releasing

1. Create `Tann-Menghong/TBchat-catalog` as a GitHub Pages repository and publish a signed `catalog.json` plus `catalog.json.sig` as described in [catalog/README.md](catalog/README.md).
2. Replace `CATALOG_PUBLIC_KEY` in `app/src/main/java/com/tannmenghong/tbchat/MainActivity.kt` with the X.509-encoded Base64 Ed25519 public key used to sign that catalog.
3. Vendor a pinned llama.cpp release under `app/src/main/cpp` and implement the JNI bridge, then complete the ONNX diffusion pipeline for the exact tested Stable Diffusion package. The present interfaces deliberately fail closed until a verified model and engine are installed.
4. Add a release keystore outside this repository and configure Android signing before producing a release APK.

The app does not include model weights. Models must be hosted as public, immutable HTTPS files with verified size/checksum and license metadata; never include license-gated artifacts in the catalog.
