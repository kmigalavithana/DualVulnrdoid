package com.dualvulndroid.dualvulndroid

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class ScanResponse(
    val prediction: String,
    val vulnerable: Boolean,
    val vulnerabilityType: String,
    val confidence: Double,
    val explanation: String
)

class APIClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    fun scanCode(code: String): ScanResponse? {


        println("CODE SENT TO BACKEND:")
        println(code)

        println("Sending request...")

        val escapedCode = code
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

        val json = """
        {
          "code":"$escapedCode"
        }
        """.trimIndent()

        val body = json.toRequestBody(
            "application/json".toMediaType()
        )

        val request = Request.Builder()
            .url("http://127.0.0.1:8080/predict")
            .post(body)
            .build()

        return try {

            client.newCall(request).execute().use { response ->

                val responseBody = response.body?.string()

                println("================================")
                println("HTTP : ${response.code}")
                println("SERVER RESPONSE:")
                println(responseBody)
                println("================================")

                if (response.isSuccessful && responseBody != null) {

                    gson.fromJson(
                        responseBody,
                        ScanResponse::class.java
                    )

                } else {

                    null

                }

            }

        } catch (e: Exception) {

            e.printStackTrace()
            null

        }

    }

}