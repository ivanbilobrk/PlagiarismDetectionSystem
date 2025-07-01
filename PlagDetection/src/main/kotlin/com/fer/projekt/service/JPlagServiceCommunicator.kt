package com.fer.projekt.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fer.projekt.controller.JPlagResource
import com.fer.projekt.controller.JPlagRunRequest
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springframework.stereotype.Component

@Component
class JPlagServiceCommunicator {

    private val client = OkHttpClient()
    private val objectMapper = jacksonObjectMapper()

    fun checkJplagRunning(resultHash: String): Map<String, Boolean>? {

        val urlBuilder = "${JPLAG_SERVER_URL}/plag/checkjplagrunning".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("resultHash", resultHash)
            ?.build() ?: throw IllegalArgumentException("Invalid URL")

        val request = Request.Builder()
            .url(urlBuilder)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return null
            }

            val responseBody = response.body?.string() ?: return null

            val mapper = jacksonObjectMapper()
            return mapper.readValue<Map<String, Boolean>>(responseBody)
        }
    }

    fun runJPlag(jPlagResources: List<JPlagResource>, languages: List<String>, resultHash: String, username: String, fileSuffixes: List<String>) {
        val runRequest = JPlagRunRequest(
            solutions = jPlagResources,
            languages = languages,
            resultHash = resultHash,
            userName = username,
            fileSuffixes = fileSuffixes
        )

        val json = objectMapper.writeValueAsString(runRequest)
        val mediaType = "application/json".toMediaType()
        val requestBody = json.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("${JPLAG_SERVER_URL}/run")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                println("Greška: ${response.code}, poruka: ${response.body?.string()}")
            } else {
                println("Uspjeh: ${response.body?.string()}")
            }
        }
    }

    fun deletePlagRunDetails(resultHash: String) {
        val urlBuilder = "${JPLAG_SERVER_URL}/plag/deletedetails".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("resultHash", resultHash)
            ?.build() ?: throw IllegalArgumentException("Invalid URL")

        val request = Request.Builder()
            .url(urlBuilder)
            .delete()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                println("Greška: ${response.code} - ${response.body?.string()}")
            } else {
                println("Uspjeh: ${response.body?.string()}")
            }
        }
    }

    companion object {
        const val JPLAG_SERVER_URL = "http://localhost:9090"
    }
}
