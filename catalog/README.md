# Signed model catalog

Host `catalog.json` and its detached Base64 Ed25519 signature as `catalog.json.sig` on GitHub Pages. The mobile app verifies the exact manifest bytes against its embedded public key before it displays any model.

Each release must contain immutable HTTPS artifact URLs, SHA-256 checksums calculated from those artifacts, exact byte sizes, license/source links, and mobile memory/storage requirements. Do not publish placeholder checksums or model files that require a Hugging Face login.

The Android app currently points to `https://tann-menghong.github.io/TBchat-catalog/catalog.json`; create that companion repository or change `CatalogRepository.manifestUrl` before release.
