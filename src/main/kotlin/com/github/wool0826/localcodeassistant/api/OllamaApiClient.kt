package com.github.wool0826.localcodeassistant.api

import com.google.gson.Gson
import com.intellij.openapi.vfs.VirtualFile
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class OllamaApiClient : ApiClient {
    private val client = OkHttpClient.Builder()
        .readTimeout(3, TimeUnit.MINUTES)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = "http://localhost:11434/api"
    private val gson = Gson()

    override fun isModelExist(modelName: String): Boolean {
        val response = client
            .newCall(
                Request
                    .Builder()
                    .url("$baseUrl/tags")
                    .get()
                    .build()
            )
            .execute()

        return response.use {
            val models = gson.fromJson(it.body?.string(), OllamaTagsResponse::class.java).models
            return@use models.any { model -> model.name == modelName }
        }
    }

    override fun downloadModel(modelName: String) {
        client
            .newCall(
                Request
                    .Builder()
                    .url("$baseUrl/pull")
                    .post(
                        gson
                            .toJson(OllamaPullingRequest(name = modelName))
                            .toRequestBody(mediaType)
                    )
                    .build()
            )
            .execute()
    }

    override fun autoComplete(targetFile: VirtualFile?, prompt: String): String {
        val metadata = "The name of the file is ${targetFile?.name}."
        val request = OllamaGenerateRequest(prompt = "$metadata\n$prompt")

        if (!isModelExist(request.model)) {
            downloadModel(request.model)
        }

        val response = client
            .newCall(
                Request
                    .Builder()
                    .url("$baseUrl/generate")
                    .post(
                        gson
                            .toJson(request)
                            .toRequestBody(mediaType)
                    )
                    .build()
            )
            .execute()

        return response.use {
            return@use gson.fromJson(it.body?.string(), OllamaGenerateResponse::class.java).response
        }
    }
}

data class OllamaGenerateRequest(
    val model: String = "llama3.1",
    val prompt: String,
    val stream: Boolean = false,
    val system: String = """
        You are a code assistant in an Intellij Plugin.
         
        I will send you code snippets where commented lines represent the user's requests, and the other lines are the code the user wants to refactor or review.
        You should write the review or refactored code according to the comments. If you need to explain something, write it as a comment.
        
        Using markdown syntax for structuring your response.
    """.trimIndent()
)

data class OllamaGenerateResponse(
    val model: String,
    val response: String,
    val done: Boolean,
)

data class OllamaTagsResponse(
    val models: List<OllamaModelResponse>,
)

data class OllamaPullingRequest(
    val name: String,
)

data class OllamaModelResponse(
    val name: String,
)
