package com.fer.projekt.controller

import com.fer.projekt.service.JPlagMongoService
import com.fer.projekt.service.JPlagService
import com.fer.projekt.service.SolutionUploadService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.Date

@RestController("/solutions")
class SolutionUploadController(
    private val solutionUploadService: SolutionUploadService,
    private val jPlagService: JPlagService,
    private val jPlagMongoService: JPlagMongoService
) {

    @PostMapping("/upload")
    suspend fun uploadSolution(
        @ModelAttribute solutionUploadRequest: SolutionUploadRequest
    ): ResponseEntity<*> {
        return try {
            val solutionId = solutionUploadService.uploadSolution(
                solutionUploadRequest.zipFile,
                solutionUploadRequest.repoUrl,
                solutionUploadRequest.branch,
                solutionUploadRequest.solutionProvider,
                solutionUploadRequest.userName,
                solutionUploadRequest.resourceName,
                solutionUploadRequest.disallowedFiles,
                solutionUploadRequest.allowedExtensions
            )
            ResponseEntity.ok(solutionId)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(e.message)
        }
    }

    @PostMapping("/run")
    fun runJPlag(
        @RequestBody jPlagRunRequest: JPlagRunRequest
    ): ResponseEntity<*> {
        return try {
            jPlagService.runJplag(
                jPlagRunRequest.solutions,
                jPlagRunRequest.languages,
                "Provjera ${Date()}",
                jPlagRunRequest.userName,
                jPlagRunRequest.fileSuffixes
            )
            ResponseEntity.ok("JPlag run started")
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(e.message)
        }
    }

    @GetMapping("/results")
    fun getAllRunsForUser(
        @RequestParam userName: String
    ): ResponseEntity<*> {
        return try {
            val resultNames = jPlagMongoService.getAllRunsForUser(userName)
            ResponseEntity.ok(resultNames)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(e.message)
        }
    }

    @GetMapping("/result")
    fun getJPlagResult(
        @RequestParam resultHash: String
    ): ResponseEntity<*> {
        return try {
            val result = jPlagMongoService.getResultByResultHash(resultHash)
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(e.message)
        }
    }

    @DeleteMapping("/result")
    fun deleteJPlagResult(
        @RequestParam resultHash: String,
    ): ResponseEntity<*> {
        return try {
            jPlagMongoService.deleteJPlagResult(resultHash)
            ResponseEntity.ok("Result deleted")
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(e.message)
        }
    }

    @GetMapping("/comparison")
    fun getSubmissionComparison(
        @RequestParam userName: String,
        @RequestParam resultName: String,
        @RequestParam firstSubmissionId: String,
        @RequestParam secondSubmissionId: String
    ): ResponseEntity<*> {
        return try {
            val comparison = jPlagMongoService.getSubmissionComparison(resultName, userName, firstSubmissionId, secondSubmissionId)
            ResponseEntity.ok(comparison)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(e.message)
        }
    }
}
