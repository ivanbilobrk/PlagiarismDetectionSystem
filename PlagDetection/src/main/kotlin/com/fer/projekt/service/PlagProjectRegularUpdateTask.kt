package com.fer.projekt.service

import com.fer.projekt.models.PlagConfig
import com.fer.projekt.models.StudentProject
import com.fer.projekt.repository.PlagConfigRepository
import com.fer.projekt.solutionproviders.ResourceProvider
import java.util.*
import java.util.concurrent.Callable

class PlagProjectRegularUpdateTask(
    private val plagConfig: PlagConfig,
    private val plagConfigRepository: PlagConfigRepository,
    private val resourceProvider: ResourceProvider
    ): Callable <List<StudentProject>> {

    override fun call(): List<StudentProject> {
        val updatedProjects = plagConfig.projects.map { project ->
            val updatedResources = project.resources.map { resource ->
                val updatedResource = resourceProvider.updateResourceBySchedule(
                    resource = resource,
                    projectName = project.name,
                    plagConfig = plagConfig
                )
                updatedResource
            }
            project.copy(resources = updatedResources)
        }
        val updatedPlagConfig = plagConfig.copy(projects = updatedProjects)
        val projectsWithUpdatedResources = updatedPlagConfig.projects.filter { project ->
            project.resources.any { resource ->
                resource.hasBeenChanged == true
            }
        }
        plagConfigRepository.save(updatedPlagConfig)
        return projectsWithUpdatedResources
    }
}
