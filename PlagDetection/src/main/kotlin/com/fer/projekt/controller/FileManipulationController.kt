package com.fer.projekt.controller

import com.fer.projekt.fileproviders.FileUtils
import com.fer.projekt.service.FileManipulationService
import com.fer.projekt.service.JPlagMongoService
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RestController
class FileManipulationController(
    private val fileManipulationService: FileManipulationService,
    private val fileUtils: FileUtils,
    private val jPlagMongoService: JPlagMongoService
) {

    @GetMapping("/download")
    fun downloadFiles(
        @RequestParam userName: String,
        @RequestParam resultName: String,
        @RequestParam firstSubmissionId: String,
        @RequestParam secondSubmissionId: String
    ): ResponseEntity<FileSystemResource> {
        return try {
            val submissionComparison = jPlagMongoService.getSubmissionComparison(resultName, userName, firstSubmissionId, secondSubmissionId)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()

            val firstSubmission = File(submissionComparison.firstSubmissionPath)
            val secondSubmission = File(submissionComparison.secondSubmissionPath)

            if (!firstSubmission.exists() || !secondSubmission.exists()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
            }

            val zipFilePath = Files.createTempFile("submissions", ".zip")
            ZipOutputStream(FileOutputStream(zipFilePath.toFile())).use { zipOut ->
                zipDirectory(firstSubmission.toPath(), zipOut, "$firstSubmissionId/")
                zipDirectory(secondSubmission.toPath(), zipOut, "$secondSubmissionId/")
            }

            val resource = FileSystemResource(zipFilePath.toFile())

            val response = ResponseEntity.ok()
                .headers(HttpHeaders().apply {
                    add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"submissions.zip\"")
                    add(HttpHeaders.CONTENT_TYPE, "application/zip")
                })
                .body(resource)

            response
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    private fun zipDirectory(folderPath: Path, zipOut: ZipOutputStream, basePath: String) {
        Files.walk(folderPath).forEach { path ->
            val zipEntry = ZipEntry(basePath + folderPath.relativize(path).toString())
            if (Files.isDirectory(path)) {
                zipOut.putNextEntry(zipEntry)
                zipOut.closeEntry()
            } else {
                zipOut.putNextEntry(zipEntry)
                Files.copy(path, zipOut)
                zipOut.closeEntry()
            }
        }
    }

    @GetMapping("/fileStructure")
    fun getFileStructure(@RequestParam userName: String): ResponseEntity<Any> {
        return try {
            val fileNodes = fileManipulationService.getFileStructure(userName)
            ResponseEntity.ok(fileNodes)
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }

    @DeleteMapping("/delete")
    fun deleteFile(@RequestBody filePaths: List<String>): ResponseEntity<Any> {
        return try {
            filePaths.forEach { filePath ->
                fileUtils.deleteByPath(filePath)
            }
            ResponseEntity.ok().build()
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }
}
