package com.example.ui.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object FontDownloader {
    private const val TAG = "FontDownloader"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun downloadGoogleFont(context: Context, fontName: String): Boolean = withContext(Dispatchers.IO) {
        val cleanName = fontName.trim()
        if (cleanName.isEmpty()) return@withContext false
        
        val urlParam = cleanName.replace(" ", "+")
        // Request CSS file from google fonts with standard user agent to get TTF URL
        val cssUrl = "https://fonts.googleapis.com/css?family=$urlParam"
        
        try {
            val request = Request.Builder()
                .url(cssUrl)
                // Use a standard Desktop User Agent so Google Fonts returns TTF rather than WOFF2 format
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "CSS query failed with code ${response.code}")
                    return@withContext false
                }
                
                val cssContent = response.body?.string() ?: return@withContext false
                Log.d(TAG, "CSS Content: $cssContent")
                
                // Match the font src url: url(https://fonts.gstatic.com/...)
                val regex = Regex("url\\((https://[^)]+)\\)")
                val matchResult = regex.find(cssContent) ?: return@withContext false
                val ttfUrl = matchResult.groupValues[1].replace("\"", "").replace("'", "")
                
                Log.d(TAG, "Extracted TTF URL: $ttfUrl")
                
                // Download actual ttf bytes
                val ttfRequest = Request.Builder().url(ttfUrl).build()
                client.newCall(ttfRequest).execute().use { ttfResponse ->
                    if (!ttfResponse.isSuccessful) {
                        Log.e(TAG, "TTF download failed with code ${ttfResponse.code}")
                        return@withContext false
                    }
                    
                    val bytes = ttfResponse.body?.bytes() ?: return@withContext false
                    val fontsDir = File(context.filesDir, "fonts")
                    if (!fontsDir.exists()) {
                        fontsDir.mkdirs()
                    }
                    
                    val destFile = File(fontsDir, getFontFileName(cleanName))
                    destFile.writeBytes(bytes)
                    Log.d(TAG, "Font downloaded successfully to: ${destFile.absolutePath}")
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed downloading font $fontName", e)
            false
        }
    }

    fun getFontFileName(fontName: String): String {
        return fontName.lowercase().trim().replace(" ", "_") + ".ttf"
    }

    fun getFontFile(context: Context, fontName: String): File {
        return File(File(context.filesDir, "fonts"), getFontFileName(fontName))
    }

    fun isFontDownloaded(context: Context, fontName: String): Boolean {
        return getFontFile(context, fontName).exists()
    }
}
