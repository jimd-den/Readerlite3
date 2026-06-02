package com.example.data.gateway

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AiGateway {
    private const val TAG = "AiGateway"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun rewriteChapter(
        originalText: String,
        rewriteStyle: String,
        customPrompt: String? = null,
        forceSimulation: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val hasValidKey = apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY" && !apiKey.contains("placeholder", ignoreCase = true)

        if (forceSimulation || !hasValidKey) {
            if (!forceSimulation && !hasValidKey) {
                throw IllegalArgumentException("No valid Gemini API key found in your environment! Please set your GEMINI_API_KEY in the Google AI Studio Secrets panel, or toggle 'Enable Demo Simulation' on.")
            }
            Log.w(TAG, "Using beautiful local simulation.")
            return@withContext simulateRewrite(originalText, customPrompt ?: rewriteStyle)
        }

        val prompt = if (!customPrompt.isNullOrBlank()) {
            """
                You are a master educator design genius like Shigeru Miyamoto.
                The user wants you to rewrite/process this chapter text using the following custom instruction/prompt:
                
                Custom Instruction / Prompt:
                "$customPrompt"
                
                Requirements:
                1. Keep it highly engaging and clear.
                2. Divide the rewritten content with clear structural subheadings using markdown headers (e.g., "## Subheading Name" or "Chapter X: Title").
                3. Ensure each sentence is impactful and complete, because the user reads this one sentence at a time.
                4. Retain all major concepts but make them effortless to read.
                
                Original Text Context:
                $originalText
            """.trimIndent()
        } else {
            """
                You are a master educator design genius like Shigeru Miyamoto. 
                Rewrite the following educational text into the specified style: "$rewriteStyle".
                Requirements:
                1. Keep it highly engaging and clear.
                2. Divide with clear structural subheadings using "## Subheading Name" or "Chapter X: Title".
                3. Ensure each sentence is impactful, because the user reads this one sentence at a time.
                4. Retain all major concepts but make them effortless to read.
                
                Original Text:
                $originalText
            """.trimIndent()
        }

        try {
            // Build Gemini request body
            val requestJson = JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
            }

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "API call failed with code: ${response.code} body: $responseBodyStr")
                    throw Exception("Google Gemini API call failed with code ${response.code}. Service output: $responseBodyStr")
                }

                val responseJson = JSONObject(responseBodyStr)
                val text = responseJson
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                
                return@withContext text
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating AI rewrite", e)
            throw e
        }
    }

    private fun simulateRewrite(originalText: String, styleOrPrompt: String): String {
        val displayStyle = if (styleOrPrompt.length > 50) styleOrPrompt.take(47) + "..." else styleOrPrompt
        return when {
            styleOrPrompt.contains("Socratic", ignoreCase = true) -> """
                # Chapter Socratic Query: $displayStyle
                ## Why do we Survey First?
                Have you ever wondered why we read chapters from start to finish without pausing?
                Does an explorer enter a dungeon without looking at the geographic blueprints?
                By surveying the headings first, we build the scaffolding of the mind.
                What happens to a building constructed without a blueprint? It collapses under the weight of details.
                
                ## The Power of the Question
                What is a Heading if not an unasked question waiting for a light?
                By transforming headlines into direct, searching questions, we awaken our natural intelligence.
                How can we record meaning unless we are actively seeking an answer to our own query?
                The note you write is the prize of a question answered.
            """.trimIndent()
            
            styleOrPrompt.contains("Elementary", ignoreCase = true) || styleOrPrompt.contains("Simple", ignoreCase = true) -> """
                # Chapter Simple Summary: $displayStyle
                ## Starting with a Clear Mind
                Let's make this simple: Learning is like playing a video game.
                Before you run into a level, you look at the total map to see what is ahead.
                This is exactly what "Surveying" means.
                If you look at the outline first, you will not get lost.
                
                ## Asking Great Questions
                Next, you turn headings into little puzzles or questions.
                For example, instead of reading "Continuous Memory Allocation", you ask, "How does memory stay in a neat row?"
                This keeps your curiosity active, just like scouting for secret treasures.
                Every paragraph becomes an answer you are actively hunting for!
            """.trimIndent()

            else -> """
                # AI Simulation: $displayStyle
                ## SQR5 Adapter Block
                Using custom instruction: "$displayStyle"
                Here is the adapted chapter content:
                - **Concept 1: Active Surveying**: Always outline your chapters so you have anchors of knowledge.
                - **Concept 2: Generative Prompts**: Actively form Socratic connections around complex definitions.
                - **Concept 3: Sentinel Checking**: Double check your summaries and errors to sharpen recall.
                - **Concept 4: Spaced Recall**: Return to notes in your AI Library repeatedly.
            """.trimIndent()
        }
    }

    suspend fun fetchOpenRouterModels(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/models")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext DEFAULT_ROUTER_MODELS
                val body = response.body?.string() ?: return@withContext DEFAULT_ROUTER_MODELS
                val json = JSONObject(body)
                val data = json.getJSONArray("data")
                val list = mutableListOf<Pair<String, String>>()
                for (i in 0 until data.length()) {
                    val obj = data.getJSONObject(i)
                    val id = obj.getString("id")
                    val name = obj.optString("name", id)
                    list.add(Pair(id, name))
                }
                if (list.isEmpty()) DEFAULT_ROUTER_MODELS else list
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching OpenRouter models", e)
            DEFAULT_ROUTER_MODELS
        }
    }

    suspend fun rewriteChapterOpenRouter(
        apiKey: String,
        modelId: String,
        originalText: String,
        style: String
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
            You are a master educator design genius like Shigeru Miyamoto. 
            Rewrite the following educational text into the specified style: "$style".
            Requirements:
            1. Keep it highly engaging and clear.
            2. Divide with clear structural subheadings using "## Subheading Name" or "Chapter X: Title".
            3. Ensure each sentence is impactful, because the user reads this one sentence at a time.
            4. Retain all major concepts but make them effortless to read.
            
            Original Text:
            $originalText
        """.trimIndent()

        try {
            val requestJson = JSONObject().apply {
                put("model", modelId)
                put("messages", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
            }

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("HTTP-Referer", "https://ai.studio.build")
                .header("X-Title", "Effortless SQ5R")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "OpenRouter failed: code=${response.code} body=$responseBodyStr")
                    throw Exception("OpenRouter request failed: code ${response.code}: $responseBodyStr")
                }

                val responseJson = JSONObject(responseBodyStr)
                val text = responseJson
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                
                return@withContext text
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in OpenRouter generation", e)
            throw e
        }
    }

    private val DEFAULT_ROUTER_MODELS = listOf(
        Pair("google/gemini-2.5-flash", "Gemini 2.5 Flash"),
        Pair("meta-llama/llama-3-8b-instruct:free", "Llama 3 8B Instruct (Free)"),
        Pair("mistralai/mistral-7b-instruct:free", "Mistral 7B Instruct (Free)"),
        Pair("microsoft/phi-3-medium-128k-instruct:free", "Phi 3 Medium (Free)"),
        Pair("openrouter/auto", "Auto Selector / Default")
    )
}

