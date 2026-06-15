package com.dualvulndroid.dualvulndroid

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class APIClient {

    private val client = OkHttpClient()

    fun scanCode(code: String): String? {

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

        println("========== JSON SENT ==========")
        println(json)
        println("================================")

        val body = json.toRequestBody(
            "application/json".toMediaType()
        )

        val request = Request.Builder()
            .url("http://localhost:8080/api/scan")
            .post(body)
            .build()

        return try {

            client.newCall(request)
                .execute()
                .use { response ->

                    val responseBody =
                        response.body?.string()

                    println("========== RESPONSE ==========")
                    println(responseBody)
                    println("================================")

                    responseBody
                }

        } catch (e: Exception) {

            e.printStackTrace()
            null
        }
    }
}