package com.dualvulndroid.dualvulndroid

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

// API Response එක ලේසියෙන් කියවගන්න Data Class එකක් හදාගමු
data class ScanResponse(
    val vulnerable: Boolean,
    val confidence: Double,
    val status: String,
    val message: String? = null
)

class APIClient {

    // ⏱️ රියල්-ටයිම් ස්කෑන් වෙන නිසා Timeout එක තත්පර 2කට සීමා කරමු (Response එක වේගවත් කරන්න)
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun scanCode(code: String): ScanResponse? {
        // JSON එක කැඩෙන්නේ නැති වෙන්න Escape කරගැනීම
        val escapedCode = code
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

        val json = """
            {
              "code": "$escapedCode"
            }
        """.trimIndent()

        val body = json.toRequestBody("application/json".toMediaType())

        // 🔥 FIX: අපේ අලුත්ම Spring Boot Endpoint එකට URL එක වෙනස් කරා
        val request = Request.Builder()
            .url("http://localhost:8080/api/v1/vulnerability/scan")
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    // JSON String එක කෙලින්ම අපේ ScanResponse object එකක් බවට හරවනවා
                    gson.fromJson(responseBody, ScanResponse::class.java)
                } else {
                    println("❌ Server error response: ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            println("❌ Failed to connect to Spring Boot API: ${e.message}")
            null
        }
    }
}