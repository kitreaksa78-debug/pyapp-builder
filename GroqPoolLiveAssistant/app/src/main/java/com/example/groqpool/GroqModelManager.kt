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

    // Fallback list in case /v1/models cannot be parsed, ordered by current Groq vision model naming conventions
    private val defaultVisionCandidates = listOf(
        "meta-llama/llama-4-scout-17b-16e-instruct",
        "meta-llama/llama-4-maverick-17b-128e-instruct",
        "llama-3.2-90b-vision-preview",
        "llama-3.2-11b-vision-preview",
        "llava-v1.5-7b-4096-preview"
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
                    if (id.isNotBlank() && active) {
                        allIds.add(id)
                    }
                }
                // Prioritize vision / multimodal models available on this account
                val visionKeywords = listOf("vision", "llama-4", "scout", "maverick", "llava", "pixtral", "qwen-vl", "gemma-3")
                val excludeKeywords = listOf("whisper", "guard", "tts", "embed", "distil")

                val filtered = allIds.filter { id ->
                    val lower = id.lowercase()
                    visionKeywords.any { lower.contains(it) } && excludeKeywords.none { lower.contains(it) }
                }.sortedWith(compareByDescending<String> {
                    val l = it.lowercase()
                    when {
                        l.contains("llama-4-scout") -> 100
                        l.contains("llama-4-maverick") -> 90
                        l.contains("vision") -> 80
                        l.contains("llama-4") -> 70
                        else -> 50
                    }
                })

                if (filtered.isNotEmpty()) {
                    cachedModels = filtered
                    lastFetchTime = now
                    if (currentIndex >= cachedModels.size) currentIndex = 0
                    return cachedModels
                }
            }
        } catch (_: Exception) {
            // Fall through to defaults if offline or temporary failure
        }

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

    /**
     * Sends a vision/chat request and automatically rotates through available free models
     * if a model hits Rate Limit (429), doesn't exist (404), or doesn't support images (400).
     */
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

                // Auto-rotate on Rate Limit (429), Not Found (404), Bad Request / unsupported image (400), or Server Overloaded (503)
                if (code == 429 || code == 404 || code == 400 || code == 503 || code == 502) {
                    lastError = "HTTP $code ($model): ${response.take(120)}"
                    val nextModel = rotateToNextModel(apiKey)
                    onStatusUpdate?.invoke("Rate limit/error on $model -> switching to $nextModel")
                    continue
                } else {
                    // Auth error (401) or invalid key -> no point rotating
                    throw Exception("Groq Auth/API Error ($code): ${response.take(150)}")
                }
            } catch (e: Exception) {
                if (e.message?.startsWith("Groq Auth/API Error") == true) throw e
                lastError = e.message ?: "Network error"
                rotateToNextModel(apiKey)
            }
        }
        throw Exception("All models busy/failed. Last: $lastError")
    }
}
