package com.tannmenghong.tbchat.catalog

import com.tannmenghong.tbchat.domain.Artifact
import com.tannmenghong.tbchat.domain.CatalogModel
import com.tannmenghong.tbchat.domain.Modality
import com.tannmenghong.tbchat.domain.ModelCatalog
import org.json.JSONObject

/** Built into the APK so a fresh install always has a safe, usable catalog. */
object StarterCatalog {
    val catalog = ModelCatalog(
        expiresAtEpochMs = Long.MAX_VALUE,
        models = listOf(
            CatalogModel(
                id = "qwen3-4b-q4-k-m",
                name = "Qwen3 4B Instruct",
                version = "Q4_K_M",
                modality = Modality.CHAT,
                artifacts = listOf(Artifact(
                    url = "https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf?download=true",
                    sha256 = "7485fe6f11af29433bc51cab58009521f205840f5b4ae3a32fa7f92e8534fdf5",
                    bytes = 2_497_280_256L,
                    relativePath = "Qwen3-4B-Q4_K_M.gguf"
                )),
                licenseName = "Apache-2.0",
                licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0",
                sourceUrl = "https://huggingface.co/Qwen/Qwen3-4B-GGUF",
                requiredRamGb = 8,
                requiredStorageBytes = 3_500_000_000L,
                engine = JSONObject("{\"format\":\"gguf\",\"contextLength\":4096}")
            )
        )
    )
}
