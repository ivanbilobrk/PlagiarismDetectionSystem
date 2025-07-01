package com.fer.projekt.service

import com.fer.projekt.config.PlagConfigExecutorServiceConfig
import com.fer.projekt.models.PlagConfig
import com.fer.projekt.models.StudentProject
import com.fer.projekt.repository.PlagConfigRepository
import com.fer.projekt.solutionproviders.ClientServiceCommunicator
import com.fer.projekt.solutionproviders.ResourceProvider
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.Date

@Service
class AutomaticResourceUpdater(
    private val plagConfigRepository: PlagConfigRepository,
    private val resourceProvider: ResourceProvider,
    private val plagConfigExecutorServiceConfig: PlagConfigExecutorServiceConfig,
    private val jPlagService: JPlagService,
    private val clientServiceCommunicator: ClientServiceCommunicator,
    private val plagConfigService: PlagConfigService,
    private val jPlagMongoService: JPlagMongoService
) {

    private val log = KotlinLogging.logger {}

    //@Scheduled(cron = "0 0 0 * * *")
    //@Scheduled(cron = "0 * * * * *")
    //@Scheduled(cron = "*/10 * * * * *")
    //@Scheduled(cron = "0 */3 * * * *")
    @Scheduled(cron = "0 22 14 * * *")
    fun runDailyTask() {
        log.info { "Running daily task to update resources" }
        CancellationManager.reset()
        val plagConfigs: List<PlagConfig> = plagConfigRepository.findAll()
        for (plagConfig in plagConfigs) {
            if (!(plagConfig.lastUpdateTime == null
                || PlagConfigService.calculateTimeDifference(plagConfig.lastUpdateTime, Date()) >= plagConfig.scheduleType.days)) {
                continue
            }
            val updatedPlagConfig = plagConfigRepository.save(plagConfig.copy(lastUpdateTime = Date()))
            val currentProjects = updatedPlagConfig.projects
            val newProjects = clientServiceCommunicator.getListOfProjectsForCurrentAcademicYear(updatedPlagConfig.subjectName)

            val projectsToAdd = newProjects
                .filter { newProject -> currentProjects.none { currentProject -> currentProject.name == newProject } }
                .takeIf { it.isNotEmpty() }
                ?.map { projectName ->
                    val academicYears = plagConfigService.getAcademicYears(updatedPlagConfig.resourcesTTL.toInt())
                    plagConfigService.createEdgarProjectForAcademicYears(
                        academicYears = academicYears,
                        projectName = projectName,
                        userName = updatedPlagConfig.userName,
                        suffixes = updatedPlagConfig.suffixes,
                        subjectName = updatedPlagConfig.subjectName,
                        configName = updatedPlagConfig.name,
                        languages = updatedPlagConfig.languages,
                        subjectId = updatedPlagConfig.subjectId,
                    )
                }
                ?: emptyList()

            if (projectsToAdd.isNotEmpty()) {
                val updatedPlagConfig = updatedPlagConfig.copy(
                    projects = currentProjects + projectsToAdd
                )
                log.info { "Adding new projects to plag config ${plagConfig.name} for user ${plagConfig.userName}" }
                val savedPlagConfig = plagConfigRepository.save(updatedPlagConfig)
                runJplagForConfig(savedPlagConfig)
            } else {
                runJplagForConfig(updatedPlagConfig)
            }
        }
    }

    private fun runJplagForConfig(plagConfig: PlagConfig) {
        plagConfigExecutorServiceConfig.plagConfigExecutorService().submit({
            val plagProjectRegularUpdateTask = PlagProjectRegularUpdateTask(
                plagConfig = plagConfig,
                plagConfigRepository = plagConfigRepository,
                resourceProvider = resourceProvider
            )
            val updatedProjects = plagProjectRegularUpdateTask.call()
            log.info { "Updated ${updatedProjects.size} projects for user ${plagConfig.userName}" }

            val firstRunProjects = plagConfig.projects.filter { it.firstRun }

            if (updatedProjects.isEmpty() && firstRunProjects.isEmpty()) {
                return@submit
            }

            runJplagForProjects(
                projects = (updatedProjects + firstRunProjects).toSet(),
                plagConfig = plagConfig
            )
        })
    }

    private fun runJplagForProjects(projects: Set<StudentProject>, plagConfig: PlagConfig) {
        projects.forEach { project ->
            try {
                log.info { "Running JPlag for user ${plagConfig.userName} and project ${project.name}" }
                jPlagService.runPlagNew(
                    configName = plagConfig.name,
                    projectName = project.name,
                    userName = plagConfig.userName,
                    firstRun = project.firstRun
                )
            } catch (e: Exception) {
                log.error { "Error while running plag detection for project ${project.name} for user ${plagConfig.userName}: ${e.message}" }
            }
        }
    }

    @Scheduled(cron = "0 0 5 * * *")
    fun killSwitchJob() {
        CancellationManager.cancel()
    }
}
