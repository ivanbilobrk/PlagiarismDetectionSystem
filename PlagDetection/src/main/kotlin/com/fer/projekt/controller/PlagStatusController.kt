package com.fer.projekt.controller;

import com.fer.projekt.service.CancellationManager
import com.fer.projekt.service.JPlagRunDetailsService
import com.fer.projekt.service.JPlagService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController;
import kotlin.let
import kotlin.to

@RestController
class PlagStatusController(
    private val jPlagService: JPlagService
) {
    @GetMapping("/plag/checkjplagrunning")
    fun checkJplagRunningForProject(
        @RequestParam resultHash: String,
    ): ResponseEntity<*>
    {
        return jPlagService.isProjectRunning(
            resultHash,
        ).let { isRunning ->
            if (isRunning) {
                ResponseEntity.ok(mapOf("running" to true))
            } else {
                ResponseEntity.ok(mapOf("running" to false))
            }
        }
    }

    @GetMapping("/plag/getPlagRunStatuses")
    fun getPlagRunState(
        @RequestParam resultHash: String
    ): ResponseEntity<Any> {
        try {
            val jPlagRunDetails = jPlagService.findByResultHash(resultHash)!!
            val normalizationDone = jPlagRunDetails.processedNormalizations
            val parsingDone = jPlagRunDetails.processedParsings
            val comparisonDone = jPlagRunDetails.processedComparisons
            return ResponseEntity.ok(PlagRunState(
                submissionTotal = jPlagRunDetails.submissionTotal,
                normalizationDone = normalizationDone,
                parsingDone = parsingDone,
                comparisonTotal = jPlagRunDetails.comparisonTotal,
                comparisonDone = comparisonDone
            ))
        } catch (_ : Exception) {
            return ResponseEntity.badRequest().body("Cannot get data for resultHash: $resultHash")
        }
    }

    @GetMapping("/plag/getPlagRunDetails")
    fun getPlagRunDetails(
        @RequestParam resultHash: String
    ): ResponseEntity<Any> {
        try {
            val jPlagRunDetails = jPlagService.findByResultHash(resultHash)!!
            return ResponseEntity.ok(jPlagRunDetails)
        } catch (_ : Exception) {
            return ResponseEntity.badRequest().body("Cannot get data for resultHash: $resultHash")
        }
    }

    @GetMapping("/plag/getCurrentlyProcessingSubmissions")
    fun getCurrentlyProcessingSubmissions(
        @RequestParam resultHash: String
    ): ResponseEntity<Any> {
        try {
            val currentlyProcessingSubmissionsForRun = jPlagService.findByResultHash(resultHash)!!.copy(currentlyProcessingSubmissions = jPlagService.getCurrentlyProcessingSubmissions(resultHash))
            return ResponseEntity.ok(currentlyProcessingSubmissionsForRun)
        } catch (_ : Exception) {
            return ResponseEntity.badRequest().body("Cannot get data for resultHash: $resultHash")
        }
    }

    @GetMapping("/plag/isSuspended")
    fun isSuspended(
        @RequestParam resultHash: String
    ): ResponseEntity<Any> {
        val jplagDetails = jPlagService.findByResultHash(resultHash)

        return ResponseEntity.ok(PlagSuspendedStatus(jplagDetails!!.isPaused == true, resultHash, ""))
    }

    @DeleteMapping("/plag/deletedetails")
    fun deletePlagRunDetails(
        @RequestParam resultHash: String
    ): ResponseEntity<Any> {
        return try {
            jPlagService.deleteByResultHash(resultHash)
            ResponseEntity.ok("Plag run details deleted")
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(e.message)
        }
    }
}
