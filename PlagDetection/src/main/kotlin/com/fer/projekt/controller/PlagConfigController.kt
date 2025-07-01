package com.fer.projekt.controller;

import com.fer.projekt.repository.PlagSuspendedTaskRepository
import com.fer.projekt.service.CancellationManager
import com.fer.projekt.service.JPlagMongoService
import com.fer.projekt.service.JPlagService
import com.fer.projekt.service.PlagConfigService;
import com.fer.projekt.solutionproviders.LocalSolutionProvider
import com.fer.projekt.utils.edgarSubjects
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController;
import java.io.File
import java.io.FileInputStream

@RestController
class PlagConfigController(
    private val plagConfigService: PlagConfigService,
    private val jPlagMongoService: JPlagMongoService,
    private val jPlagService: JPlagService,
    private val plagSuspendedTaskRepository: PlagSuspendedTaskRepository,
    private val localSolutionProvider: LocalSolutionProvider
) {
    @GetMapping("/plag/rundetection")
    fun runDetection(
        @RequestParam configName: String,
        @RequestParam projectName: String,
        @RequestParam userName: String,
    ): ResponseEntity<*> {

        return try {
            jPlagService.runPlagNew(
                configName,
                projectName,
                userName,
                true
            )
            ResponseEntity.ok("JPlag run started")
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(e.message)
        }
    }


    @PostMapping("/plag/config/basic")
    fun createDefaultConfig(
        @RequestBody createBasicConfigRequest: CreateBasicConfigRequest
    ): ResponseEntity<*> {
        try {
            val plagConfig = plagConfigService.createBasicPlagConfig(
                name = createBasicConfigRequest.name,
                subjectName = edgarSubjects[createBasicConfigRequest.subjectId] ?: "Nepoznato ime predmeta",
                resourcesTTL = createBasicConfigRequest.resourcesTTL,
                clientBackendURL = "",
                suffixes = createBasicConfigRequest.suffixes,
                languages = createBasicConfigRequest.languages,
                scheduleType = createBasicConfigRequest.scheduleType,
                userName = createBasicConfigRequest.userName,
                subjetId = createBasicConfigRequest.subjectId,
                disallowedFiles = createBasicConfigRequest.disallowedFiles?.toSet() ?: emptySet(),
            )
            return ResponseEntity.status(HttpStatus.CREATED).body(plagConfig)
        } catch (e: DuplicateKeyException) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Config with the same name already exists")
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.message)
        } catch (e: Exception) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred: ${e.message}")
        }
    }

    @GetMapping("/plag/config")
    fun getPlagConfig(
        @RequestParam userName: String,
        @RequestParam configName: String
    ): ResponseEntity<Any> {
        val plagConfig = plagConfigService.getPlagConfig(
            username = userName,
            configName = configName
        )

        return ResponseEntity.ok(plagConfig)
    }

    @GetMapping("/plag/config/all")
    fun getAllPlagConfigsForUser(
        @RequestParam userName: String
    ): ResponseEntity<Any> {
        val plagConfigs = plagConfigService.getAllPlagConfigsForUser(userName)
        return ResponseEntity.ok(plagConfigs)
    }

    @GetMapping("/plag/config/results")
    fun getAllResultsForConfigAndProject(
        @RequestParam userName: String,
        @RequestParam configName: String,
        @RequestParam projectName: String
    ): ResponseEntity<*> {
        return try {
            val results = jPlagMongoService.getAllRunsForUserNameConfigNameAndProjectName(
                userName = userName,
                configName = configName,
                projectName = projectName
            )
            ResponseEntity.ok(results)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(e.message)
        }
    }

    @GetMapping("/plag/config/checkresourceupdating")
    fun checkResourceUpdating(
        @RequestParam configName: String,
        @RequestParam projectName: String,
        @RequestParam userName: String,
        @RequestParam resourcePath: String
    ): ResponseEntity<*> {
        return try {
            val isUpdating = PlagConfigService.isResourceUpdating(
                plagConfigName = configName,
                projectName = projectName,
                userName = userName,
                resourcePath = resourcePath
            )
            ResponseEntity.ok(mapOf("updating" to isUpdating))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(e.message)
        }
    }

    @PostMapping("/plag/config/updateResource")
    fun updateResource(
        @RequestBody updateResourceRequest: UpdateResourceRequest
    ): ResponseEntity<*> {
        return try {
            plagConfigService.updateResourceForProject(
                projectName = updateResourceRequest.projectName,
                userName = updateResourceRequest.userName,
                plagConfigName = updateResourceRequest.configName,
                resourcePath = updateResourceRequest.resourcePath
            )
            ResponseEntity.ok("Resource update started")
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(e.message)
        }
    }

    @DeleteMapping("/plag/config/deleteResource")
    fun deleteResourceFromProject(
        @RequestParam userName: String,
        @RequestParam configName: String,
        @RequestParam projectName: String,
        @RequestParam resourcePath: String
    ): ResponseEntity<*> {
        return try {
            plagConfigService.deleteResourceFromProject(
                userName = userName,
                plagConfigName = configName,
                projectName = projectName,
                resourcePath = resourcePath
            )
            ResponseEntity.ok("Resource deleted successfully")
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(e.message)
        }
    }

    @PostMapping("/plag/config/manuallyAddZipResource")
    fun manuallyAddZipResourceForProject(
        @ModelAttribute manuallyAddZipResourceRequest: ManuallyAddZipResourceRequest
    ): ResponseEntity<*> {
        return try {
            plagConfigService.addZipResourceToProject(manuallyAddZipResourceRequest)
            ResponseEntity.ok("Resource added successfully")
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(e.message)
        }
    }

    @PostMapping("/plag/config/manuallyAddGitResource")
    fun manuallyAddGitResourceForProject(
        @ModelAttribute manuallyAddGitResourceRequest: ManuallyAddGitResourceRequest
    ): ResponseEntity<*> {
        return try {
            plagConfigService.addGitResourceToProject(manuallyAddGitResourceRequest)
            ResponseEntity.ok("Resource added successfully")
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(e.message)
        }
    }

    @PostMapping("/plag/config/manuallyAddAIResource")
    fun manuallyAddAIResourceForProject(
        @ModelAttribute manuallyAddAIResourceRequest: ManuallyAddAIResourceRequest
    ): ResponseEntity<*> {
        return try {
            plagConfigService.addAIResourceToProject(manuallyAddAIResourceRequest)
            ResponseEntity.ok("Resource added successfully")
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(e.message)
        }
    }

    @GetMapping("/plag/getPlagRunStatuses")
    fun getPlagRunState(
        @RequestParam plagRunId: String
    ): ResponseEntity<Any> {
        try {
            val normalizationDone = JPlagService.getStepStatus("normalization", plagRunId)
            val parsingDone = JPlagService.getStepStatus("parsing", plagRunId)
            val comparisonDone = JPlagService.getStepStatus("comparison", plagRunId)
            val jPlagRun = jPlagMongoService.getPlagRunById(plagRunId)!!
            return ResponseEntity.ok(PlagRunState(
                submissionTotal = jPlagRun.submissionTotal,
                normalizationDone = normalizationDone,
                parsingDone = parsingDone,
                comparisonTotal = jPlagRun.comparisonTotal,
                comparisonDone = comparisonDone
            ))
        } catch (_ : Exception) {
            return ResponseEntity.badRequest().body("Cannot get data for plagRunId: $plagRunId")
        }
    }

    @GetMapping("/plag/getCurrentlyProcessingSubmissions")
    fun getCurrentlyProcessingSubmissions(
        @RequestParam plagRunId: String
    ): ResponseEntity<Any> {
        try {
            val currentlyProcessingSubmissionsForRun = JPlagService.getCurrentlyProcessingSubmissionsForRun(plagRunId)
            return ResponseEntity.ok(currentlyProcessingSubmissionsForRun)
        } catch (_ : Exception) {
            return ResponseEntity.badRequest().body("Cannot get data for plagRunId: $plagRunId")
        }
    }

    @GetMapping("/plag/cancel")
    fun cancelPlagRun(
    ): ResponseEntity<Any> {
        CancellationManager.cancel()
        return ResponseEntity.ok().build()
    }

    @GetMapping("/plag/isSuspended")
    fun isSuspended(
        @RequestParam plagRunId: String
    ): ResponseEntity<Any> {
        val isSuspended = plagSuspendedTaskRepository.findByPlagRunId(plagRunId) != null
        val jPlagRun = jPlagMongoService.getPlagRunById(plagRunId)

        return ResponseEntity.ok(PlagSuspendedStatus(isSuspended, plagRunId, jPlagRun?.projectName ?: ""))
    }

    @GetMapping("/plag/testzip")
    fun unzipFile(): ResponseEntity<Any> {
        val file = File("/Users/ibilobrk/razno/2025-05-16T122726.200.zip")
        val fileInputStream = FileInputStream(file)
        val bytes = fileInputStream.readBytes()
        fileInputStream.close()

        val multipartFile = MockMultipartFile("file", file.name, "application/zip", bytes)

        localSolutionProvider.unzipAndSaveFileV2(multipartFile.inputStream, File("/Users/ibilobrk/razno/test"))
        return ResponseEntity.ok("File unzipped successfully")
    }
}
