package com.fer.projekt.service

import com.fer.projekt.config.PlagConfigExecutorServiceConfig
import com.fer.projekt.controller.ManuallyAddAIResourceRequest
import com.fer.projekt.controller.ManuallyAddGitResourceRequest
import com.fer.projekt.controller.ManuallyAddZipResourceRequest
import com.fer.projekt.fileproviders.FileUtils
import com.fer.projekt.fileproviders.disallowedExtensions
import com.fer.projekt.models.*
import com.fer.projekt.repository.PlagConfigRepository
import com.fer.projekt.solutionproviders.ClientServiceCommunicator
import com.fer.projekt.solutionproviders.GitRepositorySolutionProvider
import com.fer.projekt.solutionproviders.LocalSolutionProvider
import com.fer.projekt.solutionproviders.ResourceProvider
import org.springframework.stereotype.Service
import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDate
import java.time.Month
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit


@Service
class PlagConfigService(
    private val plagConfigRepository: PlagConfigRepository,
    private val plagConfigExecutorServiceConfig: PlagConfigExecutorServiceConfig,
    private val fileUtils: FileUtils,
    private val localSolutionProvider: LocalSolutionProvider,
    private val resourceProvider: ResourceProvider,
    private val gitRepositorySolutionProvider: GitRepositorySolutionProvider,
    private val clientServiceCommunicator: ClientServiceCommunicator
) {

    fun updateResourceForProject(
        projectName: String,
        userName: String,
        plagConfigName: String,
        resourcePath: String
    ) {
        if (isResourceUpdating(plagConfigName = plagConfigName, projectName = projectName, resourcePath = resourcePath, userName = userName)) {
            throw IllegalStateException("Resource is already being updated for PlagConfig.")
        }
        val plagConfig = plagConfigRepository.findByUserNameAndName(
            name = plagConfigName,
            userName = userName
        ) ?: throw IllegalArgumentException("PlagConfig not found for user $userName and name $plagConfigName")

        val project = plagConfig.projects.find { it.name == projectName }
            ?: throw IllegalArgumentException("Project not found for name $projectName")

        plagConfigExecutorServiceConfig.plagConfigExecutorService().submit {
            try {
                resourceProvider.updateResourceManually(
                    resource = project.resources.find { it.path == resourcePath }!!,
                    projectName = projectName,
                    plagConfig = plagConfig
                )
            } finally {
            }
        }
    }

    fun getPlagConfig(
        username: String,
        configName: String
    ): PlagConfigDTO {
        val plagConfig = plagConfigRepository.findByUserNameAndName(
            userName = username,
            name = configName
        )
        val numberOfSubmissions: Map<String, Int> = plagConfig?.projects?.associate { project ->
            project.name to fileUtils.getNumberOfSubmissions(project.resources)
        } ?: emptyMap()
        return PlagConfigDTO(
            plagConfig = plagConfig ?: throw IllegalArgumentException("PlagConfig not found for user $username and name $configName"),
            numberOfSubmissions = numberOfSubmissions
        )
    }

    fun getAllPlagConfigsForUser(
        userName: String
    ): List<PlagConfig> {
        return plagConfigRepository.findByUserName(userName)
    }

    fun createBasicPlagConfig(
        userName: String,
        name: String,
        subjectName: String,
        resourcesTTL: Int?,
        clientBackendURL: String,
        suffixes: Set<String>?,
        languages: Set<String>,
        scheduleType: ScheduleType,
        subjetId: String,
        disallowedFiles: Set<String>,
    ): PlagConfig {
        val academicYears = getAcademicYears(resourcesTTL ?: DEFAULT_RESOURCES_TTL)
        val studentProjects = mutableListOf<StudentProject>()
        val projectsForCurrentAcademicYear = clientServiceCommunicator.getListOfProjectsForCurrentAcademicYear(subjetId)

        projectsForCurrentAcademicYear.forEach { projectName ->
            studentProjects.add(
                createEdgarProjectForAcademicYears(
                    academicYears = academicYears,
                    projectName = projectName,
                    suffixes = suffixes ?: emptySet(),
                    languages = languages,
                    userName = userName,
                    subjectName = subjectName,
                    configName = name,
                    subjectId = subjetId
                )
            )
        }
        val plagConfig = PlagConfig(
            name = name,
            subjectName = subjectName,
            resourcesTTL = resourcesTTL?.toLong() ?: DEFAULT_RESOURCES_TTL.toLong(),
            clientBackendURL = clientBackendURL,
            projects = studentProjects,
            scheduleType = scheduleType,
            userName = userName,
            suffixes = suffixes ?: emptySet(),
            languages = languages,
            subjectId = subjetId,
            disallowedFiles = disallowedFiles
        )

        updatePlagConfigResources(plagConfigRepository.save(plagConfig))
        return plagConfig
    }

    fun createEdgarProjectForAcademicYears(
        academicYears: Map<Int, String>,
        projectName: String,
        suffixes: Set<String>,
        languages: Set<String>,
        userName: String,
        subjectName: String,
        configName: String,
        subjectId: String,
    ): StudentProject {
        val resources = mutableListOf<Resource>()

        academicYears.forEach { (_, academicYear) ->
            resources.add(
                createResource(
                    userName = userName,
                    academicYear = academicYear,
                    projectName = projectName,
                    resourceType = ResourceType.EDGAR,
                    subjectName = subjectName,
                    configName = configName,
                    edgarHash = null
                )
            )
        }
        resources.add(createWhitelistResource(
            userName = userName,
            academicYear = academicYears.values.first(),
            projectName = projectName,
            subjectName = subjectName,
            configName = configName,
            edgarHash = null
        ))
        return StudentProject(
            name = projectName,
            resources = resources,
            suffixes = suffixes,
            languages = languages,
            resultHashes = emptySet()
        )
    }

    private fun createWhitelistResource(
        userName: String,
        academicYear: String,
        projectName: String,
        subjectName: String,
        configName: String,
        edgarHash: String?
    ): Resource {
        val resourcePath = FileUtils.getRepoConfigFile(
            subject = subjectName,
            userName = userName,
            academicYear = academicYear,
            projectName = projectName,
            configName = configName,
            resourceType = ResourceType.WHITELIST
        ).absolutePath
        return Resource(
            resourceType = ResourceType.WHITELIST,
            lastUpdate = null,
            path = resourcePath,
            academicYear = academicYear,
            edgarHash = edgarHash
        )
    }

    private fun createResource(
        userName: String,
        academicYear: String,
        projectName: String,
        resourceType: ResourceType,
        subjectName: String,
        configName: String,
        edgarHash: String?
    ): Resource {
        val resourcePath = FileUtils.getRepoConfigFile(
            subject = subjectName,
            userName = userName,
            academicYear = academicYear,
            projectName = projectName,
            configName = configName,
            resourceType = resourceType
        ).absolutePath
        return Resource(
            resourceType = resourceType,
            lastUpdate = null,
            path = resourcePath,
            academicYear = academicYear,
            edgarHash = edgarHash
        )
    }

    fun getAcademicYears(numberOfYears: Int): Map<Int, String> {
        val academicYearsMap = mutableMapOf<Int, String>()
        val today = LocalDate.now()
        var currentYear = today.year

        if (today.isBefore(LocalDate.of(currentYear, Month.OCTOBER, 1))) {
            currentYear -= 1
        }

        for (i in 0..numberOfYears) {
            val startYear = currentYear - i
            val endYear = startYear + 1
            academicYearsMap[i] = "$startYear-$endYear"
        }

        return academicYearsMap
    }

    fun updatePlagConfigResources(plagConfig: PlagConfig) {
        plagConfigExecutorServiceConfig.plagConfigExecutorService().submit(
            ResourceDownloadTask(
                plagConfigRepository,
                resourceProvider,
                plagConfig,
            )
        )
    }

    fun deleteResourceFromProject(
        projectName: String,
        userName: String,
        plagConfigName: String,
        resourcePath: String
    ) {
        val plagConfig = plagConfigRepository.findByUserNameAndName(
            name = plagConfigName,
            userName = userName
        ) ?: throw IllegalArgumentException("PlagConfig not found for user $userName and name $plagConfigName")

        val updatedProjects = plagConfig.projects.map { project ->
            if (project.name == projectName)
                project.copy(resources = project.resources.filter { it.path != resourcePath })
            else
                project
        }

        val updatedPlagConfig = plagConfig.copy(projects = updatedProjects)
        plagConfigRepository.save(updatedPlagConfig)

        val resourceFiles = File(resourcePath)
        if (resourceFiles.exists()) {
            resourceFiles.deleteRecursively()
        }
    }

    fun addGitResourceToProject(
        manuallyAddGitResourceRequest: ManuallyAddGitResourceRequest
    ) {
        val plagConfig = plagConfigRepository.findByUserNameAndName(
            name = manuallyAddGitResourceRequest.configName,
            userName = manuallyAddGitResourceRequest.userName
        ) ?: throw IllegalArgumentException("PlagConfig not found for user ${manuallyAddGitResourceRequest.userName} and name ${manuallyAddGitResourceRequest.configName}")

        val existingProject = plagConfig.projects.find { it.name == manuallyAddGitResourceRequest.projectName }
            ?: throw IllegalArgumentException("Project not found for name ${manuallyAddGitResourceRequest.projectName}")

        val resourcePath = FileUtils.getRepoConfigFile(
            subject = plagConfig.subjectName,
            userName = manuallyAddGitResourceRequest.userName,
            academicYear = manuallyAddGitResourceRequest.academicYear!!,
            projectName = manuallyAddGitResourceRequest.projectName,
            configName = manuallyAddGitResourceRequest.configName,
            resourceType = manuallyAddGitResourceRequest.resourceType
        ).absolutePath

        if (existingProject.resources.any { it.path == resourcePath }) {
            val tempSavePath = resourcePath + "temp"
            val resourceFiles = File(tempSavePath)
            if (resourceFiles.exists()) resourceFiles.deleteRecursively()
            resourceFiles.mkdirs()
            gitRepositorySolutionProvider.saveRepoToResources(
                repoUrl = manuallyAddGitResourceRequest.repoURL,
                branch = manuallyAddGitResourceRequest.branch,
                zipFile = null,
                disallowedFiles = manuallyAddGitResourceRequest.disallowedFiles ?: emptyList(),
                saveFile = resourceFiles,
                allowedExtensions = manuallyAddGitResourceRequest.allowedExtensions ?: emptyList()
            )
            mergeDirectories(
                sourceDir = Paths.get(tempSavePath),
                targetDir = Paths.get(resourcePath)
            )
            resourceFiles.deleteRecursively()
            return
        }

        val resourceFiles = File(resourcePath)
        if (resourceFiles.exists()) resourceFiles.deleteRecursively()
        resourceFiles.mkdirs()

        gitRepositorySolutionProvider.saveRepoToResources(
            repoUrl = manuallyAddGitResourceRequest.repoURL,
            branch = manuallyAddGitResourceRequest.branch,
            zipFile = null,
            disallowedFiles = manuallyAddGitResourceRequest.disallowedFiles ?: emptyList(),
            saveFile = resourceFiles,
            allowedExtensions = manuallyAddGitResourceRequest.allowedExtensions ?: emptyList()
        )

        val resource = Resource(
            resourceType = ResourceType.GIT,
            lastUpdate = null,
            path = resourcePath,
            academicYear = manuallyAddGitResourceRequest.academicYear,
            hasBeenChanged = true,
            repoURL = manuallyAddGitResourceRequest.repoURL,
            branch = manuallyAddGitResourceRequest.branch,
            edgarHash = null
        )

        val updatedProjects = plagConfig.projects.map { project ->
            if (project.name == manuallyAddGitResourceRequest.projectName)
                project.copy(resources = project.resources + resource)
            else
                project
        }

        val updatedPlagConfig = plagConfig.copy(projects = updatedProjects)
        plagConfigRepository.save(updatedPlagConfig)
    }

    fun addZipResourceToProject(
        manuallyAddZipResourceRequest: ManuallyAddZipResourceRequest
    ) {
        val plagConfig = plagConfigRepository.findByUserNameAndName(
            name = manuallyAddZipResourceRequest.configName,
            userName = manuallyAddZipResourceRequest.userName
        ) ?: throw IllegalArgumentException("PlagConfig not found for user ${manuallyAddZipResourceRequest.userName} and name ${manuallyAddZipResourceRequest.configName}")

        val existingProject = plagConfig.projects.find { it.name == manuallyAddZipResourceRequest.projectName }
            ?: throw IllegalArgumentException("Project not found for name ${manuallyAddZipResourceRequest.projectName}")

        val resourcePath = FileUtils.getRepoConfigFile(
            subject = plagConfig.subjectName,
            userName = manuallyAddZipResourceRequest.userName,
            academicYear = manuallyAddZipResourceRequest.academicYear!!,
            projectName = manuallyAddZipResourceRequest.projectName,
            configName = manuallyAddZipResourceRequest.configName,
            resourceType = manuallyAddZipResourceRequest.resourceType
        ).absolutePath

        if (existingProject.resources.any { it.path == resourcePath }) {
            val tempSavePath = resourcePath + "temp"
            val resourceFiles = File(tempSavePath)
            if (resourceFiles.exists()) resourceFiles.deleteRecursively()
            resourceFiles.mkdirs()
            localSolutionProvider.saveRepoToResources(
                repoUrl = null,
                branch = null,
                zipFile = manuallyAddZipResourceRequest.zipFile,
                disallowedFiles = manuallyAddZipResourceRequest.disallowedFiles ?: emptyList(),
                saveFile = resourceFiles,
                allowedExtensions = manuallyAddZipResourceRequest.allowedExtensions ?: emptyList()
            )
            mergeDirectories(
                sourceDir = Paths.get(tempSavePath),
                targetDir = Paths.get(resourcePath)
            )
            resourceFiles.deleteRecursively()
            return
        }

        val resourceFiles = File(resourcePath)
        if (resourceFiles.exists()) resourceFiles.deleteRecursively()
        resourceFiles.mkdirs()

        localSolutionProvider.saveRepoToResources(
            repoUrl = null,
            branch = null,
            zipFile = manuallyAddZipResourceRequest.zipFile,
            disallowedFiles = manuallyAddZipResourceRequest.disallowedFiles ?: emptyList(),
            saveFile = resourceFiles,
            allowedExtensions = manuallyAddZipResourceRequest.allowedExtensions ?: emptyList()
        )

        val resource = Resource(
            resourceType = ResourceType.ZIP,
            lastUpdate = null,
            path = resourcePath,
            academicYear = manuallyAddZipResourceRequest.academicYear,
            hasBeenChanged = true,
            edgarHash = null
        )

        val updatedProjects = plagConfig.projects.map { project ->
            if (project.name == manuallyAddZipResourceRequest.projectName)
                project.copy(resources = project.resources + resource)
            else
                project
        }

        val updatedPlagConfig = plagConfig.copy(projects = updatedProjects)
        plagConfigRepository.save(updatedPlagConfig)
    }

    fun addAIResourceToProject(
        manuallyAddAIResourceRequest: ManuallyAddAIResourceRequest
    ) {
        var plagConfig = plagConfigRepository.findByUserNameAndName(
            name = manuallyAddAIResourceRequest.configName,
            userName = manuallyAddAIResourceRequest.userName
        ) ?: throw IllegalArgumentException("PlagConfig not found for user ${manuallyAddAIResourceRequest.userName} and name ${manuallyAddAIResourceRequest.configName}")

        val resourcePath = FileUtils.getRepoConfigFile(
            subject = plagConfig.subjectName,
            userName = manuallyAddAIResourceRequest.userName,
            academicYear = getCurrentAcademicYear(Date()),
            projectName = manuallyAddAIResourceRequest.projectName,
            configName = manuallyAddAIResourceRequest.configName,
            resourceType = manuallyAddAIResourceRequest.resourceType
        ).absolutePath

        val resourceFiles = File(resourcePath)
        if (resourceFiles.exists()) {
            resourceFiles.deleteRecursively()
            plagConfig = plagConfig.copy(
                projects = plagConfig.projects.map { project ->
                    if (project.name == manuallyAddAIResourceRequest.projectName)
                        project.copy(resources = project.resources.filter { it.path != resourcePath })
                    else
                        project
                }
            )
        }
        resourceFiles.mkdirs()

        localSolutionProvider.saveAIResource(
            saveFile = resourceFiles,
            aiSolution = clientServiceCommunicator.getAISolution(manuallyAddAIResourceRequest.taskText, manuallyAddAIResourceRequest.language),
            language = manuallyAddAIResourceRequest.language,
        )

        val resource = Resource(
            resourceType = ResourceType.AI,
            lastUpdate = null,
            path = resourcePath,
            academicYear = getCurrentAcademicYear(Date()),
            hasBeenChanged = true,
            taskText = manuallyAddAIResourceRequest.taskText,
            edgarHash = null
        )

        val updatedProjects = plagConfig.projects.map { project ->
            if (project.name == manuallyAddAIResourceRequest.projectName)
                project.copy(resources = project.resources + resource)
            else
                project
        }

        val updatedPlagConfig = plagConfig.copy(projects = updatedProjects)
        plagConfigRepository.save(updatedPlagConfig)
    }

    fun mergeDirectories(sourceDir: Path, targetDir: Path) {
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir)
        }

        Files.walkFileTree(sourceDir, object : SimpleFileVisitor<Path>(), FileVisitor<Path> {
            @Throws(IOException::class)
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val targetFile = targetDir.resolve(sourceDir.relativize(file))
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING)
                return FileVisitResult.CONTINUE
            }

            @Throws(IOException::class)
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val targetDir = targetDir.resolve(sourceDir.relativize(dir))
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir)
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path?, exc: IOException): FileVisitResult {
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path?, exc: IOException?): FileVisitResult {
                return FileVisitResult.CONTINUE
            }
        })
    }

    companion object {
        const val DEFAULT_RESOURCES_TTL = 2
        private val currentlyUpdatingResources = ConcurrentHashMap<String, Boolean>()

        fun calculateTimeDifference(lastUpdate: Date, currentTime: Date): Int {
            val diffInMillis = currentTime.time - lastUpdate.time
            return TimeUnit.MILLISECONDS.toDays(diffInMillis).toInt()
        }

        fun getCurrentAcademicYear(date: Date): String {
            val calendar = Calendar.getInstance()
            calendar.time = date
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1
            return if (month < 10) {
                "${year - 1}-$year"
            } else {
                "$year-${year + 1}"
            }
        }

        fun isResourceUpdating(plagConfigName: String, projectName: String, resourcePath: String, userName: String): Boolean {
            return currentlyUpdatingResources["$plagConfigName-$projectName-$resourcePath-$userName"] ?: false
        }
        fun setResourceUpdating(plagConfigName: String, isUpdating: Boolean, projectName: String, resourcePath: String, userName: String) {
            currentlyUpdatingResources["$plagConfigName-$projectName-$resourcePath-$userName"] = isUpdating
        }
    }
}
