package com.fer.projekt.solutionproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fer.projekt.models.EdgarProjectDTO
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.springframework.beans.factory.annotation.Value
import org.springframework.mock.web.MockMultipartFile
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate
import java.time.Month

@Component
class ClientServiceCommunicator(
    @Value("\${edgar.api.key}") private val edgarApiKey: String,
) {

    private val client = OkHttpClient()
    private val objectMapper = jacksonObjectMapper()
    private val mapper = jacksonObjectMapper()

    fun getListOfProjectsForCurrentAcademicYear(subjectId: String): List<String> {
        //return listOf("1.-laboratorijska-vježba")
        val today = LocalDate.now()
        var currentYear = today.year
        if (today.isBefore(LocalDate.of(currentYear, Month.OCTOBER, 1))) {
            currentYear -= 1
        }
        val url = "https://edgar2.fer.hr/api/m2m/exam/$subjectId/$currentYear"
        val request = Request.Builder()
            .url(url)
            .header("x-api-key", edgarApiKey)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful)
                throw RuntimeException("Error when getting Edgar projects: ${response.code}")
            val body = response.body?.string() ?: throw RuntimeException("No response from Edgar.")
            val projects: List<EdgarProjectDTO> = mapper.readValue(body)
            return projects.filter { it.idAcademicYear == currentYear }.map { it.title }
        }
    }

    fun getHashForProject(subjectId: String, academicYear: Int, projectName: String): String {
        val url = "https://edgar2.fer.hr/api/m2m/exam/$subjectId/$academicYear"
        val request = Request.Builder()
            .url(url)
            .header("x-api-key", edgarApiKey)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful)
                throw RuntimeException("Error when getting Edgar projects: ${response.code}")
            val body = response.body?.string() ?: throw RuntimeException("No response from Edgar.")
            val projects: List<EdgarProjectDTO> = mapper.readValue(body)
//            if (projectName.contains("1. laboratorijska vježba") && academicYear == 2023) {
//                println("here")
//                for (project in projects) {
//                    println("Project: ${project.title}")
//                    println("ID: ${project.idAcademicYear}")
//                    println("Hash: ${project.downloadAttachmentsUrl}")
//                    println(project.idAcademicYear == academicYear && project.title == projectName)
//                }
//                //println(projects.first { it.idAcademicYear == academicYear && it.title == projectName }.downloadAttachmentsUrl)
//                println(projects)
//            }
            return projects.first { it.idAcademicYear == academicYear && it.title.lowercase() == projectName.lowercase() }.downloadAttachmentsUrl
        }
    }

    fun getZipAttachmentsForProject(hash: String): MultipartFile? {
        val url = "https://edgar2.fer.hr/api/download/exam-attachments/$hash"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }

                val body = response.body ?: return null

                val originalFileName = response.header("Content-Disposition")?.let { cd ->
                    Regex("filename=\"([^\"]+)\"").find(cd)?.groupValues?.getOrNull(1) ?: "download.zip"
                } ?: "download.zip"

                val bytes = body.bytes()
                MockMultipartFile(
                    "file",
                    originalFileName,
                    "application/zip",
                    bytes
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getAISolution(taskText: String, language: String): String {
        val url = "http://localhost:8000/question"
        val jsonRequest = objectMapper.writeValueAsString(TaskRequest(taskText, language))
        val requestBody = jsonRequest.toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Failed to get response: ${response.code}")
            }
            val responseBody = response.body?.string() ?: throw RuntimeException("Response body is null")
            return objectMapper.readValue<QuestionModel>(responseBody).answer
        }
    }

    private data class TaskRequest(
        @JsonProperty("task_text") val taskText: String,
        @JsonProperty("language") val language: String,
    )

    private data class QuestionModel(
        @JsonProperty("question") val question: String,
        @JsonProperty("answer") val answer: String,
    )
}
