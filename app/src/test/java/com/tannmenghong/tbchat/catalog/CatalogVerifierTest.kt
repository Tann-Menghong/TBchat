package com.tannmenghong.tbchat.catalog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class CatalogVerifierTest {
    @Test fun acceptsOnlyAnExactSignedManifest() {
        val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val manifest = "{\"models\":[]}"
        val signature = Signature.getInstance("Ed25519").apply { initSign(keys.private); update(manifest.toByteArray()) }.sign()
        val verifier = CatalogVerifier(Base64.getEncoder().encodeToString(keys.public.encoded))
        assertTrue(verifier.verify(manifest, Base64.getEncoder().encodeToString(signature)))
        assertFalse(verifier.verify("$manifest ", Base64.getEncoder().encodeToString(signature)))
    }
}
