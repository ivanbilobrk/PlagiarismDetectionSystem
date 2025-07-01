package com.fer.projekt.service

import com.fer.projekt.models.PlagConfig
import com.fer.projekt.models.Resource
import com.fer.projekt.models.ResourceType
import com.fer.projekt.repository.PlagConfigRepository
import com.fer.projekt.solutionproviders.ResourceProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class ResourceDownloadTask @Autowired constructor(
    private val plagConfigRepository: PlagConfigRepository,
    private val resourceProvider: ResourceProvider,
) : Runnable {

    private lateinit var plagConfig: PlagConfig

    constructor(
        plagConfigRepository: PlagConfigRepository,
        resourceProvider: ResourceProvider,
        plagConfig: PlagConfig,
    ) : this(plagConfigRepository, resourceProvider) {
        this.plagConfig = plagConfig
    }

    override fun run() {
        val updatedProjects = plagConfig.projects.map { project ->
            val updatedResources = project.resources.map { resource ->
                val updatedResource = downloadResource(
                    resource = resource,
                    projectName = project.name,
                    plagConfigName = plagConfig.name,
                    userName = plagConfig.userName,
                    allowedExtensions = plagConfig.suffixes.toList(),
                    disallowedFiles = plagConfig.disallowedFiles.toList()
                )
                updatedResource
            }
            project.copy(resources = updatedResources)
        }
        val updatedPlagConfig = plagConfig.copy(projects = updatedProjects)
        plagConfigRepository.save(updatedPlagConfig)
    }

    private fun downloadResource(resource: Resource, projectName: String, plagConfigName: String, userName: String, allowedExtensions: List<String>, disallowedFiles: List<String>): Resource {
        val lastUpdateTime = resource.lastUpdate
        if (lastUpdateTime == null) {
            return when (resource.resourceType) {
                ResourceType.EDGAR -> {
                    resourceProvider.updateEdgarResource(
                        resource = resource,
                        projectName = projectName,
                        plagConfigName = plagConfigName,
                        userName = userName,
                        subjectId = plagConfig.subjectId,
                        allowedExtensions = allowedExtensions,
                        disallowedFiles = disallowedFiles
                    )
                }
                ResourceType.WHITELIST -> {
                    resourceProvider.updateEdgarWhitelistResource(
                        resource = resource,
                        projectName = projectName,
                        plagConfigName = plagConfig.name,
                        userName = plagConfig.userName,
                        subjectId = plagConfig.subjectId,
                        allowedExtensions = allowedExtensions,
                        disallowedFiles = disallowedFiles
                    )
                }
                ResourceType.GIT -> TODO()
                ResourceType.ZIP -> TODO()
                ResourceType.AI -> TODO()
            }
        }
        return resource
    }
}
