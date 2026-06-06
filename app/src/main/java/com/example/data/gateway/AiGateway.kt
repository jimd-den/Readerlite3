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
import org.json.JSONArray
import com.example.domain.model.WikiRecommendation
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

    suspend fun getWikipediaRecommendations(
        prompt: String,
        openRouterKey: String? = null,
        openRouterModel: String? = null,
        forceSimulation: Boolean = false
    ): List<WikiRecommendation> = withContext(Dispatchers.IO) {
        val hasOpenRouter = !openRouterKey.isNullOrBlank()
        val geminiApiKey = BuildConfig.GEMINI_API_KEY
        val hasValidGeminiKey = geminiApiKey.isNotEmpty() && geminiApiKey != "MY_GEMINI_API_KEY" && !geminiApiKey.contains("placeholder", ignoreCase = true)

        if (forceSimulation || (!hasOpenRouter && !hasValidGeminiKey)) {
            Log.w(TAG, "Using high-quality fallback Wikipedia recommendations for: $prompt")
            return@withContext simulateWikiRecommendations(prompt)
        }

        val systemPrompt = """
            You are a master curriculum advisor and academic librarian.
            Given a study topic, recommend exactly 4-5 highly-relevant academic Wikipedia articles that form a cohesive learning sequence/path.
            Return the recommendations strictly as a JSON array of objects.
            Each object in the array MUST contain exactly:
            1. "title": A reader-friendly title (e.g. "Artificial Intelligence" or "Theory of Relativity")
            2. "description": A 1-2 sentence description highlighting its academic relevance to the requested topic.
            3. "articleKey": The exact English Wikipedia page suffix used in URLs for direct retrieval (e.g. "Artificial_intelligence" or "Theory_of_relativity" - respect Wikipedia's case sensitivity).
            
            Do NOT wrap with markdown blocks (no ```json). Output raw, unformatted JSON array string only.
        """.trimIndent()

        try {
            val text = if (hasOpenRouter) {
                val requestModel = if (openRouterModel.isNullOrBlank() || openRouterModel == "openrouter/auto") "meta-llama/llama-3-8b-instruct:free" else openRouterModel
                Log.d(TAG, "Fetching Wikipedia recommendations via OpenRouter using model: $requestModel")
                val requestJson = JSONObject().apply {
                    put("model", requestModel)
                    put("messages", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", "Provide Wikipedia article recommendations for: $prompt")
                        })
                    })
                }

                val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://openrouter.ai/api/v1/chat/completions")
                    .header("Authorization", "Bearer $openRouterKey")
                    .header("HTTP-Referer", "https://ai.studio.build")
                    .header("X-Title", "Effortless SQ5R")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBodyStr = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        throw Exception("OpenRouter recommendations API failed. Code: ${response.code}: $responseBodyStr")
                    }

                    val responseJson = JSONObject(responseBodyStr)
                    responseJson
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                }
            } else {
                val requestJson = JSONObject().apply {
                    put("contents", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", org.json.JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", "Provide Wikipedia article recommendations for: $prompt")
                                })
                            })
                        })
                    })
                    put("systemInstruction", JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", systemPrompt)
                            })
                        })
                    })
                }

                val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$geminiApiKey")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBodyStr = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        throw Exception("Google Gemini recommended articles API failed. Code: ${response.code}")
                    }

                    val responseJson = JSONObject(responseBodyStr)
                    responseJson
                        .getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                }
            }

            var cleanedText = text.trim()
            if (cleanedText.startsWith("```")) {
                cleanedText = cleanedText.substringAfter("```")
                if (cleanedText.startsWith("json")) {
                    cleanedText = cleanedText.substringAfter("json")
                }
                cleanedText = cleanedText.substringBeforeLast("```").trim()
            }

            val list = mutableListOf<WikiRecommendation>()
            val array = JSONArray(cleanedText)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    WikiRecommendation(
                        title = obj.getString("title"),
                        description = obj.getString("description"),
                        articleKey = obj.getString("articleKey")
                    )
                )
            }
            return@withContext list
        } catch (e: Exception) {
            Log.e(TAG, "Error generating Wikipedia recommendations, falling back to simulation", e)
            return@withContext simulateWikiRecommendations(prompt)
        }
    }

    suspend fun fetchWikipediaArticle(articleKey: String): String = withContext(Dispatchers.IO) {
        val url = "https://en.wikipedia.org/w/api.php?action=query&prop=extracts&explaintext=TRUE&titles=${java.net.URLEncoder.encode(articleKey, "UTF-8")}&format=json&redirects=1"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "EffortlessSQ5RStudyCompanion/1.0 (violindocgfx@gmail.com)")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to fetch Wikipedia article. Code: ${response.code}")
            val bodyStr = response.body?.string() ?: ""
            val json = JSONObject(bodyStr)
            val query = json.optJSONObject("query") ?: throw Exception("Invalid response from Wikipedia")
            val pages = query.optJSONObject("pages") ?: throw Exception("No pages returned from Wikipedia")
            val keys = pages.keys()
            if (!keys.hasNext()) throw Exception("Article '$articleKey' not found on Wikipedia")
            val pageId = keys.next()
            val page = pages.getJSONObject(pageId)
            if (page.has("missing")) {
                throw Exception("Article '$articleKey' does not exist on Wikipedia")
            }
            return@withContext page.optString("extract", "")
        }
    }

    private fun simulateWikiRecommendations(prompt: String): List<WikiRecommendation> {
        val topic = prompt.trim().lowercase()
        return when {
            topic.contains("space") || topic.contains("astro") || topic.contains("moon") || topic.contains("star") -> listOf(
                WikiRecommendation("Space exploration", "An overview of physical exploration of outer space by humans and robotic spacecraft.", "Space_exploration"),
                WikiRecommendation("Apollo program", "The third United States human spaceflight program carried out by NASA, achieving landing on the Moon.", "Apollo_program"),
                WikiRecommendation("Mars", "Detailed account of Mars, its characteristics, search for life, and human exploration plans.", "Mars"),
                WikiRecommendation("Hubble Space Telescope", "The telescope launched into low Earth orbit in 1990 that revolutionized modern astronomy.", "Hubble_Space_Telescope"),
                WikiRecommendation("International Space Station", "A modular space station in low Earth orbit acting as a joint international research laboratory.", "International_Space_Station")
            )
            topic.contains("mind") || topic.contains("psych") || topic.contains("brain") || topic.contains("cogni") -> listOf(
                WikiRecommendation("Cognitive psychology", "The scientific study of mental processes such as attention, memory, perception, and problem-solving.", "Cognitive_psychology"),
                WikiRecommendation("Memory", "The mental faculty of retaining, recalling, and encoding structural information.", "Memory"),
                WikiRecommendation("Neuroscience", "The scientific study of the nervous system and physiological brain structures.", "Neuroscience"),
                WikiRecommendation("Attention", "How cognitive systems selectively concentrate on discrete aspects of information.", "Attention")
            )
            topic.contains("physics") || topic.contains("quantum") || topic.contains("relativity") || topic.contains("atom") -> listOf(
                WikiRecommendation("Quantum mechanics", "A fundamental theory in physics describing physical properties of nature at atomic scales.", "Quantum_mechanics"),
                WikiRecommendation("Theory of relativity", "Encompasses the special and general relativity theories developed by Albert Einstein.", "Theory_of_relativity"),
                WikiRecommendation("Standard Model", "The theory describing electromagnetic, weak, and strong nuclear interactions.", "Standard_Model"),
                WikiRecommendation("Subatomic particle", "An detailed analysis of hadrons, leptons, and fundamental bosons.", "Subatomic_particle")
            )
            topic.contains("computer") || topic.contains("ai") || topic.contains("cod") || topic.contains("learn") || topic.contains("data") -> listOf(
                WikiRecommendation("Artificial intelligence", "Explores the history, goals, and deep learning neural architectures of machine intelligence.", "Artificial_intelligence"),
                WikiRecommendation("Machine learning", "A field in computer science focusing on algorithms that learn from data to make predictions.", "Machine_learning"),
                WikiRecommendation("Deep learning", "Part of a broader family of machine learning methods based on artificial neural networks.", "Deep_learning"),
                WikiRecommendation("Data structure", "A data organization, management, and storage format that enables efficient access.", "Data_structure"),
                WikiRecommendation("Algorithm", "A finite sequence of rigorous instructions used to solve classes of specific problems.", "Algorithm")
            )
            else -> listOf(
                WikiRecommendation("Study skills", "The structured approaches of active reading, SQ5R, spacing, and memory consolidation.", "Study_skills"),
                WikiRecommendation("Active learning", "A learning process where students are actively engaged rather than passively listening.", "Active_learning"),
                WikiRecommendation("Information processing theory", "Cognitive approach to understanding how the human mind handles learning.", "Information_processing_theory"),
                WikiRecommendation("Academic discipline", "A branch of knowledge that is formally taught and researched as part of higher education.", "Academic_discipline")
            )
        }
    }
}

