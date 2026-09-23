package com.tannmenghong.tbchat.catalog

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Verifies a detached Ed25519 signature over the exact UTF-8 manifest bytes. */
class CatalogVerifier(private val publicKeyBase64: String) {
    fun verify(manifest: String, signatureBase64: String): Boolean = runCatching {
        val key = KeyFactory.getInstance("Ed25519").generatePublic(
            X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64))
        )
        Signature.getInstance("Ed25519").run {
            initVerify(key)
            update(manifest.toByteArray(Charsets.UTF_8))
            verify(Base64.getDecoder().decode(signatureBase64))
        }
    }.getOrDefault(false)
}
