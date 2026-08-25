package com.campusai.core.localai

import android.content.Context
import android.net.Uri
import org.json.JSONObject

data class LocalModelFile(val path: String, val size: Long, val sha256: String)
data class LocalMnnRuntime(val version: String, val commit: String, val archiveSha256: String)

data class LocalModelManifest(
    val schemaVersion: Int,
    val id: String,
    val displayName: String,
    val version: String,
    val repository: String,
    val revision: String,
    val sourceUrl: String,
    val license: String,
    val quantization: String,
    val totalBytes: Long,
    val safetyMarginBytes: Long,
    val minimumApi: Int,
    val minimumRamBytes: Long,
    val supportedAbis: List<String>,
    val contextTokens: Int,
    val maxOutputTokens: Int,
    val runtime: LocalMnnRuntime,
    val files: List<LocalModelFile>,
) {
    init {
        require(schemaVersion == 1)
        require(id.matches(Regex("[a-z0-9._-]+")))
        require(repository in TRUSTED_REPOSITORIES)
        require(revision.matches(Regex("[0-9a-f]{40}")))
        require(sourceUrl == "https://huggingface.co/$repository")
        require(files.isNotEmpty() && files.sumOf { it.size } == totalBytes)
        files.forEach { file ->
            require(file.path.matches(Regex("[A-Za-z0-9._-]+")))
            require(file.size > 0)
            require(file.sha256.matches(Regex("[0-9a-f]{64}")))
        }
    }

    fun downloadUrl(file: LocalModelFile): String = Uri.Builder()
        .scheme("https")
        .authority("huggingface.co")
        .appendPath(repository.substringBefore('/'))
        .appendPath(repository.substringAfter('/'))
        .appendPath("resolve")
        .appendPath(revision)
        .appendPath(file.path)
        .build()
        .toString()

    companion object {
        const val DEFAULT_MODEL_ID = "qwen3.5-4b-mnn"
        private val ASSET_PATHS = mapOf(
            DEFAULT_MODEL_ID to "local_models/qwen3_5_4b_mnn.json",
            "qwen3.5-2b-mnn" to "local_models/qwen3_5_2b_mnn.json",
        )
        val TRUSTED_REPOSITORIES = setOf(
            "taobao-mnn/Qwen3.5-4B-MNN",
            "taobao-mnn/Qwen3.5-2B-MNN",
        )

        fun availableModelIds(): Set<String> = ASSET_PATHS.keys

        fun load(context: Context, modelId: String = DEFAULT_MODEL_ID): LocalModelManifest = parse(
            context.assets.open(ASSET_PATHS[modelId] ?: error("Unknown local model: $modelId"))
                .bufferedReader().use { it.readText() },
        )

        fun parse(raw: String): LocalModelManifest {
            val json = JSONObject(raw)
            val runtime = json.getJSONObject("runtime")
            val fileArray = json.getJSONArray("files")
            val files = buildList {
                repeat(fileArray.length()) {
                    val file = fileArray.getJSONObject(it)
                    add(LocalModelFile(file.getString("path"), file.getLong("size"), file.getString("sha256")))
                }
            }
            val abiArray = json.getJSONArray("supportedAbis")
            val abis = buildList { repeat(abiArray.length()) { add(abiArray.getString(it)) } }
            return LocalModelManifest(
                schemaVersion = json.getInt("schemaVersion"),
                id = json.getString("id"),
                displayName = json.getString("displayName"),
                version = json.getString("version"),
                repository = json.getString("repository"),
                revision = json.getString("revision"),
                sourceUrl = json.getString("sourceUrl"),
                license = json.getString("license"),
                quantization = json.getString("quantization"),
                totalBytes = json.getLong("totalBytes"),
                safetyMarginBytes = json.getLong("safetyMarginBytes"),
                minimumApi = json.getInt("minimumApi"),
                minimumRamBytes = json.getLong("minimumRamBytes"),
                supportedAbis = abis,
                contextTokens = json.getInt("contextTokens"),
                maxOutputTokens = json.getInt("maxOutputTokens"),
                runtime = LocalMnnRuntime(runtime.getString("version"), runtime.getString("commit"), runtime.getString("archiveSha256")),
                files = files,
            )
        }
    }
}
