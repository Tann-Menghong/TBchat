package com.tannmenghong.tbchat.catalog

import android.content.Context
import com.tannmenghong.tbchat.domain.ModelCatalog
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CatalogRepository(private val context: Context, private val verifier: CatalogVerifier) {
    private val manifestUrl = "https://tann-menghong.github.io/TBchat-catalog/catalog.json"
    private val signatureUrl = "$manifestUrl.sig"
    private val manifestCache = context.filesDir.resolve("catalog.json")

    suspend fun refresh(): Result<ModelCatalog> = withContext(Dispatchers.IO) { runCatching {
        val manifest = URL(manifestUrl).readText()
        val signature = URL(signatureUrl).readText().trim()
        require(verifier.verify(manifest, signature)) { "Catalog signature is invalid." }
        val catalog = ModelCatalog.fromJson(manifest)
        require(catalog.expiresAtEpochMs > System.currentTimeMillis()) { "Catalog has expired." }
        manifestCache.writeText(manifest)
        catalog
    } }

    fun cached(): Result<ModelCatalog> = runCatching {
        require(manifestCache.exists()) { "No verified catalog has been downloaded yet." }
        val catalog = ModelCatalog.fromJson(manifestCache.readText())
        require(catalog.expiresAtEpochMs > System.currentTimeMillis()) { "Cached catalog has expired." }
        catalog
    }
}
