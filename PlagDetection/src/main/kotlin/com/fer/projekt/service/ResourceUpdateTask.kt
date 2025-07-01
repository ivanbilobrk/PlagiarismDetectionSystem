package com.fer.projekt.service

import com.fer.projekt.models.PlagConfig
import com.fer.projekt.models.Resource
import com.fer.projekt.repository.PlagConfigRepository
import com.fer.projekt.solutionproviders.ResourceProvider
import java.util.*

class ResourceUpdateTask(
    private val plagConfigRepository: PlagConfigRepository,
    private val resourceProvider: ResourceProvider,
    private val plagConfig: PlagConfig,
    private val projectName: String,
    private val resource: Resource?
) : Runnable {

    override fun run() {
        val updatedResource = resourceProvider.updateResourceBySchedule(
            resource = resource!!,
            projectName = projectName,
            plagConfig = plagConfig
        )
        val updatedResources = plagConfig.projects.flatMap { it.resources }.map { if (it == resource) updatedResource else it }
        val updatedProjects = plagConfig.projects.map { it.copy(resources = updatedResources) }
        val updatedPlagConfig = plagConfig.copy(projects = updatedProjects)
        plagConfigRepository.save(updatedPlagConfig)
        return
    }
}
