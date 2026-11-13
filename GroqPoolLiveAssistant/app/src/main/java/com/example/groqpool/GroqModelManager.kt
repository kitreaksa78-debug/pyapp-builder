package com.example.groqpool

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GroqModelManager {
    @Volatile
    private var cachedModels: List<String> = emptyList()
    @Volatile
    private var currentIndex: Int = 0
    @Volatile
    private var lastFetchTime: Long = 0L

    // Clean list of active, current free vision models on Groq
    private val defaultVisionCandidates = listOf(
        "meta-llama/llama-3.2-90b-vision-preview",
        "meta-llama/llama-3.2-11b-vision-preview"
    )

    @Synchronized
    fun getActiveModels(apiKey: String, forceRefresh: Boolean = false): List<String> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedModels.isNotEmpty() && (now - lastFetchTime) < 300_000L) {
            return cachedModels
        }
        try {
            val conn = (URL("https://api.groq.com/openai/v1/models").openConnection() as HttpURLConnection)
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json")

            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().readText()
                val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
                val allIds = mutableListOf<String>()
                for (i in 0 until data.length()) {
                    val obj = data.optJSONObject(i) ?: continue
                    val id = obj.optString("id", "").trim()
                    val active = obj.optBoolean("active", true)
                    // Filter out decommissioned or non-vision models
                    val lower = id.lowercase()
                    if (id.isNotBlank() && active && !lower.contains("decommissioned") && !lower.contains("llava") && !lower.contains("whisper") && !lower.contains("guard") && !lower.contains("embed")) {
                        if (lower.contains("vision") || lower.contains("llama-3.2")) {
                            allIds.add(id)
                        }
                    }
                }
                if (allIds.isNotEmpty()) {
                    cachedModels = allIds.distinct()
                    lastFetchTime = now
                    if (currentIndex >= cachedModels.size) currentIndex = 0
                    return cachedModels
                }
            }
        } catch (_: Exception) {}

        if (cachedModels.isEmpty()) {
            cachedModels = defaultVisionCandidates
        }
        return cachedModels
    }

    @Synchronized
    fun getCurrentModel(apiKey: String): String {
        val list = getActiveModels(apiKey)
        if (list.isEmpty()) return defaultVisionCandidates.first()
        if (currentIndex >= list.size) currentIndex = 0
        return list[currentIndex]
    }

    @Synchronized
    fun rotateToNextModel(apiKey: String): String {
        val list = getActiveModels(apiKey)
        if (list.isEmpty()) return defaultVisionCandidates.first()
        currentIndex = (currentIndex + 1) % list.size
        return list[currentIndex]
    }

    fun sendWithAutoFailover(
        apiKey: String,
        messages: JSONArray,
        maxTokens: Int = 500,
        temperature: Double = 0.1,
        onStatusUpdate: ((String) -> Unit)? = null
    ): Pair<String, String> {
        val models = getActiveModels(apiKey)
        val attempts = models.size.coerceAtLeast(1)
        var lastError = "No available models"

        for (attempt in 0 until attempts) {
            val model = getCurrentModel(apiKey)
            onStatusUpdate?.invoke("Using model: $model")
            try {
                val conn = (URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection)
                conn.requestMethod = "POST"
                conn.connectTimeout = 20000
                conn.readTimeout = 35000
                conn.doOutput = true
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.setRequestProperty("Content-Type", "application/json")

                val payload = JSONObject().apply {
                    put("model", model)
                    put("temperature", temperature)
                    put("max_tokens", maxTokens)
                    put("messages", messages)
                }

                conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                val code = conn.responseCode
                val response = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.readText() ?: ""

                if (code in 200..299) {
                    return Pair(model, response)
                }

                if (code == 429 || code == 404 || code == 400 || code == 503 || code == 502) {
                    lastError = "HTTP $code ($model)"
                    rotateToNextModel(apiKey)
                    continue
                } else {
                    throw Exception("Groq Error ($code): ${response.take(120)}")
                }
            } catch (e: Exception) {
                if (e.message?.startsWith("Groq Error") == true) throw e
                lastError = e.message ?: "Network error"
                rotateToNextModel(apiKey)
            }
        }
        throw Exception("All models failed. Last: $lastError")
    }
}
