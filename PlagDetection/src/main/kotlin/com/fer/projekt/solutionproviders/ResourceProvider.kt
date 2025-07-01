package com.fer.projekt.solutionproviders

import com.fer.projekt.models.PlagConfig
import com.fer.projekt.models.Resource
import com.fer.projekt.models.ResourceType
import com.fer.projekt.service.PlagConfigService
import com.fer.projekt.utils.edgarSubjects
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.mock.web.MockMultipartFile
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Component
class ResourceProvider(
    private val localSolutionProvider: LocalSolutionProvider,
    private val gitRepositorySolutionProvider: GitRepositorySolutionProvider,
    private val clientServiceCommunicator: ClientServiceCommunicator
) {

    fun updateResourceManually(resource: Resource, plagConfig: PlagConfig, projectName: String): Resource {
        return updateResource(
            resource = resource,
            projectName = projectName,
            plagConfigName = plagConfig.name,
            userName = plagConfig.userName,
            subjectId = plagConfig.subjectId,
            allowedExtensions = emptyList(),
            disallowedFiles = emptyList()
        )
    }

    fun updateResourceBySchedule(resource: Resource, plagConfig: PlagConfig, projectName: String): Resource {
        val lastUpdateTime = resource.lastUpdate
        if (lastUpdateTime == null ||
            (PlagConfigService.calculateTimeDifference(lastUpdateTime, Date()) >= plagConfig.scheduleType.days )) {
            return updateResource(
                resource = resource,
                projectName = projectName,
                plagConfigName = plagConfig.name,
                userName = plagConfig.userName,
                subjectId = plagConfig.subjectId,
                allowedExtensions = emptyList(),
                disallowedFiles = emptyList()
            )
        } else {
            return Resource(
                path = resource.path,
                exceptionMessage = resource.exceptionMessage,
                resourceType = resource.resourceType,
                lastUpdate = lastUpdateTime,
                academicYear = resource.academicYear,
                hash = resource.hash,
                hasBeenChanged = false,
                repoURL = resource.repoURL,
                branch = resource.branch,
                edgarHash = resource.edgarHash
            )
        }
    }

    private fun updateResource(
        resource: Resource,
        projectName: String,
        plagConfigName: String,
        userName: String,
        subjectId: String,
        allowedExtensions: List<String>,
        disallowedFiles: List<String>
    ): Resource {
        return when (resource.resourceType) {
            ResourceType.EDGAR -> {
                updateEdgarResource(
                    resource = resource,
                    projectName = projectName,
                    plagConfigName = plagConfigName,
                    userName = userName,
                    subjectId = subjectId,
                    allowedExtensions = allowedExtensions,
                    disallowedFiles = disallowedFiles
                )
            }

            ResourceType.WHITELIST -> {
                updateEdgarWhitelistResource(
                    resource = resource,
                    projectName = projectName,
                    plagConfigName = plagConfigName,
                    userName = userName,
                    subjectId = subjectId,
                    allowedExtensions = allowedExtensions,
                    disallowedFiles = disallowedFiles
                )
            }

            ResourceType.GIT -> updateGitResource(
                resource = resource,
                projectName = projectName,
                plagConfigName = plagConfigName,
                userName = userName,
                disallowedFiles = disallowedFiles,
                allowedExtensions = allowedExtensions
            )

            ResourceType.ZIP -> updateResourceLastUpdateTime(resource)
            ResourceType.AI -> updateResourceLastUpdateTime(resource)
        }
    }

    private fun updateResourceLastUpdateTime(resource: Resource) = Resource(
        path = resource.path,
        exceptionMessage = resource.exceptionMessage,
        resourceType = resource.resourceType,
        lastUpdate = Date(),
        academicYear = resource.academicYear,
        hash = resource.hash,
        hasBeenChanged = true,
        repoURL = resource.repoURL,
        branch = resource.branch,
        taskText = resource.taskText,
        edgarHash = resource.edgarHash
    )

    fun updateGitResource(
        resource: Resource,
        projectName: String,
        plagConfigName: String,
        userName: String,
        disallowedFiles: List<String>,
        allowedExtensions: List<String>
    ): Resource {
        if (PlagConfigService.isResourceUpdating(
                plagConfigName = plagConfigName,
                projectName = projectName,
                resourcePath = resource.path,
                userName = userName
        )) {
            throw IllegalStateException("Resource is already being updated for PlagConfig.")
        }
        PlagConfigService.setResourceUpdating(
            plagConfigName = plagConfigName,
            projectName = projectName,
            resourcePath = resource.path,
            userName = userName,
            isUpdating = true
        )
        val resourceFiles = File(resource.path)
        val previousResourceHash = resource.hash
        if (resourceFiles.exists()) resourceFiles.deleteRecursively()

        resourceFiles.mkdirs()

        return try {
            gitRepositorySolutionProvider.saveRepoToResources(
                repoUrl = resource.repoURL,
                branch = resource.branch,
                zipFile = null,
                disallowedFiles = disallowedFiles,
                allowedExtensions = allowedExtensions,
                saveFile = resourceFiles
            )
            val newPulledResourceHash = calculateFileHash(zipDirectory(resourceFiles.absolutePath))
            val isResourceUpdated = previousResourceHash != newPulledResourceHash
            Resource(
                path = resourceFiles.path,
                exceptionMessage = null,
                resourceType = resource.resourceType,
                lastUpdate = Date(),
                academicYear = resource.academicYear,
                hash = newPulledResourceHash,
                hasBeenChanged = isResourceUpdated,
                edgarHash = null
            )
        } catch (e: Exception) {
            Resource(
                path = resourceFiles.path,
                exceptionMessage = e.message,
                resourceType = resource.resourceType,
                lastUpdate = null,
                academicYear = resource.academicYear,
                hash = null,
                hasBeenChanged = false,
                edgarHash = null
            )
        } finally {
            PlagConfigService.setResourceUpdating(
                plagConfigName = plagConfigName,
                projectName = projectName,
                resourcePath = resource.path,
                userName = userName,
                isUpdating = false
            )
        }
    }

    fun updateEdgarResource(resource: Resource, projectName: String, plagConfigName: String, userName: String, subjectId: String, allowedExtensions: List<String>, disallowedFiles: List<String>): Resource {
        if (PlagConfigService.isResourceUpdating(
                plagConfigName = plagConfigName,
                projectName = projectName,
                resourcePath = resource.path,
                userName = userName
        )) {
            throw IllegalStateException("Resource is already being updated for PlagConfig.")
        }
        PlagConfigService.setResourceUpdating(
            plagConfigName = plagConfigName,
            projectName = projectName,
            resourcePath = resource.path,
            userName = userName,
            isUpdating = true
        )
        val resourceFiles = File(resource.path)
        val previousResourceHash = resource.hash
        if (resourceFiles.exists()) resourceFiles.deleteRecursively()

        resourceFiles.mkdirs()
        return try {
            val academicYear = resource.academicYear.split("-")[0].toInt()
            val edgarHash = clientServiceCommunicator.getHashForProject(
                subjectId = subjectId,
                academicYear = academicYear,
                projectName = projectName
            )
            val zipFile = getZipFromEdgar(
                resourceType = resource.resourceType,
                edgarHash = edgarHash
            )
            if (zipFile == null) {
                throw IllegalStateException("Neuspjelo preuzimanje ZIP datoteke s Edgara.")
            }
            localSolutionProvider.saveRepoToResources(
                repoUrl = null,
                branch = null,
                zipFile = zipFile,
                disallowedFiles = disallowedFiles,
                saveFile = resourceFiles,
                allowedExtensions = allowedExtensions
            )

            val newPulledResourceHash = calculateFileHash(zipDirectory(resourceFiles.absolutePath))
            val isResourceUpdated = previousResourceHash != newPulledResourceHash
            Resource(
                path = resourceFiles.path,
                exceptionMessage = null,
                resourceType = resource.resourceType,
                lastUpdate = Date(),
                academicYear = resource.academicYear,
                hash = newPulledResourceHash,
                hasBeenChanged = isResourceUpdated,
                edgarHash = edgarHash
            )
        } catch (e: Exception) {
            Resource(
                path = resourceFiles.path,
                exceptionMessage = e.message,
                resourceType = resource.resourceType,
                lastUpdate = null,
                academicYear = resource.academicYear,
                hash = null,
                hasBeenChanged = false,
                edgarHash = resource.edgarHash
            )
        } finally {
            PlagConfigService.setResourceUpdating(
                plagConfigName = plagConfigName,
                projectName = projectName,
                resourcePath = resource.path,
                userName = userName,
                isUpdating = false
            )
        }
    }

    fun updateEdgarWhitelistResource(resource: Resource, projectName: String, plagConfigName: String, userName: String, subjectId: String, disallowedFiles: List<String>, allowedExtensions: List<String>): Resource {
        if (PlagConfigService.isResourceUpdating(
                plagConfigName = plagConfigName,
                projectName = projectName,
                resourcePath = resource.path,
                userName = userName
            )) {
            throw IllegalStateException("Resource is already being updated for PlagConfig: $plagConfigName.")
        }
        PlagConfigService.setResourceUpdating(
            plagConfigName = plagConfigName,
            projectName = projectName,
            resourcePath = resource.path,
            userName = userName,
            isUpdating = true
        )
        val resourceFiles = File(resource.path)
        val previousResourceHash = resource.hash
        if (resourceFiles.exists()) resourceFiles.deleteRecursively()

        resourceFiles.mkdirs()

        return try {
            val zipFile = null
            if (zipFile == null) {
                throw IllegalStateException("Neuspjelo preuzimanje ZIP datoteke s Edgara.")
            }
            localSolutionProvider.saveRepoToResources(
                repoUrl = null,
                branch = null,
                zipFile = zipFile,
                disallowedFiles = disallowedFiles,
                allowedExtensions = allowedExtensions,
                saveFile = resourceFiles
            )
            val newPulledResourceHash = calculateFileHash(zipDirectory(resourceFiles.absolutePath))
            val isResourceUpdated = previousResourceHash != newPulledResourceHash
            Resource(
                path = resourceFiles.path,
                exceptionMessage = null,
                resourceType = resource.resourceType,
                lastUpdate = Date(),
                academicYear = resource.academicYear,
                hash = newPulledResourceHash,
                hasBeenChanged = isResourceUpdated,
                edgarHash = null
            )
        } catch (e: Exception) {
            Resource(
                path = resourceFiles.path,
                exceptionMessage = e.message,
                resourceType = resource.resourceType,
                lastUpdate = null,
                academicYear = resource.academicYear,
                hash = null,
                hasBeenChanged = false,
                edgarHash = resource.edgarHash
            )
        } finally {
            PlagConfigService.setResourceUpdating(
                plagConfigName = plagConfigName,
                projectName = projectName,
                resourcePath = resource.path,
                userName = userName,
                isUpdating = false
            )
        }
    }

    private fun calculateFileHash(filePath: Path): String {
        val messageDigest = MessageDigest.getInstance("SHA-256")
        FileInputStream(filePath.toFile()).use { fis ->
            val buffer = ByteArray(1024)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                messageDigest.update(buffer, 0, bytesRead)
            }
        }
        val hashBytes = messageDigest.digest()
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun zipDirectory(sourceDirPath: String): Path {
        val sourceDir = File(sourceDirPath)
        val tempZipFile = Files.createTempFile("tempDirectory", ".zip")
        ZipOutputStream(Files.newOutputStream(tempZipFile)).use { zipOut ->
            sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val relativePath = sourceDir.toPath().relativize(file.toPath()).toString()
                val zipEntry = ZipEntry(relativePath)
                zipEntry.time = 0L

                zipOut.putNextEntry(zipEntry)
                Files.copy(file.toPath(), zipOut)
                zipOut.closeEntry()
            }
        }
        return tempZipFile
    }

    private fun getZipFromServer(subject: String, academicYear: String, projectName: String): MultipartFile? {
        val edgarResourcePath = "https://edgar.fer.hr/edgar/$subject/$academicYear/$projectName"
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(edgarResourcePath)
            .build()

        return try {
            val response = client.newCall(request).execute() 
            if (!response.isSuccessful) {
                throw RuntimeException("Failed to fetch ZIP file. \nErrorCode: ${response.code} \nResponse body from Edgar: ${response.body}")
            }

            val responseBody = response.body ?: throw RuntimeException("Failed to fetch ZIP file. \nErrorCode: ${response.code} \nResponse body is empty.")
            val bytes = responseBody.bytes()

            MockMultipartFile("file", "$projectName.zip", "application/zip", bytes)
        } catch (e: Exception) {
            throw RuntimeException("Failed to fetch ZIP file. \n${e.message}.")
        }
    }

    private fun getZipFromEdgar(edgarHash: String, resourceType: ResourceType): MultipartFile? {
//        return try {
//            val filePath = "/Users/ibilobrk/razno/PlagDetection/src/main/resources/ibilobrk/opeist2/Operacijska istraživanja/1.-laboratorijska-vježba/2023-2024/EDGAR-2023-2024/ultra.zip"
//                val file = File(filePath)
//                val fileInputStream = FileInputStream(file)
//                val bytes = fileInputStream.readBytes()
//                fileInputStream.close()
//
//                MockMultipartFile("file", file.name, "application/zip", bytes)
//            } catch (e: IOException) {
//                throw RuntimeException("Failed to read ZIP file from local path. \n${e.message}.")
//            }
        if (resourceType == ResourceType.WHITELIST) {
            return null
        }
        return clientServiceCommunicator.getZipAttachmentsForProject(edgarHash)
//        if (academicYear == "2023-2024" && resourceType != ResourceType.WHITELIST) {
//            val filePath = "/Users/ibilobrk/razno/PlagDetection/src/main/resources/ibilobrk/solutions/arhiva1.zip"
//            return try {
//                val file = File(filePath)
//                val fileInputStream = FileInputStream(file)
//                val bytes = fileInputStream.readBytes()
//                fileInputStream.close()
//
//                MockMultipartFile("file", file.name, "application/zip", bytes)
//            } catch (e: IOException) {
//                throw RuntimeException("Failed to read ZIP file from local path. \n${e.message}.")
//            }
//        } else if (academicYear == "2024-2025" && resourceType != ResourceType.WHITELIST) {
//            val filePath = "/Users/ibilobrk/razno/PlagDetection/src/main/resources/ibilobrk/solutions/arhiva2.zip"
//            return try {
//                val file = File(filePath)
//                val fileInputStream = FileInputStream(file)
//                val bytes = fileInputStream.readBytes()
//                fileInputStream.close()
//
//                MockMultipartFile("file", file.name, "application/zip", bytes)
//            } catch (e: IOException) {
//                throw RuntimeException("Failed to read ZIP file from local path. \n${e.message}.")
//            }
//        } else if (academicYear == "2024-2025") {
//            val filePath = "/Users/ibilobrk/razno/PlagDetection/src/main/resources/ibilobrk/solutions/whitelist.zip"
//            return try {
//                val file = File(filePath)
//                val fileInputStream = FileInputStream(file)
//                val bytes = fileInputStream.readBytes()
//                fileInputStream.close()
//
//                MockMultipartFile("file", file.name, "application/zip", bytes)
//            } catch (e: IOException) {
//                throw RuntimeException("Failed to read ZIP file from local path. \n${e.message}.")
//            }
        }
}
