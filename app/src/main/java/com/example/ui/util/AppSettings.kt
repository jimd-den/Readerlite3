package com.example.ui.util

import android.content.Context
import android.util.Log
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object AppSettings {
    private const val PREFS_NAME = "study_reader_prefs"
    private const val KEY_OPEN_ROUTER_KEY = "open_router_key"
    private const val KEY_OPEN_ROUTER_MODEL = "open_router_model"
    private const val KEY_OPEN_ROUTER_MODELS_CACHE = "open_router_models_cache"
    private const val KEY_ACTIVE_FONT = "active_font"
    private const val KEY_DOWNLOADED_FONTS = "downloaded_fonts"
    private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    private const val KEY_CREATIVITY_LEVEL = "creative_dial_level" // live creativity dial

    private const val DEFAULT_MODEL = "meta-llama/llama-3-8b-instruct:free"

    private val httpClient = OkHttpClient()

    fun getOpenRouterKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_OPEN_ROUTER_KEY, "") ?: ""
    }

    fun setOpenRouterKey(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_OPEN_ROUTER_KEY, key).apply()
    }

    fun getOpenRouterModel(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_OPEN_ROUTER_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    }

    fun setOpenRouterModel(context: Context, model: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_OPEN_ROUTER_MODEL, model).apply()
    }

    fun getCachedModels(context: Context): List<Pair<String, String>> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cacheStr = prefs.getString(KEY_OPEN_ROUTER_MODELS_CACHE, "") ?: ""
        if (cacheStr.isEmpty()) {
            return listOf(
                DEFAULT_MODEL to "Llama 3 8B Free",
                "google/gemini-2.5-flash" to "Gemini 2.5 Flash",
                "google/gemini-2.5-pro" to "Gemini 2.5 Pro",
                "deepseek/deepseek-chat" to "DeepSeek v3"
            )
        }
        return try {
            val arr = JSONArray(cacheStr)
            val list = mutableListOf<Pair<String, String>>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(obj.getString("id") to obj.getString("name"))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setCachedModels(context: Context, models: List<Pair<String, String>>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        models.forEach { (id, name) ->
            val obj = JSONObject().apply {
                put("id", id)
                put("name", name)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_OPEN_ROUTER_MODELS_CACHE, arr.toString()).apply()
    }

    fun getActiveFont(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE_FONT, "Default") ?: "Default"
    }

    fun setActiveFont(context: Context, font: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACTIVE_FONT, font).apply()
    }

    fun getDownloadedFonts(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_DOWNLOADED_FONTS, emptySet()) ?: emptySet()
        return set.toList().sorted()
    }

    private fun addDownloadedFont(context: Context, fontName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_DOWNLOADED_FONTS, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(fontName)
        prefs.edit().putStringSet(KEY_DOWNLOADED_FONTS, set).apply()
    }

    fun getActiveProfileId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE_PROFILE_ID, "calm-focus") ?: "calm-focus"
    }

    fun setActiveProfileId(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, id).apply()
    }

    fun getCreativityLevel(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_CREATIVITY_LEVEL, 0.5f)
    }

    fun setCreativityLevel(context: Context, level: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_CREATIVITY_LEVEL, level).apply()
    }

    // Google Fonts Downloader
    suspend fun downloadFont(context: Context, fontName: String): Result<Unit> {
        val formattedFontName = fontName.trim()
        if (formattedFontName.isEmpty()) return Result.failure(Exception("Font name cannot be empty"))

        val folder = File(context.filesDir, "fonts/$formattedFontName")
        if (!folder.exists()) {
            folder.mkdirs()
        }

        val urlEncodedName = formattedFontName.replace(" ", "%20")
        val downloadUrl = "https://fonts.google.com/download?family=$urlEncodedName"

        return try {
            val request = Request.Builder().url(downloadUrl).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(Exception("Failed to download font. Status: ${response.code}"))
            }

            val body = response.body ?: return Result.failure(Exception("Response body is empty"))
            val zipStream = ZipInputStream(body.byteStream())
            var entry = zipStream.nextEntry
            var hasSavedTtf = false

            while (entry != null) {
                val name = entry.name
                if (name.endsWith(".ttf", ignoreCase = true)) {
                    val outputFile = File(folder, name.substringAfterLast("/"))
                    FileOutputStream(outputFile).use { out ->
                        zipStream.copyTo(out)
                    }
                    hasSavedTtf = true
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
            zipStream.close()

            if (hasSavedTtf) {
                addDownloadedFont(context, formattedFontName)
                Result.success(Unit)
            } else {
                Result.failure(Exception("No TTF file found inDownloaded package."))
            }
        } catch (e: Exception) {
            Log.e("AppSettings", "Error downloading font: $fontName", e)
            Result.failure(e)
        }
    }

    // Dynamic Google Fonts Loader
    fun loadFontFamily(context: Context, fontName: String): FontFamily {
        if (fontName == "Default" || fontName.isEmpty()) {
            return FontFamily.Default
        }
        val folder = File(context.filesDir, "fonts/$fontName")
        if (!folder.exists()) return FontFamily.Default

        val files = folder.listFiles { _, name -> name.endsWith(".ttf", ignoreCase = true) }
        if (files.isNullOrEmpty()) {
            return FontFamily.Default
        }

        return try {
            val list = mutableListOf<Font>()
            for (file in files) {
                val lowercaseName = file.name.lowercase()
                var weight = FontWeight.Normal
                var style = FontStyle.Normal
                
                if (lowercaseName.contains("bold")) weight = FontWeight.Bold
                if (lowercaseName.contains("semibold")) weight = FontWeight.SemiBold
                if (lowercaseName.contains("medium")) weight = FontWeight.Medium
                if (lowercaseName.contains("light")) weight = FontWeight.Light
                if (lowercaseName.contains("italic")) style = FontStyle.Italic
                
                list.add(Font(file = file, weight = weight, style = style))
            }
            FontFamily(list)
        } catch (e: Exception) {
            Log.e("AppSettings", "Error loading custom FontFamily for $fontName", e)
            FontFamily.Default
        }
    }

    // Dynamic fetch of models from OpenRouter catalog
    suspend fun fetchOpenRouterModels(context: Context): Result<List<Pair<String, String>>> {
        return try {
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/models")
                .header("Content-Type", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(Exception("Error retrieving models: code ${response.code}"))
            }

            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val data = json.getJSONArray("data")
            val list = mutableListOf<Pair<String, String>>()
            
            for (i in 0 until data.length()) {
                val obj = data.getJSONObject(i)
                val id = obj.getString("id")
                // Fallback to name or ID
                val name = obj.optString("name", id)
                list.add(id to name)
            }
            
            // Save cache
            setCachedModels(context, list)
            Result.success(list)
        } catch (e: Exception) {
            Log.e("AppSettings", "Error fetching OpenRouter models", e)
            Result.failure(e)
        }
    }
}
