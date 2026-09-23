package com.tannmenghong.tbchat.domain

import org.json.JSONArray
import org.json.JSONObject

enum class Modality { CHAT, IMAGE }

data class Artifact(
    val url: String,
    val sha256: String,
    val bytes: Long,
    val relativePath: String
)

data class CatalogModel(
    val id: String,
    val name: String,
    val version: String,
    val modality: Modality,
    val artifacts: List<Artifact>,
    val licenseName: String,
    val licenseUrl: String,
    val sourceUrl: String,
    val requiredRamGb: Int,
    val requiredStorageBytes: Long,
    val engine: JSONObject
)

data class ModelCatalog(val expiresAtEpochMs: Long, val models: List<CatalogModel>) {
    companion object {
        fun fromJson(json: String): ModelCatalog {
            val root = JSONObject(json)
            val models = root.getJSONArray("models").mapObjects { item ->
                val artifacts = item.getJSONArray("artifacts").mapObjects { a ->
                    Artifact(a.getString("url"), a.getString("sha256"), a.getLong("bytes"), a.getString("path"))
                }
                CatalogModel(
                    id = item.getString("id"), name = item.getString("name"), version = item.getString("version"),
                    modality = Modality.valueOf(item.getString("modality")), artifacts = artifacts,
                    licenseName = item.getString("licenseName"), licenseUrl = item.getString("licenseUrl"),
                    sourceUrl = item.getString("sourceUrl"), requiredRamGb = item.getInt("requiredRamGb"),
                    requiredStorageBytes = item.getLong("requiredStorageBytes"), engine = item.getJSONObject("engine")
                )
            }
            return ModelCatalog(root.getLong("expiresAtEpochMs"), models)
        }
    }
}

private fun <T> JSONArray.mapObjects(mapper: (JSONObject) -> T): List<T> =
    (0 until length()).map { mapper(getJSONObject(it)) }
